package com.agent1.javaagent.html.inspect;

/**
 * 审查模式注入脚本（18 号规划 Phase B / REQ-121）。
 *
 * <p>常量字符串而非 assets：便于 JVM 单测与 Kotlin/JS 两侧共同引用。
 * 注入后暴露 window.__agent1Inspect* 接口；回传 tap 元素 →
 * {@code Agent1Inspect.onElementPicked(JSON)}（Android 桥）或桌面 postMessage（见 Web UI 计划）。
 */
public final class HtmlInspectScript {

    /** 安装即注入主体；幂等（window.__agent1InspectInstalled 防重复）。 */
    public static final String INSTALL_JS = """
(function() {
  if (window.__agent1InspectInstalled) return;
  window.__agent1InspectInstalled = true;
  var state = { enabled: false, picked: null, prevStyle: null };

  function nthOfType(node) {
    var n = 1, sib = node.previousElementSibling;
    while (sib) {
      if (sib.tagName === node.tagName) n++;
      sib = sib.previousElementSibling;
    }
    return n;
  }

  function describe(el) {
    var ancestors = [];
    var node = el.parentElement;
    var depth = 0;
    while (node && node !== document.documentElement && depth < 6) {
      ancestors.push({
        tag: node.tagName.toLowerCase(),
        id: node.id || '',
        classes: Array.prototype.slice.call(node.classList || []),
        nth: nthOfType(node)
      });
      node = node.parentElement;
      depth++;
    }
    return {
      tag: el.tagName.toLowerCase(),
      id: el.id || '',
      classes: Array.prototype.slice.call(el.classList || []),
      textPreview: (el.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 80),
      outerHtml: (el.outerHTML || '').slice(0, 400),
      render: (function() {
        try {
          var cs = getComputedStyle(el);
          var rect = el.getBoundingClientRect();
          return {
            color: cs.color || '',
            backgroundColor: cs.backgroundColor || '',
            fontSize: cs.fontSize || '',
            display: cs.display || '',
            width: Math.round(rect.width) || 0,
            height: Math.round(rect.height) || 0
          };
        } catch (e) {
          return null;
        }
      })(),
      ancestors: ancestors
    };
  }

  function clearOutline() {
    if (!state.picked) return;
    var el = state.picked;
    if (state.prevStyle === null) {
      el.removeAttribute('style');
    } else {
      el.setAttribute('style', state.prevStyle);
    }
    state.picked = null;
    state.prevStyle = null;
  }

  function onPick(event) {
    if (!state.enabled) return;
    event.preventDefault();
    event.stopPropagation();
    var el = event.target;
    if (!el || el.nodeType !== 1) return;
    clearOutline();
    state.picked = el;
    state.prevStyle = el.getAttribute('style');
    el.style.outline = '2px solid #ff6d00';
    el.style.outlineOffset = '2px';
    var payload;
    try {
      payload = JSON.stringify(describe(el));
    } catch (e) {
      payload = JSON.stringify({ tag: el.tagName.toLowerCase(), id: el.id || '', classes: [], textPreview: '', outerHtml: '', ancestors: [] });
    }
    if (window.Agent1Inspect && window.Agent1Inspect.onElementPicked) {
      window.Agent1Inspect.onElementPicked(payload);
    }
    if (window.parent && window.parent !== window) {
      try {
        window.parent.postMessage({ type: 'agent1-inspect', payload: payload }, window.location.origin);
      } catch (e) { /* ignore */ }
    }
  }

  document.addEventListener('click', onPick, true);

  window.__agent1InspectEnable = function() { state.enabled = true; };
  window.__agent1InspectDisable = function() {
    state.enabled = false;
    clearOutline();
  };
  window.__agent1InspectClear = function() { clearOutline(); };
})();
""";

    public static final String ENABLE_JS =
        "window.__agent1InspectEnable && window.__agent1InspectEnable();";

    public static final String DISABLE_JS =
        "window.__agent1InspectDisable && window.__agent1InspectDisable();";

    public static final String CLEAR_JS =
        "window.__agent1InspectClear && window.__agent1InspectClear();";

    /** Android 注入的桥对象名（addJavascriptInterface）。 */
    public static final String BRIDGE_NAME = "Agent1Inspect";

    /** 桌面 Web UI postMessage 的 type 字段。 */
    public static final String POST_MESSAGE_TYPE = "agent1-inspect";

    private HtmlInspectScript() {
    }
}
