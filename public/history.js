const albumGrid = document.getElementById("album-grid");
const albumTemplate = document.getElementById("album-item-template");
const albumAuthOverlay = document.getElementById("album-auth-overlay");
const albumLoginForm = document.getElementById("album-login-form");
const albumPasswordInput = document.getElementById("album-password-input");
const albumAuthStatus = document.getElementById("album-auth-status");
const albumLogoutButton = document.getElementById("album-logout-button");

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
  const needsPassword = passwordEnabled && !authenticated;
  albumAuthOverlay.classList.toggle("hidden", !needsPassword);
  albumAuthOverlay.setAttribute("aria-hidden", String(!needsPassword));
  albumLogoutButton.classList.toggle("hidden", !passwordEnabled || !authenticated);
  if (needsPassword) {
    albumPasswordInput.focus();
  }
}

async function deleteAlbumItem(id) {
  const response = await fetch(`/api/history/${id}`, { method: "DELETE" });
  const payload = await readJsonSafely(response);
  if (!response.ok) {
    throw new Error(payload.error || "删除失败");
  }
}

function renderAlbum(items) {
  albumGrid.innerHTML = "";
  if (!items || items.length === 0) {
    albumGrid.innerHTML = `<div class="empty-history">还没有历史图片。</div>`;
    return;
  }

  items.forEach((entry) => {
    const node = albumTemplate.content.firstElementChild.cloneNode(true);
    node.querySelector(".album-image").src = entry.imageUrl;
    node.querySelector(".album-image").alt = entry.prompt || "历史相册图片";
    node.querySelector(".album-time").textContent = formatAlbumDate(entry.createdAt);
    node.querySelector(".album-tags").textContent = `${entry.aspectRatio} · ${entry.imageSize}`;
    node.querySelector(".album-prompt").textContent = entry.prompt || "未填写提示词";
    node.querySelector(".album-open").href = entry.imageUrl;
    node.querySelector(".album-download").href = entry.imageUrl;
    node.querySelector(".album-download").download = entry.downloadName || "banana-pro-image";
    node.querySelector(".album-delete").addEventListener("click", async () => {
      try {
        await deleteAlbumItem(entry.id);
        await loadAlbum();
      } catch (error) {
        alert(error instanceof Error ? error.message : "删除失败");
      }
    });
    albumGrid.appendChild(node);
  });
}

async function loadAlbum() {
  const response = await fetch("/api/history");
  if (response.status === 401) {
    setAlbumAuthUI(false, true);
    albumGrid.innerHTML = `<div class="empty-history">登录后可查看历史相册。</div>`;
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

albumLogoutButton.addEventListener("click", async () => {
  try {
    await fetch("/api/auth/logout", { method: "POST" });
  } finally {
    setAlbumAuthUI(false, true);
    albumGrid.innerHTML = `<div class="empty-history">登录后可查看历史相册。</div>`;
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

bootstrapAlbum();
