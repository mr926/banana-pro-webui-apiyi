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
const promptLibraryFile = document.getElementById("prompt-library-file");
const promptTextarea = document.getElementById("prompt");

const state = {
  currentResult: null,
  progressTimer: null,
  progressValue: 0,
  historyHydrated: false,
  audioContext: null,
  authenticated: false,
  passwordEnabled: false,
  baseImageFile: null,
  referenceFiles: [],
  promptLibrary: [],
};

const PROMPT_LIBRARY_STORAGE_KEY = "banana_prompt_library";

const progressSteps = [
  { value: 12, label: "正在上传素材" },
  { value: 28, label: "正在整理提示词" },
  { value: 46, label: "Banana Pro 正在理解基础图" },
  { value: 68, label: "正在融合风格参考" },
  { value: 84, label: "正在渲染细节" },
  { value: 94, label: "正在整理结果" },
];

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

function savePromptLibrary() {
  localStorage.setItem(PROMPT_LIBRARY_STORAGE_KEY, JSON.stringify(state.promptLibrary));
}

function renderPromptLibraryOptions() {
  if (!promptLibrarySelect) return;
  promptLibrarySelect.innerHTML = `<option value="">从提示词列表中选择并插入</option>`;
  state.promptLibrary.forEach((item, index) => {
    const option = document.createElement("option");
    option.value = String(index);
    option.textContent = item;
    promptLibrarySelect.appendChild(option);
  });
}

function loadPromptLibrary() {
  try {
    const raw = localStorage.getItem(PROMPT_LIBRARY_STORAGE_KEY);
    const items = raw ? JSON.parse(raw) : [];
    state.promptLibrary = Array.isArray(items) ? items.filter(Boolean) : [];
  } catch (error) {
    state.promptLibrary = [];
  }
  renderPromptLibraryOptions();
}

function appendPromptText(text) {
  if (!promptTextarea || !text) return;
  const current = promptTextarea.value.trim();
  promptTextarea.value = current ? `${current}\n${text}` : text;
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

baseInput.addEventListener("change", () => {
  const file = baseInput.files && baseInput.files[0];
  state.baseImageFile = file || null;
  baseInput.required = !state.baseImageFile;
  renderBasePreview();
});

referenceInput.addEventListener("change", () => {
  const newFiles = Array.from(referenceInput.files || []);
  if (newFiles.length === 0) {
    return;
  }

  const remainingSlots = Math.max(0, 6 - state.referenceFiles.length);
  const acceptedFiles = newFiles.slice(0, remainingSlots);
  state.referenceFiles = [...state.referenceFiles, ...acceptedFiles];
  syncNativeInputFiles(referenceInput, state.referenceFiles);

  if (newFiles.length > remainingSlots) {
    setStatus("参考图最多上传 6 张，超出的图片已忽略。", true);
  } else {
    setStatus("");
  }
  renderReferencePreview();
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();

  if (!state.baseImageFile) {
    setStatus("请先上传基础结构图。", true);
    return;
  }

  submitButton.disabled = true;
  setStatus("图片正在生成，请稍等...");
  renderLoading();

  const formData = new FormData();
  formData.set("prompt", promptTextarea.value || "");
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
    renderCurrentResult(payload);
    await loadHistory();
  } catch (error) {
    clearProgress();
    const message = error instanceof Error ? error.message : "生成失败";
    setStatus(message, true);
    playErrorSound();
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

promptLibraryFile?.addEventListener("change", async () => {
  const file = promptLibraryFile.files && promptLibraryFile.files[0];
  if (!file) {
    return;
  }
  try {
    const text = await file.text();
    state.promptLibrary = text
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean);
    savePromptLibrary();
    renderPromptLibraryOptions();
    setStatus(`提示词列表已替换，当前共 ${state.promptLibrary.length} 条。`);
  } catch (error) {
    setStatus("导入提示词列表失败。", true);
  } finally {
    promptLibraryFile.value = "";
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
    historyList.innerHTML = `<div class="empty-history">登录后可查看历史记录。</div>`;
    renderCurrentResult(null);
    setAuthUI(false, true);
  }
});

async function bootstrap() {
  try {
    loadPromptLibrary();
    const response = await fetch("/api/auth/status");
    const payload = await readJsonSafely(response);
    setAuthUI(Boolean(payload.authenticated), Boolean(payload.passwordEnabled));
    if (!payload.passwordEnabled || payload.authenticated) {
      await loadHistory();
    } else {
      historyList.innerHTML = `<div class="empty-history">登录后可查看历史记录。</div>`;
    }
  } catch (error) {
    historyList.innerHTML = `<div class="empty-history">初始化失败，请刷新重试。</div>`;
  }
}

bootstrap();
