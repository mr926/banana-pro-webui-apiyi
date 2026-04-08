(function initBananaPwaRuntime() {
  const state = {
    beforeInstallPromptEvent: null,
    registrationPromise: null,
  };

  function getPlatformInfo() {
    const ua = navigator.userAgent || "";
    const lower = ua.toLowerCase();
    const isAndroid = /android/i.test(ua);
    const isIOS = /iphone|ipad|ipod/i.test(ua) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
    const isSafari = /safari/i.test(ua) && !/chrome|crios|android/i.test(lower);
    const isStandalone =
      window.matchMedia?.("(display-mode: standalone)")?.matches ||
      window.navigator.standalone === true;

    let canShareFiles = false;
    try {
      canShareFiles =
        typeof navigator.canShare === "function" &&
        typeof File !== "undefined" &&
        navigator.canShare({
          files: [new File(["banana"], "banana.txt", { type: "text/plain" })],
        });
    } catch (error) {
      canShareFiles = false;
    }

    return {
      isAndroid,
      isIOS,
      isSafari,
      isMobile: isAndroid || isIOS,
      isStandalone,
      supportsNotifications: typeof window.Notification !== "undefined",
      supportsVibration: typeof navigator.vibrate === "function",
      supportsShare: typeof navigator.share === "function",
      supportsShareFiles: canShareFiles,
    };
  }

  const platform = getPlatformInfo();

  function sameOrigin(url) {
    try {
      return new URL(url, window.location.href).origin === window.location.origin;
    } catch (error) {
      return false;
    }
  }

  function triggerAnchorDownload(url, filename) {
    const link = document.createElement("a");
    link.href = url;
    link.download = filename || "banana-pro-image";
    link.rel = "noopener";
    document.body.appendChild(link);
    link.click();
    link.remove();
  }

  function triggerHiddenIframe(url) {
    const frame = document.createElement("iframe");
    frame.style.display = "none";
    frame.src = url;
    document.body.appendChild(frame);
    window.setTimeout(() => {
      frame.remove();
    }, 45000);
  }

  function saveBlob(blob, filename) {
    const objectUrl = URL.createObjectURL(blob);
    triggerAnchorDownload(objectUrl, filename);
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 30000);
  }

  async function blobToFile(blob, filename) {
    return new File([blob], filename || "banana-pro-image", {
      type: blob.type || "application/octet-stream",
      lastModified: Date.now(),
    });
  }

  async function fetchBlob(url) {
    const response = await fetch(url, {
      credentials: sameOrigin(url) ? "include" : "omit",
    });
    if (!response.ok) {
      throw new Error("下载内容读取失败，请稍后重试。");
    }
    return response.blob();
  }

  async function shareFileOrUrl(url, filename, title, text) {
    if (platform.supportsShareFiles) {
      try {
        const blob = await fetchBlob(url);
        const file = await blobToFile(blob, filename);
        if (navigator.canShare({ files: [file] })) {
          await navigator.share({
            title,
            text,
            files: [file],
          });
          return { method: "share-file" };
        }
      } catch (error) {
        // Fall through to URL share/open fallback.
      }
    }

    if (platform.supportsShare) {
      try {
        await navigator.share({
          title,
          text,
          url,
        });
        return { method: "share-url" };
      } catch (error) {
        if (error?.name === "AbortError") {
          return { method: "cancelled" };
        }
      }
    }

    window.open(url, "_blank", "noopener,noreferrer");
    return { method: "open-tab" };
  }

  function parseFilenameFromDisposition(disposition, fallback = "banana-pro-download") {
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

  async function requestArchive(endpoint, payload, fallbackName) {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!response.ok) {
      let message = "打包下载失败。";
      try {
        const maybeJson = await response.clone().json();
        message = maybeJson?.error || message;
      } catch (error) {
        // Ignore parse failures and keep generic message.
      }
      const failure = new Error(message);
      failure.status = response.status;
      throw failure;
    }
    const blob = await response.blob();
    const filename = parseFilenameFromDisposition(
      response.headers.get("Content-Disposition"),
      fallbackName || "banana-pro-history.zip",
    );
    return { blob, filename };
  }

  function getDownloadActionLabel(kind = "single") {
    if (kind === "batch") {
      return platform.isMobile ? "打包保存" : "批量下载";
    }
    return platform.isMobile ? "保存 / 分享" : "直接下载";
  }

  function getPlatformDescription() {
    if (platform.isIOS) {
      return platform.isStandalone
        ? "iPhone / iPad 已处于 Web App 模式，适合拍照上传、分享保存和完成提醒。"
        : "iPhone / iPad 推荐用 Safari 安装到主屏幕，上传、保存和提醒会更稳定。";
    }
    if (platform.isAndroid) {
      return platform.isStandalone
        ? "Android 已处于 Web App 模式，支持相机上传、系统分享和完成通知。"
        : "Android 可直接安装到主屏幕，拍照上传和系统保存会更接近应用体验。";
    }
    return "桌面浏览器保留拖拽上传、直接下载和标签标题提醒，适合高频批量操作。";
  }

  function syncPlatformHandoffs() {
    const description = getPlatformDescription();
    const installVisible = Boolean(state.beforeInstallPromptEvent) || (platform.isIOS && !platform.isStandalone);
    const notificationPermission = platform.supportsNotifications ? Notification.permission : "unsupported";

    document.querySelectorAll("[data-platform-title]").forEach((node) => {
      node.textContent = platform.isMobile ? "移动端 Web App 已就绪" : "桌面端 Web 工作台已就绪";
    });
    document.querySelectorAll("[data-platform-description]").forEach((node) => {
      node.textContent = description;
    });
    document.querySelectorAll("[data-install-app]").forEach((button) => {
      button.classList.toggle("hidden", !installVisible);
      button.disabled = false;
      if (platform.isStandalone) {
        button.classList.add("hidden");
        return;
      }
      button.textContent = platform.isIOS ? "安装指引" : "安装到桌面";
    });
    document.querySelectorAll("[data-enable-notifications]").forEach((button) => {
      const unsupported = !platform.supportsNotifications;
      const granted = notificationPermission === "granted";
      button.classList.toggle("hidden", unsupported || granted);
      button.disabled = notificationPermission === "denied";
      if (notificationPermission === "denied") {
        button.textContent = "通知已被禁用";
      } else {
        button.textContent = "开启完成通知";
      }
    });
  }

  async function promptInstall() {
    if (state.beforeInstallPromptEvent) {
      const deferred = state.beforeInstallPromptEvent;
      state.beforeInstallPromptEvent = null;
      await deferred.prompt();
      try {
        await deferred.userChoice;
      } catch (error) {
        // Ignore cancelled install prompt.
      }
      syncPlatformHandoffs();
      return true;
    }

    if (platform.isIOS && !platform.isStandalone) {
      window.alert("请在 Safari 中点击“分享”，再选择“添加到主屏幕”。");
      return false;
    }

    return false;
  }

  async function requestNotificationPermission() {
    if (!platform.supportsNotifications) return false;
    if (Notification.permission === "granted") return true;
    if (Notification.permission === "denied") return false;
    try {
      const result = await Notification.requestPermission();
      syncPlatformHandoffs();
      return result === "granted";
    } catch (error) {
      syncPlatformHandoffs();
      return false;
    }
  }

  async function registerServiceWorker() {
    if (!("serviceWorker" in navigator) || !window.isSecureContext) {
      return null;
    }
    if (!state.registrationPromise) {
      state.registrationPromise = navigator.serviceWorker
        .register("/sw.js", { scope: "/" })
        .then((registration) => {
          registration.update().catch(() => {});
          return registration;
        })
        .catch(() => null);
    }
    return state.registrationPromise;
  }

  async function showNotification(title, body, options = {}) {
    if (!platform.supportsNotifications || Notification.permission !== "granted") {
      return false;
    }

    const notificationOptions = {
      body,
      tag: options.tag || "banana-pro-generate",
      renotify: true,
      icon: "/app-icon.svg",
      badge: "/app-icon.svg",
      data: {
        url: options.url || window.location.href,
      },
    };

    try {
      const registration = await registerServiceWorker();
      if (registration?.showNotification) {
        await registration.showNotification(title, notificationOptions);
        return true;
      }
    } catch (error) {
      // Fallback to page-level notification below.
    }

    try {
      const notification = new Notification(title, notificationOptions);
      notification.onclick = () => {
        window.focus();
        notification.close();
      };
      return true;
    } catch (error) {
      return false;
    }
  }

  function vibrate(pattern) {
    if (platform.supportsVibration) {
      navigator.vibrate(pattern);
    }
  }

  async function deliverDownload({ url, filename, source = "local", title = "Banana Pro", text = "图片已生成完成。" }) {
    if (platform.isMobile) {
      return shareFileOrUrl(url, filename, title, text);
    }

    if (source === "oss-signed") {
      triggerHiddenIframe(url);
      return { method: "iframe" };
    }

    triggerAnchorDownload(url, filename);
    return { method: "anchor" };
  }

  async function deliverBlobDownload({ blob, filename, title = "Banana Pro", text = "文件已准备完成。" }) {
    if (platform.isMobile && platform.supportsShareFiles) {
      try {
        const file = await blobToFile(blob, filename);
        if (navigator.canShare({ files: [file] })) {
          await navigator.share({
            title,
            text,
            files: [file],
          });
          return { method: "share-file" };
        }
      } catch (error) {
        if (error?.name === "AbortError") {
          return { method: "cancelled" };
        }
      }
    }

    saveBlob(blob, filename);
    return { method: "blob-download" };
  }

  function bindPlatformButtons() {
    document.querySelectorAll("[data-install-app]").forEach((button) => {
      button.addEventListener("click", async () => {
        await promptInstall();
      });
    });

    document.querySelectorAll("[data-enable-notifications]").forEach((button) => {
      button.addEventListener("click", async () => {
        await requestNotificationPermission();
      });
    });
  }

  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    state.beforeInstallPromptEvent = event;
    syncPlatformHandoffs();
  });

  window.addEventListener("appinstalled", () => {
    platform.isStandalone = true;
    state.beforeInstallPromptEvent = null;
    syncPlatformHandoffs();
  });

  document.addEventListener("DOMContentLoaded", () => {
    document.documentElement.dataset.platform = platform.isIOS ? "ios" : platform.isAndroid ? "android" : "desktop";
    document.documentElement.dataset.displayMode = platform.isStandalone ? "standalone" : "browser";
    syncPlatformHandoffs();
    bindPlatformButtons();
    void registerServiceWorker();
  });

  window.BananaPWA = {
    platform,
    registerServiceWorker,
    requestNotificationPermission,
    showNotification,
    vibrate,
    promptInstall,
    syncPlatformHandoffs,
    getDownloadActionLabel,
    requestArchive,
    deliverDownload,
    deliverBlobDownload,
  };
})();
