const form = document.getElementById("generate-form");
const baseInput = document.getElementById("base-image");
const referenceInput = document.getElementById("reference-images");
const basePreview = document.getElementById("base-preview");
const referencePreview = document.getElementById("reference-preview");
const submitButton = document.getElementById("submit-button");
const formStatus = document.getElementById("form-status");
const resultStage = document.getElementById("result-stage");
const resultMeta = document.getElementById("result-meta");
const historyList = document.getElementById("history-list");
const historyTemplate = document.getElementById("history-item-template");
const refreshHistoryButton = document.getElementById("refresh-history");
const progressTemplate = document.getElementById("progress-template");
const authOverlay = document.getElementById("auth-overlay");
const loginForm = document.getElementById("login-form");
const passwordInput = document.getElementById("password-input");
const authStatus = document.getElementById("auth-status");
const logoutButton = document.getElementById("logout-button");
const promptLibrarySelect = document.getElementById("prompt-library-select");
const managePromptLibraryButton = document.getElementById("manage-prompt-library-button");
const promptTextarea = document.getElementById("prompt");
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
const promptPersonaFilename = document.getElementById("prompt-persona-filename");
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
  audioContext: null,
  authenticated: false,
  passwordEnabled: false,
  baseImageFile: null,
  referenceFiles: [],
  promptLibrary: [],
  promptLibraryContent: "",
  promptPersonas: [],
  selectedPromptPersonaId: "",
  editingPromptPersonaId: "",
  editingPromptPersonaFilename: "",
  optimizedPrompt: "",
  optimizingPrompt: false,
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

function revokePreviewUrls(container) {
  container.querySelectorAll("[data-object-url]").forEach((img) => {
    URL.revokeObjectURL(img.dataset.objectUrl);
  });
}

function syncNativeInputFiles(input, files) {
  const dt = new DataTransfer();
  files.forEach((file) => dt.items.add(file));
  input.files = dt.files;
}

function formatFileSize(bytes) {
  if (bytes >= MB) {
    return `${(bytes / MB).toFixed(2)} MB`;
  }
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
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

function renderFiles(container, files, typeLabel, options = {}) {
  revokePreviewUrls(container);
  container.innerHTML = "";

  if (!files || files.length === 0) {
    return;
  }

  const { onRemove } = options;
  Array.from(files).forEach((file, index) => {
    const url = URL.createObjectURL(file);
    const card = document.createElement("div");
    card.className = "thumb-card";

    const img = document.createElement("img");
    img.src = url;
    img.alt = file.name;
    img.dataset.objectUrl = url;

    const badge = document.createElement("span");
    badge.className = "thumb-badge";
    badge.textContent = `${typeLabel} ${index + 1}`;

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
  renderFiles(basePreview, files, "基础图", {
    onRemove: () => {
      state.baseImageFile = null;
      baseInput.value = "";
      baseInput.required = true;
      renderBasePreview();
    },
  });
}

function renderReferencePreview() {
  renderFiles(referencePreview, state.referenceFiles, "参考图", {
    onRemove: (index) => {
      state.referenceFiles.splice(index, 1);
      syncNativeInputFiles(referenceInput, state.referenceFiles);
      renderReferencePreview();
    },
  });
}

function setStatus(message, isError = false) {
  formStatus.textContent = message || "";
  formStatus.style.color = isError ? "#d14343" : "";
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

function setModalVisible(modal, visible) {
  if (!modal) return;
  modal.classList.toggle("hidden", !visible);
  modal.setAttribute("aria-hidden", String(!visible));
}

function resetPromptPersonaEditor() {
  state.editingPromptPersonaId = "";
  state.editingPromptPersonaFilename = "";
  if (promptPersonaFilename) promptPersonaFilename.textContent = "未选择";
  if (promptPersonaNameInput) promptPersonaNameInput.value = "";
  if (promptPersonaSummaryInput) promptPersonaSummaryInput.value = "";
  if (promptPersonaContentInput) promptPersonaContentInput.value = "";
  if (savePromptPersonaButton) savePromptPersonaButton.disabled = true;
  if (deletePromptPersonaButton) deletePromptPersonaButton.disabled = true;
}

function renderPromptPersonaManagerList() {
  if (!promptPersonaList) return;
  promptPersonaList.innerHTML = "";

  if (state.promptPersonas.length === 0) {
    promptPersonaList.innerHTML = `<div class="empty-history">暂无人设</div>`;
    return;
  }

  state.promptPersonas.forEach((persona) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "persona-item";
    if (persona.id === state.editingPromptPersonaId) {
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
        const message = error instanceof Error ? error.message : "加载人设详情失败";
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
    promptPersonaSelect.innerHTML = `<option value="">暂无可用人设</option>`;
    promptPersonaSelect.disabled = true;
    if (promptPersonaSummary) {
      promptPersonaSummary.textContent = "请先在 data/personas 文件夹中添加人设 .md 文件。";
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
    const response = await fetch("/api/prompt-personas");
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
    if (!state.selectedPromptPersonaId && state.promptPersonas[0]) {
      state.selectedPromptPersonaId = state.promptPersonas[0].id;
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
  const response = await fetch(`/api/prompt-personas/${encodeURIComponent(personaId)}`);
  const payload = await readJsonSafely(response);
  if (!response.ok) {
    throw new Error(payload.error || "加载人设详情失败");
  }

  state.editingPromptPersonaId = payload.id || personaId;
  state.editingPromptPersonaFilename = payload.filename || "";
  promptPersonaFilename.textContent = payload.filename || "未命名";
  promptPersonaNameInput.value = payload.name || "";
  promptPersonaSummaryInput.value = payload.summary || "";
  promptPersonaContentInput.value = payload.content || "";
  savePromptPersonaButton.disabled = false;
  deletePromptPersonaButton.disabled = false;
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
        const message = error instanceof Error ? error.message : "加载人设详情失败";
        setPromptPersonaModalStatus(message, true);
      }
    } else {
      resetPromptPersonaEditor();
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
  promptTextarea.value = current ? `${current}\n${text}` : text;
  resetOptimizedPrompt();
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
  if (!submitButton) return;
  if (state.progressTimer) return;

  const promptMode = getPromptMode();
  const canSubmit = state.baseImageFile && (promptMode === "optimized" ? hasOptimizedPrompt() : true);
  submitButton.disabled = !canSubmit;
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
  return typeof window !== "undefined" && "Notification" in window;
}

async function ensureBrowserNotificationPermission() {
  if (!supportsBrowserNotifications()) return false;
  if (Notification.permission === "granted") return true;
  if (Notification.permission === "denied") return false;

  if (!state.notificationPermissionPromise) {
    state.notificationPermissionPromise = Notification.requestPermission()
      .catch(() => "denied")
      .finally(() => {
        state.notificationPermissionPromise = null;
      });
  }

  const permission = await state.notificationPermissionPromise;
  return permission === "granted";
}

async function sendBrowserNotification(title, body) {
  const allowed = await ensureBrowserNotificationPermission();
  if (!allowed) return;

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

function renderMeta(entry) {
  resultMeta.innerHTML = "";
  if (!entry) return;

  const values = [
    `比例 ${entry.aspectRatio}`,
    `大小 ${entry.imageSize}`,
    entry.promptMode === "optimized" ? "AI 翻译优化" : "默认提示词",
    entry.enableSearch ? "已开启搜索" : "未开启搜索",
    `${entry.referenceCount || 0} 张参考图`,
  ];

  values.forEach((value) => {
    const tag = document.createElement("span");
    tag.className = "meta-pill";
    tag.textContent = value;
    resultMeta.appendChild(tag);
  });
}

function renderCurrentResult(entry) {
  state.currentResult = entry;
  renderMeta(entry);

  if (!entry) {
    clearProgress();
    resultStage.className = "result-stage empty";
    resultStage.innerHTML = `
      <div class="placeholder">
        <div class="placeholder-icon">▣</div>
        <h3>准备生成</h3>
        <p>上传基础图并填写提示词后，就可以开始渲染。</p>
      </div>
    `;
    return;
  }

  resultStage.className = "result-stage";
  resultStage.innerHTML = `
    <div class="result-view">
      <img src="${entry.imageUrl}" alt="生成结果" />
      <div class="result-actions">
        <p>${entry.prompt ? entry.prompt : "未填写提示词"} </p>
        <div class="result-action-buttons">
          <button type="button" class="ghost-button" data-action="close-result">关闭</button>
          <button type="button" class="danger-button" data-action="delete-result">删除</button>
          <a class="ghost-button" href="${entry.imageUrl}" download="${entry.downloadName}">直接下载</a>
        </div>
      </div>
    </div>
  `;

  const closeButton = resultStage.querySelector('[data-action="close-result"]');
  const deleteButton = resultStage.querySelector('[data-action="delete-result"]');

  closeButton?.addEventListener("click", () => {
    renderCurrentResult(null);
  });

  deleteButton?.addEventListener("click", async () => {
    await deleteHistoryItem(entry.id, true);
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

  const fill = resultStage.querySelector(".progress-fill");
  const label = resultStage.querySelector(".progress-label");
  const value = resultStage.querySelector(".progress-value");

  const applyStep = (step) => {
    state.progressValue = step.value;
    if (fill) fill.style.width = `${step.value}%`;
    if (label) label.textContent = step.label;
    if (value) value.textContent = `${step.value}%`;
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
  resultMeta.innerHTML = "";
  resultStage.className = "result-stage loading";
  resultStage.innerHTML = "";

  const progressNode = progressTemplate.content.firstElementChild.cloneNode(true);
  resultStage.appendChild(progressNode);
  resultStage.insertAdjacentHTML(
    "beforeend",
    `
      <div class="placeholder">
        <div class="placeholder-icon">⟳</div>
        <h3>正在生成</h3>
        <p>这是阶段进度，不是上游接口返回的真实百分比，但会更直观一些。</p>
      </div>
    `,
  );

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

function renderHistory(items) {
  historyList.innerHTML = "";

  if (!items || items.length === 0) {
    historyList.innerHTML = `<div class="empty-history">还没有历史记录，先生成第一张吧。</div>`;
    return;
  }

  items.forEach((entry) => {
    const node = historyTemplate.content.firstElementChild.cloneNode(true);
    const img = node.querySelector(".history-image");
    const time = node.querySelector(".history-time");
    const tags = node.querySelector(".history-tags");
    const prompt = node.querySelector(".history-prompt");
    const viewButton = node.querySelector(".history-view");
    const downloadLink = node.querySelector(".history-download");
    const deleteButton = node.querySelector(".history-delete");

    img.src = entry.imageUrl;
    img.alt = entry.prompt || "历史图片";
    time.textContent = formatDate(entry.createdAt);
    tags.textContent = `${entry.aspectRatio} · ${entry.imageSize}`;
    prompt.textContent = entry.prompt || "未填写提示词";

    viewButton.addEventListener("click", () => renderCurrentResult(entry));

    downloadLink.href = entry.imageUrl;
    downloadLink.download = entry.downloadName || "banana-pro-image";
    deleteButton.addEventListener("click", async () => {
      await deleteHistoryItem(entry.id, state.currentResult?.id === entry.id);
    });

    historyList.appendChild(node);
  });
}

async function deleteHistoryItem(id, clearCurrent = false) {
  try {
    const response = await fetch(`/api/history/${id}`, { method: "DELETE" });
    const payload = await readJsonSafely(response);
    if (!response.ok) {
      throw new Error(payload.details || payload.error || "删除失败");
    }
    if (clearCurrent) {
      renderCurrentResult(null);
    }
    setStatus("图片已删除。");
    await loadHistory();
  } catch (error) {
    const message = error instanceof Error ? error.message : "删除失败";
    setStatus(message, true);
  }
}

async function loadHistory() {
  try {
    const response = await fetch("/api/history");
    if (response.status === 401) {
      setAuthUI(false, true);
      historyList.innerHTML = `<div class="empty-history">登录后可查看历史记录。</div>`;
      return;
    }
    const payload = await response.json();
    renderHistory(payload.items || []);
    if (!state.historyHydrated && !state.currentResult && payload.items && payload.items[0]) {
      renderCurrentResult(payload.items[0]);
    }
    state.historyHydrated = true;
  } catch (error) {
    historyList.innerHTML = `<div class="empty-history">历史记录加载失败，请刷新重试。</div>`;
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
  const response = await fetch("/api/prompt-personas", {
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
    throw new Error(payload.error || "上传人设失败");
  }

  promptPersonaFileInput.value = "";
  await loadPromptPersonas();
  if (payload.item?.id) {
    await loadPromptPersonaDetail(payload.item.id);
    state.selectedPromptPersonaId = payload.item.id;
    renderPromptPersonaOptions();
  }
}

async function saveEditingPromptPersona() {
  if (!state.editingPromptPersonaId) {
    throw new Error("请先选择一个要编辑的人设。");
  }

  const response = await fetch(`/api/prompt-personas/${encodeURIComponent(state.editingPromptPersonaId)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      filename: state.editingPromptPersonaFilename,
      name: promptPersonaNameInput?.value || "",
      summary: promptPersonaSummaryInput?.value || "",
      content: promptPersonaContentInput?.value || "",
    }),
  });
  const payload = await readJsonSafely(response);
  if (!response.ok) {
    throw new Error(payload.error || "保存人设失败");
  }

  await loadPromptPersonas();
  if (payload.item?.id) {
    state.selectedPromptPersonaId = payload.item.id;
    await loadPromptPersonaDetail(payload.item.id);
    renderPromptPersonaOptions();
  }
}

async function deleteEditingPromptPersona() {
  if (!state.editingPromptPersonaId) {
    throw new Error("请先选择一个要删除的人设。");
  }

  const response = await fetch(`/api/prompt-personas/${encodeURIComponent(state.editingPromptPersonaId)}`, {
    method: "DELETE",
  });
  const payload = await readJsonSafely(response);
  if (!response.ok) {
    throw new Error(payload.error || "删除人设失败");
  }

  await loadPromptPersonas();
  if (state.selectedPromptPersonaId) {
    try {
      await loadPromptPersonaDetail(state.selectedPromptPersonaId);
    } catch (error) {
      resetPromptPersonaEditor();
    }
  } else {
    resetPromptPersonaEditor();
  }
}

async function optimizePrompt() {
  const sourcePrompt = (promptTextarea.value || "").trim();
  if (!sourcePrompt) {
    setOptimizeStatus("请先输入需要优化的提示词。", true);
    return false;
  }
  if (!state.selectedPromptPersonaId) {
    setOptimizeStatus("请先选择一个转译人设。", true);
    return false;
  }

  state.optimizingPrompt = true;
  optimizePromptButton.disabled = true;
  setOptimizeStatus("正在调用 GPT-5.4 优化提示词...");
  startOptimizeProgressSimulation();

  try {
    const response = await fetch("/api/optimize-prompt", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        prompt: sourcePrompt,
        personaId: state.selectedPromptPersonaId,
      }),
    });
    const payload = await readJsonSafely(response);
    if (response.status === 404) {
      throw new Error("当前服务还没有加载提示词优化接口，请重启服务后再试。");
    }
    if (!response.ok) {
      throw new Error(payload.details || payload.error || "提示词优化失败");
    }

    state.optimizedPrompt = payload.prompt || "";
    if (!state.optimizedPrompt) {
      throw new Error("提示词优化成功，但没有拿到结果。");
    }

    optimizedPromptTextarea.value = state.optimizedPrompt;
    finishOptimizeProgress("优化完成");
    setOptimizeStatus(`提示词优化完成，当前使用人设：${payload.personaName || "未命名人设"}。`);
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

baseInput.addEventListener("change", async () => {
  const file = baseInput.files && baseInput.files[0];
  let processedFile = file || null;

  if (file) {
    try {
      const result = await compressBaseImageIfNeeded(file);
      processedFile = result.file;
      if (result.compressed) {
        setStatus(
          `基础结构图已压缩为 JPG，${formatFileSize(file.size)} -> ${formatFileSize(processedFile.size)}。`,
        );
      } else {
        setStatus("");
      }
    } catch (error) {
      processedFile = null;
      baseInput.value = "";
      const message = error instanceof Error ? error.message : "基础结构图压缩失败。";
      setStatus(message, true);
    }
  }

  state.baseImageFile = processedFile;
  if (processedFile) {
    syncNativeInputFiles(baseInput, [processedFile]);
  }
  baseInput.required = !state.baseImageFile;
  renderBasePreview();
  syncSubmitButtonState();
});

referenceInput.addEventListener("change", async () => {
  const newFiles = Array.from(referenceInput.files || []);
  if (newFiles.length === 0) {
    return;
  }

  const remainingSlots = Math.max(0, 6 - state.referenceFiles.length);
  const acceptedFiles = newFiles.slice(0, remainingSlots);
  const processedFiles = [];
  let compressedCount = 0;
  let failedCount = 0;

  for (const file of acceptedFiles) {
    try {
      const result = await compressReferenceImageIfNeeded(file);
      processedFiles.push(result.file);
      if (result.compressed) {
        compressedCount += 1;
      }
    } catch (error) {
      failedCount += 1;
    }
  }

  state.referenceFiles = [...state.referenceFiles, ...processedFiles];
  syncNativeInputFiles(referenceInput, state.referenceFiles);

  const messages = [];
  if (compressedCount > 0) {
    messages.push(`${compressedCount} 张参考图已压缩到 2MB 以内。`);
  }
  if (newFiles.length > remainingSlots) {
    messages.push("参考图最多上传 6 张，超出的图片已忽略。");
  }
  if (failedCount > 0) {
    messages.push(`${failedCount} 张参考图压缩失败，已跳过。`);
  }

  if (failedCount > 0) {
    setStatus(messages.join(" "), true);
  } else {
    setStatus(messages.join(" "));
  }
  renderReferencePreview();
  syncSubmitButtonState();
});

promptTextarea?.addEventListener("input", () => {
  if (getPromptMode() === "optimized") {
    resetOptimizedPrompt();
  }
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

  const promptMode = getPromptMode();
  let finalPrompt = (promptTextarea.value || "").trim();
  if (promptMode === "optimized") {
    finalPrompt = (optimizedPromptTextarea?.value || state.optimizedPrompt || "").trim();
    if (!finalPrompt) {
      setStatus("当前处于 AI 翻译优化模式，请先点击“提示词优化”。", true);
      syncSubmitButtonState();
      return;
    }
  }

  submitButton.disabled = true;
  setStatus("图片正在生成，请稍等...");
  renderLoading();
  void ensureBrowserNotificationPermission();

  const formData = new FormData();
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
      throw new Error(payload.details || payload.error || "生成失败");
    }

    setStatus("生成完成，可以直接下载，也会自动保留到历史记录。");
    playSuccessSound();
    sendBrowserNotification("Banana Pro 图片生成成功", "图片已经生成完成，可以回到页面查看和下载。");
    renderCurrentResult(payload);
    await loadHistory();
  } catch (error) {
    clearProgress();
    const message = error instanceof Error ? error.message : "生成失败";
    setStatus(message, true);
    playErrorSound();
    sendBrowserNotification("Banana Pro 图片生成失败", message);
    renderCurrentResult(state.currentResult);
  } finally {
    clearProgress();
    submitButton.disabled = false;
  }
});

refreshHistoryButton.addEventListener("click", loadHistory);

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
    setPromptPersonaModalStatus("人设上传成功。");
    setOptimizeStatus("");
  } catch (error) {
    const message = error instanceof Error ? error.message : "上传人设失败";
    setPromptPersonaModalStatus(message, true);
  }
});

savePromptPersonaButton?.addEventListener("click", async () => {
  try {
    await saveEditingPromptPersona();
    setPromptPersonaModalStatus("人设已保存。");
    setOptimizeStatus("");
  } catch (error) {
    const message = error instanceof Error ? error.message : "保存人设失败";
    setPromptPersonaModalStatus(message, true);
  }
});

deletePromptPersonaButton?.addEventListener("click", async () => {
  if (!state.editingPromptPersonaId) {
    return;
  }
  const confirmed = window.confirm("确定删除当前人设吗？对应的 md 文件也会被删除。");
  if (!confirmed) {
    return;
  }

  try {
    await deleteEditingPromptPersona();
    setPromptPersonaModalStatus("人设已删除。");
    setOptimizeStatus("");
  } catch (error) {
    const message = error instanceof Error ? error.message : "删除人设失败";
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
    await loadPromptLibrary();
    await loadPromptPersonas();
    await loadHistory();
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
    closePromptLibraryModal();
    closePromptPersonaModal();
    resetPromptPersonaEditor();
    renderPromptLibraryOptions();
    renderPromptPersonaOptions();
    historyList.innerHTML = `<div class="empty-history">登录后可查看历史记录。</div>`;
    renderCurrentResult(null);
    setAuthUI(false, true);
  }
});

async function bootstrap() {
  try {
    renderPromptLibraryOptions();
    renderPromptPersonaOptions();
    syncPromptModeUI();
    syncAspectRatioPreview();
    const response = await fetch("/api/auth/status");
    const payload = await readJsonSafely(response);
    setAuthUI(Boolean(payload.authenticated), Boolean(payload.passwordEnabled));
    if (!payload.passwordEnabled || payload.authenticated) {
      await loadPromptLibrary();
      await loadPromptPersonas();
      await loadHistory();
    } else {
      historyList.innerHTML = `<div class="empty-history">登录后可查看历史记录。</div>`;
    }
  } catch (error) {
    historyList.innerHTML = `<div class="empty-history">初始化失败，请刷新重试。</div>`;
  }

  syncSubmitButtonState();
  syncAspectRatioPreview();
}

bootstrap();
