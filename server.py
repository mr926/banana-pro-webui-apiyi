import base64
import hashlib
import hmac
import io
import json
import mimetypes
import os
import re
import secrets
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime
from email.parser import BytesParser
from email.policy import default as email_policy_default
from email.utils import formatdate
from http import client as http_client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from urllib.parse import parse_qsl, quote, unquote, urlencode, urlparse, urlunparse

try:
    from PIL import Image, ImageOps
except Exception:
    Image = None
    ImageOps = None

try:
    from pillow_heif import register_heif_opener
except Exception:
    register_heif_opener = None

if Image is not None and register_heif_opener is not None:
    try:
        register_heif_opener()
    except Exception:
        # Keep startup resilient even if HEIF plugin registration fails.
        pass

mimetypes.add_type("application/manifest+json", ".webmanifest")


ROOT_DIR = Path(__file__).resolve().parent
PUBLIC_DIR = ROOT_DIR / "public"
DATA_DIR = ROOT_DIR / "data"
GENERATED_DIR = DATA_DIR / "generated"
THUMB_DIR = GENERATED_DIR / "thumbs"
SKILLS_DIR = DATA_DIR / "skills"
LEGACY_PERSONAS_DIR = DATA_DIR / "personas"
HISTORY_FILE = DATA_DIR / "history.json"
PROMPT_LIBRARY_FILE = DATA_DIR / "prompt-library.md"
API_PLATFORMS_FILE = DATA_DIR / "api-platforms.xml"
ENV_FILE = ROOT_DIR / ".env"

DEFAULT_IMAGE_MODEL = "gemini-3-pro-image-preview"
DEFAULT_API_URL = "https://api.apiyi.com/v1beta/models/{model}:generateContent"
DEFAULT_PROMPT_OPTIMIZER_BASE_URL = "https://api.apiyi.com/v1"
DEFAULT_PROMPT_OPTIMIZER_URL = "https://api.apiyi.com/v1/chat/completions"
DEFAULT_PROMPT_OPTIMIZER_MODEL = "gpt-5.4"
NANO_BANANA_COMPAT_MODEL = "gemini-2.5-flash-image"
DEFAULT_IMAGE_PLATFORM_ID = "default"
DEFAULT_IMAGE_PLATFORM_NAME = "默认平台"
DEFAULT_MODEL_SEPARATOR = "|"
DEFAULT_PORT = 8787
TIMEOUT_MAP = {"1K": 180, "2K": 300, "4K": 360}
PROMPT_OPTIMIZER_TIMEOUT = 90
UPSTREAM_MAX_ATTEMPTS = 2
RETRYABLE_UPSTREAM_STATUS_CODES = {408, 429, 500, 502, 503, 504}
SESSION_COOKIE_NAME = "banana_ui_session"
SESSION_MAX_AGE = 7 * 24 * 3600  # 7 days in seconds
# SESSIONS: token -> expiry timestamp (float). Persisted to data/sessions.json.
SESSIONS: Dict[str, float] = {}
SESSIONS_FILE = DATA_DIR / "sessions.json"


def load_sessions() -> None:
    """Load persisted sessions from disk, pruning expired ones."""
    global SESSIONS
    try:
        if SESSIONS_FILE.exists():
            raw = json.loads(SESSIONS_FILE.read_text(encoding="utf-8"))
            now = time.time()
            SESSIONS = {k: v for k, v in raw.items() if isinstance(v, (int, float)) and v > now}
    except Exception:
        SESSIONS = {}


def save_sessions() -> None:
    """Persist current sessions to disk."""
    try:
        SESSIONS_FILE.write_text(json.dumps(SESSIONS), encoding="utf-8")
    except Exception:
        pass
THUMB_MAX_EDGE = 480
THUMB_JPEG_QUALITY = 72
DEFAULT_HISTORY_PAGE_SIZE = 60
MAX_HISTORY_PAGE_SIZE = 200


def ensure_dirs() -> None:
    PUBLIC_DIR.mkdir(parents=True, exist_ok=True)
    GENERATED_DIR.mkdir(parents=True, exist_ok=True)
    THUMB_DIR.mkdir(parents=True, exist_ok=True)
    SKILLS_DIR.mkdir(parents=True, exist_ok=True)
    LEGACY_PERSONAS_DIR.mkdir(parents=True, exist_ok=True)
    if not HISTORY_FILE.exists():
        HISTORY_FILE.write_text("[]", encoding="utf-8")
    if not PROMPT_LIBRARY_FILE.exists():
        PROMPT_LIBRARY_FILE.write_text("", encoding="utf-8")
    if not API_PLATFORMS_FILE.exists():
        API_PLATFORMS_FILE.write_text(build_default_api_platforms_xml(), encoding="utf-8")


def load_env_file() -> Dict[str, str]:
    values: Dict[str, str] = {}
    if not ENV_FILE.exists():
        return values

    for raw_line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip("'").strip('"')
        values[key] = value
    return values


ENV_VALUES = load_env_file()


def get_setting(name: str, default: str = "") -> str:
    return os.environ.get(name) or ENV_VALUES.get(name, default)


def get_bool_setting(name: str, default: bool = False) -> bool:
    fallback = "true" if default else "false"
    value = get_setting(name, fallback)
    return str(value).strip().lower() in {"1", "true", "yes", "on"}


class ImagePlatformConfigError(Exception):
    def __init__(self, message: str, status_code: int = 500):
        super().__init__(message)
        self.status_code = status_code


def platform_id_slug(value: str, fallback: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", str(value or "").strip().lower()).strip("-")
    return slug or fallback


def get_xml_child_text(parent: ET.Element, tag: str, default: str = "") -> str:
    node = parent.find(tag)
    if node is None or node.text is None:
        return default
    return node.text.strip()


def split_image_models(raw_models: str, separator: str = "") -> List[str]:
    normalized = str(raw_models or "").replace("\r\n", "\n").replace("\r", "\n").strip()
    if not normalized:
        return []

    if separator:
        candidates = normalized.split(separator)
    else:
        candidates = re.split(r"[\n,，;；|]+", normalized)

    result: List[str] = []
    seen = set()
    for item in candidates:
        model = str(item or "").strip()
        if not model or model in seen:
            continue
        seen.add(model)
        result.append(model)
    return result


def build_default_api_platforms_xml() -> str:
    root = ET.Element("apiPlatforms")
    root.set("version", "1")

    platform = ET.SubElement(root, "platform")
    platform.set("id", DEFAULT_IMAGE_PLATFORM_ID)
    platform.set("name", DEFAULT_IMAGE_PLATFORM_NAME)
    platform.set("default", "true")

    url_node = ET.SubElement(platform, "url")
    url_node.text = get_setting("BANANA_PRO_API_URL", DEFAULT_API_URL)

    key_node = ET.SubElement(platform, "key")
    key_node.text = get_setting("BANANA_PRO_API_KEY", "")

    models_node = ET.SubElement(platform, "models")
    models_node.set("separator", DEFAULT_MODEL_SEPARATOR)
    models_node.text = get_setting("BANANA_PRO_IMAGE_MODEL", DEFAULT_IMAGE_MODEL)

    if hasattr(ET, "indent"):
        ET.indent(root, space="  ")
    return ET.tostring(root, encoding="utf-8", xml_declaration=True).decode("utf-8")


def read_image_platforms() -> List[dict]:
    try:
        tree = ET.parse(API_PLATFORMS_FILE)
    except FileNotFoundError:
        ensure_dirs()
        tree = ET.parse(API_PLATFORMS_FILE)
    except ET.ParseError as exc:
        raise ImagePlatformConfigError(
            f"`{API_PLATFORMS_FILE.name}` 格式不正确，请检查 XML 语法：{exc}",
            status_code=500,
        ) from exc
    except OSError as exc:
        raise ImagePlatformConfigError(
            f"读取 `{API_PLATFORMS_FILE.name}` 失败：{exc}",
            status_code=500,
        ) from exc

    root = tree.getroot()
    if root.tag != "apiPlatforms":
        raise ImagePlatformConfigError(
            f"`{API_PLATFORMS_FILE.name}` 根节点必须是 `<apiPlatforms>`。",
            status_code=500,
        )

    platforms: List[dict] = []
    used_ids = set()
    for index, node in enumerate(root.findall("platform"), start=1):
        raw_name = node.get("name") or node.get("label") or f"平台 {index}"
        fallback_id = f"platform-{index}"
        platform_id = platform_id_slug(node.get("id") or raw_name, fallback_id)
        unique_id = platform_id
        duplicate_index = 2
        while unique_id in used_ids:
            unique_id = f"{platform_id}-{duplicate_index}"
            duplicate_index += 1
        used_ids.add(unique_id)

        api_url = get_xml_child_text(node, "url", get_setting("BANANA_PRO_API_URL", DEFAULT_API_URL))
        api_key = get_xml_child_text(node, "key", "")
        models_node = node.find("models")
        model_separator = (
            (models_node.get("separator", "") if models_node is not None else "")
            or node.get("modelSeparator", "")
            or DEFAULT_MODEL_SEPARATOR
        )
        raw_models = get_xml_child_text(node, "models", get_setting("BANANA_PRO_IMAGE_MODEL", DEFAULT_IMAGE_MODEL))
        models = split_image_models(raw_models, model_separator)
        if not models:
            models = [DEFAULT_IMAGE_MODEL]

        requested_default_model = (node.get("defaultModel") or "").strip()
        default_model = requested_default_model if requested_default_model in models else models[0]
        is_default = str(node.get("default", "")).strip().lower() in {"1", "true", "yes", "on"}

        platforms.append(
            {
                "id": unique_id,
                "name": raw_name.strip() or f"平台 {index}",
                "api_url": api_url.strip() or DEFAULT_API_URL,
                "api_key": api_key.strip(),
                "models": models,
                "default_model": default_model,
                "is_default": is_default,
            }
        )

    if not platforms:
        raise ImagePlatformConfigError(
            f"`{API_PLATFORMS_FILE.name}` 里至少需要一个 `<platform>` 配置。",
            status_code=500,
        )

    return platforms


def get_default_image_platform(platforms: List[dict]) -> dict:
    for platform in platforms:
        if platform.get("is_default"):
            return platform
    return platforms[0]


def build_image_platforms_response(platforms: List[dict]) -> dict:
    default_platform = get_default_image_platform(platforms)
    return {
        "items": [
            {
                "id": platform["id"],
                "name": platform["name"],
                "models": platform["models"],
                "defaultModel": platform["default_model"],
            }
            for platform in platforms
        ],
        "defaultPlatformId": default_platform["id"],
        "defaultImageModel": default_platform["default_model"],
        "filename": API_PLATFORMS_FILE.name,
    }


def resolve_image_generation_platform(platform_id: str = "", image_model: str = "") -> dict:
    platforms = read_image_platforms()
    selected_platform: Optional[dict] = None
    normalized_platform_id = str(platform_id or "").strip()
    if normalized_platform_id:
        selected_platform = next((item for item in platforms if item["id"] == normalized_platform_id), None)
        if selected_platform is None:
            raise ImagePlatformConfigError("未找到所选的 API 平台，请刷新页面后重试。", status_code=400)
    else:
        selected_platform = get_default_image_platform(platforms)

    requested_model = str(image_model or "").strip()
    if requested_model:
        if requested_model not in selected_platform["models"]:
            raise ImagePlatformConfigError(
                f"API 平台「{selected_platform['name']}」未配置模型 `{requested_model}`。",
                status_code=400,
            )
        selected_model = requested_model
    else:
        selected_model = selected_platform["default_model"]

    api_key = selected_platform["api_key"] or get_setting("BANANA_PRO_API_KEY", "")
    if not api_key:
        raise ImagePlatformConfigError(
            f"API 平台「{selected_platform['name']}」缺少 key，请先在 `{API_PLATFORMS_FILE.name}` 中配置。",
            status_code=500,
        )

    return {
        "platform_id": selected_platform["id"],
        "platform_name": selected_platform["name"],
        "api_key": api_key,
        "api_url": resolve_image_generation_api_url(selected_platform["api_url"], selected_model),
        "image_model": selected_model,
    }


def get_default_image_platform_api_key() -> str:
    try:
        platforms = read_image_platforms()
        return get_default_image_platform(platforms).get("api_key", "").strip() or get_setting("BANANA_PRO_API_KEY", "")
    except ImagePlatformConfigError:
        return get_setting("BANANA_PRO_API_KEY", "")


def is_google_generative_endpoint(api_url: str) -> bool:
    parsed = urlparse(str(api_url or "").strip())
    host = (parsed.netloc or "").lower()
    return host.endswith("generativelanguage.googleapis.com")


def with_query_param(url: str, key: str, value: str) -> str:
    parsed = urlparse(str(url or "").strip())
    pairs = [(k, v) for k, v in parse_qsl(parsed.query, keep_blank_values=True) if k != key]
    pairs.append((key, value))
    return urlunparse(parsed._replace(query=urlencode(pairs)))


def build_authenticated_request(api_url: str, body: bytes, api_key: str) -> urllib.request.Request:
    request_url = str(api_url or "").strip()
    headers = {"Content-Type": "application/json"}

    # Google Generative Language REST expects API key auth (query/header), not Bearer tokens.
    if is_google_generative_endpoint(request_url):
        request_url = with_query_param(request_url, "key", api_key)
    else:
        headers["Authorization"] = f"Bearer {api_key}"

    return urllib.request.Request(request_url, data=body, headers=headers, method="POST")


class UpstreamHttpError(Exception):
    def __init__(self, status_code: int, details: str):
        super().__init__(details)
        self.status_code = status_code
        self.details = details


def is_retryable_upstream_exception(exc: Exception) -> bool:
    retryable = (
        socket.timeout,
        TimeoutError,
        ConnectionAbortedError,
        ConnectionResetError,
        BrokenPipeError,
        http_client.RemoteDisconnected,
    )
    if isinstance(exc, retryable):
        return True
    if isinstance(exc, urllib.error.URLError):
        reason = exc.reason
        return isinstance(reason, retryable + (OSError,))
    return False


def describe_upstream_exception(exc: Exception) -> str:
    if isinstance(exc, urllib.error.URLError):
        reason = exc.reason
        if isinstance(reason, BaseException):
            detail = str(reason).strip()
            if "Software caused connection abort" in detail:
                return (
                    "上游连接被中断（Software caused connection abort）。"
                    "这通常是网络抖动、代理异常或上游服务主动断开导致的，请重试；"
                    "如果持续出现，请检查当前 API 平台对应服务是否稳定。"
                )
            if detail:
                return f"{reason.__class__.__name__}: {detail}"
        return f"URLError: {str(exc).strip() or '上游连接失败。'}"

    detail = str(exc).strip()
    if "Software caused connection abort" in detail:
        return (
            "上游连接被中断（Software caused connection abort）。"
            "这通常是网络抖动、代理异常或上游服务主动断开导致的，请重试；"
            "如果持续出现，请检查当前 API 平台对应服务是否稳定。"
        )
    if detail:
        return f"{exc.__class__.__name__}: {detail}"
    return exc.__class__.__name__


def post_json_to_upstream(api_url: str, body: bytes, api_key: str, timeout: int) -> dict:
    attempts = max(1, UPSTREAM_MAX_ATTEMPTS)
    for attempt in range(1, attempts + 1):
        request = build_authenticated_request(api_url, body, api_key)
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            if attempt < attempts and exc.code in RETRYABLE_UPSTREAM_STATUS_CODES:
                time.sleep(min(0.8 * attempt, 1.6))
                continue
            raise UpstreamHttpError(exc.code, raw) from exc
        except Exception as exc:
            if attempt < attempts and is_retryable_upstream_exception(exc):
                time.sleep(min(0.8 * attempt, 1.6))
                continue
            raise RuntimeError(describe_upstream_exception(exc)) from exc


def normalize_oss_prefix(prefix: str) -> str:
    return "/".join(part for part in str(prefix or "").strip("/").split("/") if part)


def normalize_oss_endpoint(endpoint: str) -> Tuple[str, str]:
    raw = str(endpoint or "").strip()
    if not raw:
        raise RuntimeError("BANANA_PRO_OSS_ENDPOINT 不能为空。")

    if "://" not in raw:
        raw = f"https://{raw}"
    parsed = urlparse(raw)

    scheme = parsed.scheme.lower() if parsed.scheme else "https"
    if scheme not in {"http", "https"}:
        raise RuntimeError("BANANA_PRO_OSS_ENDPOINT 必须以 http:// 或 https:// 开头。")

    host = (parsed.netloc or parsed.path).strip().strip("/")
    if not host:
        raise RuntimeError("BANANA_PRO_OSS_ENDPOINT 格式不正确。")
    return scheme, host


def quote_oss_object_key(object_key: str) -> str:
    return quote(object_key, safe="/-_.~")


def build_oss_public_url(config: dict, object_key: str) -> str:
    encoded_key = quote_oss_object_key(object_key)
    public_base_url = str(config.get("public_base_url", "")).strip().rstrip("/")
    if public_base_url:
        return f"{public_base_url}/{encoded_key}"
    return f"{config['scheme']}://{config['host']}/{encoded_key}"


def get_oss_config() -> Optional[dict]:
    if not get_bool_setting("BANANA_PRO_OSS_ENABLED", False):
        return None

    bucket = get_setting("BANANA_PRO_OSS_BUCKET", "").strip()
    access_key_id = get_setting("BANANA_PRO_OSS_ACCESS_KEY_ID", "").strip()
    access_key_secret = get_setting("BANANA_PRO_OSS_ACCESS_KEY_SECRET", "").strip()
    endpoint = get_setting("BANANA_PRO_OSS_ENDPOINT", "").strip()
    missing: List[str] = []
    if not bucket:
        missing.append("BANANA_PRO_OSS_BUCKET")
    if not access_key_id:
        missing.append("BANANA_PRO_OSS_ACCESS_KEY_ID")
    if not access_key_secret:
        missing.append("BANANA_PRO_OSS_ACCESS_KEY_SECRET")
    if not endpoint:
        missing.append("BANANA_PRO_OSS_ENDPOINT")
    if missing:
        joined = ", ".join(missing)
        raise RuntimeError(f"已启用 OSS，但缺少必要配置：{joined}")

    scheme, endpoint_host = normalize_oss_endpoint(endpoint)
    bucket_lower = bucket.lower()
    endpoint_lower = endpoint_host.lower()
    host = endpoint_host if endpoint_lower.startswith(f"{bucket_lower}.") else f"{bucket}.{endpoint_host}"

    prefix = normalize_oss_prefix(get_setting("BANANA_PRO_OSS_PREFIX", "banana-pro"))

    public_base_url = get_setting("BANANA_PRO_OSS_PUBLIC_BASE_URL", "").strip()
    if public_base_url and "://" not in public_base_url:
        public_base_url = f"https://{public_base_url}"

    return {
        "scheme": scheme,
        "host": host,
        "bucket": bucket,
        "prefix": prefix,
        "access_key_id": access_key_id,
        "access_key_secret": access_key_secret,
        "public_base_url": public_base_url,
    }


def build_oss_object_key(prefix: str, date_segment: str, category: str, filename: str) -> str:
    parts: List[str] = []
    if prefix:
        parts.append(prefix)
    if date_segment:
        parts.append(date_segment.strip("/"))
    if category:
        parts.append(category.strip("/"))
    if filename:
        parts.append(filename.strip("/"))
    return "/".join(part for part in parts if part)


def sign_oss_request(secret: str, string_to_sign: str) -> str:
    digest = hmac.new(secret.encode("utf-8"), string_to_sign.encode("utf-8"), hashlib.sha1).digest()
    return base64.b64encode(digest).decode("utf-8")


def build_oss_signed_download_url(
    config: dict,
    object_key: str,
    download_name: str,
    expires_seconds: int = 600,
) -> str:
    normalized_key = "/".join(part for part in str(object_key or "").split("/") if part)
    if not normalized_key:
        raise RuntimeError("OSS 对象 Key 不能为空。")

    expires = int(datetime.now().timestamp()) + max(60, int(expires_seconds))
    safe_download_name = safe_name(download_name or Path(normalized_key).name or "banana-pro-image")
    content_disposition = f"attachment; filename*=UTF-8''{quote(safe_download_name)}"

    canonical_resource = (
        f"/{config['bucket']}/{normalized_key}"
        f"?response-content-disposition={content_disposition}"
    )
    string_to_sign = "\n".join(
        [
            "GET",
            "",
            "",
            str(expires),
            canonical_resource,
        ]
    )
    signature = sign_oss_request(config["access_key_secret"], string_to_sign)

    query = urlencode(
        {
            "OSSAccessKeyId": config["access_key_id"],
            "Expires": str(expires),
            "Signature": signature,
            "response-content-disposition": content_disposition,
        }
    )
    encoded_key = quote_oss_object_key(normalized_key)
    return f"{config['scheme']}://{config['host']}/{encoded_key}?{query}"


def infer_oss_object_key_from_url(oss_url: str) -> str:
    parsed = urlparse(str(oss_url or "").strip())
    return parsed.path.lstrip("/")


def upload_bytes_to_oss(config: dict, object_key: str, body: bytes, content_type: str) -> str:
    normalized_key = "/".join(part for part in str(object_key or "").split("/") if part)
    if not normalized_key:
        raise RuntimeError("OSS 对象 Key 不能为空。")

    date_value = formatdate(usegmt=True)
    headers = {
        "Date": date_value,
        "Content-Type": content_type or "application/octet-stream",
    }

    string_to_sign = "\n".join(
        [
            "PUT",
            "",
            headers["Content-Type"],
            date_value,
            f"/{config['bucket']}/{normalized_key}",
        ]
    )
    signature = sign_oss_request(config["access_key_secret"], string_to_sign)
    headers["Authorization"] = f"OSS {config['access_key_id']}:{signature}"

    upload_url = f"{config['scheme']}://{config['host']}/{quote_oss_object_key(normalized_key)}"
    request = urllib.request.Request(upload_url, data=body, headers=headers, method="PUT")

    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            status = getattr(response, "status", 200)
            if status < 200 or status >= 300:
                raise RuntimeError(f"OSS 返回了非成功状态码：HTTP {status}")
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        details = raw.strip()[:800] or str(exc.reason or "")
        raise RuntimeError(f"OSS 上传失败（HTTP {exc.code}）：{details}") from exc
    except Exception as exc:
        raise RuntimeError(f"OSS 上传失败：{exc}") from exc

    return build_oss_public_url(config, normalized_key)


def upload_generated_assets_to_oss(
    config: dict,
    now: datetime,
    image_filename: str,
    image_bytes: bytes,
    image_mime_type: str,
    thumb_filename: str,
    thumb_bytes: bytes,
    metadata_filename: str,
    metadata_bytes: bytes,
) -> dict:
    # Group objects by YYMM folder, e.g. 2603.
    date_segment = now.strftime("%y%m")
    # Main image goes directly under YYMM/.
    image_key = build_oss_object_key(config["prefix"], date_segment, "", image_filename)
    thumb_key = build_oss_object_key(config["prefix"], date_segment, "thumbs", thumb_filename)
    metadata_key = build_oss_object_key(config["prefix"], date_segment, "XML", metadata_filename)
    image_url = upload_bytes_to_oss(config, image_key, image_bytes, image_mime_type)
    thumb_url = upload_bytes_to_oss(config, thumb_key, thumb_bytes, "image/jpeg")
    metadata_url = upload_bytes_to_oss(
        config,
        metadata_key,
        metadata_bytes,
        "application/xml; charset=utf-8",
    )
    return {
        "image_url": image_url,
        "thumb_url": thumb_url,
        "metadata_url": metadata_url,
        "image_key": image_key,
        "thumb_key": thumb_key,
        "metadata_key": metadata_key,
    }


def build_generation_metadata_xml(
    file_id: str,
    created_at: datetime,
    prompt: str,
    source_prompt: str,
    prompt_mode: str,
    aspect_ratio: str,
    image_size: str,
    enable_search: bool,
    base_image_name: str,
    base_image_sha256: str,
    reference_count: int,
    api_platform_id: str,
    api_platform_name: str,
    image_model: str,
    mime_type: str,
) -> bytes:
    root = ET.Element("bananaProGeneration")
    root.set("id", str(file_id))
    root.set("createdAt", created_at.isoformat(timespec="seconds"))

    fields = {
        "prompt": prompt,
        "sourcePrompt": source_prompt,
        "promptMode": prompt_mode,
        "aspectRatio": aspect_ratio,
        "imageSize": image_size,
        "enableSearch": "true" if enable_search else "false",
        "baseImageName": base_image_name,
        "baseImageSha256": base_image_sha256,
        "referenceCount": str(reference_count),
        "apiPlatformId": api_platform_id,
        "apiPlatformName": api_platform_name,
        "imageModel": image_model,
        "mimeType": mime_type,
    }
    for key, value in fields.items():
        node = ET.SubElement(root, key)
        node.text = str(value or "")

    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


def read_history() -> List[dict]:
    try:
        return json.loads(HISTORY_FILE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return []


def write_history(items: List[dict]) -> None:
    HISTORY_FILE.write_text(
        json.dumps(items, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def append_history(entry: dict) -> None:
    history = read_history()
    history.insert(0, entry)
    write_history(history)


def parse_positive_int(
    value: Optional[str],
    default: int,
    *,
    minimum: int = 1,
    maximum: Optional[int] = None,
) -> int:
    try:
        parsed = int(str(value or "").strip())
    except (TypeError, ValueError):
        return default

    if parsed < minimum:
        return default
    if maximum is not None:
        parsed = min(parsed, maximum)
    return parsed


def get_thumbnail_filename(item_id: str) -> str:
    return f"{safe_name(item_id)}.jpg"


def get_thumbnail_path(item_id: str) -> Path:
    return THUMB_DIR / get_thumbnail_filename(item_id)


def get_thumbnail_url(item_id: str) -> str:
    return f"/generated/thumbs/{get_thumbnail_filename(item_id)}"


def resolve_generated_path(image_url: str) -> Optional[Path]:
    if not isinstance(image_url, str) or not image_url.startswith("/generated/"):
        return None
    relative = image_url.removeprefix("/generated/")
    target = (GENERATED_DIR / relative).resolve()
    try:
        target.relative_to(GENERATED_DIR.resolve())
    except ValueError:
        return None
    return target


def build_thumbnail(source_path: Path, thumb_path: Path) -> None:
    if Image is None:
        raise RuntimeError("缺少 Pillow 依赖，无法生成缩略图。")

    thumb_path.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(source_path) as image:
        if image.mode in ("RGBA", "LA") or (image.mode == "P" and "transparency" in image.info):
            alpha = image.convert("RGBA")
            background = Image.new("RGB", alpha.size, (255, 255, 255))
            background.paste(alpha, mask=alpha.getchannel("A"))
            image = background
        else:
            image = image.convert("RGB")

        resample = Image.Resampling.LANCZOS if hasattr(Image, "Resampling") else Image.LANCZOS
        image.thumbnail((THUMB_MAX_EDGE, THUMB_MAX_EDGE), resample=resample)
        image.save(
            thumb_path,
            format="JPEG",
            quality=THUMB_JPEG_QUALITY,
            optimize=True,
            progressive=True,
        )


def render_image_as_rgb(image) -> "Image.Image":
    source = ImageOps.exif_transpose(image) if ImageOps is not None else image
    if source.mode in ("RGBA", "LA") or (source.mode == "P" and "transparency" in source.info):
        alpha = source.convert("RGBA")
        background = Image.new("RGB", alpha.size, (255, 255, 255))
        background.paste(alpha, mask=alpha.getchannel("A"))
        return background
    return source.convert("RGB")


def convert_image_with_pillow_to_jpeg(data: bytes) -> bytes:
    if Image is None:
        raise RuntimeError("缺少 Pillow 依赖，无法转换图片。")

    output = io.BytesIO()
    with Image.open(io.BytesIO(data)) as image:
        rgb = render_image_as_rgb(image)
        rgb.save(
            output,
            format="JPEG",
            quality=90,
            optimize=True,
            progressive=True,
        )
    return output.getvalue()


def convert_image_with_sips_to_jpeg(data: bytes, filename: str, mime_type: str) -> bytes:
    if sys.platform != "darwin":
        raise RuntimeError("当前环境不支持 sips 转换。")

    suffix = Path(filename or "upload-image").suffix or mimetypes.guess_extension(mime_type or "") or ".img"
    stem = Path(filename or "upload-image").stem or "upload-image"
    with tempfile.TemporaryDirectory(prefix="banana-pro-image-") as temp_dir:
        source_path = Path(temp_dir) / f"{safe_name(stem)}{suffix}"
        output_path = Path(temp_dir) / f"{safe_name(stem)}.jpg"
        source_path.write_bytes(data)
        try:
            subprocess.run(
                ["sips", "-s", "format", "jpeg", str(source_path), "--out", str(output_path)],
                check=True,
                capture_output=True,
                text=True,
            )
        except (OSError, subprocess.CalledProcessError) as exc:
            stderr = ""
            if isinstance(exc, subprocess.CalledProcessError):
                stderr = (exc.stderr or "").strip()
            raise RuntimeError(stderr or "sips 图片转换失败。") from exc
        return output_path.read_bytes()


def is_heif_like_upload(filename: str, mime_type: str) -> bool:
    suffix = Path(filename or "").suffix.lower()
    normalized_mime = str(mime_type or "").split(";", 1)[0].strip().lower()
    if suffix in {".heic", ".heif", ".heics", ".heifs"}:
        return True
    return "heic" in normalized_mime or "heif" in normalized_mime


def convert_uploaded_image_to_jpeg(data: bytes, filename: str, mime_type: str) -> bytes:
    errors: List[str] = []
    heif_like = is_heif_like_upload(filename, mime_type)

    try:
        return convert_image_with_pillow_to_jpeg(data)
    except Exception as exc:
        errors.append(str(exc))

    if heif_like and register_heif_opener is None:
        errors.append("当前服务未安装 HEIC/HEIF 解码插件 pillow-heif。")

    if sys.platform == "darwin":
        try:
            return convert_image_with_sips_to_jpeg(data, filename, mime_type)
        except Exception as exc:
            errors.append(str(exc))

    details = "；".join(message for message in errors if message) or "未找到可用的图片转换器。"
    raise RuntimeError(details)


def ensure_thumbnail_for_history_entry(entry: dict) -> Tuple[dict, bool]:
    if not isinstance(entry, dict):
        return entry, False

    item_id = str(entry.get("id", "")).strip()
    if not item_id:
        return entry, False

    source_path = resolve_generated_path(str(entry.get("imageUrl", "")))
    if not source_path or not source_path.exists() or not source_path.is_file():
        return entry, False

    thumb_path = get_thumbnail_path(item_id)
    if not thumb_path.exists():
        build_thumbnail(source_path, thumb_path)

    thumb_url = get_thumbnail_url(item_id)
    if entry.get("thumbUrl") == thumb_url:
        return entry, False

    patched = dict(entry)
    patched["thumbUrl"] = thumb_url
    return patched, True


def read_history_with_thumbnails() -> List[dict]:
    history = read_history()
    changed = False
    result: List[dict] = []

    for item in history:
        try:
            patched, item_changed = ensure_thumbnail_for_history_entry(item)
        except Exception:
            patched, item_changed = item, False
        result.append(patched)
        if item_changed:
            changed = True

    if changed:
        write_history(result)
    return result


def build_history_response_payload(query_string: str = "") -> dict:
    history = read_history_with_thumbnails()
    total = len(history)
    query = dict(parse_qsl(query_string, keep_blank_values=True))
    paginated = any(key in query for key in ("page", "pageSize", "page_size"))

    if not paginated:
        return {
            "items": history,
            "total": total,
            "page": 1,
            "pageSize": total if total > 0 else 0,
            "totalPages": 1 if total > 0 else 0,
            "hasMore": False,
            "paginated": False,
        }

    page_size_raw = query.get("pageSize") if "pageSize" in query else query.get("page_size")
    page_size = parse_positive_int(
        page_size_raw,
        DEFAULT_HISTORY_PAGE_SIZE,
        maximum=MAX_HISTORY_PAGE_SIZE,
    )
    total_pages = (total + page_size - 1) // page_size if total > 0 else 0
    page = parse_positive_int(query.get("page"), 1)
    effective_page = min(page, total_pages or 1)
    start = (effective_page - 1) * page_size
    end = start + page_size

    return {
        "items": history[start:end],
        "total": total,
        "page": effective_page,
        "pageSize": page_size,
        "totalPages": total_pages,
        "hasMore": total_pages > 0 and effective_page < total_pages,
        "paginated": True,
    }


def delete_history_item(item_id: str) -> Optional[dict]:
    history = read_history()
    remaining: List[dict] = []
    removed: Optional[dict] = None
    for item in history:
        if item.get("id") == item_id and removed is None:
            removed = item
            continue
        remaining.append(item)

    if removed is None:
        return None

    write_history(remaining)
    return removed


def read_prompt_library_content() -> str:
    try:
        return PROMPT_LIBRARY_FILE.read_text(encoding="utf-8")
    except OSError:
        return ""


def parse_prompt_library_items(content: str) -> List[str]:
    return [line.strip() for line in content.splitlines() if line.strip()]


def write_prompt_library_content(content: str) -> None:
    normalized = "\n".join(line.rstrip() for line in content.replace("\r\n", "\n").split("\n"))
    PROMPT_LIBRARY_FILE.write_text(normalized.strip(), encoding="utf-8")


def json_response(
    handler: BaseHTTPRequestHandler,
    status: int,
    payload: dict,
    extra_headers: Optional[List[Tuple[str, str]]] = None,
) -> None:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(body)))
    for key, value in extra_headers or []:
        handler.send_header(key, value)
    handler.end_headers()
    handler.wfile.write(body)


def binary_response(
    handler: BaseHTTPRequestHandler,
    status: int,
    body: bytes,
    content_type: str,
    extra_headers: Optional[List[Tuple[str, str]]] = None,
) -> None:
    payload = body or b""
    handler.send_response(status)
    handler.send_header("Content-Type", content_type)
    handler.send_header("Content-Length", str(len(payload)))
    for key, value in extra_headers or []:
        handler.send_header(key, value)
    handler.end_headers()
    handler.wfile.write(payload)


def parse_cookie_header(raw_cookie: str) -> Dict[str, str]:
    result: Dict[str, str] = {}
    if not raw_cookie:
        return result
    for item in raw_cookie.split(";"):
        if "=" not in item:
            continue
        key, value = item.split("=", 1)
        result[key.strip()] = value.strip()
    return result


def get_ui_password() -> str:
    return get_setting("BANANA_PRO_UI_PASSWORD", "")


def password_enabled() -> bool:
    return bool(get_ui_password().strip())


def make_session_token(password: str) -> str:
    raw = f"{password}:{secrets.token_urlsafe(24)}".encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def safe_name(name: str) -> str:
    return re.sub(r"[^a-zA-Z0-9._-]+", "-", name).strip("-") or "image"


def infer_mime_type(filename: str, provided: Optional[str], data: bytes) -> str:
    normalized_provided = str(provided or "").split(";", 1)[0].strip().lower()
    if (
        normalized_provided
        and "/" in normalized_provided
        and normalized_provided != "application/octet-stream"
        and not normalized_provided.endswith("/*")
    ):
        return normalized_provided
    guessed, _ = mimetypes.guess_type(filename)
    if guessed:
        return guessed
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if data.startswith(b"\xff\xd8"):
        return "image/jpeg"
    if data.startswith(b"RIFF") and data[8:12] == b"WEBP":
        return "image/webp"
    if data[:6] in (b"GIF87a", b"GIF89a"):
        return "image/gif"
    return "application/octet-stream"


def parse_png_size(data: bytes) -> Optional[Tuple[int, int]]:
    if len(data) < 24 or not data.startswith(b"\x89PNG\r\n\x1a\n"):
        return None
    width = int.from_bytes(data[16:20], "big")
    height = int.from_bytes(data[20:24], "big")
    return width, height


def parse_gif_size(data: bytes) -> Optional[Tuple[int, int]]:
    if len(data) < 10 or data[:6] not in (b"GIF87a", b"GIF89a"):
        return None
    width = int.from_bytes(data[6:8], "little")
    height = int.from_bytes(data[8:10], "little")
    return width, height


def parse_webp_size(data: bytes) -> Optional[Tuple[int, int]]:
    if len(data) < 30 or not (data.startswith(b"RIFF") and data[8:12] == b"WEBP"):
        return None
    chunk = data[12:16]
    if chunk == b"VP8 " and len(data) >= 30:
        width = int.from_bytes(data[26:28], "little") & 0x3FFF
        height = int.from_bytes(data[28:30], "little") & 0x3FFF
        return width, height
    if chunk == b"VP8L" and len(data) >= 25:
        bits = int.from_bytes(data[21:25], "little")
        width = (bits & 0x3FFF) + 1
        height = ((bits >> 14) & 0x3FFF) + 1
        return width, height
    if chunk == b"VP8X" and len(data) >= 30:
        width = int.from_bytes(data[24:27], "little") + 1
        height = int.from_bytes(data[27:30], "little") + 1
        return width, height
    return None


def parse_jpeg_size(data: bytes) -> Optional[Tuple[int, int]]:
    if len(data) < 4 or not data.startswith(b"\xff\xd8"):
        return None

    index = 2
    while index < len(data) - 1:
        if data[index] != 0xFF:
            index += 1
            continue
        marker = data[index + 1]
        index += 2
        if marker in (0xD8, 0xD9):
            continue
        if index + 2 > len(data):
            return None
        segment_length = int.from_bytes(data[index:index + 2], "big")
        if segment_length < 2 or index + segment_length > len(data):
            return None
        if marker in {
            0xC0, 0xC1, 0xC2, 0xC3,
            0xC5, 0xC6, 0xC7,
            0xC9, 0xCA, 0xCB,
            0xCD, 0xCE, 0xCF,
        }:
            if index + 7 > len(data):
                return None
            height = int.from_bytes(data[index + 3:index + 5], "big")
            width = int.from_bytes(data[index + 5:index + 7], "big")
            return width, height
        index += segment_length
    return None


def detect_image_size(data: bytes, mime_type: str) -> Optional[Tuple[int, int]]:
    if mime_type == "image/png":
        return parse_png_size(data)
    if mime_type == "image/jpeg":
        return parse_jpeg_size(data)
    if mime_type == "image/webp":
        return parse_webp_size(data)
    if mime_type == "image/gif":
        return parse_gif_size(data)
    return None


def aspect_ratio_label(width: int, height: int) -> str:
    if width <= 0 or height <= 0:
        return "1:1"

    ratio = width / height
    candidates = {
        "1:1": 1.0,
        "4:3": 4 / 3,
        "3:4": 3 / 4,
        "16:9": 16 / 9,
        "9:16": 9 / 16,
        "3:2": 3 / 2,
        "2:3": 2 / 3,
        "21:9": 21 / 9,
        "5:4": 5 / 4,
        "4:5": 4 / 5,
    }
    return min(candidates, key=lambda key: abs(candidates[key] - ratio))


def map_output_size(size_key: str) -> str:
    mapping = {
        "1K": "1K",
        "2K": "2K",
        "4K": "4K",
    }
    return mapping.get(size_key.upper(), "4K")


def normalize_image_model(model: str, default: str = DEFAULT_IMAGE_MODEL) -> str:
    normalized = str(model or "").strip()
    return normalized or default


def replace_image_model_in_path(path: str, model: str) -> str:
    encoded_model = quote(model, safe="._-")
    return re.sub(
        r"(/models/)[^/:]+(:generateContent)$",
        rf"\1{encoded_model}\2",
        path,
    )


def resolve_image_generation_api_url(api_url: str, image_model: str) -> str:
    configured_url = str(api_url or "").strip() or DEFAULT_API_URL
    normalized_model = str(image_model or "").strip()

    if "{model}" in configured_url:
        return configured_url.replace(
            "{model}",
            quote(normalize_image_model(normalized_model), safe="._-"),
        )

    parsed = urlparse(configured_url)
    path = parsed.path.rstrip("/")
    if path == "/v1/draw/nano-banana":
        # This app sends Gemini official payloads. Some providers expose a native
        # draw endpoint at /v1/draw/nano-banana that expects a top-level prompt
        # field, but the same host also supports Gemini-compatible paths.
        compat_model = normalize_image_model(normalized_model, default=NANO_BANANA_COMPAT_MODEL)
        return urlunparse(
            parsed._replace(
                path=f"/v1beta/models/{quote(compat_model, safe='._-')}:generateContent",
                params="",
                query="",
                fragment="",
            )
        )

    if normalized_model:
        if path.endswith(":generateContent") and "/models/" in path:
            return urlunparse(parsed._replace(path=replace_image_model_in_path(path, normalized_model)))

        if re.fullmatch(r"/v\d+(?:beta(?:\d+)?)?", path):
            return urlunparse(
                parsed._replace(
                    path=f"{path}/models/{quote(normalized_model, safe='._-')}:generateContent"
                )
            )

        if re.fullmatch(r"/v\d+(?:beta(?:\d+)?)?/models", path):
            return urlunparse(
                parsed._replace(
                    path=f"{path}/{quote(normalized_model, safe='._-')}:generateContent"
                )
            )

    return configured_url


def build_prompt(user_prompt: str, reference_count: int) -> str:
    base = (
        "你将收到一张基础结构图作为主要约束，它的图片编号固定为 BASE。"
        "请严格保留 BASE 对应图片中的主体构图、空间关系和关键结构。"
    )
    if reference_count > 0:
        base += (
            f"另外还会提供 {reference_count} 张参考图，它们会按上传顺序编号为 REF1 到 REF{reference_count}。"
            "这些参考图只用于借鉴风格、材质、灯光、色彩和氛围，不要直接复制参考图中的具体主体内容。"
        )
    else:
        base += "没有提供风格参考图时，请根据提示词自行补足材质、光线与氛围。"
    base += "如果用户提示词里提到 BASE、REF1、REF2 等编号，请严格按这些编号去对应图片。"

    if user_prompt.strip():
        base += f"\n\n用户提示词：{user_prompt.strip()}"
    else:
        base += "\n\n用户提示词：请生成一张高质量、细节完整、风格统一的结果图。"
    return base


def build_payload(
    prompt: str,
    base_image: dict,
    reference_images: List[dict],
    aspect_ratio: str,
    image_size: str,
    enable_search: bool,
) -> dict:
    parts = [
        {"text": build_prompt(prompt, len(reference_images))},
        {"text": "图片编号 BASE。这张图是基础结构图，请以它为核心，并严格保留它的主体结构。"},
        {
            "inlineData": {
                "mimeType": base_image["mime_type"],
                "data": base_image["base64"],
            }
        }
    ]

    for index, image in enumerate(reference_images, start=1):
        parts.append(
            {"text": f"图片编号 REF{index}。这是一张参考图，仅用于风格、材质、灯光、色彩和氛围参考。"},
        )
        parts.append(
            {
                "inlineData": {
                    "mimeType": image["mime_type"],
                    "data": image["base64"],
                }
            }
        )

    payload = {
        "contents": [
            {
                "parts": parts,
            }
        ],
        "generationConfig": {
            "responseModalities": ["IMAGE"],
            "imageConfig": {
                "aspectRatio": aspect_ratio,
                "imageSize": image_size,
            },
        },
    }

    if enable_search:
        payload["tools"] = [{"googleSearch": {}}]

    return payload


def normalize_upload_list(value: object) -> List[dict]:
    if value is None:
        return []
    if isinstance(value, list):
        return [item for item in value if isinstance(item, dict)]
    if isinstance(value, dict):
        return [value]
    return []


def parse_response_image(payload: dict) -> Tuple[Optional[bytes], Optional[str], Optional[str]]:
    candidates = payload.get("candidates") or []
    for candidate in candidates:
        content = candidate.get("content") or {}
        for part in content.get("parts") or []:
            inline = part.get("inlineData") or part.get("inline_data")
            if inline and inline.get("data"):
                mime_type = inline.get("mimeType") or inline.get("mime_type") or "image/png"
                try:
                    return base64.b64decode(inline["data"]), mime_type, part.get("text")
                except (ValueError, TypeError):
                    continue

    text_chunks: List[str] = []
    for candidate in candidates:
        content = candidate.get("content") or {}
        for part in content.get("parts") or []:
            if part.get("text"):
                text_chunks.append(part["text"])
    return None, None, "\n".join(text_chunks).strip() or None


def extension_for_mime(mime_type: str) -> str:
    mapping = {
        "image/png": ".png",
        "image/jpeg": ".jpg",
        "image/webp": ".webp",
        "image/gif": ".gif",
    }
    return mapping.get(mime_type, ".png")


def slugify_name(value: str) -> str:
    return re.sub(r"[^a-zA-Z0-9._-]+", "-", value).strip("-").lower() or "skill"


def normalize_skill_filename(filename: str) -> str:
    basename = str(filename or "").strip().replace("\\", "/").split("/")[-1]
    stem, suffix = os.path.splitext(basename)
    safe_stem = re.sub(r"[^\w\u4e00-\u9fff-]+", "-", stem, flags=re.UNICODE).strip(" .-_")
    if not safe_stem:
        safe_stem = "skill"
    safe_suffix = suffix or ".md"
    if safe_suffix.lower() != ".md":
        safe_suffix = ".md"
    return f"{safe_stem}{safe_suffix}"


def iter_prompt_skill_paths() -> List[Path]:
    result: List[Path] = []
    seen = set()
    for directory in (SKILLS_DIR, LEGACY_PERSONAS_DIR):
        for path in sorted(directory.glob("*.md")):
            dedupe_key = path.name.lower()
            if dedupe_key in seen:
                continue
            seen.add(dedupe_key)
            result.append(path)
    return result


def parse_skill_markdown(path: Path) -> Optional[dict]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return None

    if len(lines) < 3:
        return None

    name = lines[0].strip()
    summary = lines[1].strip()
    content = "\n".join(lines[2:]).strip()
    if not name or not summary or not content:
        return None

    return {
        "id": path.name,
        "name": name,
        "summary": summary,
        "content": content,
        "filename": path.name,
        "storage": "skills" if path.parent == SKILLS_DIR else "legacy-personas",
    }


def build_skill_markdown(name: str, summary: str, content: str) -> str:
    return "\n".join([name.strip(), summary.strip(), content.strip()]).strip() + "\n"


def read_prompt_skills() -> List[dict]:
    skills: List[dict] = []
    for path in iter_prompt_skill_paths():
        skill = parse_skill_markdown(path)
        if skill:
            skills.append(skill)
    return skills


def get_prompt_skill(skill_id: str) -> Optional[dict]:
    target_path = get_prompt_skill_path(skill_id)
    if not target_path:
        return None
    return parse_skill_markdown(target_path)


def get_prompt_skill_path(skill_id: str) -> Optional[Path]:
    target = unquote(skill_id).strip()
    if not target:
        return None
    if "/" in target or "\\" in target or ".." in target:
        return None

    for directory in (SKILLS_DIR, LEGACY_PERSONAS_DIR):
        direct = directory / target
        if direct.exists() and direct.is_file() and direct.suffix.lower() == ".md":
            return direct

    lowered = target.lower()
    for path in iter_prompt_skill_paths():
        if path.name.lower() == lowered or slugify_name(path.stem) == lowered:
            return path
    return None


def validate_skill_payload(payload: dict) -> Tuple[Optional[str], Optional[str], Optional[str], Optional[str]]:
    name = str(payload.get("name", "")).strip()
    summary = str(payload.get("summary", "")).strip()
    content = str(payload.get("content", "")).strip()
    filename = normalize_skill_filename(str(payload.get("filename", "")).strip() or "skill.md")
    if not name or not summary or not content:
        return None, None, None, "技能名称、简介和内容都不能为空。"
    return name, summary, content, filename


def create_prompt_skill(payload: dict) -> Tuple[Optional[dict], Optional[str]]:
    name, summary, content, filename_or_error = validate_skill_payload(payload)
    if not name or not summary or not content:
        return None, filename_or_error

    filename = filename_or_error or "skill.md"
    target = SKILLS_DIR / filename
    if target.exists() or (LEGACY_PERSONAS_DIR / filename).exists():
        return None, "同名 md 文件已存在，请先重命名后再上传。"

    target.write_text(build_skill_markdown(name, summary, content), encoding="utf-8")
    skill = parse_skill_markdown(target)
    if not skill:
        return None, "上传的技能文件格式不正确，必须至少包含名称、简介和正文三部分。"
    return skill, None


def update_prompt_skill(skill_id: str, payload: dict) -> Tuple[Optional[dict], Optional[str]]:
    target = get_prompt_skill_path(skill_id)
    if not target:
        return None, "未找到对应的技能文件。"

    name, summary, content, filename_or_error = validate_skill_payload(payload)
    error = filename_or_error if not (name and summary and content) else None
    if error:
        return None, error

    next_filename = filename_or_error or target.name
    next_target = target.parent / next_filename
    if next_target.resolve() != target.resolve():
        if next_target.exists():
            return None, "目标文件名已存在，请换一个文件名。"
        try:
            target.rename(next_target)
            target = next_target
        except OSError as exc:
            return None, f"重命名文件失败：{exc}"

    target.write_text(build_skill_markdown(name, summary, content), encoding="utf-8")
    skill = parse_skill_markdown(target)
    if not skill:
        return None, "保存失败，技能文件格式不正确。"
    return skill, None


def delete_prompt_skill(skill_id: str) -> bool:
    target = get_prompt_skill_path(skill_id)
    if not target or not target.exists():
        return False
    try:
        target.unlink()
    except OSError:
        return False
    return True


def normalize_persona_filename(filename: str) -> str:
    return normalize_skill_filename(filename)


def parse_persona_markdown(path: Path) -> Optional[dict]:
    return parse_skill_markdown(path)


def build_persona_markdown(name: str, summary: str, content: str) -> str:
    return build_skill_markdown(name, summary, content)


def read_prompt_personas() -> List[dict]:
    return read_prompt_skills()


def get_prompt_persona(persona_id: str) -> Optional[dict]:
    return get_prompt_skill(persona_id)


def get_prompt_persona_path(persona_id: str) -> Optional[Path]:
    return get_prompt_skill_path(persona_id)


def validate_persona_payload(payload: dict) -> Tuple[Optional[str], Optional[str], Optional[str], Optional[str]]:
    return validate_skill_payload(payload)


def create_prompt_persona(payload: dict) -> Tuple[Optional[dict], Optional[str]]:
    return create_prompt_skill(payload)


def update_prompt_persona(persona_id: str, payload: dict) -> Tuple[Optional[dict], Optional[str]]:
    return update_prompt_skill(persona_id, payload)


def delete_prompt_persona(persona_id: str) -> bool:
    return delete_prompt_skill(persona_id)


def build_prompt_optimizer_url(base_url: str) -> str:
    normalized = base_url.rstrip("/")
    if normalized.endswith("/chat/completions"):
        return normalized
    return normalized + "/chat/completions"


def build_image_data_url(upload: dict) -> str:
    mime_type = str(upload.get("mime_type", "")).strip() or "image/jpeg"
    base64_data = str(upload.get("base64", "")).strip()
    if not base64_data:
        raise ValueError("图片内容为空。")
    return f"data:{mime_type};base64,{base64_data}"


def build_prompt_optimizer_payload(user_prompt: str, persona_content: str, model: str, base_image: Optional[dict] = None) -> dict:
    source_text = user_prompt.strip()
    user_content: object
    if isinstance(base_image, dict) and base_image.get("base64"):
        user_content = [
            {
                "type": "text",
                "text": (
                    "请结合这张基础结构图，把以下中文需求转译成适合 nano banana pro 模型生成图片使用的英文提示词。"
                    "需要明确保留基础图中的主体构图、空间关系和关键结构。"
                    "如果用户需求里出现 BASE、REF1、REF2 这类图片编号，请在英文提示词里原样保留这些编号，不要翻译、不要改写。"
                    "只输出最终英文提示词，不要解释。\n\n"
                    f"用户需求：{source_text}"
                ),
            },
            {
                "type": "image_url",
                "image_url": {
                    "url": build_image_data_url(base_image),
                },
            },
        ]
    else:
        user_content = (
            "请把以下内容转译成适合 nano banana pro 模型生成图片使用的英文提示词。"
            "如果内容里出现 BASE、REF1、REF2 这类图片编号，请在英文提示词里原样保留这些编号，不要翻译、不要改写。\n\n"
            f"用户需求：{source_text}"
        )

    return {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": persona_content,
            },
            {
                "role": "user",
                "content": user_content,
            },
        ],
    }


def extract_text_from_llm_response(payload: dict) -> Optional[str]:
    output_text = payload.get("output_text")
    if isinstance(output_text, str) and output_text.strip():
        return output_text.strip()

    choices = payload.get("choices")
    if isinstance(choices, list):
        for choice in choices:
            message = choice.get("message") or {}
            content = message.get("content")
            if isinstance(content, str) and content.strip():
                return content.strip()
            if isinstance(content, list):
                parts: List[str] = []
                for item in content:
                    if isinstance(item, dict) and item.get("type") == "text" and item.get("text"):
                        parts.append(str(item["text"]))
                merged = "\n".join(part.strip() for part in parts if part and part.strip()).strip()
                if merged:
                    return merged

    output = payload.get("output")
    if isinstance(output, list):
        parts: List[str] = []
        for item in output:
            if not isinstance(item, dict):
                continue
            for content in item.get("content") or []:
                if isinstance(content, dict) and content.get("type") == "output_text" and content.get("text"):
                    parts.append(str(content["text"]))
        merged = "\n".join(part.strip() for part in parts if part and part.strip()).strip()
        if merged:
            return merged

    return None


class AppHandler(BaseHTTPRequestHandler):
    server_version = "BananaProUI/1.0"

    def is_authenticated(self) -> bool:
        if not password_enabled():
            return True
        cookies = parse_cookie_header(self.headers.get("Cookie", ""))
        token = cookies.get(SESSION_COOKIE_NAME, "")
        if not token:
            return False
        expiry = SESSIONS.get(token)
        if expiry is None or time.time() > expiry:
            SESSIONS.pop(token, None)
            return False
        return True

    def ensure_authenticated(self) -> bool:
        if self.is_authenticated():
            return True
        json_response(self, 401, {"error": "未登录或登录已过期。", "authenticated": False})
        return False

    def read_json_body(self) -> dict:
        length = int(self.headers.get("Content-Length", "0") or "0")
        if length <= 0:
            return {}
        raw = self.rfile.read(length)
        if not raw:
            return {}
        return json.loads(raw.decode("utf-8"))

    def do_HEAD(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/api/history":
            if not self.is_authenticated():
                body = json.dumps({"error": "未登录或登录已过期。", "authenticated": False}, ensure_ascii=False).encode("utf-8")
                self.send_response(401)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                return
            try:
                payload = build_history_response_payload(parsed.query)
            except RuntimeError as exc:
                body = json.dumps({"error": str(exc)}, ensure_ascii=False).encode("utf-8")
                self.send_response(500)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                return
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            return

        if parsed.path.startswith("/generated/"):
            if not self.is_authenticated():
                self.send_response(401)
                self.end_headers()
                return
            relative = parsed.path.removeprefix("/generated/")
            target = GENERATED_DIR / relative
            return self.serve_file(target, headers_only=True)

        if parsed.path == "/":
            return self.serve_file(PUBLIC_DIR / "index.html", headers_only=True)

        static_target = PUBLIC_DIR / parsed.path.lstrip("/")
        if static_target.exists():
            return self.serve_file(static_target, headers_only=True)

        json_response(self, 404, {"error": "Not found"})

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/api/auth/status":
            return json_response(
                self,
                200,
                {
                    "authenticated": self.is_authenticated(),
                    "passwordEnabled": password_enabled(),
                },
            )

        if parsed.path == "/api/image-platforms":
            if not self.ensure_authenticated():
                return
            try:
                payload = build_image_platforms_response(read_image_platforms())
            except ImagePlatformConfigError as exc:
                return json_response(self, exc.status_code, {"error": str(exc)})
            return json_response(self, 200, payload)

        if parsed.path == "/api/prompt-library":
            if not self.ensure_authenticated():
                return
            content = read_prompt_library_content()
            return json_response(
                self,
                200,
                {
                    "items": parse_prompt_library_items(content),
                    "content": content,
                    "filename": PROMPT_LIBRARY_FILE.name,
                },
            )

        if parsed.path in {"/api/prompt-personas", "/api/prompt-skills"}:
            if not self.ensure_authenticated():
                return
            skills = read_prompt_skills()
            return json_response(
                self,
                200,
                {
                    "items": [
                        {
                            "id": skill["id"],
                            "name": skill["name"],
                            "summary": skill["summary"],
                            "filename": skill["filename"],
                        }
                        for skill in skills
                    ]
                },
            )

        if parsed.path.startswith("/api/prompt-personas/") or parsed.path.startswith("/api/prompt-skills/"):
            if not self.ensure_authenticated():
                return
            if parsed.path.startswith("/api/prompt-skills/"):
                skill_id = parsed.path.removeprefix("/api/prompt-skills/").strip()
            else:
                skill_id = parsed.path.removeprefix("/api/prompt-personas/").strip()
            skill = get_prompt_skill(skill_id)
            if not skill:
                return json_response(self, 404, {"error": "未找到对应的技能。"})
            return json_response(self, 200, skill)

        if parsed.path == "/api/history":
            if not self.ensure_authenticated():
                return
            try:
                payload = build_history_response_payload(parsed.query)
            except RuntimeError as exc:
                return json_response(self, 500, {"error": str(exc)})
            return json_response(self, 200, payload)

        if parsed.path.startswith("/generated/"):
            if not self.ensure_authenticated():
                return
            relative = parsed.path.removeprefix("/generated/")
            target = GENERATED_DIR / relative
            return self.serve_file(target)

        if parsed.path == "/":
            return self.serve_file(PUBLIC_DIR / "index.html")

        static_target = PUBLIC_DIR / parsed.path.lstrip("/")
        if static_target.exists():
            return self.serve_file(static_target)

        json_response(self, 404, {"error": "Not found"})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/api/auth/login":
            return self.handle_login()

        if parsed.path == "/api/auth/logout":
            return self.handle_logout()

        if parsed.path == "/api/prepare-image":
            if password_enabled() and not self.ensure_authenticated():
                return
            return self.handle_prepare_image()

        if parsed.path == "/api/history/download-zip":
            if not self.ensure_authenticated():
                return
            return self.handle_download_history_zip()

        if parsed.path == "/api/history/download-links":
            if not self.ensure_authenticated():
                return
            return self.handle_download_history_links()

        if parsed.path == "/api/generate":
            if not self.ensure_authenticated():
                return
            try:
                return self.handle_generate()
            except Exception as exc:
                return json_response(
                    self,
                    500,
                    {
                        "error": "服务端处理生成请求时发生异常。",
                        "details": str(exc),
                    },
                )

        if parsed.path == "/api/optimize-prompt":
            if not self.ensure_authenticated():
                return
            try:
                return self.handle_optimize_prompt()
            except Exception as exc:
                return json_response(
                    self,
                    500,
                    {
                        "error": "服务端处理提示词优化请求时发生异常。",
                        "details": str(exc),
                    },
                )

        if parsed.path == "/api/prompt-library":
            if not self.ensure_authenticated():
                return
            return self.handle_save_prompt_library()

        if parsed.path in {"/api/prompt-personas", "/api/prompt-skills"}:
            if not self.ensure_authenticated():
                return
            return self.handle_create_prompt_skill()

        json_response(self, 404, {"error": "Not found"})

    def do_PUT(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path.startswith("/api/prompt-personas/") or parsed.path.startswith("/api/prompt-skills/"):
            if not self.ensure_authenticated():
                return
            if parsed.path.startswith("/api/prompt-skills/"):
                skill_id = parsed.path.removeprefix("/api/prompt-skills/").strip()
            else:
                skill_id = parsed.path.removeprefix("/api/prompt-personas/").strip()
            if not skill_id:
                return json_response(self, 400, {"error": "缺少要更新的技能 id。"})
            return self.handle_update_prompt_skill(skill_id)

        json_response(self, 404, {"error": "Not found"})

    def do_DELETE(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path.startswith("/api/prompt-personas/") or parsed.path.startswith("/api/prompt-skills/"):
            if not self.ensure_authenticated():
                return
            if parsed.path.startswith("/api/prompt-skills/"):
                skill_id = parsed.path.removeprefix("/api/prompt-skills/").strip()
            else:
                skill_id = parsed.path.removeprefix("/api/prompt-personas/").strip()
            if not skill_id:
                return json_response(self, 400, {"error": "缺少要删除的技能 id。"})
            return self.handle_delete_prompt_skill(skill_id)

        if parsed.path.startswith("/api/history/"):
            if not self.ensure_authenticated():
                return
            item_id = parsed.path.removeprefix("/api/history/").strip()
            if not item_id:
                return json_response(self, 400, {"error": "缺少要删除的记录 id。"})
            return self.handle_delete_history(item_id)

        json_response(self, 404, {"error": "Not found"})

    def serve_file(self, target: Path, headers_only: bool = False) -> None:
        if not target.exists() or not target.is_file():
            return json_response(self, 404, {"error": "File not found"})

        mime_type, _ = mimetypes.guess_type(str(target))
        mime_type = mime_type or "application/octet-stream"
        content = target.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", mime_type)
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        if not headers_only:
            self.wfile.write(content)

    def handle_login(self) -> None:
        if not password_enabled():
            return json_response(self, 200, {"ok": True, "authenticated": True, "passwordEnabled": False})

        try:
            payload = self.read_json_body()
        except Exception:
            return json_response(self, 400, {"error": "登录请求格式不正确。"})

        password = str(payload.get("password", ""))
        if password != get_ui_password():
            return json_response(self, 401, {"error": "密码错误。", "authenticated": False})

        token = make_session_token(password)
        SESSIONS[token] = time.time() + SESSION_MAX_AGE
        save_sessions()
        cookie = (
            f"{SESSION_COOKIE_NAME}={token}; Path=/; "
            f"Max-Age={SESSION_MAX_AGE}; HttpOnly; SameSite=Lax"
        )
        return json_response(
            self,
            200,
            {"ok": True, "authenticated": True, "passwordEnabled": True},
            extra_headers=[("Set-Cookie", cookie)],
        )

    def handle_logout(self) -> None:
        cookies = parse_cookie_header(self.headers.get("Cookie", ""))
        token = cookies.get(SESSION_COOKIE_NAME, "")
        if token:
            SESSIONS.pop(token, None)
            save_sessions()
        return json_response(
            self,
            200,
            {"ok": True, "authenticated": False},
            extra_headers=[("Set-Cookie", f"{SESSION_COOKIE_NAME}=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax")],
        )

    def handle_prepare_image(self) -> None:
        form = self.parse_multipart_form()
        if form is None:
            return

        upload = form.get("image")
        if not isinstance(upload, dict):
            return json_response(self, 400, {"error": "缺少待处理的图片文件。"})

        try:
            converted = convert_uploaded_image_to_jpeg(
                upload["data"],
                str(upload.get("filename", "upload-image")),
                str(upload.get("mime_type", "")),
            )
        except Exception as exc:
            return json_response(
                self,
                415,
                {
                    "error": "当前图片格式无法直接预览，也没能自动转换成 JPG。",
                    "details": str(exc),
                },
            )

        download_name = f"{Path(str(upload.get('filename', 'upload-image'))).stem or 'upload-image'}.jpg"
        return binary_response(
            self,
            200,
            converted,
            "image/jpeg",
            extra_headers=[
                ("Content-Disposition", f"inline; filename*=UTF-8''{quote(download_name)}"),
                ("Cache-Control", "no-store"),
            ],
        )

    def handle_generate(self) -> None:
        if Image is None:
            return json_response(
                self,
                500,
                {"error": "缺少 Pillow 依赖，无法生成历史缩略图，请先安装 Pillow。"},
            )

        try:
            oss_config = get_oss_config()
        except RuntimeError as exc:
            return json_response(
                self,
                500,
                {
                    "error": "OSS 配置无效。",
                    "details": str(exc),
                },
            )

        form = self.parse_multipart_form()
        if form is None:
            return

        selected_platform_id = str(form.get("apiPlatformId", "")).strip()
        requested_image_model = str(form.get("imageModel", "")).strip()
        try:
            image_platform = resolve_image_generation_platform(selected_platform_id, requested_image_model)
        except ImagePlatformConfigError as exc:
            return json_response(self, exc.status_code, {"error": str(exc)})

        prompt = form.get("prompt", "")
        source_prompt = form.get("sourcePrompt", "")
        prompt_mode = form.get("promptMode", "default")
        aspect_ratio = form.get("aspectRatio", "auto")
        image_size = map_output_size(form.get("imageSize", "4K"))
        enable_search = form.get("enableSearch", "false").lower() == "true"

        if not str(prompt).strip():
            return json_response(self, 400, {"error": "提示词不能为空，请先输入提示词。"})

        base_upload = form.get("baseImage")
        if not isinstance(base_upload, dict):
            return json_response(self, 400, {"error": "基础图为必填项。"})

        ref_uploads = normalize_upload_list(form.get("referenceImages"))
        if len(ref_uploads) > 6:
            return json_response(self, 400, {"error": "参考图最多上传 6 张。"})

        if aspect_ratio == "auto":
            detected = detect_image_size(base_upload["data"], base_upload["mime_type"])
            if detected:
                aspect_ratio = aspect_ratio_label(*detected)
            else:
                aspect_ratio = "1:1"

        payload = build_payload(
            prompt=prompt,
            base_image=base_upload,
            reference_images=ref_uploads,
            aspect_ratio=aspect_ratio,
            image_size=image_size,
            enable_search=enable_search,
        )

        try:
            body = json.dumps(payload).encode("utf-8")
            response_payload = post_json_to_upstream(
                api_url=image_platform["api_url"],
                body=body,
                api_key=image_platform["api_key"],
                timeout=TIMEOUT_MAP.get(image_size, 300),
            )
        except UpstreamHttpError as exc:
            return json_response(
                self,
                exc.status_code,
                {
                    "error": "上游接口返回错误。",
                    "details": exc.details,
                },
            )
        except Exception as exc:
            return json_response(
                self,
                502,
                {
                    "error": "调用图生图接口失败。",
                    "details": str(exc),
                },
            )

        image_bytes, mime_type, message = parse_response_image(response_payload)
        if not image_bytes or not mime_type:
            return json_response(
                self,
                502,
                {
                    "error": "接口返回成功，但没有拿到图片数据。",
                    "details": message or response_payload,
                },
            )

        now = datetime.now()
        file_id = f"{now.strftime('%Y%m%d-%H%M%S')}-{uuid.uuid4().hex[:8]}"
        filename = f"{file_id}{extension_for_mime(mime_type)}"
        output_path = GENERATED_DIR / filename
        output_path.write_bytes(image_bytes)
        thumb_path = get_thumbnail_path(file_id)

        try:
            build_thumbnail(output_path, thumb_path)
        except Exception as exc:
            try:
                if output_path.exists():
                    output_path.unlink()
            except OSError:
                pass
            return json_response(
                self,
                500,
                {
                    "error": "结果图已生成，但缩略图创建失败。",
                    "details": str(exc),
                },
            )

        oss_image_url: Optional[str] = None
        oss_thumb_url: Optional[str] = None
        oss_metadata_url: Optional[str] = None
        oss_image_key: Optional[str] = None
        oss_thumb_key: Optional[str] = None
        oss_metadata_key: Optional[str] = None
        oss_error: Optional[str] = None
        if oss_config:
            try:
                metadata_filename = f"{file_id}.xml"
                metadata_bytes = build_generation_metadata_xml(
                    file_id=file_id,
                    created_at=now,
                    prompt=prompt,
                    source_prompt=source_prompt or prompt,
                    prompt_mode=prompt_mode,
                    aspect_ratio=aspect_ratio,
                    image_size=image_size,
                    enable_search=enable_search,
                    base_image_name=base_upload["filename"],
                    base_image_sha256=base_upload.get("sha256", ""),
                    reference_count=len(ref_uploads),
                    api_platform_id=image_platform["platform_id"],
                    api_platform_name=image_platform["platform_name"],
                    image_model=image_platform["image_model"],
                    mime_type=mime_type,
                )
                uploaded = upload_generated_assets_to_oss(
                    config=oss_config,
                    now=now,
                    image_filename=filename,
                    image_bytes=image_bytes,
                    image_mime_type=mime_type,
                    thumb_filename=thumb_path.name,
                    thumb_bytes=thumb_path.read_bytes(),
                    metadata_filename=metadata_filename,
                    metadata_bytes=metadata_bytes,
                )
                oss_image_url = uploaded["image_url"]
                oss_thumb_url = uploaded["thumb_url"]
                oss_metadata_url = uploaded["metadata_url"]
                oss_image_key = uploaded["image_key"]
                oss_thumb_key = uploaded["thumb_key"]
                oss_metadata_key = uploaded["metadata_key"]
            except Exception as exc:
                oss_error = str(exc)

        entry = {
            "id": file_id,
            "createdAt": now.isoformat(timespec="seconds"),
            "prompt": prompt,
            "sourcePrompt": source_prompt or prompt,
            "promptMode": prompt_mode,
            "aspectRatio": aspect_ratio,
            "imageSize": image_size,
            "enableSearch": enable_search,
            "baseImageName": base_upload["filename"],
            "baseImageSha256": base_upload.get("sha256", ""),
            "referenceCount": len(ref_uploads),
            "apiPlatformId": image_platform["platform_id"],
            "apiPlatformName": image_platform["platform_name"],
            "imageModel": image_platform["image_model"],
            "imageUrl": f"/generated/{filename}",
            "thumbUrl": get_thumbnail_url(file_id),
            "downloadName": f"banana-pro-{file_id}{extension_for_mime(mime_type)}",
            "message": message,
        }
        if oss_image_url:
            entry["ossImageUrl"] = oss_image_url
        if oss_thumb_url:
            entry["ossThumbUrl"] = oss_thumb_url
        if oss_image_key:
            entry["ossImageKey"] = oss_image_key
        if oss_thumb_key:
            entry["ossThumbKey"] = oss_thumb_key
        if oss_metadata_url:
            entry["ossMetadataXmlUrl"] = oss_metadata_url
        if oss_metadata_key:
            entry["ossMetadataXmlKey"] = oss_metadata_key
        if oss_error:
            entry["ossUploadError"] = oss_error
        append_history(entry)
        json_response(self, 200, entry)

    def handle_save_prompt_library(self) -> None:
        try:
            payload = self.read_json_body()
        except Exception:
            return json_response(self, 400, {"error": "提示词列表请求格式不正确。"})

        content = str(payload.get("content", ""))
        write_prompt_library_content(content)
        saved = read_prompt_library_content()
        return json_response(
            self,
            200,
            {
                "ok": True,
                "items": parse_prompt_library_items(saved),
                "content": saved,
                "filename": PROMPT_LIBRARY_FILE.name,
            },
        )

    def handle_create_prompt_skill(self) -> None:
        try:
            payload = self.read_json_body()
        except Exception:
            return json_response(self, 400, {"error": "技能上传请求格式不正确。"})

        skill, error = create_prompt_skill(payload)
        if error:
            return json_response(self, 400, {"error": error})
        return json_response(self, 200, {"ok": True, "item": skill})

    def handle_update_prompt_skill(self, skill_id: str) -> None:
        try:
            payload = self.read_json_body()
        except Exception:
            return json_response(self, 400, {"error": "技能保存请求格式不正确。"})

        skill, error = update_prompt_skill(skill_id, payload)
        if error:
            status = 404 if "未找到" in error else 400
            return json_response(self, status, {"error": error})
        return json_response(self, 200, {"ok": True, "item": skill})

    def handle_optimize_prompt(self) -> None:
        api_key = get_setting("BANANA_PRO_LLM_API_KEY") or get_default_image_platform_api_key()
        api_url = get_setting("BANANA_PRO_LLM_API_URL")
        llm_model = get_setting("BANANA_PRO_LLM_MODEL", DEFAULT_PROMPT_OPTIMIZER_MODEL).strip() or DEFAULT_PROMPT_OPTIMIZER_MODEL
        if not api_url:
            legacy_base_url = get_setting("BANANA_PRO_LLM_API_BASE_URL", DEFAULT_PROMPT_OPTIMIZER_BASE_URL)
            api_url = build_prompt_optimizer_url(legacy_base_url)
        if not api_url:
            api_url = DEFAULT_PROMPT_OPTIMIZER_URL

        if not api_key:
            return json_response(
                self,
                500,
                {"error": "缺少 BANANA_PRO_LLM_API_KEY，且默认图片平台也没有可用 key，请先在 .env 或 data/api-platforms.xml 中配置。"},
            )

        content_type = self.headers.get("Content-Type", "")
        base_image = None
        if "multipart/form-data" in content_type:
            payload = self.parse_multipart_form()
            if payload is None:
                return
            if isinstance(payload.get("baseImage"), dict):
                base_image = payload.get("baseImage")
        else:
            try:
                payload = self.read_json_body()
            except Exception:
                return json_response(self, 400, {"error": "提示词优化请求格式不正确。"})

        prompt = str(payload.get("prompt", "")).strip()
        skill_id = str(payload.get("skillId", "")).strip() or str(payload.get("personaId", "")).strip()
        if not prompt:
            return json_response(self, 400, {"error": "请先提供需要优化的提示词。"})

        skills = read_prompt_skills()
        if not skills:
            return json_response(self, 500, {"error": "未找到可用的技能文件，请先在 data/skills 中添加 .md 文件。旧版 data/personas 目录也会自动兼容。"})

        skill = get_prompt_skill(skill_id) if skill_id else skills[0]
        if not skill:
            return json_response(self, 400, {"error": "未找到对应的技能，请重新选择。"})

        request_payload = build_prompt_optimizer_payload(prompt, skill["content"], llm_model, base_image=base_image)
        request_body = json.dumps(request_payload).encode("utf-8")

        try:
            response_payload = post_json_to_upstream(
                api_url=api_url,
                body=request_body,
                api_key=api_key,
                timeout=PROMPT_OPTIMIZER_TIMEOUT,
            )
        except UpstreamHttpError as exc:
            return json_response(
                self,
                exc.status_code,
                {
                    "error": "上游提示词优化接口返回错误。",
                    "details": exc.details,
                },
            )
        except Exception as exc:
            return json_response(
                self,
                502,
                {
                    "error": "调用提示词优化接口失败。",
                    "details": str(exc),
                },
            )

        optimized_prompt = extract_text_from_llm_response(response_payload)
        if not optimized_prompt:
            return json_response(
                self,
                502,
                {
                    "error": "提示词优化接口返回成功，但没有拿到文本结果。",
                    "details": response_payload,
                },
            )

        json_response(
            self,
            200,
            {
                "prompt": optimized_prompt,
                "model": llm_model,
                "skillId": skill["id"],
                "skillName": skill["name"],
                "usedBaseImage": bool(base_image),
                "personaId": skill["id"],
                "personaName": skill["name"],
            },
        )

    def handle_delete_history(self, item_id: str) -> None:
        removed = delete_history_item(item_id)
        if removed is None:
            return json_response(self, 404, {"error": "未找到对应的历史记录。"})

        # Keep OSS objects intentionally; only local cache files are removed.
        image_target = resolve_generated_path(str(removed.get("imageUrl", "")))
        if image_target:
            try:
                if image_target.exists():
                    image_target.unlink()
            except OSError:
                pass

        thumb_target = resolve_generated_path(str(removed.get("thumbUrl", ""))) or get_thumbnail_path(item_id)
        try:
            if thumb_target and thumb_target.exists():
                thumb_target.unlink()
        except OSError:
            pass

        json_response(self, 200, {"ok": True, "id": item_id})

    def handle_download_history_zip(self) -> None:
        try:
            payload = self.read_json_body()
        except Exception:
            return json_response(self, 400, {"error": "请求格式不正确。"})

        raw_ids = payload.get("ids")
        if not isinstance(raw_ids, list):
            return json_response(self, 400, {"error": "缺少要下载的图片 id 列表。"})

        selected_ids: List[str] = []
        for item in raw_ids:
            item_id = str(item).strip()
            if item_id and item_id not in selected_ids:
                selected_ids.append(item_id)

        if not selected_ids:
            return json_response(self, 400, {"error": "请至少选择一张图片。"})

        history_map = {
            str(entry.get("id")): entry
            for entry in read_history()
            if isinstance(entry, dict) and entry.get("id")
        }
        selected_entries = [history_map[item_id] for item_id in selected_ids if item_id in history_map]
        if not selected_entries:
            return json_response(self, 404, {"error": "未找到所选历史记录。"})

        generated_root = GENERATED_DIR.resolve()
        zip_buffer = io.BytesIO()
        used_names: Dict[str, int] = {}
        added_count = 0

        with zipfile.ZipFile(zip_buffer, mode="w", compression=zipfile.ZIP_DEFLATED) as archive:
            for entry in selected_entries:
                image_url = entry.get("imageUrl", "")
                if not isinstance(image_url, str) or not image_url.startswith("/generated/"):
                    continue

                relative = image_url.removeprefix("/generated/")
                target = (GENERATED_DIR / relative).resolve()

                try:
                    target.relative_to(generated_root)
                except ValueError:
                    continue

                if not target.exists() or not target.is_file():
                    continue

                preferred_name = str(entry.get("downloadName", "")).strip() or target.name
                safe_filename = safe_name(preferred_name) or target.name
                stem = Path(safe_filename).stem or "banana-pro-image"
                suffix = Path(safe_filename).suffix or target.suffix or ".png"

                key = f"{stem}{suffix}"
                duplicate_count = used_names.get(key, 0)
                used_names[key] = duplicate_count + 1
                archive_name = key if duplicate_count == 0 else f"{stem}-{duplicate_count}{suffix}"

                archive.write(target, arcname=archive_name)
                added_count += 1

        if added_count == 0:
            return json_response(self, 404, {"error": "所选图片文件不存在或无法读取。"})

        body = zip_buffer.getvalue()
        zip_name = f"banana-pro-history-{datetime.now().strftime('%Y%m%d-%H%M%S')}.zip"
        encoded_filename = quote(zip_name)

        self.send_response(200)
        self.send_header("Content-Type", "application/zip")
        self.send_header("Content-Length", str(len(body)))
        self.send_header(
            "Content-Disposition",
            f"attachment; filename=\"{zip_name}\"; filename*=UTF-8''{encoded_filename}",
        )
        self.end_headers()
        self.wfile.write(body)

    def handle_download_history_links(self) -> None:
        try:
            payload = self.read_json_body()
        except Exception:
            return json_response(self, 400, {"error": "请求格式不正确。"})

        raw_ids = payload.get("ids")
        if not isinstance(raw_ids, list):
            return json_response(self, 400, {"error": "缺少要下载的图片 id 列表。"})

        selected_ids: List[str] = []
        for item in raw_ids:
            item_id = str(item).strip()
            if item_id and item_id not in selected_ids:
                selected_ids.append(item_id)

        if not selected_ids:
            return json_response(self, 400, {"error": "请至少选择一张图片。"})

        history_map = {
            str(entry.get("id")): entry
            for entry in read_history()
            if isinstance(entry, dict) and entry.get("id")
        }
        selected_entries = [history_map[item_id] for item_id in selected_ids if item_id in history_map]
        if not selected_entries:
            return json_response(self, 404, {"error": "未找到所选历史记录。"})

        try:
            oss_config = get_oss_config()
        except RuntimeError:
            oss_config = None

        items: List[dict] = []
        skipped = 0
        for entry in selected_entries:
            item_id = str(entry.get("id", "")).strip()
            download_name = str(entry.get("downloadName", "")).strip() or "banana-pro-image"

            signed_oss_url: Optional[str] = None
            if oss_config:
                object_key = str(entry.get("ossImageKey", "")).strip()
                if not object_key:
                    object_key = infer_oss_object_key_from_url(str(entry.get("ossImageUrl", "")))
                if object_key:
                    try:
                        signed_oss_url = build_oss_signed_download_url(oss_config, object_key, download_name)
                    except Exception:
                        signed_oss_url = None

            if signed_oss_url:
                items.append(
                    {
                        "id": item_id,
                        "url": signed_oss_url,
                        "downloadName": download_name,
                        "source": "oss-signed",
                    }
                )
                continue

            local_url = str(entry.get("imageUrl", "")).strip()
            if local_url:
                items.append(
                    {
                        "id": item_id,
                        "url": local_url,
                        "downloadName": download_name,
                        "source": "local",
                    }
                )
                continue

            skipped += 1

        return json_response(
            self,
            200,
            {
                "items": items,
                "requested": len(selected_entries),
                "skipped": skipped,
            },
        )

    def handle_delete_prompt_skill(self, skill_id: str) -> None:
        if not delete_prompt_skill(skill_id):
            return json_response(self, 404, {"error": "未找到对应的技能文件。"})
        json_response(self, 200, {"ok": True, "id": skill_id})

    def handle_create_prompt_persona(self) -> None:
        return self.handle_create_prompt_skill()

    def handle_update_prompt_persona(self, persona_id: str) -> None:
        return self.handle_update_prompt_skill(persona_id)

    def handle_delete_prompt_persona(self, persona_id: str) -> None:
        return self.handle_delete_prompt_skill(persona_id)

    def parse_multipart_form(self) -> Optional[dict]:
        content_type = self.headers.get("Content-Type", "")
        if "multipart/form-data" not in content_type:
            json_response(self, 400, {"error": "请求必须为 multipart/form-data。"})
            return None

        try:
            content_length = int(self.headers.get("Content-Length", "0") or "0")
        except ValueError:
            json_response(self, 400, {"error": "请求体长度不正确。"})
            return None

        if content_length <= 0:
            json_response(self, 400, {"error": "请求体为空。"})
            return None

        try:
            raw_body = self.rfile.read(content_length)
            message = BytesParser(policy=email_policy_default).parsebytes(
                f"Content-Type: {content_type}\r\nMIME-Version: 1.0\r\n\r\n".encode("utf-8") + raw_body
            )
        except Exception as exc:
            json_response(self, 400, {"error": f"解析表单失败：{exc}"})
            return None

        data: dict = {}
        for field in message.iter_parts():
            key = str(field.get_param("name", header="content-disposition") or "").strip()
            if not key:
                continue
            normalized = self.normalize_field(field)
            if normalized is None:
                continue
            if key in data:
                existing = data[key]
                if isinstance(existing, list):
                    existing.append(normalized)
                else:
                    data[key] = [existing, normalized]
            else:
                data[key] = normalized
        return data

    def normalize_field(self, field) -> Optional[object]:
        filename = field.get_filename()
        if filename:
            raw = field.get_payload(decode=True) or b""
            if not raw:
                return None
            filename = safe_name(filename)
            mime_type = infer_mime_type(filename, field.get_content_type(), raw)
            return {
                "filename": filename,
                "mime_type": mime_type,
                "data": raw,
                "base64": base64.b64encode(raw).decode("utf-8"),
                "sha256": hashlib.sha256(raw).hexdigest(),
            }
        raw = field.get_payload(decode=True) or b""
        charset = field.get_content_charset() or "utf-8"
        try:
            value = raw.decode(charset)
        except Exception:
            value = raw.decode("utf-8", errors="replace")
        return value if value != "" else None

    def log_message(self, fmt: str, *args) -> None:
        sys.stdout.write("%s - - [%s] %s\n" % (
            self.address_string(),
            self.log_date_time_string(),
            fmt % args,
        ))


def main() -> None:
    ensure_dirs()
    load_sessions()
    host = get_setting("BANANA_PRO_HOST", "127.0.0.1")
    port = int(get_setting("BANANA_PRO_PORT", str(DEFAULT_PORT)))
    server = ThreadingHTTPServer((host, port), AppHandler)
    print(f"Banana Pro UI is running on http://{host}:{port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
