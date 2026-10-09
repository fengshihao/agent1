package com.agent1.android.productivity.logic.business

/**
 * 审查模式注入脚本（18 号规划 Phase B / REQ-121）。
 *
 * 常量字符串而非 assets：便于 JVM 单测与 Kotlin/JS 两侧共同引用，避免资产读取 IO。
 * 注入后暴露 window.__agent1Inspect* 接口：
 * - [ENABLE_JS]/[DISABLE_JS]/[CLEAR_JS]：开关与清除高亮（由 Kotlin 侧 evaluateJavascript 调用）
 * - 回传：tap 元素 → `Agent1Inspect.onElementPicked(JSON)`（Android 注入的桥）
 *
 * 只采集元素描述（tag/id/class/文本/截断 outerHTML/祖先链），不做任何 eval/网络/存储。
 */
object HtmlInspectScript {

    /** 安装即注入主体；幂等（window.__agent1InspectInstalled 防重复）。 */
    const val INSTALL_JS: String = """
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
      textPreview: (el.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 80),
      outerHtml: (el.outerHTML || '').slice(0, 400),
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
  }

  document.addEventListener('click', onPick, true);

  window.__agent1InspectEnable = function() { state.enabled = true; };
  window.__agent1InspectDisable = function() {
    state.enabled = false;
    clearOutline();
  };
  window.__agent1InspectClear = function() { clearOutline(); };
})();
"""

    const val ENABLE_JS: String = "window.__agent1InspectEnable && window.__agent1InspectEnable();"
    const val DISABLE_JS: String = "window.__agent1InspectDisable && window.__agent1InspectDisable();"
    const val CLEAR_JS: String = "window.__agent1InspectClear && window.__agent1InspectClear();"

    /** Android 注入的桥对象名（addJavascriptInterface）。 */
    const val BRIDGE_NAME: String = "Agent1Inspect"
}