/**
 * 第 4 步：检查 Key 形态，再交给 AgentOne 深链保存。
 */
(function () {
  function t(key, fallback) {
    if (window.Agent1SiteI18n && window.Agent1SiteI18n.t) {
      var value = window.Agent1SiteI18n.t(key);
      if (value) return value;
    }
    return fallback;
  }

  function plausible(key) {
    return key.length >= 16 && key.length <= 256 && /^sk[-_]/.test(key);
  }

  function setStatus(el, message, kind) {
    el.textContent = message;
    el.className = "howto-status" + (kind ? " is-" + kind : "");
  }

  function boot() {
    var input = document.getElementById("setup-key");
    var button = document.getElementById("setup-key-apply");
    var status = document.getElementById("setup-key-status");
    if (!input || !button || !status) return;
    button.addEventListener("click", function () {
      var key = (input.value || "").trim();
      if (!key) {
        setStatus(status, t("setupKeyEmpty", "请先粘贴 Key。"), "error");
        input.focus();
        return;
      }
      if (!plausible(key)) {
        setStatus(status, t("setupKeyInvalid", "这不太像 API Key。一般是 sk- 开头。"), "error");
        input.focus();
        return;
      }
      setStatus(status, t("setupKeyOpening", "正在交给 AgentOne…"), "ok");
      window.location.href = "agent1://model-settings?key=" + encodeURIComponent(key);
    });
    input.addEventListener("keydown", function (event) {
      if (event.key === "Enter") {
        event.preventDefault();
        button.click();
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
