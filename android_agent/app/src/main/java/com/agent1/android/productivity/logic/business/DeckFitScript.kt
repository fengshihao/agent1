package com.agent1.android.productivity.logic.business

/**
 * deck 预览缩放适配注入脚本（21 号规划 T1）。
 *
 * deck.html 固定 1280×720 CSS 像素，手机 WebView 按原始尺寸渲染只能看局部。
 * 注入后对每个 `.slide` 包一层 wrapper：wrapper 布局尺寸 = 页尺寸 × scale
 * （transform 不改布局，直接缩放会叠页），slide 自身 `transform: scale()`。
 * scale = min(1, 视口宽 / 页宽)：竖屏每页等宽、横屏铺满；窗口/旋转变化重算。
 *
 * 常量字符串而非 assets：便于 JVM 单测与 Kotlin 两侧共同引用（对齐 [HtmlInspectScript]）。
 * 只改预览渲染（wrapper 为 `data-agent1-deck-wrap` 标记的内联节点），不改 deck 源文件；
 * html2pptx 导出走无头 `webview_exec`，不经过本预览，互不影响。
 */
object DeckFitScript {

    /** 安装即注入并立即 fit 一次；幂等（`__agent1DeckFitInstalled` 防重复包 wrapper）。 */
    const val INSTALL_JS: String = """
(function() {
  if (window.__agent1DeckFitInstalled) return;
  window.__agent1DeckFitInstalled = true;
  var WRAP_ATTR = 'data-agent1-deck-wrap';
  var PAGE_GAP_PX = 12;

  function firstPageSize(slides) {
    var first = slides[0];
    return { w: first.offsetWidth || 1280, h: first.offsetHeight || 720 };
  }

  function fit() {
    try {
      var slides = document.querySelectorAll('.slide');
      if (!slides.length) return;
      var size = firstPageSize(slides);
      var vw = document.documentElement.clientWidth || window.innerWidth || 0;
      if (!size.w || !vw) return;
      var scale = Math.min(1, vw / size.w);
      scale = Math.round(scale * 1000) / 1000;
      slides.forEach(function(slide) {
        var wrap = slide.parentElement;
        if (!wrap || !wrap.hasAttribute || !wrap.hasAttribute(WRAP_ATTR)) {
          wrap = document.createElement('div');
          wrap.setAttribute(WRAP_ATTR, '');
          slide.parentNode.insertBefore(wrap, slide);
          wrap.appendChild(slide);
        }
        wrap.style.width = Math.round(size.w * scale) + 'px';
        wrap.style.height = Math.round(size.h * scale) + 'px';
        wrap.style.margin = PAGE_GAP_PX + 'px auto';
        wrap.style.overflow = 'hidden';
        slide.style.transform = 'scale(' + scale + ')';
        slide.style.transformOrigin = '0 0';
      });
    } catch (e) {}
  }

  var pending = null;
  function scheduleFit() {
    if (pending != null) return;
    pending = setTimeout(function() { pending = null; fit(); }, 100);
  }

  fit();
  window.addEventListener('resize', scheduleFit);
  window.addEventListener('orientationchange', scheduleFit);
})();
"""

    /** 卸载：还原 wrapper（AI 改源后 reload 由 DOM 重建自然还原，此常量备用）。 */
    const val UNINSTALL_JS: String = """
(function() {
  try {
    document.querySelectorAll('[data-agent1-deck-wrap] > .slide').forEach(function(slide) {
      var wrap = slide.parentElement;
      wrap.parentNode.insertBefore(slide, wrap);
      slide.style.removeProperty('transform');
      slide.style.removeProperty('transform-origin');
      wrap.remove();
    });
    window.__agent1DeckFitInstalled = false;
  } catch (e) {}
})();
"""
}
