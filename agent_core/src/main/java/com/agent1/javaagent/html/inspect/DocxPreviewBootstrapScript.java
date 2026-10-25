package com.agent1.javaagent.html.inspect;

/** 在 onPageFinished 后由宿主注入：等待 docx-preview 与 [Agent1Docx] 就绪再渲染。 */
public final class DocxPreviewBootstrapScript {

    /** 与 host-web shell 内 {@code __agent1DocxBoot} 对齐；在 onPageFinished 后调用。 */
    public static final String RENDER_FROM_BRIDGE_JS =
        "window.__agent1DocxBoot && window.__agent1DocxBoot();";

    /** 真机诊断：写入 logcat（Tag DocxPreview）。 */
    public static final String DIAGNOSTIC_JS = """
(function() {
  var o = { href: String(location.href || '') };
  o.docx = (typeof docx === 'undefined') ? 'missing' : 'ok';
  try { o.agent1 = (typeof Agent1Docx === 'undefined') ? 'missing' : 'ok'; } catch (e) { o.agent1 = String(e); }
  try {
    var b = (typeof Agent1Docx !== 'undefined') ? Agent1Docx : window.Agent1Docx;
    o.b64Len = (b && b.readDocxBase64) ? (b.readDocxBase64() || '').length : -1;
  } catch (e) { o.b64Err = String(e); }
  var c = document.getElementById('docx-container');
  o.htmlLen = c ? c.innerHTML.length : -1;
  o.textLen = c ? (c.innerText || '').length : -1;
  o.textHead = c ? (c.innerText || '').slice(0, 80) : '';
  var er = document.getElementById('error');
  o.err = (er && !er.hidden) ? String(er.textContent || '') : '';
  return JSON.stringify(o);
})();
""";

    private DocxPreviewBootstrapScript() {
    }
}
