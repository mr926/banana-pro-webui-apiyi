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

function getPreferredImageUrl(entry) {
  return String(entry?.ossImageUrl || entry?.imageUrl || "").trim();
}

function getPreferredThumbUrl(entry) {
  return String(entry?.ossThumbUrl || entry?.thumbUrl || "").trim();
}

function getTransferImageUrl(entry) {
  return String(entry?.imageUrl || getPreferredImageUrl(entry) || "").trim();
}

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
  const imageUrl = getTransferImageUrl(entry);
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

function buildDirectDownloadUrl(imageUrl, filename) {
  const trimmedUrl = String(imageUrl || "").trim();
  if (!trimmedUrl) return "";
  return trimmedUrl;
}

function triggerBrowserDownload(url, filename) {
  const link = document.createElement("a");
  link.href = url;
  link.download = filename || "banana-pro-image";
  link.rel = "noopener";
  document.body.appendChild(link);
  link.click();
  link.remove();
}

function triggerBackgroundRequest(url) {
  const frame = document.createElement("iframe");
  frame.style.display = "none";
  frame.src = url;
  document.body.appendChild(frame);
  window.setTimeout(() => {
    frame.remove();
  }, 45000);
}

function wait(ms) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
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

async function downloadSelectedInBatch() {
  const ids = Array.from(albumState.selectedIds);
  if (ids.length === 0) return;

  if (albumDownloadSelectedButton) {
    albumDownloadSelectedButton.disabled = true;
    albumDownloadSelectedButton.textContent = "正在准备下载...";
  }

  try {
    const response = await fetch("/api/history/download-links", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ids }),
    });
    if (response.status === 401) {
      setAlbumAuthUI(false, true);
      albumGrid.innerHTML = `<div class="empty-history">登录后可查看历史相册。</div>`;
      return;
    }
    const payload = await readJsonSafely(response);
    if (!response.ok) {
      throw new Error(payload.error || "获取批量下载链接失败。");
    }

    const targets = Array.isArray(payload.items) ? payload.items.filter((item) => item?.url) : [];

    if (targets.length === 0) {
      throw new Error("选中的图片没有可用下载地址。");
    }

    if (albumDownloadSelectedButton) {
      albumDownloadSelectedButton.textContent = "正在批量下载...";
    }

    for (const [index, item] of targets.entries()) {
      const downloadUrl = buildDirectDownloadUrl(item.url, item.downloadName || item.filename);
      const source = String(item.source || "");
      if (source === "oss-signed") {
        triggerBackgroundRequest(downloadUrl);
      } else {
        triggerBrowserDownload(downloadUrl, item.downloadName || item.filename);
      }
      if (index < targets.length - 1) {
        await wait(180);
      }
    }

    const ossCount = targets.filter((item) => item.source === "oss-signed").length;
    const localCount = targets.length - ossCount;
    const skipped = Math.max(0, Number(payload.skipped || 0));
    if (localCount > 0 || skipped > 0) {
      alert(`已触发下载：OSS ${ossCount} 张，本地 ${localCount} 张，跳过 ${skipped} 张。`);
    }
  } catch (error) {
    alert(error instanceof Error ? error.message : "批量下载失败");
  } finally {
    if (albumDownloadSelectedButton) {
      albumDownloadSelectedButton.textContent = "批量下载选中";
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
    const thumbUrl = getPreferredThumbUrl(entry) || getPreferredImageUrl(entry);
    const imageUrl = getPreferredImageUrl(entry);
    image.src = thumbUrl;
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
    node.querySelector(".album-open").href = imageUrl;
    node.querySelector(".album-download").href = imageUrl;
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
  await downloadSelectedInBatch();
});

syncAlbumToolbar();
bootstrapAlbum();
