const form = document.getElementById("generate-form");
const baseInputs = Array.from(document.querySelectorAll('input[data-upload-input="base"]'));
const referenceInputs = Array.from(document.querySelectorAll('input[data-upload-input="reference"]'));
const basePreview = document.getElementById("base-preview");
const referencePreview = document.getElementById("reference-preview");
const basePreviewMobile = document.getElementById("base-preview-m");
const referencePreviewMobile = document.getElementById("reference-preview-m");
const submitButton = document.getElementById("submit-button");
const submitButtonMobile = document.getElementById("submit-button-mobile");
const formStatus = document.getElementById("form-status");
const formStatusMobile = document.getElementById("form-status-mobile");
const resultStage = document.getElementById("result-stage");
const resultMeta = document.getElementById("result-meta");
const resultStageMobile = document.getElementById("result-stage-mobile");
const resultMetaMobile = document.getElementById("result-meta-mobile");
const historyList = document.getElementById("history-list");
const historyTemplate = document.getElementById("history-item-template");
const refreshHistoryButton = document.getElementById("refresh-history");
const historyPagination = document.getElementById("history-pagination");
const historyPaginationSummary = document.getElementById("history-pagination-summary");
const historyPageInfo = document.getElementById("history-page-info");
const historyPrevPageButton = document.getElementById("history-prev-page");
const historyNextPageButton = document.getElementById("history-next-page");
const progressTemplate = document.getElementById("progress-template");
const authOverlay = document.getElementById("auth-overlay");
const loginForm = document.getElementById("login-form");
const passwordInput = document.getElementById("password-input");
const authStatus = document.getElementById("auth-status");
const logoutButton = document.getElementById("logout-button");
const apiPlatformSelect = document.getElementById("api-platform-select");
const imageModelSelect = document.getElementById("image-model-select");
const apiPlatformSelectMobile = document.getElementById("api-platform-select-m");
const imageModelSelectMobile = document.getElementById("image-model-select-m");
const apiPlatformHint = document.getElementById("api-platform-hint");
const promptLibrarySelect = document.getElementById("prompt-library-select");
const managePromptLibraryButton = document.getElementById("manage-prompt-library-button");
const promptTextarea = document.getElementById("prompt");
const promptTextareaMobile = document.getElementById("prompt-m");
const optimizePromptButton = document.getElementById("optimize-prompt-button");
const optimizedPromptPanel = document.getElementById("optimized-prompt-panel");
const optimizedPromptTextarea = document.getElementById("optimized-prompt");
const optimizeProgress = document.getElementById("optimize-progress");
const optimizeStatus = document.getElementById("optimize-status");
const promptPersonaSelect = document.getElementById("prompt-persona-select");
const managePromptPersonaButton = document.getElementById("manage-prompt-persona-button");
const promptPersonaSummary = document.getElementById("prompt-persona-summary");
const aspectRatioGroup = document.getElementById("aspect-ratio-group");
const ratioOrientationPreview = document.getElementById("ratio-orientation-preview");
const promptLibraryModal = document.getElementById("prompt-library-modal");
const closePromptLibraryModalButton = document.getElementById("close-prompt-library-modal");
const promptLibraryEditor = document.getElementById("prompt-library-editor");
const savePromptLibraryButton = document.getElementById("save-prompt-library-button");
const promptLibraryModalStatus = document.getElementById("prompt-library-modal-status");
const promptPersonaModal = document.getElementById("prompt-persona-modal");
const closePromptPersonaModalButton = document.getElementById("close-prompt-persona-modal");
const promptPersonaFileInput = document.getElementById("prompt-persona-file");
const uploadPromptPersonaButton = document.getElementById("upload-prompt-persona-button");
const promptPersonaList = document.getElementById("prompt-persona-list");
const createPromptPersonaButton = document.getElementById("create-prompt-persona-button");
const promptPersonaFilename = document.getElementById("prompt-persona-filename");
const promptPersonaFilenameInput = document.getElementById("prompt-persona-filename-input");
const promptPersonaNameInput = document.getElementById("prompt-persona-name-input");
const promptPersonaSummaryInput = document.getElementById("prompt-persona-summary-input");
const promptPersonaContentInput = document.getElementById("prompt-persona-content-input");
const savePromptPersonaButton = document.getElementById("save-prompt-persona-button");
const deletePromptPersonaButton = document.getElementById("delete-prompt-persona-button");
const promptPersonaModalStatus = document.getElementById("prompt-persona-modal-status");

const state = {
  currentResult: null,
  progressTimer: null,
  optimizeProgressTimer: null,
  progressValue: 0,
  optimizeProgressValue: 0,
  notificationPermissionPromise: null,
  historyHydrated: false,
  historyPage: 1,
  historyPageSize: 10,
  historyTotal: 0,
  historyTotalPages: 0,
  audioContext: null,
  authenticated: false,
  passwordEnabled: false,
  baseImageFile: null,
  referenceFiles: [],
  apiPlatforms: [],
  selectedApiPlatformId: "",
  selectedImageModel: "",
  promptLibrary: [],
  promptLibraryContent: "",
  promptPersonas: [],
  selectedPromptPersonaId: "",
  editingPromptPersonaId: "",
  editingPromptPersonaFilename: "",
  creatingPromptPersona: false,
  optimizedPrompt: "",
  optimizingPrompt: false,
  titleFlashTimer: null,
  promptCursorTarget: "",
  promptCursorStart: 0,
  promptCursorEnd: 0,
};

const progressSteps = [
  { value: 12, label: "正在上传素材" },
  { value: 28, label: "正在整理提示词" },
  { value: 46, label: "Banana Pro 正在理解基础图" },
  { value: 68, label: "正在融合风格参考" },
  { value: 84, label: "正在渲染细节" },
  { value: 94, label: "正在整理结果" },
];

const optimizeProgressSteps = [
  { value: 16, label: "正在发送提示词" },
  { value: 44, label: "GPT-5.4 正在翻译优化" },
  { value: 72, label: "正在整理英文提示词" },
  { value: 92, label: "即将完成" },
];

const MB = 1024 * 1024;
const BASE_IMAGE_MAX_BYTES = 4 * MB;
const BASE_IMAGE_MAX_LONG_EDGE = 4000;
const BASE_IMAGE_QUALITY = 0.85;
const REFERENCE_IMAGE_MAX_BYTES = 2 * MB;
const REFERENCE_IMAGE_LIMIT = 6;
const HISTORY_PAGE_SIZE = 10;
const IMAGE_TRANSFER_QUEUE_KEY = "banana-pro-image-transfer-queue";
const DEFAULT_PAGE_TITLE = document.title;
const SAFE_PREVIEW_MIME_TYPES = new Set(["image/jpeg", "image/png", "image/webp", "image/gif", "image/avif"]);
const FORCE_JPEG_EXTENSIONS = new Set(["heic", "heif", "heics", "heifs", "bmp", "dib", "tif", "tiff"]);
const FORCE_JPEG_MIME_KEYWORDS = ["heic", "heif", "tiff", "bmp"];
const basePreviewTargets = [basePreview, basePreviewMobile].filter(Boolean);
const referencePreviewTargets = [referencePreview, referencePreviewMobile].filter(Boolean);
const submitButtons = [submitButton, submitButtonMobile].filter(Boolean);
const formStatusNodes = [formStatus, formStatusMobile].filter(Boolean);
const resultTargets = [
  { stage: resultStage, meta: resultMeta },
  { stage: resultStageMobile, meta: resultMetaMobile },
].filter((item) => item.stage && item.meta);

function getPwaRuntime() {
  return window.BananaPWA || null;
}

function getDownloadActionLabel(kind = "single") {
  return getPwaRuntime()?.getDownloadActionLabel(kind) || (kind === "batch" ? "批量下载" : "直接下载");
}

function revokePreviewUrls(container) {
  if (!container) return;
  container.querySelectorAll("[data-object-url]").forEach((img) => {
    URL.revokeObjectURL(img.dataset.objectUrl);
  });
}

function getInputGroup(inputs) {
  if (Array.isArray(inputs)) {
    return inputs.filter(Boolean);
  }
  return inputs ? [inputs] : [];
}

function syncNativeInputFiles(inputs, files, options = {}) {
  const group = getInputGroup(inputs);
  if (!group.length) {
    return;
  }
  const dt = new DataTransfer();
  files.forEach((file) => dt.items.add(file));
  group.forEach((input) => {
    input.files = dt.files;
    if (typeof options.required === "boolean") {
      input.required = options.required;
    }
  });
}

function clearNativeInputFiles(inputs, options = {}) {
  getInputGroup(inputs).forEach((input) => {
    input.value = "";
    if (typeof options.required === "boolean") {
      input.required = options.required;
    }
  });
}

function setSubmitButtonsDisabled(disabled) {
  submitButtons.forEach((button) => {
    button.disabled = Boolean(disabled);
  });
}

function setSubmitButtonLabel(label = "开始生成") {
  submitButtons.forEach((button) => {
    button.textContent = label;
  });
}

function formatFileSize(bytes) {
  if (bytes >= MB) {
    return `${(bytes / MB).toFixed(2)} MB`;
  }
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

function extractErrorMessage(value) {
  if (typeof value === "string") {
    const text = value.trim();
    return text || null;
  }

  if (Array.isArray(value)) {
    const messages = value
      .map((item) => extractErrorMessage(item))
      .filter(Boolean);
    return messages.length > 0 ? messages.join("；") : null;
  }

  if (value && typeof value === "object") {
    const directKeys = ["message", "error", "detail", "details", "msg", "reason"];
    for (const key of directKeys) {
      const message = extractErrorMessage(value[key]);
      if (message) {
        return message;
      }
    }

    for (const nested of Object.values(value)) {
      const message = extractErrorMessage(nested);
      if (message) {
        return message;
      }
    }

    try {
      return JSON.stringify(value, null, 2);
    } catch (error) {
      return null;
    }
  }

  return null;
}

function getPayloadErrorMessage(payload, fallback) {
  return (
    extractErrorMessage(payload?.details) ||
    extractErrorMessage(payload?.error) ||
    fallback
  );
}

async function copyTextToClipboard(text) {
  if (navigator.clipboard?.writeText && window.isSecureContext) {
    await navigator.clipboard.writeText(text);
    return;
  }

  const textarea = document.createElement("textarea");
  textarea.value = text;
  textarea.setAttribute("readonly", "");
  textarea.style.position = "fixed";
  textarea.style.opacity = "0";
  textarea.style.pointerEvents = "none";
  document.body.appendChild(textarea);
  textarea.focus();
  textarea.select();

  const copied = document.execCommand("copy");
  document.body.removeChild(textarea);
  if (!copied) {
    throw new Error("浏览器不支持复制，请手动复制。");
  }
}

async function copyPromptText(prompt, sourceLabel = "提示词") {
  const fullPrompt = String(prompt || "").trim();
  if (!fullPrompt) {
    setStatus("当前记录没有可复制的提示词。", true);
    return false;
  }

  try {
    await copyTextToClipboard(fullPrompt);
    setStatus(`${sourceLabel}已复制。`);
    return true;
  } catch (error) {
    const message = error instanceof Error ? error.message : "复制失败，请重试。";
    setStatus(message, true);
    return false;
  }
}

function readImageTransferQueue() {
  try {
    const raw = localStorage.getItem(IMAGE_TRANSFER_QUEUE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch (error) {
    return [];
  }
}

function consumeImageTransferQueue() {
  const queue = readImageTransferQueue();
  try {
    localStorage.removeItem(IMAGE_TRANSFER_QUEUE_KEY);
  } catch (error) {
    // Ignore storage cleanup errors and continue.
  }
  return queue;
}

function extFromMimeType(type) {
  const normalized = String(type || "").toLowerCase();
  if (normalized.includes("png")) return "png";
  if (normalized.includes("webp")) return "webp";
  if (normalized.includes("gif")) return "gif";
  if (normalized.includes("bmp")) return "bmp";
  if (normalized.includes("avif")) return "avif";
  return "jpg";
}

function normalizeMimeType(type) {
  return String(type || "").split(";", 1)[0].trim().toLowerCase();
}

function getFileExtension(name) {
  const match = String(name || "").trim().match(/\.([a-z0-9]{2,8})$/i);
  return match?.[1]?.toLowerCase() || "";
}

function ensureImageFileName(name, mimeType, fallback = "history-image") {
  const safe = String(name || fallback)
    .trim()
    .replace(/[\\/:*?"<>|]+/g, "-");
  const stem = safe.replace(/\.[a-z0-9]{2,6}$/i, "") || fallback;
  const ext = /\.[a-z0-9]{2,6}$/i.test(safe) ? safe.match(/\.([a-z0-9]{2,6})$/i)?.[1] : extFromMimeType(mimeType);
  return `${stem}.${ext || "jpg"}`;
}

function shouldConvertUploadToJpeg(file) {
  const mimeType = normalizeMimeType(file?.type);
  const extension = getFileExtension(file?.name);
  if (FORCE_JPEG_EXTENSIONS.has(extension)) {
    return true;
  }
  if (FORCE_JPEG_MIME_KEYWORDS.some((keyword) => mimeType.includes(keyword))) {
    return true;
  }
  if (mimeType.startsWith("image/") && !SAFE_PREVIEW_MIME_TYPES.has(mimeType)) {
    return true;
  }
  return false;
}

function parseFilenameFromDisposition(disposition, fallback = "image.jpg") {
  const raw = String(disposition || "");
  const utf8Match = raw.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1]);
    } catch (error) {
      return utf8Match[1];
    }
  }

  const asciiMatch = raw.match(/filename="?([^"]+)"?/i);
  return asciiMatch?.[1] || fallback;
}

function getPreferredImageUrl(entry) {
  return String(entry?.ossImageUrl || entry?.imageUrl || "").trim();
}

function getPreferredThumbUrl(entry) {
  return String(entry?.ossThumbUrl || entry?.thumbUrl || "").trim();
}

function getTransferImageUrl(entry) {
  return String(entry?.imageUrl || getPreferredImageUrl(entry) || "").trim();
}

function triggerDownloadLink(url, filename) {
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename || "banana-pro-image";
  anchor.rel = "noopener";
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
}

function triggerBackgroundDownload(url) {
  const frame = document.createElement("iframe");
  frame.style.display = "none";
  frame.src = url;
  document.body.appendChild(frame);
  window.setTimeout(() => {
    frame.remove();
  }, 45000);
}

async function resolveDownloadTarget(entry) {
  const id = String(entry?.id || "").trim();
  const fallbackUrl = getPreferredImageUrl(entry);
  const fallbackName = entry?.downloadName || "banana-pro-image";

  if (!id) {
    if (!fallbackUrl) {
      throw new Error("未找到可用下载地址。");
    }
    return { url: fallbackUrl, source: "local", downloadName: fallbackName };
  }

  const response = await fetch("/api/history/download-links", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ids: [id] }),
  });
  if (response.status === 401) {
    throw new Error("__AUTH_REQUIRED__");
  }
  const payload = await readJsonSafely(response);
  if (!response.ok) {
    throw new Error(payload.error || "获取下载链接失败。");
  }

  const first = Array.isArray(payload.items) ? payload.items[0] : null;
  if (first?.url) {
    return {
      url: String(first.url),
      source: String(first.source || "local"),
      downloadName: first.downloadName || fallbackName,
    };
  }

  if (!fallbackUrl) {
    throw new Error("未找到可用下载地址。");
  }
  return { url: fallbackUrl, source: "local", downloadName: fallbackName };
}

async function downloadEntryImage(entry) {
  const target = await resolveDownloadTarget(entry);
  const runtime = getPwaRuntime();
  if (!runtime) {
    if (target.source === "oss-signed") {
      triggerBackgroundDownload(target.url);
      return { method: "iframe" };
    }
    triggerDownloadLink(target.url, target.downloadName);
    return { method: "anchor" };
  }
  return runtime.deliverDownload({
    url: target.url,
    filename: target.downloadName,
    source: target.source,
    title: "Banana Pro 图片已生成",
    text: "可以保存到本地，也可以直接转发到其他应用。",
  });
}

async function fetchImageAsFile(imageUrl, preferredName = "history-image") {
  const response = await fetch(imageUrl, { credentials: "include" });
  if (!response.ok) {
    throw new Error("读取历史图片失败，请稍后重试。");
  }

  const blob = await response.blob();
  if (!blob.size) {
    throw new Error("历史图片内容为空，无法发送。");
  }

  const fileName = ensureImageFileName(preferredName, blob.type, "history-image");
  return new File([blob], fileName, {
    type: blob.type || "image/jpeg",
    lastModified: Date.now(),
  });
}

function renameFileToJpg(name) {
  const stem = name.replace(/\.[^.]+$/, "") || "image";
  return `${stem}.jpg`;
}

function getScaledDimensions(width, height, maxLongEdge) {
  const longEdge = Math.max(width, height);
  if (!maxLongEdge || longEdge <= maxLongEdge) {
    return { width, height };
  }

  const scale = maxLongEdge / longEdge;
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  };
}

function readImageFile(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const image = new Image();

    image.onload = () => {
      URL.revokeObjectURL(url);
      resolve(image);
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error(`无法读取图片：${file.name}`));
    };

    image.src = url;
  });
}

async function canPreviewImageFile(file) {
  try {
    await readImageFile(file);
    return true;
  } catch (error) {
    return false;
  }
}

function canvasToBlob(canvas, type, quality) {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new Error("图片压缩失败。"));
          return;
        }
        resolve(blob);
      },
      type,
      quality,
    );
  });
}

async function renderCompressedFile(file, options) {
  const {
    maxLongEdge = null,
    widthScale = 1,
    quality = 0.85,
    type = "image/jpeg",
    fileName = renameFileToJpg(file.name),
  } = options;
  const image = await readImageFile(file);
  const limited = getScaledDimensions(image.naturalWidth, image.naturalHeight, maxLongEdge);
  const targetWidth = Math.max(1, Math.round(limited.width * widthScale));
  const targetHeight = Math.max(1, Math.round(limited.height * widthScale));
  const canvas = document.createElement("canvas");
  canvas.width = targetWidth;
  canvas.height = targetHeight;

  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("浏览器不支持图片压缩。");
  }

  context.drawImage(image, 0, 0, targetWidth, targetHeight);
  const blob = await canvasToBlob(canvas, type, quality);
  return new File([blob], fileName, {
    type,
    lastModified: Date.now(),
  });
}

async function convertImageViaServer(file) {
  const formData = new FormData();
  formData.set("image", file, file.name || "upload-image");

  const response = await fetch("/api/prepare-image", {
    method: "POST",
    body: formData,
  });
  if (response.status === 401) {
    throw new Error("请先登录后再上传图片。");
  }
  if (!response.ok) {
    const payload = await readJsonSafely(response);
    throw new Error(getPayloadErrorMessage(payload, "图片转换失败，请换一张图片再试。"));
  }

  const blob = await response.blob();
  const filename = parseFilenameFromDisposition(
    response.headers.get("Content-Disposition"),
    renameFileToJpg(file.name || "upload-image"),
  );
  return new File([blob], filename, {
    type: blob.type || "image/jpeg",
    lastModified: Date.now(),
  });
}

async function preparePreviewableImageFile(file) {
  if (!file) {
    return { file: null, converted: false, method: null };
  }

  const previewable = await canPreviewImageFile(file);
  const needsJpeg = shouldConvertUploadToJpeg(file);
  if (!needsJpeg && previewable) {
    return { file, converted: false, method: null };
  }

  if (previewable) {
    const convertedFile = await renderCompressedFile(file, {
      quality: 0.92,
      type: "image/jpeg",
      fileName: renameFileToJpg(file.name),
    });
    return { file: convertedFile, converted: true, method: "client" };
  }

  const convertedFile = await convertImageViaServer(file);
  return { file: convertedFile, converted: true, method: "server" };
}

async function compressBaseImageIfNeeded(file) {
  if (file.size <= BASE_IMAGE_MAX_BYTES) {
    return { file, compressed: false };
  }

  const compressedFile = await renderCompressedFile(file, {
    maxLongEdge: BASE_IMAGE_MAX_LONG_EDGE,
    quality: BASE_IMAGE_QUALITY,
    type: "image/jpeg",
  });
  return { file: compressedFile, compressed: true };
}

async function compressReferenceImageIfNeeded(file) {
  if (file.size <= REFERENCE_IMAGE_MAX_BYTES) {
    return { file, compressed: false };
  }

  const qualitySteps = [0.85, 0.78, 0.72, 0.66, 0.6, 0.54, 0.48, 0.42];
  const scaleSteps = [1, 0.92, 0.84, 0.76, 0.68, 0.6];

  for (const scale of scaleSteps) {
    for (const quality of qualitySteps) {
      const compressedFile = await renderCompressedFile(file, {
        maxLongEdge: BASE_IMAGE_MAX_LONG_EDGE,
        widthScale: scale,
        quality,
        type: "image/jpeg",
      });
      if (compressedFile.size <= REFERENCE_IMAGE_MAX_BYTES) {
        return { file: compressedFile, compressed: true };
      }
    }
  }

  throw new Error(`参考图 ${file.name} 压缩后仍超过 2MB，请换一张图片再试。`);
}

async function applyBaseInputFiles(files) {
  const incomingFiles = Array.from(files || []);
  const file = incomingFiles[0] || null;
  let processedFile = file;
  const ignoredCount = Math.max(0, incomingFiles.length - 1);
  let prepared = { converted: false, method: null };

  if (file) {
    try {
      prepared = await preparePreviewableImageFile(file);
      const result = await compressBaseImageIfNeeded(prepared.file);
      processedFile = result.file;
      const messages = [];
      if (prepared.converted) {
        messages.push(prepared.method === "server" ? "基础结构图已自动转换为 JPG（兼容转换）。" : "基础结构图已自动转换为 JPG。");
      }
      if (result.compressed) {
        messages.push(`已压缩 ${formatFileSize(file.size)} -> ${formatFileSize(processedFile.size)}。`);
      }
      if (ignoredCount > 0) {
        messages.push(`另有 ${ignoredCount} 个文件已忽略。`);
      }
      setStatus(messages.join(" "));
    } catch (error) {
      processedFile = null;
      clearNativeInputFiles(baseInputs, { required: true });
      const message = error instanceof Error ? error.message : "基础结构图压缩失败。";
      setStatus(message, true);
    }
  }

  state.baseImageFile = processedFile;
  syncNativeInputFiles(baseInputs, processedFile ? [processedFile] : [], {
    required: !state.baseImageFile,
  });
  renderBasePreview();
  syncSubmitButtonState();
}

async function appendReferenceInputFiles(files) {
  const newFiles = Array.from(files || []);
  if (newFiles.length === 0) {
    return;
  }

  const remainingSlots = Math.max(0, REFERENCE_IMAGE_LIMIT - state.referenceFiles.length);
  const acceptedFiles = newFiles.slice(0, remainingSlots);
  const processedFiles = [];
  let compressedCount = 0;
  let convertedCount = 0;
  let serverConvertedCount = 0;
  let failedCount = 0;

  for (const file of acceptedFiles) {
    try {
      const prepared = await preparePreviewableImageFile(file);
      const result = await compressReferenceImageIfNeeded(prepared.file);
      processedFiles.push(result.file);
      if (prepared.converted) {
        convertedCount += 1;
        if (prepared.method === "server") {
          serverConvertedCount += 1;
        }
      }
      if (result.compressed) {
        compressedCount += 1;
      }
    } catch (error) {
      failedCount += 1;
    }
  }

  state.referenceFiles = [...state.referenceFiles, ...processedFiles];
  syncNativeInputFiles(referenceInputs, state.referenceFiles);

  const messages = [];
  if (convertedCount > 0) {
    messages.push(
      `${convertedCount} 张参考图已自动转换为 JPG${serverConvertedCount > 0 ? `（其中 ${serverConvertedCount} 张走兼容转换）` : ""}。`,
    );
  }
  if (compressedCount > 0) {
    messages.push(`${compressedCount} 张参考图已压缩到 2MB 以内。`);
  }
  if (newFiles.length > remainingSlots) {
    messages.push(`参考图最多上传 ${REFERENCE_IMAGE_LIMIT} 张，超出的图片已忽略。`);
  }
  if (failedCount > 0) {
    messages.push(`${failedCount} 张参考图压缩失败，已跳过。`);
  }

  if (messages.length > 0) {
    setStatus(messages.join(" "), failedCount > 0);
  } else {
    setStatus("");
  }
  renderReferencePreview();
  syncSubmitButtonState();
}

function bindUploadDropZones() {
  const handlers = {
    base: async (files) => applyBaseInputFiles(files),
    reference: async (files) => appendReferenceInputFiles(files),
  };

  document.querySelectorAll("[data-upload-zone]").forEach((zone) => {
    const target = zone.getAttribute("data-upload-zone") || "base";
    const handleFiles = handlers[target];
    if (!handleFiles) return;

    ["dragenter", "dragover"].forEach((eventName) => {
      zone.addEventListener(eventName, (event) => {
        event.preventDefault();
        zone.classList.add("is-dragover");
      });
    });
    ["dragleave", "drop"].forEach((eventName) => {
      zone.addEventListener(eventName, (event) => {
        event.preventDefault();
        zone.classList.remove("is-dragover");
      });
    });
    zone.addEventListener("drop", async (event) => {
      const droppedFiles = event.dataTransfer?.files;
      if (!droppedFiles?.length) {
        return;
      }
      await handleFiles(droppedFiles);
    });
  });
}

function bindMirroredRadioGroup(desktopName, mobileName) {
  const desktopNodes = Array.from(document.querySelectorAll(`input[name="${desktopName}"]`));
  const mobileNodes = Array.from(document.querySelectorAll(`input[name="${mobileName}"]`));
  if (!desktopNodes.length || !mobileNodes.length) return;

  const syncMobile = () => {
    const selected = desktopNodes.find((node) => node.checked)?.value;
    mobileNodes.forEach((node) => {
      node.checked = node.value === selected;
    });
  };

  const syncDesktop = () => {
    const selected = mobileNodes.find((node) => node.checked)?.value;
    if (!selected) return;
    const target = desktopNodes.find((node) => node.value === selected);
    if (!target) return;
    target.checked = true;
    target.dispatchEvent(new Event("change", { bubbles: true }));
  };

  desktopNodes.forEach((node) => {
    node.addEventListener("change", syncMobile);
  });
  mobileNodes.forEach((node) => {
    node.addEventListener("change", syncDesktop);
  });
  syncMobile();
}

function renderFiles(container, files, typeLabel, options = {}) {
  revokePreviewUrls(container);
  container.innerHTML = "";

  if (!files || files.length === 0) {
    return;
  }

  const { onRemove, getReferenceToken, onInsertReference } = options;
  Array.from(files).forEach((file, index) => {
    const url = URL.createObjectURL(file);
    const card = document.createElement("div");
    card.className = "thumb-card";

    const img = document.createElement("img");
    img.src = url;
    img.alt = file.name;
    img.dataset.objectUrl = url;

    const badge = document.createElement("button");
    const token = getReferenceToken?.(index, file) || `${typeLabel} ${index + 1}`;
    badge.className = "thumb-badge";
    badge.type = "button";
    badge.title = `插入编号 ${token} 到提示词`;
    badge.setAttribute("aria-label", `插入编号 ${token} 到提示词`);
    badge.addEventListener("pointerdown", (event) => {
      event.preventDefault();
    });
    badge.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      onInsertReference?.(token, file, index);
    });

    const badgeToken = document.createElement("strong");
    badgeToken.className = "thumb-badge-token";
    badgeToken.textContent = token;

    const badgeName = document.createElement("span");
    badgeName.className = "thumb-badge-name";
    badgeName.textContent = String(file?.name || "").trim() || token;

    badge.appendChild(badgeToken);
    badge.appendChild(badgeName);

    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.className = "thumb-remove";
    removeButton.textContent = "×";
    removeButton.setAttribute("aria-label", `删除${typeLabel}${index + 1}`);
    removeButton.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      onRemove?.(index);
    });

    card.appendChild(img);
    card.appendChild(badge);
    card.appendChild(removeButton);
    container.appendChild(card);
  });
}

function renderBasePreview() {
  const files = state.baseImageFile ? [state.baseImageFile] : [];
  basePreviewTargets.forEach((container) => {
    renderFiles(container, files, "基础图", {
      getReferenceToken: (index) => getImageReferenceToken("base", index),
      onInsertReference: (token) => insertTextIntoPromptAtCursor(token),
      onRemove: () => {
        state.baseImageFile = null;
        clearNativeInputFiles(baseInputs, { required: true });
        renderBasePreview();
        syncSubmitButtonState();
      },
    });
  });
}

function renderReferencePreview() {
  referencePreviewTargets.forEach((container) => {
    renderFiles(container, state.referenceFiles, "参考图", {
      getReferenceToken: (index) => getImageReferenceToken("reference", index),
      onInsertReference: (token) => insertTextIntoPromptAtCursor(token),
      onRemove: (index) => {
        state.referenceFiles.splice(index, 1);
        syncNativeInputFiles(referenceInputs, state.referenceFiles);
        renderReferencePreview();
        syncSubmitButtonState();
      },
    });
  });
}

async function sendImageToBaseFromEntry(entry, options = {}) {
  const { silent = false } = options;
  const transferImageUrl = getTransferImageUrl(entry);
  if (!transferImageUrl) {
    if (!silent) {
      setStatus("图片地址无效，无法发送到基础结构图。", true);
    }
    return false;
  }

  try {
    const sourceFile = await fetchImageAsFile(transferImageUrl, entry.downloadName || "base-image");
    const result = await compressBaseImageIfNeeded(sourceFile);
    state.baseImageFile = result.file;
    syncNativeInputFiles(baseInputs, [result.file], { required: false });
    renderBasePreview();
    syncSubmitButtonState();
    if (!silent) {
      setStatus(result.compressed ? "已发送到基础结构图，并自动压缩完成。" : "已发送到基础结构图。");
    }
    return true;
  } catch (error) {
    if (!silent) {
      const message = error instanceof Error ? error.message : "发送到基础结构图失败。";
      setStatus(message, true);
    }
    return false;
  }
}

async function sendImageToReferenceFromEntry(entry, options = {}) {
  const { silent = false } = options;
  const transferImageUrl = getTransferImageUrl(entry);
  if (!transferImageUrl) {
    if (!silent) {
      setStatus("图片地址无效，无法发送到风格参考图。", true);
    }
    return false;
  }

  if (state.referenceFiles.length >= REFERENCE_IMAGE_LIMIT) {
    if (!silent) {
      setStatus(`参考图最多 ${REFERENCE_IMAGE_LIMIT} 张，请先删除后再添加。`, true);
    }
    return false;
  }

  try {
    const sourceFile = await fetchImageAsFile(transferImageUrl, entry.downloadName || "reference-image");
    const result = await compressReferenceImageIfNeeded(sourceFile);
    state.referenceFiles = [...state.referenceFiles, result.file];
    syncNativeInputFiles(referenceInputs, state.referenceFiles);
    renderReferencePreview();
    syncSubmitButtonState();
    if (!silent) {
      setStatus(result.compressed ? "已发送到风格参考图，并自动压缩完成。" : "已发送到风格参考图。");
    }
    return true;
  } catch (error) {
    if (!silent) {
      const message = error instanceof Error ? error.message : "发送到风格参考图失败。";
      setStatus(message, true);
    }
    return false;
  }
}

async function applyPendingImageTransfers() {
  const queue = consumeImageTransferQueue();
  if (!queue.length) {
    return;
  }

  let baseCount = 0;
  let referenceCount = 0;
  let failedCount = 0;

  for (const item of queue) {
    const target = item?.target === "base" ? "base" : "reference";
    const entry = {
      imageUrl: item?.imageUrl || "",
      downloadName: item?.downloadName || "history-image",
    };
    const success =
      target === "base"
        ? await sendImageToBaseFromEntry(entry, { silent: true })
        : await sendImageToReferenceFromEntry(entry, { silent: true });

    if (success) {
      if (target === "base") {
        baseCount += 1;
      } else {
        referenceCount += 1;
      }
    } else {
      failedCount += 1;
    }
  }

  const summary = [];
  if (baseCount > 0) summary.push(`基础图 ${baseCount} 张`);
  if (referenceCount > 0) summary.push(`参考图 ${referenceCount} 张`);
  if (failedCount > 0) summary.push(`失败 ${failedCount} 张`);
  if (summary.length) {
    setStatus(`已从历史图片导入：${summary.join("，")}。`, failedCount > 0);
  }
}

function setStatus(message, isError = false) {
  formStatusNodes.forEach((node) => {
    node.textContent = message || "";
    node.style.color = isError ? "#d14343" : "";
  });
}

function setOptimizeStatus(message, isError = false) {
  if (!optimizeStatus) return;
  optimizeStatus.textContent = message || "";
  optimizeStatus.style.color = isError ? "#d14343" : "";
}

function setAuthStatus(message, isError = false) {
  authStatus.textContent = message || "";
  authStatus.style.color = isError ? "#d14343" : "";
}

function setAuthUI(authenticated, passwordEnabled) {
  state.authenticated = authenticated;
  state.passwordEnabled = passwordEnabled;
  const needsPassword = passwordEnabled && !authenticated;
  authOverlay.classList.toggle("hidden", !needsPassword);
  authOverlay.setAttribute("aria-hidden", String(!needsPassword));
  logoutButton.classList.toggle("hidden", !passwordEnabled || !authenticated);
  if (needsPassword) {
    passwordInput.focus();
  }
}

function renderPromptLibraryOptions() {
  if (!promptLibrarySelect) return;
  promptLibrarySelect.innerHTML = `<option value="">从提示词列表中选择并插入</option>`;
  if (state.promptLibrary.length === 0) {
    const option = document.createElement("option");
    option.value = "";
    option.textContent = "暂无可用提示词";
    promptLibrarySelect.appendChild(option);
    return;
  }
  state.promptLibrary.forEach((item, index) => {
    const option = document.createElement("option");
    option.value = String(index);
    option.textContent = item;
    promptLibrarySelect.appendChild(option);
  });
}

function setPromptLibraryModalStatus(message, isError = false) {
  if (!promptLibraryModalStatus) return;
  promptLibraryModalStatus.textContent = message || "";
  promptLibraryModalStatus.style.color = isError ? "#d14343" : "";
}

function setPromptPersonaModalStatus(message, isError = false) {
  if (!promptPersonaModalStatus) return;
  promptPersonaModalStatus.textContent = message || "";
  promptPersonaModalStatus.style.color = isError ? "#d14343" : "";
}

function getPromptTargetKey(textarea) {
  if (!textarea) return "";
  return textarea === promptTextareaMobile ? "mobile" : "desktop";
}

function getPromptTextareaByKey(targetKey) {
  if (targetKey === "mobile" && promptTextareaMobile) {
    return promptTextareaMobile;
  }
  if (targetKey === "desktop" && promptTextarea) {
    return promptTextarea;
  }
  const prefersMobile = window.matchMedia?.("(max-width: 767px)")?.matches;
  if (prefersMobile && promptTextareaMobile) {
    return promptTextareaMobile;
  }
  return promptTextarea || promptTextareaMobile || null;
}

function rememberPromptCursor(textarea) {
  if (!textarea) return;
  state.promptCursorTarget = getPromptTargetKey(textarea);
  state.promptCursorStart = Number.isInteger(textarea.selectionStart) ? textarea.selectionStart : textarea.value.length;
  state.promptCursorEnd = Number.isInteger(textarea.selectionEnd) ? textarea.selectionEnd : state.promptCursorStart;
}

function syncPromptTextareas(value) {
  if (promptTextarea) {
    promptTextarea.value = value;
  }
  if (promptTextareaMobile) {
    promptTextareaMobile.value = value;
  }
}

function insertTextIntoPromptAtCursor(text) {
  if (!text) return;
  const activeTextarea =
    document.activeElement === promptTextarea || document.activeElement === promptTextareaMobile
      ? document.activeElement
      : null;
  const targetTextarea = activeTextarea || getPromptTextareaByKey(state.promptCursorTarget);
  if (!targetTextarea) return;

  const value = targetTextarea.value || "";
  const start = activeTextarea
    ? (targetTextarea.selectionStart ?? value.length)
    : Math.min(state.promptCursorStart ?? value.length, value.length);
  const end = activeTextarea
    ? (targetTextarea.selectionEnd ?? start)
    : Math.min(state.promptCursorEnd ?? start, value.length);
  const nextValue = `${value.slice(0, start)}${text}${value.slice(end)}`;
  const nextCursor = start + text.length;

  syncPromptTextareas(nextValue);
  resetOptimizedPrompt();
  syncSubmitButtonState();

  const nextTarget = getPromptTextareaByKey(getPromptTargetKey(targetTextarea));
  if (nextTarget) {
    nextTarget.focus({ preventScroll: true });
    nextTarget.setSelectionRange(nextCursor, nextCursor);
    rememberPromptCursor(nextTarget);
  }
}

function getImageReferenceToken(kind, index) {
  return kind === "base" ? "BASE" : `REF${index + 1}`;
}

function setModalVisible(modal, visible) {
  if (!modal) return;
  modal.classList.toggle("hidden", !visible);
  modal.setAttribute("aria-hidden", String(!visible));
}

function normalizePersonaFilename(value) {
  const raw = String(value || "").trim().replace(/\\/g, "/").split("/").pop() || "";
  const withoutExt = raw.replace(/\.md$/i, "").trim();
  const safeStem = withoutExt.replace(/[^\w\u4e00-\u9fff-]+/g, "-").replace(/^-+|-+$/g, "");
  const finalStem = safeStem || "skill";
  return `${finalStem}.md`;
}

function syncPromptPersonaEditorState() {
  if (state.creatingPromptPersona) {
    if (promptPersonaFilename) promptPersonaFilename.textContent = "新建中";
    if (savePromptPersonaButton) {
      savePromptPersonaButton.disabled = false;
      savePromptPersonaButton.textContent = "创建技能";
    }
    if (deletePromptPersonaButton) deletePromptPersonaButton.disabled = true;
    return;
  }

  if (savePromptPersonaButton) {
    savePromptPersonaButton.disabled = !state.editingPromptPersonaId;
    savePromptPersonaButton.textContent = "保存修改";
  }
  if (deletePromptPersonaButton) deletePromptPersonaButton.disabled = !state.editingPromptPersonaId;
}

function resetPromptPersonaEditor() {
  state.creatingPromptPersona = false;
  state.editingPromptPersonaId = "";
  state.editingPromptPersonaFilename = "";
  if (promptPersonaFilename) promptPersonaFilename.textContent = "未选择";
  if (promptPersonaFilenameInput) promptPersonaFilenameInput.value = "";
  if (promptPersonaNameInput) promptPersonaNameInput.value = "";
  if (promptPersonaSummaryInput) promptPersonaSummaryInput.value = "";
  if (promptPersonaContentInput) promptPersonaContentInput.value = "";
  syncPromptPersonaEditorState();
}

function prepareNewPromptPersona() {
  state.creatingPromptPersona = true;
  state.editingPromptPersonaId = "";
  state.editingPromptPersonaFilename = "";
  if (promptPersonaFilenameInput && !promptPersonaFilenameInput.value.trim()) {
    const stamp = new Date().toISOString().slice(0, 10).replace(/-/g, "");
    promptPersonaFilenameInput.value = `skill-${stamp}.md`;
  }
  if (promptPersonaFilename) promptPersonaFilename.textContent = "新建中";
  if (promptPersonaNameInput && !promptPersonaNameInput.value.trim()) {
    promptPersonaNameInput.value = "新建技能";
  }
  if (promptPersonaSummaryInput && !promptPersonaSummaryInput.value.trim()) {
    promptPersonaSummaryInput.value = "请填写一句技能简介";
  }
  if (promptPersonaContentInput && !promptPersonaContentInput.value.trim()) {
    promptPersonaContentInput.value = "你是一个擅长将中文图像需求转写为高质量英文提示词的助手。";
  }
  syncPromptPersonaEditorState();
  promptPersonaFilenameInput?.focus();
  renderPromptPersonaManagerList();
}

function renderPromptPersonaManagerList() {
  if (!promptPersonaList) return;
  promptPersonaList.innerHTML = "";

  if (state.promptPersonas.length === 0) {
    promptPersonaList.innerHTML = `<div class="empty-history">暂无技能</div>`;
    return;
  }

  state.promptPersonas.forEach((persona) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "persona-item";
    if (!state.creatingPromptPersona && persona.id === state.editingPromptPersonaId) {
      button.classList.add("active");
    }

    const title = document.createElement("strong");
    title.textContent = persona.name;
    const summary = document.createElement("span");
    summary.textContent = persona.summary || persona.filename || "";

    button.appendChild(title);
    button.appendChild(summary);
    button.addEventListener("click", async () => {
      try {
        await loadPromptPersonaDetail(persona.id);
        setPromptPersonaModalStatus("");
      } catch (error) {
        const message = error instanceof Error ? error.message : "加载技能详情失败";
        setPromptPersonaModalStatus(message, true);
      }
    });

    promptPersonaList.appendChild(button);
  });
}

async function loadPromptLibrary() {
  try {
    const response = await fetch("/api/prompt-library");
    if (response.status === 401) {
      state.promptLibrary = [];
      state.promptLibraryContent = "";
      renderPromptLibraryOptions();
      return;
    }
    const payload = await readJsonSafely(response);
    state.promptLibrary = Array.isArray(payload.items) ? payload.items : [];
    state.promptLibraryContent = typeof payload.content === "string" ? payload.content : state.promptLibrary.join("\n");
  } catch (error) {
    state.promptLibrary = [];
    state.promptLibraryContent = "";
  }
  renderPromptLibraryOptions();
}

function renderPromptPersonaOptions() {
  if (!promptPersonaSelect) return;

  promptPersonaSelect.innerHTML = "";
  if (state.promptPersonas.length === 0) {
    promptPersonaSelect.innerHTML = `<option value="">暂无可用技能</option>`;
    promptPersonaSelect.disabled = true;
    if (promptPersonaSummary) {
      promptPersonaSummary.textContent = "请先在 data/skills 文件夹中添加技能 .md 文件。旧版 data/personas 也会自动兼容。";
    }
    return;
  }

  promptPersonaSelect.disabled = false;
  state.promptPersonas.forEach((persona) => {
    const option = document.createElement("option");
    option.value = persona.id;
    option.textContent = persona.name;
    if (persona.id === state.selectedPromptPersonaId) {
      option.selected = true;
    }
    promptPersonaSelect.appendChild(option);
  });

  const activePersona = state.promptPersonas.find((persona) => persona.id === state.selectedPromptPersonaId) || state.promptPersonas[0];
  if (activePersona && state.selectedPromptPersonaId !== activePersona.id) {
    state.selectedPromptPersonaId = activePersona.id;
    promptPersonaSelect.value = activePersona.id;
  }
  if (promptPersonaSummary) {
    promptPersonaSummary.textContent = activePersona?.summary || "";
  }
}

async function loadPromptPersonas() {
  try {
    const response = await fetch("/api/prompt-skills");
    if (response.status === 401) {
      state.promptPersonas = [];
      state.selectedPromptPersonaId = "";
      state.editingPromptPersonaId = "";
      renderPromptPersonaOptions();
      renderPromptPersonaManagerList();
      return;
    }

    const payload = await readJsonSafely(response);
    state.promptPersonas = Array.isArray(payload.items) ? payload.items : [];
    const selectedExists = state.promptPersonas.some((persona) => persona.id === state.selectedPromptPersonaId);
    if (!selectedExists) {
      state.selectedPromptPersonaId = state.promptPersonas[0]?.id || "";
    }
    const editingExists = state.promptPersonas.some((persona) => persona.id === state.editingPromptPersonaId);
    if (!editingExists) {
      state.editingPromptPersonaId = "";
      if (!state.creatingPromptPersona) {
        state.editingPromptPersonaFilename = "";
      }
    }
    renderPromptPersonaOptions();
    renderPromptPersonaManagerList();
  } catch (error) {
    state.promptPersonas = [];
    state.selectedPromptPersonaId = "";
    state.editingPromptPersonaId = "";
    renderPromptPersonaOptions();
    renderPromptPersonaManagerList();
  }
}

async function loadPromptPersonaDetail(personaId) {
  const response = await fetch(`/api/prompt-skills/${encodeURIComponent(personaId)}`);
  const payload = await readJsonSafely(response);
  if (!response.ok) {
    throw new Error(payload.error || "加载技能详情失败");
  }

  state.creatingPromptPersona = false;
  state.editingPromptPersonaId = payload.id || personaId;
  state.editingPromptPersonaFilename = payload.filename || "";
  promptPersonaFilename.textContent = payload.filename || "未命名";
  if (promptPersonaFilenameInput) {
    promptPersonaFilenameInput.value = payload.filename || "";
  }
  promptPersonaNameInput.value = payload.name || "";
  promptPersonaSummaryInput.value = payload.summary || "";
  promptPersonaContentInput.value = payload.content || "";
  syncPromptPersonaEditorState();
  renderPromptPersonaManagerList();
}

function openPromptLibraryModal() {
  if (promptLibraryEditor) {
    promptLibraryEditor.value = state.promptLibraryContent || state.promptLibrary.join("\n");
    promptLibraryEditor.focus();
  }
  setPromptLibraryModalStatus("");
  setModalVisible(promptLibraryModal, true);
}

function closePromptLibraryModal() {
  setModalVisible(promptLibraryModal, false);
}

function openPromptPersonaModal() {
  setPromptPersonaModalStatus("");
  setModalVisible(promptPersonaModal, true);
  void (async () => {
    await loadPromptPersonas();
    if (state.selectedPromptPersonaId) {
      try {
        await loadPromptPersonaDetail(state.selectedPromptPersonaId);
      } catch (error) {
        const message = error instanceof Error ? error.message : "加载技能详情失败";
        setPromptPersonaModalStatus(message, true);
      }
    } else {
      resetPromptPersonaEditor();
      prepareNewPromptPersona();
    }
  })();
}

function closePromptPersonaModal() {
  setModalVisible(promptPersonaModal, false);
  setPromptPersonaModalStatus("");
}

function handleModalBackdropClick(event) {
  if (event.target === promptLibraryModal) {
    closePromptLibraryModal();
  }
  if (event.target === promptPersonaModal) {
    closePromptPersonaModal();
  }
}

function appendPromptText(text) {
  if (!promptTextarea || !text) return;
  const current = promptTextarea.value.trim();
  syncPromptTextareas(current ? `${current}\n${text}` : text);
  resetOptimizedPrompt();
  syncSubmitButtonState();
}

function setApiPlatformHint(message, isError = false) {
  if (!apiPlatformHint) return;
  apiPlatformHint.textContent = message || "";
  apiPlatformHint.classList.toggle("is-error", Boolean(isError));
}

function getSelectedApiPlatform() {
  return state.apiPlatforms.find((platform) => platform.id === state.selectedApiPlatformId) || null;
}

function renderApiPlatformOptions() {
  if (!apiPlatformSelect) return;

  apiPlatformSelect.innerHTML = "";
  if (!state.apiPlatforms.length) {
    apiPlatformSelect.disabled = true;
    apiPlatformSelect.innerHTML = `<option value="">未找到 API 平台</option>`;
    return;
  }

  state.apiPlatforms.forEach((platform) => {
    const option = document.createElement("option");
    option.value = platform.id;
    option.textContent = platform.name;
    option.selected = platform.id === state.selectedApiPlatformId;
    apiPlatformSelect.appendChild(option);
  });
  apiPlatformSelect.disabled = false;

  // Sync mobile select
  if (apiPlatformSelectMobile) {
    apiPlatformSelectMobile.innerHTML = apiPlatformSelect.innerHTML;
    apiPlatformSelectMobile.disabled = apiPlatformSelect.disabled;
    apiPlatformSelectMobile.value = apiPlatformSelect.value;
  }
}

function syncImageModelOptions(preferredModel = "") {
  if (!imageModelSelect) return;

  const platform = getSelectedApiPlatform();
  imageModelSelect.innerHTML = "";

  if (!platform) {
    state.selectedImageModel = "";
    imageModelSelect.disabled = true;
    imageModelSelect.innerHTML = `<option value="">请先选择 API 平台</option>`;
    setApiPlatformHint("将根据所选平台自动匹配对应的接口地址和密钥。");
    return;
  }

  const candidateModel =
    preferredModel ||
    state.selectedImageModel ||
    platform.defaultModel ||
    platform.models?.[0] ||
    "";
  const fallbackModel = platform.models.includes(candidateModel)
    ? candidateModel
    : (platform.defaultModel && platform.models.includes(platform.defaultModel) ? platform.defaultModel : platform.models[0]);

  state.selectedImageModel = fallbackModel || "";
  platform.models.forEach((model) => {
    const option = document.createElement("option");
    option.value = model;
    option.textContent = model;
    option.selected = model === state.selectedImageModel;
    imageModelSelect.appendChild(option);
  });
  imageModelSelect.disabled = !platform.models.length;

  // Sync mobile select
  if (imageModelSelectMobile) {
    imageModelSelectMobile.innerHTML = imageModelSelect.innerHTML;
    imageModelSelectMobile.disabled = imageModelSelect.disabled;
    imageModelSelectMobile.value = imageModelSelect.value;
  }

  const hintParts = [platform.name];
  if (state.selectedImageModel) {
    hintParts.push(`当前模型 ${state.selectedImageModel}`);
  }
  hintParts.push(`${platform.models.length} 个可选模型`);
  setApiPlatformHint(hintParts.join(" · "));
}

function syncHeaderChips() {
  if (typeof window.updateHeaderChips !== "function") return;
  const platform = state.apiPlatforms.find(p => p.id === state.selectedApiPlatformId);
  const checkedRatio = document.querySelector('input[name="aspectRatio"]:checked');
  const checkedSize = document.querySelector('input[name="imageSize"]:checked');
  window.updateHeaderChips({
    platform: platform?.name || "",
    model: state.selectedImageModel || "",
    ratio: checkedRatio?.value || "自动",
    size: checkedSize?.value || "4K",
  });
}

function applyApiPlatformSelection(platformId, preferredModel = "") {
  const nextPlatform =
    state.apiPlatforms.find((platform) => platform.id === platformId) ||
    state.apiPlatforms[0] ||
    null;

  state.selectedApiPlatformId = nextPlatform?.id || "";
  renderApiPlatformOptions();
  syncImageModelOptions(preferredModel);
  syncSubmitButtonState();
  syncHeaderChips();
}

function resetApiPlatforms(message = "登录后加载 API 平台配置。") {
  state.apiPlatforms = [];
  state.selectedApiPlatformId = "";
  state.selectedImageModel = "";

  if (apiPlatformSelect) {
    apiPlatformSelect.disabled = true;
    apiPlatformSelect.innerHTML = `<option value="">暂无平台配置</option>`;
  }
  if (apiPlatformSelectMobile) {
    apiPlatformSelectMobile.disabled = true;
    apiPlatformSelectMobile.innerHTML = `<option value="">暂无平台配置</option>`;
  }
  if (imageModelSelect) {
    imageModelSelect.disabled = true;
    imageModelSelect.innerHTML = `<option value="">暂无模型可选</option>`;
  }
  if (imageModelSelectMobile) {
    imageModelSelectMobile.disabled = true;
    imageModelSelectMobile.innerHTML = `<option value="">暂无模型可选</option>`;
  }
  setApiPlatformHint(message);
  syncSubmitButtonState();
}

async function loadApiPlatforms() {
  const response = await fetch("/api/image-platforms");
  if (response.status === 401) {
    setAuthUI(false, true);
    resetApiPlatforms("登录后加载 API 平台配置。");
    return;
  }

  const payload = await readJsonSafely(response);
  if (!response.ok) {
    throw new Error(getPayloadErrorMessage(payload, "读取 API 平台配置失败"));
  }

  const items = Array.isArray(payload.items) ? payload.items : [];
  state.apiPlatforms = items.map((item) => ({
    id: String(item.id || "").trim(),
    name: String(item.name || "未命名平台").trim() || "未命名平台",
    models: Array.isArray(item.models)
      ? item.models.map((model) => String(model || "").trim()).filter(Boolean)
      : [],
    defaultModel: String(item.defaultModel || "").trim(),
  })).filter((item) => item.id && item.models.length > 0);
  if (!state.apiPlatforms.length) {
    resetApiPlatforms("`data/api-platforms.xml` 里还没有可用的平台配置。");
    return;
  }

  applyApiPlatformSelection(payload.defaultPlatformId, payload.defaultImageModel || "");
}

function getPromptMode() {
  return form.querySelector('input[name="promptMode"]:checked')?.value || "default";
}

function hasOptimizedPrompt() {
  const value = optimizedPromptTextarea?.value || state.optimizedPrompt || "";
  return Boolean(value.trim());
}

function hasSourcePrompt() {
  return Boolean((promptTextarea?.value || "").trim());
}

function syncSubmitButtonState() {
  if (!submitButtons.length) return;
  if (state.progressTimer) return;

  const promptMode = getPromptMode();
  const hasImageTarget = Boolean(state.selectedApiPlatformId && state.selectedImageModel);
  const canSubmit =
    hasImageTarget &&
    state.baseImageFile &&
    (promptMode === "optimized" ? hasOptimizedPrompt() : hasSourcePrompt());
  setSubmitButtonLabel("开始生成");
  setSubmitButtonsDisabled(!canSubmit);
}

function resetOptimizedPrompt() {
  state.optimizedPrompt = "";
  if (optimizedPromptTextarea) {
    optimizedPromptTextarea.value = "";
  }
  setOptimizeStatus("");
  syncSubmitButtonState();
}

function syncPromptModeUI() {
  const isOptimized = getPromptMode() === "optimized";
  optimizedPromptPanel?.classList.toggle("hidden", !isOptimized);
  if (!isOptimized) {
    clearOptimizeProgress(true);
    setOptimizeStatus("");
  }
  syncSubmitButtonState();
  renderPromptPersonaOptions();
}

function getAspectOrientation(value) {
  if (value === "3:4" || value === "9:16" || value === "4:5") return "portrait";
  if (value === "4:3" || value === "16:9" || value === "5:4") return "landscape";
  return null;
}

function syncAspectRatioPreview() {
  if (!ratioOrientationPreview) return;
  const selected = form.querySelector('input[name="aspectRatio"]:checked')?.value || "auto";
  const orientation = getAspectOrientation(selected);

  ratioOrientationPreview.querySelectorAll(".orientation-card").forEach((node) => {
    const isActive = orientation && node.dataset.orientation === orientation;
    node.classList.toggle("active", Boolean(isActive));
  });
}

function supportsBrowserNotifications() {
  const runtime = getPwaRuntime();
  return Boolean(runtime?.platform?.supportsNotifications || (typeof window !== "undefined" && "Notification" in window));
}

async function ensureBrowserNotificationPermission() {
  const runtime = getPwaRuntime();
  if (runtime?.requestNotificationPermission) {
    return runtime.requestNotificationPermission();
  }
  if (!supportsBrowserNotifications()) return false;
  if (Notification.permission === "granted") return true;
  if (Notification.permission === "denied") return false;
  return Notification.requestPermission().catch(() => "denied").then((permission) => permission === "granted");
}

async function sendBrowserNotification(title, body) {
  const runtime = getPwaRuntime();
  if (runtime?.showNotification) {
    await runtime.showNotification(title, body, { url: window.location.href });
    return;
  }
  if (!supportsBrowserNotifications() || Notification.permission !== "granted") return;
  try {
    const notification = new Notification(title, {
      body,
      tag: "banana-pro-generate",
      renotify: true,
    });
    notification.onclick = () => {
      window.focus();
      notification.close();
    };
  } catch (error) {
    // Ignore notification failures so generation flow is unaffected.
  }
}

function stopTitleFlash(resetTitle = true) {
  if (state.titleFlashTimer) {
    clearInterval(state.titleFlashTimer);
    state.titleFlashTimer = null;
  }
  if (resetTitle) {
    document.title = DEFAULT_PAGE_TITLE;
  }
}

function startTitleFlash(prefix) {
  const text = String(prefix || "").trim();
  if (!text) return;

  stopTitleFlash(false);
  const flashTitle = `${text} · ${DEFAULT_PAGE_TITLE}`;
  const visibleAtStart = !document.hidden && document.hasFocus();
  let tick = 0;

  document.title = flashTitle;
  state.titleFlashTimer = setInterval(() => {
    tick += 1;
    document.title = tick % 2 === 0 ? flashTitle : DEFAULT_PAGE_TITLE;

    if (visibleAtStart && tick >= 8) {
      stopTitleFlash();
    }
  }, 650);
}

function getAudioContext() {
  const AudioCtx = window.AudioContext || window.webkitAudioContext;
  if (!AudioCtx) return null;
  if (!state.audioContext) {
    state.audioContext = new AudioCtx();
  }
  return state.audioContext;
}

async function ensureAudioReady() {
  const audioContext = getAudioContext();
  if (!audioContext) return null;
  if (audioContext.state === "suspended") {
    try {
      await audioContext.resume();
    } catch (error) {
      return null;
    }
  }
  return audioContext;
}

async function playSuccessSound() {
  const audioContext = await ensureAudioReady();
  if (!audioContext) return;

  const now = audioContext.currentTime;
  const gain = audioContext.createGain();
  gain.gain.setValueAtTime(0.0001, now);
  gain.gain.exponentialRampToValueAtTime(0.24, now + 0.01);
  gain.gain.exponentialRampToValueAtTime(0.0001, now + 1.1);
  gain.connect(audioContext.destination);

  [
    { start: 0, end: 0.18, a: 1318.51, b: 1760 },
    { start: 0.22, end: 0.4, a: 1567.98, b: 2093 },
    { start: 0.44, end: 0.66, a: 1760, b: 2349.32 },
  ].forEach((note) => {
    const oscA = audioContext.createOscillator();
    oscA.type = "sine";
    oscA.frequency.setValueAtTime(note.a, now + note.start);
    oscA.connect(gain);
    oscA.start(now + note.start);
    oscA.stop(now + note.end);

    const oscB = audioContext.createOscillator();
    oscB.type = "triangle";
    oscB.frequency.setValueAtTime(note.b, now + note.start + 0.01);
    oscB.connect(gain);
    oscB.start(now + note.start + 0.01);
    oscB.stop(now + note.end + 0.05);
  });
}

async function playErrorSound() {
  const audioContext = await ensureAudioReady();
  if (!audioContext) return;

  const now = audioContext.currentTime;
  const gain = audioContext.createGain();
  gain.gain.setValueAtTime(0.0001, now);
  gain.gain.exponentialRampToValueAtTime(0.18, now + 0.015);
  gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.72);
  gain.connect(audioContext.destination);

  const osc = audioContext.createOscillator();
  osc.type = "triangle";
  osc.frequency.setValueAtTime(220, now);
  osc.frequency.exponentialRampToValueAtTime(110, now + 0.32);
  osc.connect(gain);
  osc.start(now);
  osc.stop(now + 0.38);

  const osc2 = audioContext.createOscillator();
  osc2.type = "sine";
  osc2.frequency.setValueAtTime(146.83, now + 0.11);
  osc2.frequency.exponentialRampToValueAtTime(98, now + 0.45);
  osc2.connect(gain);
  osc2.start(now + 0.11);
  osc2.stop(now + 0.58);
}

function formatDate(isoString) {
  if (!isoString) return "";
  const date = new Date(isoString);
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function renderMetaInto(container, entry) {
  if (!container) return;
  container.innerHTML = "";
  if (!entry) return;

  const values = [
    entry.apiPlatformName ? `平台 ${entry.apiPlatformName}` : "",
    entry.imageModel ? `模型 ${entry.imageModel}` : "",
    `比例 ${entry.aspectRatio}`,
    `大小 ${entry.imageSize}`,
    entry.promptMode === "optimized" ? "AI 翻译优化" : "默认提示词",
    entry.enableSearch ? "已开启搜索" : "未开启搜索",
    `${entry.referenceCount || 0} 张参考图`,
  ].filter(Boolean);

  values.forEach((value) => {
    const tag = document.createElement("span");
    tag.className = "meta-pill";
    tag.textContent = value;
    container.appendChild(tag);
  });
}

function renderMeta(entry) {
  resultTargets.forEach(({ meta }) => {
    renderMetaInto(meta, entry);
  });
}

function getEmptyResultMarkup() {
  return `
    <div class="placeholder">
      <div class="placeholder-icon">▣</div>
      <h3>准备生成</h3>
      <p>上传基础图并填写提示词后，就可以开始渲染。</p>
    </div>
  `;
}

function bindResultActions(stage, entry) {
  const closeButton = stage.querySelector('[data-action="close-result"]');
  const deleteButton = stage.querySelector('[data-action="delete-result"]');
  const copyPromptButton = stage.querySelector('[data-action="copy-prompt"]');
  const downloadButton = stage.querySelector('[data-action="download-result"]');
  const sendBaseButton = stage.querySelector('[data-action="send-base"]');
  const sendReferenceButton = stage.querySelector('[data-action="send-reference"]');

  if (copyPromptButton) {
    copyPromptButton.disabled = !String(entry.prompt || "").trim();
  }

  closeButton?.addEventListener("click", () => {
    renderCurrentResult(null);
  });

  copyPromptButton?.addEventListener("click", async () => {
    await copyPromptText(entry.prompt, "当前结果提示词");
  });

  downloadButton?.addEventListener("click", async () => {
    try {
      await downloadEntryImage(entry);
    } catch (error) {
      if (error instanceof Error && error.message === "__AUTH_REQUIRED__") {
        setAuthUI(false, true);
        setStatus("登录后可下载图片。", true);
        return;
      }
      const message = error instanceof Error ? error.message : "下载失败，请重试。";
      setStatus(message, true);
    }
  });

  sendBaseButton?.addEventListener("click", async () => {
    await sendImageToBaseFromEntry(entry);
  });

  sendReferenceButton?.addEventListener("click", async () => {
    await sendImageToReferenceFromEntry(entry);
  });

  deleteButton?.addEventListener("click", async () => {
    await deleteHistoryItem(entry.id, true);
  });
}

function renderCurrentResult(entry) {
  state.currentResult = entry;
  renderMeta(entry);

  if (!entry) {
    clearProgress();
    resultTargets.forEach(({ stage }) => {
      stage.className = "result-stage empty";
      stage.innerHTML = getEmptyResultMarkup();
    });
    return;
  }

  const displayImageUrl = getPreferredImageUrl(entry);
  const downloadLabel = getDownloadActionLabel("single");
  resultTargets.forEach(({ stage }) => {
    stage.className = "result-stage";
    stage.innerHTML = `
      <div class="result-view">
        <div class="result-image-shell">
          <img class="result-image-original" src="${displayImageUrl}" alt="生成结果" />
          <div class="image-transfer-actions">
            <button type="button" class="image-transfer-button" data-action="send-base">基础图</button>
            <button type="button" class="image-transfer-button" data-action="send-reference">参考图</button>
          </div>
        </div>
        <div class="result-actions">
          <div class="result-action-buttons">
            <button type="button" class="ghost-button" data-action="copy-prompt">复制提示词</button>
            <button type="button" class="ghost-button" data-action="close-result">关闭</button>
            <button type="button" class="danger-button" data-action="delete-result">删除</button>
            <button type="button" class="ghost-button" data-action="download-result">${downloadLabel}</button>
          </div>
        </div>
      </div>
    `;
    bindResultActions(stage, entry);
  });
}

function clearProgress() {
  if (state.progressTimer) {
    clearInterval(state.progressTimer);
    state.progressTimer = null;
  }
  syncSubmitButtonState();
}

function clearOptimizeProgress(hide = false) {
  if (state.optimizeProgressTimer) {
    clearInterval(state.optimizeProgressTimer);
    state.optimizeProgressTimer = null;
  }
  if (hide) {
    optimizeProgress?.classList.add("hidden");
    if (optimizeProgress) {
      optimizeProgress.innerHTML = "";
    }
  }
}

function startProgressSimulation() {
  clearProgress();
  state.progressValue = 0;
  let stepIndex = 0;

  const applyStep = (step) => {
    state.progressValue = step.value;
    resultTargets.forEach(({ stage }) => {
      const fill = stage.querySelector(".progress-fill");
      const label = stage.querySelector(".progress-label");
      const value = stage.querySelector(".progress-value");
      if (fill) fill.style.width = `${step.value}%`;
      if (label) label.textContent = step.label;
      if (value) value.textContent = `${step.value}%`;
    });
  };

  applyStep(progressSteps[0]);
  stepIndex = 1;

  state.progressTimer = setInterval(() => {
    if (stepIndex >= progressSteps.length) {
      clearProgress();
      return;
    }
    applyStep(progressSteps[stepIndex]);
    stepIndex += 1;
  }, 2200);
}

function renderLoading() {
  resultTargets.forEach(({ stage, meta }) => {
    meta.innerHTML = "";
    stage.className = "result-stage loading";
    stage.innerHTML = "";

    const progressNode = progressTemplate.content.firstElementChild.cloneNode(true);
    stage.appendChild(progressNode);
    stage.insertAdjacentHTML(
      "beforeend",
      `
        <div class="placeholder">
          <div class="placeholder-icon">⟳</div>
          <h3>正在生成</h3>
          <p>这是阶段进度，不是上游接口返回的真实百分比，但会更直观一些。</p>
        </div>
      `,
    );
  });

  startProgressSimulation();
}

function startOptimizeProgressSimulation() {
  if (!optimizeProgress || !progressTemplate) return;

  clearOptimizeProgress();
  state.optimizeProgressValue = 0;
  optimizeProgress.classList.remove("hidden");
  optimizeProgress.innerHTML = "";

  const progressNode = progressTemplate.content.firstElementChild.cloneNode(true);
  optimizeProgress.appendChild(progressNode);

  const fill = optimizeProgress.querySelector(".progress-fill");
  const label = optimizeProgress.querySelector(".progress-label");
  const value = optimizeProgress.querySelector(".progress-value");

  const applyStep = (step) => {
    state.optimizeProgressValue = step.value;
    if (fill) fill.style.width = `${step.value}%`;
    if (label) label.textContent = step.label;
    if (value) value.textContent = `${step.value}%`;
  };

  applyStep(optimizeProgressSteps[0]);
  let stepIndex = 1;

  state.optimizeProgressTimer = setInterval(() => {
    if (stepIndex >= optimizeProgressSteps.length) {
      clearOptimizeProgress();
      return;
    }
    applyStep(optimizeProgressSteps[stepIndex]);
    stepIndex += 1;
  }, 900);
}

function finishOptimizeProgress(message = "优化完成") {
  if (!optimizeProgress) return;
  clearOptimizeProgress();

  const fill = optimizeProgress.querySelector(".progress-fill");
  const label = optimizeProgress.querySelector(".progress-label");
  const value = optimizeProgress.querySelector(".progress-value");
  if (fill) fill.style.width = "100%";
  if (label) label.textContent = message;
  if (value) value.textContent = "100%";
}

function buildHistoryNode(entry) {
  const node = historyTemplate.content.firstElementChild.cloneNode(true);
  const img = node.querySelector(".history-image");
  const time = node.querySelector(".history-time");
  const tags = node.querySelector(".history-tags");
  const viewButton = node.querySelector(".history-view");
  const downloadLink = node.querySelector(".history-download");
  const copyButton = node.querySelector(".history-copy");
  const sendBaseButton = node.querySelector(".history-send-base");
  const sendReferenceButton = node.querySelector(".history-send-reference");
  const deleteButton = node.querySelector(".history-delete");
  const hasPrompt = Boolean(String(entry.prompt || "").trim());

  const thumbUrl = getPreferredThumbUrl(entry) || getPreferredImageUrl(entry);
  const imageUrl = getPreferredImageUrl(entry);
  img.src = thumbUrl;
  img.alt = entry.prompt || "历史图片";
  img.loading = "lazy";
  img.decoding = "async";
  time.textContent = formatDate(entry.createdAt);
  tags.textContent = [
    entry.aspectRatio,
    entry.imageSize,
    entry.apiPlatformName || "",
    entry.imageModel || "",
  ].filter(Boolean).join(" · ");

  viewButton.addEventListener("click", () => {
    renderCurrentResult(entry);
    // On mobile, switch to result tab
    if (window.onMobileResultReady) window.onMobileResultReady();
  });

  downloadLink.href = imageUrl;
  downloadLink.download = entry.downloadName || "banana-pro-image";
  downloadLink.textContent = getDownloadActionLabel("single");
  downloadLink.addEventListener("click", async (event) => {
    event.preventDefault();
    try {
      await downloadEntryImage(entry);
    } catch (error) {
      if (error instanceof Error && error.message === "__AUTH_REQUIRED__") {
        setAuthUI(false, true);
        setStatus("登录后可下载图片。", true);
        return;
      }
      const message = error instanceof Error ? error.message : "下载失败，请重试。";
      setStatus(message, true);
    }
  });
  if (copyButton) {
    copyButton.disabled = !hasPrompt;
    copyButton.addEventListener("click", async () => {
      await copyPromptText(entry.prompt, "历史提示词");
    });
  }
  sendBaseButton?.addEventListener("click", async () => {
    await sendImageToBaseFromEntry(entry);
  });
  sendReferenceButton?.addEventListener("click", async () => {
    await sendImageToReferenceFromEntry(entry);
  });
  deleteButton.addEventListener("click", async () => {
    await deleteHistoryItem(entry.id, state.currentResult?.id === entry.id);
  });

  return node;
}

function renderHistory(items) {
  const latestItems = Array.isArray(items) ? items : [];
  const mobileHistoryList = document.getElementById("history-list-mobile");

  const emptyHtml = `<div class="empty-history">还没有历史记录，先生成第一张吧。</div>`;

  if (latestItems.length === 0) {
    historyList.innerHTML = emptyHtml;
    if (mobileHistoryList) mobileHistoryList.innerHTML = emptyHtml;
    return;
  }

  historyList.innerHTML = "";
  if (mobileHistoryList) mobileHistoryList.innerHTML = "";

  latestItems.forEach((entry) => {
    const node = buildHistoryNode(entry);
    historyList.appendChild(node);
    if (mobileHistoryList) {
      mobileHistoryList.appendChild(buildHistoryNode(entry));
    }
  });
}

function syncHistoryPagination() {
  const total = Math.max(0, Number(state.historyTotal || 0));
  const totalPages = Math.max(0, Number(state.historyTotalPages || 0));
  const currentPage = Math.max(1, Number(state.historyPage || 1));
  const hasItems = total > 0;

  historyPagination?.classList.toggle("hidden", !hasItems);

  if (historyPaginationSummary) {
    historyPaginationSummary.textContent = hasItems ? `共 ${total} 条历史记录` : "";
  }
  if (historyPageInfo) {
    historyPageInfo.textContent = hasItems ? `第 ${currentPage} / ${Math.max(totalPages, 1)} 页` : "";
  }
  if (historyPrevPageButton) {
    historyPrevPageButton.disabled = !hasItems || currentPage <= 1;
  }
  if (historyNextPageButton) {
    historyNextPageButton.disabled = !hasItems || currentPage >= totalPages;
  }
}

function resetHistoryPagination() {
  state.historyHydrated = false;
  state.historyPage = 1;
  state.historyTotal = 0;
  state.historyTotalPages = 0;
  syncHistoryPagination();
}

function buildHistoryRequestUrl(page = state.historyPage) {
  const params = new URLSearchParams();
  params.set("page", String(Math.max(1, Number(page) || 1)));
  params.set("pageSize", String(state.historyPageSize || HISTORY_PAGE_SIZE));
  return `/api/history?${params.toString()}`;
}

async function deleteHistoryItem(id, clearCurrent = false) {
  try {
    const response = await fetch(`/api/history/${id}`, { method: "DELETE" });
    const payload = await readJsonSafely(response);
    if (!response.ok) {
      throw new Error(getPayloadErrorMessage(payload, "删除失败"));
    }
    if (clearCurrent) {
      renderCurrentResult(null);
    }
    setStatus("图片已删除。");
    await loadHistory(state.historyPage);
  } catch (error) {
    const message = error instanceof Error ? error.message : "删除失败";
    setStatus(message, true);
  }
}

async function loadHistory(page = state.historyPage) {
  const targetPage = Math.max(1, Number(page) || 1);
  try {
    const response = await fetch(buildHistoryRequestUrl(targetPage));
    if (response.status === 401) {
      setAuthUI(false, true);
      historyList.innerHTML = `<div class="empty-history">登录后可查看历史记录。</div>`;
      resetHistoryPagination();
      return;
    }
    const payload = await readJsonSafely(response);
    if (!response.ok) {
      throw new Error(getPayloadErrorMessage(payload, "历史记录加载失败。"));
    }

    const items = Array.isArray(payload.items) ? payload.items : [];
    state.historyPage = Math.max(1, Number(payload.page || targetPage || 1));
    state.historyTotal = Math.max(0, Number(payload.total || 0));
    state.historyTotalPages = Math.max(
      0,
      Number(payload.totalPages || (items.length > 0 ? 1 : 0)),
    );

    renderHistory(items);
    syncHistoryPagination();

    if (!state.historyHydrated && !state.currentResult && state.historyPage === 1 && items[0]) {
      renderCurrentResult(items[0]);
    }
    state.historyHydrated = true;
  } catch (error) {
    historyList.innerHTML = `<div class="empty-history">历史记录加载失败，请刷新重试。</div>`;
    resetHistoryPagination();
  }
}

async function hydrateAuthenticatedWorkspace() {
  const loaders = [loadApiPlatforms, loadPromptLibrary, loadPromptPersonas, loadHistory];
  let firstErrorMessage = "";

  for (const loader of loaders) {
    try {
      await loader();
    } catch (error) {
      const message = error instanceof Error ? error.message : "初始化失败，请稍后重试。";
      if (loader === loadApiPlatforms) {
        resetApiPlatforms(message);
        setApiPlatformHint(message, true);
      }
      if (!firstErrorMessage) {
        firstErrorMessage = message;
      }
    }
  }

  // Update header chips after everything loads
  syncHeaderChips();

  try {
    await applyPendingImageTransfers();
  } catch (error) {
    if (!firstErrorMessage) {
      firstErrorMessage = error instanceof Error ? error.message : "初始化失败，请稍后重试。";
    }
  }

  if (firstErrorMessage) {
    setStatus(firstErrorMessage, true);
  }
}

async function readJsonSafely(response) {
  const text = await response.text();
  try {
    return text ? JSON.parse(text) : {};
  } catch (error) {
    return { error: text || "服务返回了无法解析的内容。" };
  }
}

async function savePromptLibraryContent() {
  const content = promptLibraryEditor?.value || "";
  const response = await fetch("/api/prompt-library", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ content }),
  });
  const payload = await readJsonSafely(response);
  if (!response.ok) {
    throw new Error(payload.error || "保存提示词失败");
  }

  state.promptLibrary = Array.isArray(payload.items) ? payload.items : [];
  state.promptLibraryContent = typeof payload.content === "string" ? payload.content : content;
  renderPromptLibraryOptions();
}

async function uploadPromptPersonaFile() {
  const file = promptPersonaFileInput?.files && promptPersonaFileInput.files[0];
  if (!file) {
    throw new Error("请先选择一个 md 文件。");
  }

  const text = await file.text();
  const lines = text.split(/\r?\n/);
  const response = await fetch("/api/prompt-skills", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      filename: file.name,
      name: (lines[0] || "").trim(),
      summary: (lines[1] || "").trim(),
      content: lines.slice(2).join("\n").trim(),
    }),
  });
  const payload = await readJsonSafely(response);
  if (!response.ok) {
    throw new Error(payload.error || "上传技能失败");
  }

  promptPersonaFileInput.value = "";
  await loadPromptPersonas();
  if (payload.item?.id) {
    await loadPromptPersonaDetail(payload.item.id);
    state.selectedPromptPersonaId = payload.item.id;
    renderPromptPersonaOptions();
  }
}

function collectPromptPersonaEditorPayload() {
  return {
    filename: normalizePersonaFilename(promptPersonaFilenameInput?.value || state.editingPromptPersonaFilename || "skill.md"),
    name: (promptPersonaNameInput?.value || "").trim(),
    summary: (promptPersonaSummaryInput?.value || "").trim(),
    content: (promptPersonaContentInput?.value || "").trim(),
  };
}

async function createPromptPersonaFromEditor() {
  const payload = collectPromptPersonaEditorPayload();
  const response = await fetch("/api/prompt-skills", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const body = await readJsonSafely(response);
  if (!response.ok) {
    throw new Error(body.error || "新建技能失败");
  }
  return body;
}

async function savePromptPersonaFromEditor() {
  if (state.creatingPromptPersona || !state.editingPromptPersonaId) {
    const created = await createPromptPersonaFromEditor();
    await loadPromptPersonas();
    if (created.item?.id) {
      state.selectedPromptPersonaId = created.item.id;
      await loadPromptPersonaDetail(created.item.id);
      renderPromptPersonaOptions();
    }
    return { mode: "create", item: created.item };
  }

  const response = await fetch(`/api/prompt-skills/${encodeURIComponent(state.editingPromptPersonaId)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(collectPromptPersonaEditorPayload()),
  });
  const payload = await readJsonSafely(response);
  if (!response.ok) {
    throw new Error(payload.error || "保存技能失败");
  }

  await loadPromptPersonas();
  if (payload.item?.id) {
    state.selectedPromptPersonaId = payload.item.id;
    await loadPromptPersonaDetail(payload.item.id);
    renderPromptPersonaOptions();
  }
  return { mode: "update", item: payload.item };
}

async function deleteEditingPromptPersona() {
  if (!state.editingPromptPersonaId) {
    throw new Error("请先选择一个要删除的技能。");
  }

  const response = await fetch(`/api/prompt-skills/${encodeURIComponent(state.editingPromptPersonaId)}`, {
    method: "DELETE",
  });
  const payload = await readJsonSafely(response);
  if (!response.ok) {
    throw new Error(payload.error || "删除技能失败");
  }

  await loadPromptPersonas();
  if (state.selectedPromptPersonaId) {
    try {
      await loadPromptPersonaDetail(state.selectedPromptPersonaId);
    } catch (error) {
      resetPromptPersonaEditor();
      prepareNewPromptPersona();
    }
  } else {
    resetPromptPersonaEditor();
    prepareNewPromptPersona();
  }
}

async function optimizePrompt() {
  const sourcePrompt = (promptTextarea.value || "").trim();
  if (!sourcePrompt) {
    setOptimizeStatus("请先输入需要优化的提示词。", true);
    return false;
  }
  if (!state.selectedPromptPersonaId) {
    setOptimizeStatus("请先选择一个转译技能。", true);
    return false;
  }

  state.optimizingPrompt = true;
  optimizePromptButton.disabled = true;
  setOptimizeStatus(state.baseImageFile ? "正在调用 GPT-5.4 结合基础图优化提示词..." : "正在调用 GPT-5.4 优化提示词...");
  startOptimizeProgressSimulation();

  try {
    const formData = new FormData();
    formData.set("prompt", sourcePrompt);
    formData.set("skillId", state.selectedPromptPersonaId);
    if (state.baseImageFile) {
      formData.set("baseImage", state.baseImageFile, state.baseImageFile.name || "base-image.jpg");
    }

    const response = await fetch("/api/optimize-prompt", {
      method: "POST",
      body: formData,
    });
    const payload = await readJsonSafely(response);
    if (response.status === 404) {
      throw new Error("当前服务还没有加载提示词优化接口，请重启服务后再试。");
    }
    if (!response.ok) {
      throw new Error(getPayloadErrorMessage(payload, "提示词优化失败"));
    }

    state.optimizedPrompt = payload.prompt || "";
    if (!state.optimizedPrompt) {
      throw new Error("提示词优化成功，但没有拿到结果。");
    }

    optimizedPromptTextarea.value = state.optimizedPrompt;
    finishOptimizeProgress("优化完成");
    setOptimizeStatus(
      `提示词优化完成，当前使用技能：${payload.skillName || payload.personaName || "未命名技能"}${payload.usedBaseImage ? "，并已结合基础图理解需求。" : "。"}`
    );
    syncSubmitButtonState();
    return true;
  } catch (error) {
    clearOptimizeProgress(true);
    const message = error instanceof Error ? error.message : "提示词优化失败";
    setOptimizeStatus(message, true);
    return false;
  } finally {
    state.optimizingPrompt = false;
    optimizePromptButton.disabled = false;
  }
}

baseInputs.forEach((input) => {
  input.addEventListener("click", () => {
    input.value = "";
  });
  input.addEventListener("change", async (event) => {
    await applyBaseInputFiles(event.currentTarget?.files);
  });
});

referenceInputs.forEach((input) => {
  input.addEventListener("click", () => {
    input.value = "";
  });
  input.addEventListener("change", async (event) => {
    await appendReferenceInputFiles(event.currentTarget?.files);
  });
});

promptTextarea?.addEventListener("input", () => {
  rememberPromptCursor(promptTextarea);
  if (promptTextareaMobile && promptTextareaMobile.value !== promptTextarea.value) {
    promptTextareaMobile.value = promptTextarea.value;
  }
  if (getPromptMode() === "optimized") {
    resetOptimizedPrompt();
  }
  syncSubmitButtonState();
});

promptTextareaMobile?.addEventListener("input", () => {
  rememberPromptCursor(promptTextareaMobile);
  if (promptTextarea && promptTextarea.value !== promptTextareaMobile.value) {
    promptTextarea.value = promptTextareaMobile.value;
  }
  if (getPromptMode() === "optimized") {
    resetOptimizedPrompt();
  }
  syncSubmitButtonState();
});

[promptTextarea, promptTextareaMobile].filter(Boolean).forEach((textarea) => {
  ["focus", "click", "keyup", "select"].forEach((eventName) => {
    textarea.addEventListener(eventName, () => {
      rememberPromptCursor(textarea);
    });
  });
});

apiPlatformSelect?.addEventListener("change", () => {
  applyApiPlatformSelection(apiPlatformSelect.value);
});

imageModelSelect?.addEventListener("change", () => {
  state.selectedImageModel = imageModelSelect.value;
  syncImageModelOptions(state.selectedImageModel);
  syncSubmitButtonState();
  syncHeaderChips();
});

promptPersonaSelect?.addEventListener("change", () => {
  state.selectedPromptPersonaId = promptPersonaSelect.value;
  const activePersona = state.promptPersonas.find((persona) => persona.id === state.selectedPromptPersonaId);
  if (promptPersonaSummary) {
    promptPersonaSummary.textContent = activePersona?.summary || "";
  }
  resetOptimizedPrompt();
});

form.querySelectorAll('input[name="promptMode"]').forEach((node) => {
  node.addEventListener("change", () => {
    syncPromptModeUI();
  });
});

aspectRatioGroup?.querySelectorAll('input[name="aspectRatio"]').forEach((node) => {
  node.addEventListener("change", () => {
    syncAspectRatioPreview();
    syncHeaderChips();
  });
});

optimizePromptButton?.addEventListener("click", async () => {
  await optimizePrompt();
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();

  if (!state.baseImageFile) {
    setStatus("请先上传基础结构图。", true);
    return;
  }

  if (!state.selectedApiPlatformId || !state.selectedImageModel) {
    setStatus("请先选择可用的 API 平台和生成模型。", true);
    syncSubmitButtonState();
    return;
  }

  const promptMode = getPromptMode();
  let finalPrompt = (promptTextarea.value || "").trim();
  if (promptMode === "optimized") {
    finalPrompt = (optimizedPromptTextarea?.value || state.optimizedPrompt || "").trim();
    if (!finalPrompt) {
      setStatus("当前处于 AI 翻译优化模式，请先点击“提示词优化”。", true);
      syncSubmitButtonState();
      return;
    }
  } else if (!finalPrompt) {
    setStatus("请先输入提示词。", true);
    syncSubmitButtonState();
    return;
  }

  setSubmitButtonLabel("生成中...");
  setSubmitButtonsDisabled(true);
  setStatus("图片正在生成，请稍等...");
  renderLoading();
  void ensureBrowserNotificationPermission();

  const formData = new FormData();
  formData.set("apiPlatformId", state.selectedApiPlatformId);
  formData.set("imageModel", state.selectedImageModel);
  formData.set("prompt", finalPrompt);
  formData.set("sourcePrompt", promptTextarea.value || "");
  formData.set("promptMode", promptMode);
  formData.set("aspectRatio", form.querySelector('input[name="aspectRatio"]:checked').value);
  formData.set("imageSize", form.querySelector('input[name="imageSize"]:checked').value);
  formData.append("baseImage", state.baseImageFile);
  state.referenceFiles.forEach((file) => {
    formData.append("referenceImages", file);
  });
  const searchEnabled = form.querySelector('input[name="enableSearch"]').checked;
  formData.set("enableSearch", String(searchEnabled));

  try {
    const response = await fetch("/api/generate", {
      method: "POST",
      body: formData,
    });
    const payload = await readJsonSafely(response);
    clearProgress();

    if (!response.ok) {
      throw new Error(getPayloadErrorMessage(payload, "生成失败"));
    }

    setStatus("生成完成，可以直接下载，也会自动保留到历史记录。");
    playSuccessSound();
    getPwaRuntime()?.vibrate?.([120, 60, 120]);
    sendBrowserNotification("Banana Pro 图片生成成功", "图片已经生成完成，可以回到页面查看和下载。");
    startTitleFlash("生成成功");
    renderCurrentResult(payload);
    // Notify mobile tab system to switch to result
    if (window.onMobileResultReady) window.onMobileResultReady();
    await loadHistory(1);
  } catch (error) {
    clearProgress();
    const message = error instanceof Error ? error.message : "生成失败";
    setStatus(message, true);
    playErrorSound();
    getPwaRuntime()?.vibrate?.([180, 80, 180, 80, 180]);
    sendBrowserNotification("Banana Pro 图片生成失败", message);
    startTitleFlash("生成失败");
    renderCurrentResult(state.currentResult);
  } finally {
    clearProgress();
    setSubmitButtonLabel("开始生成");
    setSubmitButtonsDisabled(false);
    syncSubmitButtonState();
  }
});

refreshHistoryButton.addEventListener("click", () => {
  void loadHistory(1);
});

historyPrevPageButton?.addEventListener("click", () => {
  if (state.historyPage <= 1) return;
  void loadHistory(state.historyPage - 1);
});

historyNextPageButton?.addEventListener("click", () => {
  if (state.historyPage >= state.historyTotalPages) return;
  void loadHistory(state.historyPage + 1);
});

promptLibrarySelect?.addEventListener("change", () => {
  const index = Number(promptLibrarySelect.value);
  if (!Number.isInteger(index) || !state.promptLibrary[index]) {
    return;
  }
  appendPromptText(state.promptLibrary[index]);
  promptLibrarySelect.value = "";
});

managePromptLibraryButton?.addEventListener("click", openPromptLibraryModal);
closePromptLibraryModalButton?.addEventListener("click", closePromptLibraryModal);
promptLibraryModal?.addEventListener("click", handleModalBackdropClick);

savePromptLibraryButton?.addEventListener("click", async () => {
  try {
    await savePromptLibraryContent();
    setPromptLibraryModalStatus(`已保存，共 ${state.promptLibrary.length} 条提示词。`);
    setStatus(`提示词列表已更新，当前共 ${state.promptLibrary.length} 条。`);
  } catch (error) {
    const message = error instanceof Error ? error.message : "保存提示词失败";
    setPromptLibraryModalStatus(message, true);
  }
});

managePromptPersonaButton?.addEventListener("click", openPromptPersonaModal);
closePromptPersonaModalButton?.addEventListener("click", closePromptPersonaModal);
promptPersonaModal?.addEventListener("click", handleModalBackdropClick);

uploadPromptPersonaButton?.addEventListener("click", async () => {
  try {
    await uploadPromptPersonaFile();
    setPromptPersonaModalStatus("技能上传成功。");
    setOptimizeStatus("");
  } catch (error) {
    const message = error instanceof Error ? error.message : "上传技能失败";
    setPromptPersonaModalStatus(message, true);
  }
});

createPromptPersonaButton?.addEventListener("click", () => {
  prepareNewPromptPersona();
  setPromptPersonaModalStatus("已切换到新建模式，填写后点击“创建技能”。");
});

promptPersonaFilenameInput?.addEventListener("blur", () => {
  if (!promptPersonaFilenameInput.value.trim()) return;
  promptPersonaFilenameInput.value = normalizePersonaFilename(promptPersonaFilenameInput.value);
});

savePromptPersonaButton?.addEventListener("click", async () => {
  try {
    const result = await savePromptPersonaFromEditor();
    setPromptPersonaModalStatus(result.mode === "create" ? "技能新建成功。" : "技能已保存。");
    setOptimizeStatus("");
  } catch (error) {
    const message = error instanceof Error ? error.message : "保存技能失败";
    setPromptPersonaModalStatus(message, true);
  }
});

deletePromptPersonaButton?.addEventListener("click", async () => {
  if (!state.editingPromptPersonaId) {
    return;
  }
  const confirmed = window.confirm("确定删除当前技能吗？对应的 md 文件也会被删除。");
  if (!confirmed) {
    return;
  }

  try {
    await deleteEditingPromptPersona();
    setPromptPersonaModalStatus("技能已删除。");
    setOptimizeStatus("");
  } catch (error) {
    const message = error instanceof Error ? error.message : "删除技能失败";
    setPromptPersonaModalStatus(message, true);
  }
});

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const password = passwordInput.value.trim();
  if (!password) {
    setAuthStatus("请输入密码。", true);
    return;
  }

  try {
    const response = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ password }),
    });
    const payload = await readJsonSafely(response);
    if (!response.ok) {
      throw new Error(payload.error || "登录失败");
    }
    setAuthStatus("");
    passwordInput.value = "";
    setAuthUI(true, payload.passwordEnabled !== false);
    await hydrateAuthenticatedWorkspace();
  } catch (error) {
    const message = error instanceof Error ? error.message : "登录失败";
    setAuthStatus(message, true);
  }
});

logoutButton.addEventListener("click", async () => {
  try {
    await fetch("/api/auth/logout", { method: "POST" });
  } finally {
    state.historyHydrated = false;
    state.promptLibrary = [];
    state.promptLibraryContent = "";
    state.promptPersonas = [];
    state.selectedPromptPersonaId = "";
    resetApiPlatforms("登录后加载 API 平台配置。");
    closePromptLibraryModal();
    closePromptPersonaModal();
    resetPromptPersonaEditor();
    renderPromptLibraryOptions();
    renderPromptPersonaOptions();
    historyList.innerHTML = `<div class="empty-history">登录后可查看历史记录。</div>`;
    resetHistoryPagination();
    renderCurrentResult(null);
    setAuthUI(false, true);
  }
});

async function bootstrap() {
  try {
    resetApiPlatforms("正在读取 API 平台配置。");
    renderPromptLibraryOptions();
    renderPromptPersonaOptions();
    syncPromptModeUI();
    syncAspectRatioPreview();
    const response = await fetch("/api/auth/status");
    const payload = await readJsonSafely(response);
    setAuthUI(Boolean(payload.authenticated), Boolean(payload.passwordEnabled));
    if (!payload.passwordEnabled || payload.authenticated) {
      await hydrateAuthenticatedWorkspace();
    } else {
      resetApiPlatforms("登录后加载 API 平台配置。");
      historyList.innerHTML = `<div class="empty-history">登录后可查看历史记录。</div>`;
      resetHistoryPagination();
    }
  } catch (error) {
    resetApiPlatforms("API 平台配置初始化失败。");
    historyList.innerHTML = `<div class="empty-history">初始化失败，请刷新重试。</div>`;
    resetHistoryPagination();
  }

  syncSubmitButtonState();
  syncAspectRatioPreview();
}

document.addEventListener("visibilitychange", () => {
  if (!document.hidden && document.hasFocus()) {
    stopTitleFlash();
  }
});

window.addEventListener("focus", () => {
  if (!document.hidden) {
    stopTitleFlash();
  }
});

if (promptTextareaMobile && promptTextarea) {
  promptTextareaMobile.value = promptTextarea.value;
}
bindMirroredRadioGroup("aspectRatio", "aspectRatio-m");
bindMirroredRadioGroup("imageSize", "imageSize-m");
bindUploadDropZones();

bootstrap();
