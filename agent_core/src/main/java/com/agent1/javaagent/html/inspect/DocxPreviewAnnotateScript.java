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
  function sniffMime(b64) {
    var sample = String(b64 || '').slice(0, 80).replace(/\\s/g, '');
    var head;
    try { head = atob(sample); } catch (e) { return null; }
    var b0 = head.charCodeAt(0);
    var b1 = head.charCodeAt(1);
    if (b0 === 0x89 && head.slice(1, 4) === 'PNG') return 'image/png';
    if (b0 === 0xff && b1 === 0xd8) return 'image/jpeg';
    if (head.slice(0, 3) === 'GIF') return 'image/gif';
    if (head.slice(0, 4) === 'RIFF' && head.slice(8, 12) === 'WEBP') return 'image/webp';
    var start = head.replace(/^\\s+/, '');
    if (start.indexOf('<svg') === 0 || start.indexOf('<?xml') === 0) return 'image/svg+xml';
    return null;
  }
  function retintImages() {
    var root = document.getElementById('docx-container');
    if (!root) return;
    var imgs = root.querySelectorAll('img');
    for (var i = 0; i < imgs.length; i++) {
      var src = imgs[i].getAttribute('src') || '';
      if (src.indexOf('data:image/') === 0 || src.indexOf('data:') !== 0) continue;
      var comma = src.indexOf(',');
      if (comma < 0 || src.indexOf(';base64') < 0) continue;
      var mime = sniffMime(src.slice(comma + 1));
      if (!mime) continue;
      imgs[i].src = 'data:' + mime + ';base64,' + src.slice(comma + 1);
    }
  }
  function pageSections(wrapper) {
    var pages = [];
    var kids = wrapper.children;
    for (var i = 0; i < kids.length; i++) {
      var el = kids[i];
      if (el.tagName === 'SECTION' && el.classList.contains('docx')) pages.push(el);
    }
    return pages;
  }
  // docx-preview 把 wrapNone 浮动图的外框收成 0×0，再用 left/top 按页坐标平移，
  // 父级 overflow:hidden 会把图裁掉。改回图片自身尺寸并取消这段额外平移，让图留在页内。
  function showFloatingImages(root) {
    var boxes = root.querySelectorAll('div');
    for (var i = 0; i < boxes.length; i++) {
      var box = boxes[i];
      if (box.style.width !== '0px' || box.style.height !== '0px') continue;
      var img = box.querySelector('img');
      if (!img) continue;
      var w = img.style.width;
      var h = img.style.height;
      if (!w || w === '0px' || !h || h === '0px') continue;
      box.style.width = w;
      box.style.height = h;
      box.style.left = '0px';
      box.style.top = '0px';
      box.style.position = 'relative';
      box.style.overflow = 'visible';
      img.style.left = '0px';
      img.style.top = '0px';
    }
  }
  function refitPage() {
    var host = document.getElementById('docx-container');
    var wrapper = host && host.querySelector('.docx-wrapper');
    if (!host || !wrapper) return;
    var pages = pageSections(wrapper);
    if (!pages.length) return;
    var i;
    for (i = 0; i < pages.length; i++) pages[i].style.zoom = '';
    wrapper.style.zoom = '';
    wrapper.style.transform = 'none';
    wrapper.style.alignItems = 'flex-start';
    wrapper.style.padding = '0';
    var pageWidth = pages[0].offsetWidth;
    var contentHeight = wrapper.offsetHeight;
    var avail = window.innerWidth || document.documentElement.clientWidth;
    if (!pageWidth || !contentHeight || !avail) return;
    var scale = Math.min(1, avail / pageWidth);
    wrapper.style.width = pageWidth + 'px';
    wrapper.style.transformOrigin = 'top left';
    wrapper.style.transform = 'scale(' + scale + ')';
    host.style.width = '100%';
    host.style.height = Math.ceil(contentHeight * scale) + 'px';
    host.style.overflow = 'hidden';
  }
  window.__agent1DocxAfterRender = function() {
    var root = document.getElementById('docx-container');
    retintImages();
    if (root) showFloatingImages(root);
    refitPage();
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
