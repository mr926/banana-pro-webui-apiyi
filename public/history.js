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
const albumPagination = document.getElementById("album-pagination");
const albumPaginationSummary = document.getElementById("album-pagination-summary");
const albumPageInfo = document.getElementById("album-page-info");
const albumPrevPageButton = document.getElementById("album-prev-page");
const albumNextPageButton = document.getElementById("album-next-page");
const ALBUM_PAGE_SIZE = 24;
const IMAGE_TRANSFER_QUEUE_KEY = "banana-pro-image-transfer-queue";

const albumState = {
  items: [],
  selectedIds: new Set(),
  authenticated: false,
  passwordEnabled: false,
  page: 1,
  pageSize: ALBUM_PAGE_SIZE,
  total: 0,
  totalPages: 0,
};

function getPwaRuntime() {
  return window.BananaPWA || null;
}

function getDownloadActionLabel(kind = "single") {
  return getPwaRuntime()?.getDownloadActionLabel(kind) || (kind === "batch" ? "批量下载" : "下载");
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

function getOssUploadStatus(entry) {
  return String(entry?.ossUploadStatus || "").trim().toLowerCase();
}

function isOssUploadPending(entry) {
  return ["queued", "uploading", "retrying"].includes(getOssUploadStatus(entry));
}

function getOssUploadProgress(entry) {
  const status = getOssUploadStatus(entry);
  const fallbackByStatus = {
    queued: 0,
    uploading: 12,
    retrying: 8,
    uploaded: 100,
    failed: 0,
  };
  const raw = Number(entry?.ossUploadProgress);
  const fallback = fallbackByStatus[status] ?? 0;
  if (!Number.isFinite(raw)) {
    return fallback;
  }
  return Math.max(0, Math.min(100, Math.round(raw)));
}

function getOssUploadProgressLabel(entry) {
  const explicit = String(entry?.ossUploadProgressLabel || "").trim();
  if (explicit) return explicit;
  const status = getOssUploadStatus(entry);
  if (status === "queued") return "等待轮到当前任务";
  if (status === "uploading") return "正在上传原图";
  if (status === "retrying") return "等待自动重试";
  if (status === "uploaded") return "原图、缩略图和 XML 已同步到 OSS";
  if (status === "failed") return "上传失败";
  return "";
}

function getOssQueueAheadCount(entry) {
  const raw = Number(entry?.ossQueueAheadCount);
  if (!Number.isFinite(raw)) return 0;
  return Math.max(0, Math.round(raw));
}

function getOssQueueHintText(entry) {
  const status = getOssUploadStatus(entry);
  if (status === "queued") {
    const ahead = getOssQueueAheadCount(entry);
    return ahead > 0 ? `前方还有 ${ahead} 张图片等待上传。` : "前方没有排队任务，即将开始上传。";
  }
  const progressLabel = getOssUploadProgressLabel(entry);
  return progressLabel || "";
}

function getOssStatusPresentation(entry) {
  const status = getOssUploadStatus(entry);
  if (status === "queued") {
    return {
      label: "OSS 排队中",
      tone: "warning",
      placeholderTitle: "正在排队上传到 OSS",
      placeholderText: "图片已生成完成，后台会按顺序上传，当前不加载预览图。",
    };
  }
  if (status === "uploading") {
    return {
      label: "OSS 上传中",
      tone: "info",
      placeholderTitle: "正在上传到 OSS",
      placeholderText: "为了节省带宽，上传完成前不自动拉取图片预览。",
    };
  }
  if (status === "retrying") {
    return {
      label: "OSS 重试中",
      tone: "warning",
      placeholderTitle: "OSS 正在重试上传",
      placeholderText: "上一轮上传失败，后台正在自动重试，当前不显示图片。",
    };
  }
  if (status === "uploaded") {
    return { label: "OSS 已同步", tone: "success" };
  }
  if (status === "failed") {
    return { label: "OSS 上传失败", tone: "danger" };
  }
  return null;
}

function buildPendingMediaPlaceholderMarkup(entry, compact = false) {
  const status = getOssStatusPresentation(entry);
  const title = status?.placeholderTitle || "正在处理中";
  const text = status?.placeholderText || "当前不加载图片预览。";
  const progress = getOssUploadProgress(entry);
  const progressLabel = getOssUploadProgressLabel(entry);
  const queueHint = getOssQueueHintText(entry);
  return `
    <div class="media-upload-placeholder${compact ? " is-compact" : ""}">
      <div class="media-upload-placeholder-icon">⇪</div>
      <h3>${title}</h3>
      <p>${text}</p>
      <div class="media-upload-progress${compact ? " is-compact" : ""}">
        <div class="media-upload-progress-track">
          <div class="media-upload-progress-fill" style="width:${progress}%"></div>
        </div>
        <div class="media-upload-progress-meta">
          <strong>${progressLabel || "处理中"}</strong>
          <span>${progress}%</span>
        </div>
      </div>
      ${queueHint ? `<p class="media-upload-placeholder-detail">${queueHint}</p>` : ""}
    </div>
  `;
}

function appendMediaStatusBadge(container, entry) {
  const status = getOssStatusPresentation(entry);
  if (!container || !status) return;
  const badge = document.createElement("span");
  badge.className = `media-status-badge is-${status.tone}`;
  badge.textContent = status.label;
  container.appendChild(badge);
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
  const downloadUrl = buildDirectDownloadUrl(target.url, target.downloadName);
  if (!runtime) {
    if (target.source === "oss-signed") {
      triggerBackgroundRequest(downloadUrl);
      return { method: "iframe" };
    }
    triggerBrowserDownload(downloadUrl, target.downloadName);
    return { method: "anchor" };
  }
  return runtime.deliverDownload({
    url: downloadUrl,
    filename: target.downloadName,
    source: target.source,
    title: "Banana Pro 历史图片",
    text: "可以保存到本地，也可以直接转发到其他应用。",
  });
}

function getHistorySummaryText() {
  const pageTotal = albumState.items.length;
  const selectedOnPage = getSelectedCountOnPage();
  const selectedTotal = albumState.selectedIds.size;
  const total = Math.max(0, Number(albumState.total || 0));
  if (total === 0) {
    return "还没有历史图片。";
  }
  if (selectedTotal > 0) {
    return `本页已选 ${selectedOnPage} / ${pageTotal} 张，累计已选 ${selectedTotal} 张，共 ${total} 张历史。`;
  }
  return `本页 ${pageTotal} 张，共 ${total} 张历史。`;
}

function getSelectedCountOnPage() {
  return albumState.items.reduce((count, item) => {
    if (typeof item?.id === "string" && item.id && albumState.selectedIds.has(item.id)) {
      return count + 1;
    }
    return count;
  }, 0);
}

function syncAlbumToolbar() {
  const needsPassword = albumState.passwordEnabled && !albumState.authenticated;
  const total = albumState.items.length;
  const selectedCount = albumState.selectedIds.size;
  const selectedOnPage = getSelectedCountOnPage();

  if (albumSelectionStatus) {
    albumSelectionStatus.textContent = needsPassword ? "登录后可进行批量选择与下载。" : getHistorySummaryText();
    albumSelectionStatus.style.color = "";
  }

  if (albumSelectAllButton) {
    albumSelectAllButton.textContent = total > 0 && selectedOnPage === total ? "本页已全选" : "本页全选";
    albumSelectAllButton.disabled = needsPassword || total === 0 || selectedOnPage === total;
  }
  if (albumClearSelectionButton) {
    albumClearSelectionButton.disabled = needsPassword || selectedCount === 0;
  }
  if (albumDownloadSelectedButton) {
    albumDownloadSelectedButton.disabled = needsPassword || selectedCount === 0;
  }
}

function syncAlbumPagination() {
  const total = Math.max(0, Number(albumState.total || 0));
  const currentPage = Math.max(1, Number(albumState.page || 1));
  const totalPages = Math.max(0, Number(albumState.totalPages || 0));
  const hasItems = total > 0;

  albumPagination?.classList.toggle("hidden", !hasItems);

  if (albumPaginationSummary) {
    albumPaginationSummary.textContent = hasItems ? `共 ${total} 张历史图片` : "";
  }
  if (albumPageInfo) {
    albumPageInfo.textContent = hasItems ? `第 ${currentPage} / ${Math.max(totalPages, 1)} 页` : "";
  }
  if (albumPrevPageButton) {
    albumPrevPageButton.disabled = !hasItems || currentPage <= 1;
  }
  if (albumNextPageButton) {
    albumNextPageButton.disabled = !hasItems || currentPage >= totalPages;
  }
}

function resetAlbumPagination(clearSelection = false) {
  albumState.items = [];
  albumState.page = 1;
  albumState.total = 0;
  albumState.totalPages = 0;
  if (clearSelection) {
    albumState.selectedIds.clear();
  }
  syncAlbumToolbar();
  syncAlbumPagination();
}

function buildAlbumHistoryRequestUrl(page = albumState.page) {
  const params = new URLSearchParams();
  params.set("page", String(Math.max(1, Number(page) || 1)));
  params.set("pageSize", String(albumState.pageSize || ALBUM_PAGE_SIZE));
  return `/api/history?${params.toString()}`;
}

async function downloadSelectedInBatch() {
  const ids = Array.from(albumState.selectedIds);
  if (ids.length === 0) return;

  if (albumDownloadSelectedButton) {
    albumDownloadSelectedButton.disabled = true;
    albumDownloadSelectedButton.textContent = "正在准备下载...";
  }

  try {
    const runtime = getPwaRuntime();
    if (runtime?.platform?.isMobile) {
      const archive = await runtime.requestArchive(
        "/api/history/download-zip",
        { ids },
        `banana-pro-history-${new Date().toISOString().slice(0, 10)}.zip`,
      );
      const result = await runtime.deliverBlobDownload({
        blob: archive.blob,
        filename: archive.filename,
        title: "Banana Pro 历史相册",
        text: "历史图片压缩包已准备完成。",
      });
      if (result?.method !== "cancelled") {
        alert("已准备打包文件，你可以直接保存或分享。");
      }
      return;
    }

    const response = await fetch("/api/history/download-links", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ids }),
    });
    if (response.status === 401) {
      setAlbumAuthUI(false, true);
      albumGrid.innerHTML = `<div class="empty-history">登录后可查看历史相册。</div>`;
      resetAlbumPagination(true);
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
    if (error?.status === 401) {
      setAlbumAuthUI(false, true);
      albumGrid.innerHTML = `<div class="empty-history">登录后可查看历史相册。</div>`;
      resetAlbumPagination(true);
      return;
    }
    alert(error instanceof Error ? error.message : "批量下载失败");
  } finally {
    if (albumDownloadSelectedButton) {
      albumDownloadSelectedButton.textContent = getDownloadActionLabel("batch");
    }
    syncAlbumToolbar();
  }
}

function renderAlbum(items) {
  albumState.items = Array.isArray(items) ? items : [];
  albumGrid.innerHTML = "";
  if (albumState.items.length === 0) {
    albumGrid.innerHTML = `<div class="empty-history">还没有历史图片。</div>`;
    syncAlbumToolbar();
    return;
  }

  albumState.items.forEach((entry) => {
    const node = albumTemplate.content.firstElementChild.cloneNode(true);
    const image = node.querySelector(".album-image");
    const imageWrap = node.querySelector(".album-image-wrap");
    const selector = node.querySelector(".album-select");
    const copyButton = node.querySelector(".album-copy");
    const sendBaseButton = node.querySelector(".album-send-base");
    const sendReferenceButton = node.querySelector(".album-send-reference");
    const downloadLink = node.querySelector(".album-download");
    const openLink = node.querySelector(".album-open");
    const hasValidId = typeof entry.id === "string" && entry.id;
    const thumbUrl = getPreferredThumbUrl(entry) || getPreferredImageUrl(entry);
    const imageUrl = getPreferredImageUrl(entry);
    const pendingUpload = isOssUploadPending(entry);
    if (pendingUpload) {
      image.removeAttribute("src");
      image.hidden = true;
      imageWrap?.classList.add("is-upload-pending");
      imageWrap?.insertAdjacentHTML("beforeend", buildPendingMediaPlaceholderMarkup(entry, true));
    } else {
      image.src = thumbUrl;
      image.alt = entry.prompt || "历史相册图片";
      image.loading = "lazy";
      image.decoding = "async";
      image.hidden = false;
    }
    appendMediaStatusBadge(imageWrap, entry);
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
    node.querySelector(".album-tags").textContent = [
      entry.aspectRatio,
      entry.imageSize,
      getOssStatusPresentation(entry)?.label || "",
    ].filter(Boolean).join(" · ");
    node.querySelector(".album-prompt").textContent = entry.prompt || "未填写提示词";
    if (pendingUpload) {
      openLink.removeAttribute("href");
      openLink.setAttribute("aria-disabled", "true");
      openLink.textContent = "上传中";
      openLink.classList.add("is-disabled");
    } else {
      openLink.href = imageUrl;
      openLink.removeAttribute("aria-disabled");
      openLink.textContent = "查看";
      openLink.classList.remove("is-disabled");
    }
    downloadLink.href = imageUrl;
    downloadLink.download = entry.downloadName || "banana-pro-image";
    downloadLink.textContent = getDownloadActionLabel("single");
    downloadLink.addEventListener("click", async (event) => {
      event.preventDefault();
      try {
        await downloadEntryImage(entry);
      } catch (error) {
        if (error instanceof Error && error.message === "__AUTH_REQUIRED__") {
          setAlbumAuthUI(false, true);
          albumGrid.innerHTML = `<div class="empty-history">登录后可查看历史相册。</div>`;
          resetAlbumPagination(true);
          return;
        }
        alert(error instanceof Error ? error.message : "下载失败，请重试。");
      }
    });
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

async function loadAlbum(page = albumState.page) {
  const targetPage = Math.max(1, Number(page) || 1);
  const response = await fetch(buildAlbumHistoryRequestUrl(targetPage));
  if (response.status === 401) {
    setAlbumAuthUI(false, true);
    albumGrid.innerHTML = `<div class="empty-history">登录后可查看历史相册。</div>`;
    resetAlbumPagination(true);
    return;
  }

  const payload = await readJsonSafely(response);
  if (!response.ok) {
    throw new Error(payload.error || "相册加载失败，请刷新重试。");
  }

  albumState.page = Math.max(1, Number(payload.page || targetPage || 1));
  albumState.total = Math.max(0, Number(payload.total || 0));
  albumState.totalPages = Math.max(
    0,
    Number(payload.totalPages || (Array.isArray(payload.items) && payload.items.length > 0 ? 1 : 0)),
  );
  renderAlbum(payload.items || []);
  syncAlbumPagination();
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
    await loadAlbum(1);
  } catch (error) {
    setAlbumAuthStatus(error instanceof Error ? error.message : "登录失败", true);
  }
});

async function bootstrapAlbum() {
  try {
    if (albumDownloadSelectedButton) {
      albumDownloadSelectedButton.textContent = getDownloadActionLabel("batch");
    }
    const response = await fetch("/api/auth/status");
    const payload = await readJsonSafely(response);
    setAlbumAuthUI(Boolean(payload.authenticated), Boolean(payload.passwordEnabled));
    if (!payload.passwordEnabled || payload.authenticated) {
      await loadAlbum(1);
    } else {
      albumGrid.innerHTML = `<div class="empty-history">登录后可查看历史相册。</div>`;
      resetAlbumPagination(true);
    }
  } catch (error) {
    albumGrid.innerHTML = `<div class="empty-history">相册加载失败，请刷新重试。</div>`;
    resetAlbumPagination(true);
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

albumPrevPageButton?.addEventListener("click", async () => {
  if (albumState.page <= 1) return;
  try {
    await loadAlbum(albumState.page - 1);
  } catch (error) {
    alert(error instanceof Error ? error.message : "相册加载失败，请刷新重试。");
  }
});

albumNextPageButton?.addEventListener("click", async () => {
  if (albumState.page >= albumState.totalPages) return;
  try {
    await loadAlbum(albumState.page + 1);
  } catch (error) {
    alert(error instanceof Error ? error.message : "相册加载失败，请刷新重试。");
  }
});

syncAlbumToolbar();
syncAlbumPagination();
bootstrapAlbum();
