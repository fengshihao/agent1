/**
 * Agent1 Android WebView：读 Kotlin 桥注入 Markdown，重渲染时强制覆盖磁盘变更。
 */
(function () {
  function fixEmbedViewportHeight() {
    var h = window.innerHeight;
    if (h < 50) return;
    var header = document.querySelector(".agent1-embed .reader-header");
    var top = header ? Math.ceil(header.getBoundingClientRect().height) : 64;
    if (top < 48) top = 64;
    if (top > h - 80) top = Math.max(48, h - 80);
    document.documentElement.style.setProperty("--agent1-reader-top", top + "px");
    document.documentElement.style.setProperty("--agent1-menu-max", Math.max(240, h - top - 16) + "px");
    var rb = document.getElementById("readerBody");
    if (rb) {
      rb.style.top = top + "px";
      rb.style.bottom = "0px";
      rb.style.height = (h - top) + "px";
    }
  }

  window.addEventListener("resize", fixEmbedViewportHeight);
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", fixEmbedViewportHeight);
  } else {
    fixEmbedViewportHeight();
  }

  function showError(msg) {
    var el = document.getElementById("agent1MdError");
    if (!el) return;
    el.hidden = false;
    el.textContent = msg;
  }

  function bridge() {
    try {
      return window.Agent1Molan || (typeof Agent1Molan !== "undefined" ? Agent1Molan : null);
    } catch (e) {
      return null;
    }
  }

  function injectJson(json, opts) {
    var data = JSON.parse(json);
    if (!window.MolanHost || typeof window.MolanHost.openText !== "function") return false;
    return window.MolanHost.openText(data.name || "document.md", data.text || "", opts || {});
  }

  var MAX_ATTEMPTS = 300;

  function attempt(n) {
    if (!window.MolanHost || typeof window.MolanHost.isReady !== "function") {
      if (n < MAX_ATTEMPTS) {
        setTimeout(function () { attempt(n + 1); }, 60);
        return;
      }
      showError("墨览脚本未加载，请检查网络");
      return;
    }
    if (!window.MolanHost.isReady()) {
      if (n < MAX_ATTEMPTS) {
        setTimeout(function () { attempt(n + 1); }, 60);
        return;
      }
      showError("墨览初始化超时");
      return;
    }
    var b = bridge();
    if (!b || typeof b.readMdJson !== "function") {
      showError("预览桥未就绪");
      return;
    }
    try {
      var json = b.readMdJson();
      if (!json) {
        showError("无法读取 Markdown 文件");
        return;
      }
      if (!injectJson(json)) {
        showError("墨览注入失败（文档可能为空）");
        return;
      }
      waitForPreviewPaint(150);
    } catch (e) {
      showError(String(e && e.message ? e.message : e));
    }
  }

  function previewPainted() {
    var rb = document.getElementById("readerBody");
    var body = document.getElementById("molanPreviewBody");
    if (!body || body.innerHTML.length < 20) return false;
    var h = rb ? rb.getBoundingClientRect().height : body.getBoundingClientRect().height;
    return h > 120;
  }

  function waitForPreviewPaint(triesLeft) {
    fixEmbedViewportHeight();
    if (previewPainted()) {
      revealEmbed();
      return;
    }
    if (triesLeft <= 0) {
      showError("Markdown 预览渲染失败，请检查网络后重试");
      revealEmbed();
      return;
    }
    setTimeout(function () {
      waitForPreviewPaint(triesLeft - 1);
    }, 100);
  }

  function signalHostContentReady() {
    try {
      var b = bridge();
      if (b && b.onContentReady) b.onContentReady();
    } catch (e) { /* ignore */ }
  }

  function revealEmbed() {
    fixEmbedViewportHeight();
    document.documentElement.classList.remove("agent1-boot");
    document.documentElement.classList.add("agent1-ready");
    signalHostContentReady();
  }

  window.__agent1MolanRenderFromBridge = function () {
    var b = bridge();
    if (!b || !window.MolanHost) return "bridge-missing";
    try {
      var json = b.readMdJson();
      if (!json) return "empty";
      return injectJson(json, { force: true }) ? "ok" : "inject-failed";
    } catch (e) {
      return "error:" + String(e && e.message ? e.message : e);
    }
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () { attempt(0); });
  } else {
    attempt(0);
  }
})();
