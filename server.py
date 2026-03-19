import base64
import cgi
import hashlib
import json
import mimetypes
import os
import re
import secrets
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from urllib.parse import urlparse


ROOT_DIR = Path(__file__).resolve().parent
PUBLIC_DIR = ROOT_DIR / "public"
DATA_DIR = ROOT_DIR / "data"
GENERATED_DIR = DATA_DIR / "generated"
HISTORY_FILE = DATA_DIR / "history.json"
ENV_FILE = ROOT_DIR / ".env"

DEFAULT_API_URL = "https://api.apiyi.com/v1beta/models/gemini-3-pro-image-preview:generateContent"
DEFAULT_PORT = 8787
TIMEOUT_MAP = {"1K": 180, "2K": 300, "4K": 360}
SESSION_COOKIE_NAME = "banana_ui_session"
SESSIONS: Dict[str, bool] = {}


def ensure_dirs() -> None:
    PUBLIC_DIR.mkdir(parents=True, exist_ok=True)
    GENERATED_DIR.mkdir(parents=True, exist_ok=True)
    if not HISTORY_FILE.exists():
        HISTORY_FILE.write_text("[]", encoding="utf-8")


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
    write_history(history[:60])


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
    if provided and "/" in provided:
        return provided
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
    return mapping.get(size_key.upper(), "2K")


def build_prompt(user_prompt: str, reference_count: int) -> str:
    base = "你将收到一张基础结构图作为主要约束，请严格保留基础图的主体构图、空间关系和关键结构。"
    if reference_count > 0:
        base += (
            f"另外还会提供 {reference_count} 张参考图，它们只用于借鉴风格、材质、灯光、色彩和氛围，不要直接复制参考图中的具体主体内容。"
        )
    else:
        base += "没有提供风格参考图时，请根据提示词自行补足材质、光线与氛围。"

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
        {"text": "第 1 张图是基础结构图，请以它为核心。"},
        {
            "inlineData": {
                "mimeType": base_image["mime_type"],
                "data": base_image["base64"],
            }
        }
    ]

    for index, image in enumerate(reference_images, start=1):
        parts.append(
            {"text": f"第 {index} 张参考图，仅用于风格参考。"},
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


class AppHandler(BaseHTTPRequestHandler):
    server_version = "BananaProUI/1.0"

    def is_authenticated(self) -> bool:
        if not password_enabled():
            return True
        cookies = parse_cookie_header(self.headers.get("Cookie", ""))
        token = cookies.get(SESSION_COOKIE_NAME, "")
        return bool(token and token in SESSIONS)

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
            body = json.dumps({"items": read_history()}, ensure_ascii=False).encode("utf-8")
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

        if parsed.path == "/api/history":
            if not self.ensure_authenticated():
                return
            history = read_history()
            return json_response(self, 200, {"items": history})

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

        json_response(self, 404, {"error": "Not found"})

    def do_DELETE(self) -> None:
        parsed = urlparse(self.path)
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
        SESSIONS[token] = True
        cookie = f"{SESSION_COOKIE_NAME}={token}; Path=/; HttpOnly; SameSite=Lax"
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
        return json_response(
            self,
            200,
            {"ok": True, "authenticated": False},
            extra_headers=[("Set-Cookie", f"{SESSION_COOKIE_NAME}=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax")],
        )

    def handle_generate(self) -> None:
        api_key = get_setting("BANANA_PRO_API_KEY")
        api_url = get_setting("BANANA_PRO_API_URL", DEFAULT_API_URL)

        if not api_key:
            return json_response(
                self,
                500,
                {"error": "缺少 BANANA_PRO_API_KEY，请先在 .env 中配置。"},
            )

        form = self.parse_multipart_form()
        if form is None:
            return

        prompt = form.get("prompt", "")
        aspect_ratio = form.get("aspectRatio", "auto")
        image_size = map_output_size(form.get("imageSize", "2K"))
        enable_search = form.get("enableSearch", "false").lower() == "true"

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
            request = urllib.request.Request(
                api_url,
                data=body,
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {api_key}",
                },
                method="POST",
            )
            with urllib.request.urlopen(request, timeout=TIMEOUT_MAP.get(image_size, 300)) as response:
                response_payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            return json_response(
                self,
                exc.code,
                {
                    "error": "上游接口返回错误。",
                    "details": raw,
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

        entry = {
            "id": file_id,
            "createdAt": now.isoformat(timespec="seconds"),
            "prompt": prompt,
            "aspectRatio": aspect_ratio,
            "imageSize": image_size,
            "enableSearch": enable_search,
            "baseImageName": base_upload["filename"],
            "referenceCount": len(ref_uploads),
            "imageUrl": f"/generated/{filename}",
            "downloadName": f"banana-pro-{file_id}{extension_for_mime(mime_type)}",
            "message": message,
        }
        append_history(entry)
        json_response(self, 200, entry)

    def handle_delete_history(self, item_id: str) -> None:
        removed = delete_history_item(item_id)
        if removed is None:
            return json_response(self, 404, {"error": "未找到对应的历史记录。"})

        image_url = removed.get("imageUrl", "")
        if isinstance(image_url, str) and image_url.startswith("/generated/"):
            target = GENERATED_DIR / image_url.removeprefix("/generated/")
            try:
                if target.exists():
                    target.unlink()
            except OSError:
                pass

        json_response(self, 200, {"ok": True, "id": item_id})

    def parse_multipart_form(self) -> Optional[dict]:
        content_type = self.headers.get("Content-Type", "")
        if "multipart/form-data" not in content_type:
            json_response(self, 400, {"error": "请求必须为 multipart/form-data。"})
            return None

        environ = {
            "REQUEST_METHOD": "POST",
            "CONTENT_TYPE": content_type,
        }
        try:
            form = cgi.FieldStorage(
                fp=self.rfile,
                headers=self.headers,
                environ=environ,
            )
        except Exception as exc:
            json_response(self, 400, {"error": f"解析表单失败：{exc}"})
            return None

        data: dict = {}
        for key in form.keys():
            field = form[key]
            if isinstance(field, list):
                values = [self.normalize_field(item) for item in field]
                data[key] = [item for item in values if item is not None]
            else:
                normalized = self.normalize_field(field)
                if normalized is not None:
                    data[key] = normalized
        return data

    def normalize_field(self, field) -> Optional[object]:
        if getattr(field, "filename", None):
            raw = field.file.read()
            if not raw:
                return None
            filename = safe_name(field.filename)
            mime_type = infer_mime_type(filename, field.type, raw)
            return {
                "filename": filename,
                "mime_type": mime_type,
                "data": raw,
                "base64": base64.b64encode(raw).decode("utf-8"),
            }
        value = field.value
        return value if value != "" else None

    def log_message(self, fmt: str, *args) -> None:
        sys.stdout.write("%s - - [%s] %s\n" % (
            self.address_string(),
            self.log_date_time_string(),
            fmt % args,
        ))


def main() -> None:
    ensure_dirs()
    host = get_setting("BANANA_PRO_HOST", "127.0.0.1")
    port = int(get_setting("BANANA_PRO_PORT", str(DEFAULT_PORT)))
    server = ThreadingHTTPServer((host, port), AppHandler)
    print(f"Banana Pro UI is running on http://{host}:{port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
