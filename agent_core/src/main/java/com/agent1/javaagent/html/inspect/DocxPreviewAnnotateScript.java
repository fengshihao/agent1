package com.agent1.javaagent.html.inspect;

/** docx 渲染完成后，把 readDocx textView 的 blockIndex 挂到 DOM 上供审查/编辑。 */
public final class DocxPreviewAnnotateScript {

    public static final String ANNOTATE_JS = """
(function() {
  if (window.__agent1DocxAnnotateInstalled) return;
  window.__agent1DocxAnnotateInstalled = true;
  function norm(s) {
    return (s || '').replace(/\\s+/g, ' ').trim();
  }
  window.__agent1AnnotateBlocks = function(blocksJson) {
    var blocks;
    try { blocks = JSON.parse(blocksJson); } catch (e) { return 0; }
    if (!blocks || !blocks.length) return 0;
    var root = document.getElementById('docx-container');
    if (!root) return 0;
    var nodes = root.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li');
    var used = {};
    var tagged = 0;
    for (var i = 0; i < blocks.length; i++) {
      var b = blocks[i];
      if (b.blockIndex === undefined) continue;
      var want = norm(b.text || b.preview || '');
      if (!want) continue;
      for (var n = 0; n < nodes.length; n++) {
        if (used[n]) continue;
        var el = nodes[n];
        var got = norm(el.innerText || el.textContent || '');
        if (!got) continue;
        if (got === want || got.indexOf(want) === 0 || want.indexOf(got) === 0) {
          el.setAttribute('data-agent1-block-index', String(b.blockIndex));
          used[n] = true;
          tagged++;
          break;
        }
      }
    }
    return tagged;
  };
  function docxBridge() {
    try {
      if (window.Agent1Docx) return window.Agent1Docx;
      if (typeof Agent1Docx !== 'undefined') return Agent1Docx;
    } catch (e) { /* ignore */ }
    return null;
  }
  window.__agent1DocxAfterRender = function() {
    var b = docxBridge();
    if (!b || !b.getBlockTextViewJson) return;
    try {
      var json = b.getBlockTextViewJson();
      if (json) window.__agent1AnnotateBlocks(json);
    } catch (e) { /* ignore */ }
  };
})();
""";

    private DocxPreviewAnnotateScript() {
    }
}
