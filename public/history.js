const albumGrid = document.getElementById("album-grid");
const albumTemplate = document.getElementById("album-item-template");
const albumAuthOverlay = document.getElementById("album-auth-overlay");
const albumLoginForm = document.getElementById("album-login-form");
const albumPasswordInput = document.getElementById("album-password-input");
const albumAuthStatus = document.getElementById("album-auth-status");
const albumSelectAllButton = document.getElementById("album-select-all");
const albumClearSelectionButton = document.getElementById("album-clear-selection");
const albumDownloadSelectedButton = document.getElementById("album-download-selected");
const albumSelectionStatus = document.getElementById("album-selection-status");
const IMAGE_TRANSFER_QUEUE_KEY = "banana-pro-image-transfer-queue";

const albumState = {
  items: [],
  selectedIds: new Set(),
  authenticated: false,
  passwordEnabled: false,
};

function setAlbumAuthStatus(message, isError = false) {
  albumAuthStatus.textContent = message || "";
  albumAuthStatus.style.color = isError ? "#d14343" : "";
}

function formatAlbumDate(isoString) {
  if (!isoString) return "";
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(isoString));
}

async function readJsonSafely(response) {
  const text = await response.text();
  try {
    return text ? JSON.parse(text) : {};
  } catch (error) {
    return { error: text || "服务返回了无法解析的内容。" };
  }
}

function setAlbumAuthUI(authenticated, passwordEnabled) {
  albumState.authenticated = authenticated;
  albumState.passwordEnabled = passwordEnabled;
  const needsPassword = passwordEnabled && !authenticated;
  albumAuthOverlay.classList.toggle("hidden", !needsPassword);
  albumAuthOverlay.setAttribute("aria-hidden", String(!needsPassword));
  if (needsPassword) {
    albumPasswordInput.focus();
  }
  syncAlbumToolbar();
}

async function deleteAlbumItem(id) {
  const response = await fetch(`/api/history/${id}`, { method: "DELETE" });
  const payload = await readJsonSafely(response);
  if (!response.ok) {
    throw new Error(payload.error || "删除失败");
  }
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

async function copyPrompt(button, prompt) {
  const fullPrompt = String(prompt || "").trim();
  if (!fullPrompt) {
    alert("当前记录没有可复制的提示词。");
    return;
  }

  try {
    await copyTextToClipboard(fullPrompt);
    const originalText = button.textContent;
    button.textContent = "已复制";
    button.disabled = true;
    window.setTimeout(() => {
      button.textContent = originalText;
      button.disabled = false;
    }, 1200);
  } catch (error) {
    alert(error instanceof Error ? error.message : "复制失败，请重试。");
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

function enqueueImageTransfer(entry, target) {
  const imageUrl = String(entry?.imageUrl || "").trim();
  if (!imageUrl) {
    throw new Error("图片地址无效，无法发送。");
  }

  const queue = readImageTransferQueue();
  queue.push({
    target: target === "base" ? "base" : "reference",
    imageUrl,
    downloadName: entry.downloadName || "",
    createdAt: Date.now(),
  });
  localStorage.setItem(IMAGE_TRANSFER_QUEUE_KEY, JSON.stringify(queue));
}

function sendImageToWorkbench(entry, target) {
  try {
    enqueueImageTransfer(entry, target);
    window.location.href = "/";
  } catch (error) {
    alert(error instanceof Error ? error.message : "发送失败，请重试。");
  }
}

function parseDownloadFilename(response) {
  const disposition = response.headers.get("Content-Disposition") || "";
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match && utf8Match[1]) {
    try {
      return decodeURIComponent(utf8Match[1]);
    } catch (error) {
      // Ignore malformed value and continue fallback parsing.
    }
  }

  const plainMatch = disposition.match(/filename="?([^"]+)"?/i);
  if (plainMatch && plainMatch[1]) {
    return plainMatch[1];
  }
  return "banana-pro-history.zip";
}

function getHistorySummaryText() {
  const total = albumState.items.length;
  const selected = albumState.selectedIds.size;
  if (total === 0) {
    return "暂无可选图片。";
  }
  return `已选择 ${selected} / ${total} 张`;
}

function normalizeSelection(items) {
  const validIds = new Set(
    items
      .map((item) => item.id)
      .filter((id) => typeof id === "string" && id),
  );

  Array.from(albumState.selectedIds).forEach((id) => {
    if (!validIds.has(id)) {
      albumState.selectedIds.delete(id);
    }
  });
}

function syncAlbumToolbar() {
  const needsPassword = albumState.passwordEnabled && !albumState.authenticated;
  const total = albumState.items.length;
  const selectedCount = albumState.selectedIds.size;

  if (albumSelectionStatus) {
    albumSelectionStatus.textContent = needsPassword ? "登录后可进行批量选择与下载。" : getHistorySummaryText();
    albumSelectionStatus.style.color = "";
  }

  if (albumSelectAllButton) {
    albumSelectAllButton.disabled = needsPassword || total === 0 || selectedCount === total;
  }
  if (albumClearSelectionButton) {
    albumClearSelectionButton.disabled = needsPassword || selectedCount === 0;
  }
  if (albumDownloadSelectedButton) {
    albumDownloadSelectedButton.disabled = needsPassword || selectedCount === 0;
  }
}

async function downloadSelectedAsZip() {
  const ids = Array.from(albumState.selectedIds);
  if (ids.length === 0) return;

  if (albumDownloadSelectedButton) {
    albumDownloadSelectedButton.disabled = true;
    albumDownloadSelectedButton.textContent = "正在打包...";
  }

  try {
    const response = await fetch("/api/history/download-zip", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ids }),
    });

    if (response.status === 401) {
      setAlbumAuthUI(false, true);
      albumGrid.innerHTML = `<div class="empty-history">登录后可查看历史相册。</div>`;
      return;
    }

    if (!response.ok) {
      const payload = await readJsonSafely(response);
      throw new Error(payload.error || "下载 ZIP 失败");
    }

    const blob = await response.blob();
    if (!blob.size) {
      throw new Error("打包结果为空，请重试。");
    }

    const filename = parseDownloadFilename(response);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  } catch (error) {
    alert(error instanceof Error ? error.message : "下载 ZIP 失败");
  } finally {
    if (albumDownloadSelectedButton) {
      albumDownloadSelectedButton.textContent = "下载选中 ZIP";
    }
    syncAlbumToolbar();
  }
}

function renderAlbum(items) {
  albumState.items = Array.isArray(items) ? items : [];
  normalizeSelection(albumState.items);
  albumGrid.innerHTML = "";
  if (albumState.items.length === 0) {
    albumGrid.innerHTML = `<div class="empty-history">还没有历史图片。</div>`;
    syncAlbumToolbar();
    return;
  }

  albumState.items.forEach((entry) => {
    const node = albumTemplate.content.firstElementChild.cloneNode(true);
    const image = node.querySelector(".album-image");
    const selector = node.querySelector(".album-select");
    const copyButton = node.querySelector(".album-copy");
    const sendBaseButton = node.querySelector(".album-send-base");
    const sendReferenceButton = node.querySelector(".album-send-reference");
    const hasValidId = typeof entry.id === "string" && entry.id;
    image.src = entry.thumbUrl || "";
    image.alt = entry.prompt || "历史相册图片";
    image.loading = "lazy";
    image.decoding = "async";
    selector.checked = hasValidId ? albumState.selectedIds.has(entry.id) : false;
    selector.disabled = !hasValidId;
    selector.addEventListener("change", () => {
      if (!hasValidId) return;
      if (selector.checked) {
        albumState.selectedIds.add(entry.id);
      } else {
        albumState.selectedIds.delete(entry.id);
      }
      syncAlbumToolbar();
    });
    node.querySelector(".album-time").textContent = formatAlbumDate(entry.createdAt);
    node.querySelector(".album-tags").textContent = `${entry.aspectRatio} · ${entry.imageSize}`;
    node.querySelector(".album-prompt").textContent = entry.prompt || "未填写提示词";
    node.querySelector(".album-open").href = entry.imageUrl;
    node.querySelector(".album-download").href = entry.imageUrl;
    node.querySelector(".album-download").download = entry.downloadName || "banana-pro-image";
    copyButton?.addEventListener("click", async () => {
      await copyPrompt(copyButton, entry.prompt);
    });
    sendBaseButton?.addEventListener("click", () => {
      sendImageToWorkbench(entry, "base");
    });
    sendReferenceButton?.addEventListener("click", () => {
      sendImageToWorkbench(entry, "reference");
    });
    node.querySelector(".album-delete").addEventListener("click", async () => {
      try {
        await deleteAlbumItem(entry.id);
        albumState.selectedIds.delete(entry.id);
        await loadAlbum();
      } catch (error) {
        alert(error instanceof Error ? error.message : "删除失败");
      }
    });
    albumGrid.appendChild(node);
  });
  syncAlbumToolbar();
}

async function loadAlbum() {
  const response = await fetch("/api/history");
  if (response.status === 401) {
    setAlbumAuthUI(false, true);
    albumGrid.innerHTML = `<div class="empty-history">登录后可查看历史相册。</div>`;
    albumState.items = [];
    albumState.selectedIds.clear();
    syncAlbumToolbar();
    return;
  }
  const payload = await readJsonSafely(response);
  renderAlbum(payload.items || []);
}

albumLoginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const password = albumPasswordInput.value.trim();
  if (!password) {
    setAlbumAuthStatus("请输入密码。", true);
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
    setAlbumAuthStatus("");
    albumPasswordInput.value = "";
    setAlbumAuthUI(true, payload.passwordEnabled !== false);
    await loadAlbum();
  } catch (error) {
    setAlbumAuthStatus(error instanceof Error ? error.message : "登录失败", true);
  }
});

async function bootstrapAlbum() {
  try {
    const response = await fetch("/api/auth/status");
    const payload = await readJsonSafely(response);
    setAlbumAuthUI(Boolean(payload.authenticated), Boolean(payload.passwordEnabled));
    if (!payload.passwordEnabled || payload.authenticated) {
      await loadAlbum();
    } else {
      albumGrid.innerHTML = `<div class="empty-history">登录后可查看历史相册。</div>`;
    }
  } catch (error) {
    albumGrid.innerHTML = `<div class="empty-history">相册加载失败，请刷新重试。</div>`;
  }
}

albumSelectAllButton?.addEventListener("click", () => {
  albumState.items.forEach((entry) => {
    if (typeof entry.id === "string" && entry.id) {
      albumState.selectedIds.add(entry.id);
    }
  });
  renderAlbum(albumState.items);
});

albumClearSelectionButton?.addEventListener("click", () => {
  albumState.selectedIds.clear();
  renderAlbum(albumState.items);
});

albumDownloadSelectedButton?.addEventListener("click", async () => {
  await downloadSelectedAsZip();
});

syncAlbumToolbar();
bootstrapAlbum();
