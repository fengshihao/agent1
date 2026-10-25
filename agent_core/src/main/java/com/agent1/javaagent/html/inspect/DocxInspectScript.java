package com.agent1.javaagent.html.inspect;

/** docx 预览审查：在 {@link HtmlInspectScript} 基础上回传 {@code blockIndex}。 */
public final class DocxInspectScript {

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

  function blockIndexFor(el) {
    var node = el;
    while (node && node.nodeType === 1) {
      var raw = node.getAttribute('data-agent1-block-index');
      if (raw !== null && raw !== '') {
        var v = parseInt(raw, 10);
        if (!isNaN(v)) return v;
      }
      node = node.parentElement;
    }
    return null;
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
    var bi = blockIndexFor(el);
    return {
      tag: el.tagName.toLowerCase(),
      id: el.id || '',
      classes: Array.prototype.slice.call(el.classList || []),
      textPreview: (el.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 80),
      outerHtml: (el.outerHTML || '').slice(0, 400),
      blockIndex: bi,
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
      payload = JSON.stringify({ tag: el.tagName.toLowerCase(), id: el.id || '', classes: [], textPreview: '', outerHtml: '', blockIndex: null, ancestors: [] });
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
""";

    private DocxInspectScript() {
    }
}
