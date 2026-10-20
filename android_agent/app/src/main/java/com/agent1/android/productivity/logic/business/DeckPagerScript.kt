package com.agent1.android.productivity.logic.business

import org.json.JSONObject

/**
 * deck 演示视角注入脚本（21 号规划 T2：翻页 + 页码）。
 *
 * 与 [DeckFitScript]（编辑视角：垂直滚动、按宽适配）互斥使用——进入全屏演示时
 * Kotlin 侧先跑 [DeckFitScript.UNINSTALL_JS] 再装本脚本，退出时反向。
 * 结构：所有 `.slide` 移入 `position:fixed` 全屏 pager（横向 flex + scroll-snap），
 * 每页 `flex:0 0 100%` 一屏一页，slide `scale(min(视口宽/页宽, 视口高/页高))`
 * 整页 fit 居中（横屏 16:9 铺满、竖屏上下留边）；横向滑动翻页（scroll-snap），
 * 页码变化经 [BRIDGE_NAME] 桥回传 Kotlin 显示「3/16」。
 *
 * 只采集页码与翻页意图，不提供 eval/文件/网络能力（对齐审查桥约束）。
 */
object DeckPagerScript {

    /** 页码回传桥（addJavascriptInterface 注入名）。 */
    const val BRIDGE_NAME: String = "Agent1Deck"

    /** 安装即构建 pager 并进入第 1 页；幂等（重装前须先 UNINSTALL 还原 DOM）。 */
    const val INSTALL_JS: String = """
(function() {
  if (window.__agent1DeckPagerInstalled) return;
  window.__agent1DeckPagerInstalled = true;
  var PAGER_ATTR = 'data-agent1-deck-pager';

  function pagerEl() {
    return document.querySelector('[' + PAGER_ATTR + ']');
  }

  function currentPage() {
    var pager = pagerEl();
    if (!pager) return 1;
    var itemW = pager.clientWidth || 1;
    return Math.round(pager.scrollLeft / itemW) + 1;
  }

  function fit() {
    var pager = pagerEl();
    if (!pager) return;
    // 全屏切换瞬间 pager 可能尚未完成布局：回退到 window / visualViewport。
    var vw = pager.clientWidth || window.innerWidth || 0;
    var vh = pager.clientHeight || window.innerHeight || 0;
    try {
      if ((!vw || !vh) && window.visualViewport) {
        vw = vw || window.visualViewport.width || 0;
        vh = vh || window.visualViewport.height || 0;
      }
    } catch (e) {}
    var slides = pager.querySelectorAll('.slide');
    if (!slides.length || !vw || !vh) return;
    var first = slides[0];
    var w = first.offsetWidth || first.scrollWidth || 0;
    var h = first.offsetHeight || first.scrollHeight || 0;
    if (!w || !h) return;
    var scale = Math.round(Math.min(vw / w, vh / h) * 1000) / 1000;
    if (!(scale > 0)) return;
    slides.forEach(function(slide) {
      slide.style.flex = '0 0 auto';
      slide.style.transform = 'scale(' + scale + ')';
      slide.style.transformOrigin = 'center center';
    });
  }

  function build() {
    var slides = document.querySelectorAll('.slide');
    if (!slides.length) return;
    var pager = document.createElement('div');
    pager.setAttribute(PAGER_ATTR, '');
    pager.style.cssText = 'position:fixed;left:0;top:0;right:0;bottom:0;'
      + 'display:flex;flex-direction:row;overflow-x:auto;overflow-y:hidden;'
      + 'scroll-snap-type:x mandatory;background:#111;z-index:2147483000;';
    Array.prototype.forEach.call(slides, function(slide) {
      // UNINSTALL 还原依据：原父节点与后兄弟（expando，不进 DOM 属性）。
      slide.__agent1DeckOrigin = { parent: slide.parentNode, next: slide.nextSibling };
      var item = document.createElement('div');
      item.style.cssText = 'flex:0 0 100%;scroll-snap-align:center;'
        + 'display:flex;align-items:center;justify-content:center;';
      pager.appendChild(item);
      item.appendChild(slide);
    });
    document.body.appendChild(pager);
    document.documentElement.style.overflow = 'hidden';
    fit();
    pager.addEventListener('scroll', onScroll, { passive: true });
    notify(1, slides.length);
  }

  var notifyPending = null;
  function onScroll() {
    if (notifyPending != null) return;
    notifyPending = setTimeout(function() {
      notifyPending = null;
      var pager = pagerEl();
      if (!pager) return;
      notify(currentPage(), pager.querySelectorAll('.slide').length);
    }, 80);
  }

  function notify(page, total) {
    try {
      if (window.Agent1Deck && window.Agent1Deck.onPageChanged) {
        window.Agent1Deck.onPageChanged(JSON.stringify({ page: page, total: total }));
      }
    } catch (e) {}
  }

  window.__agent1DeckGoTo = function(page) {
    var pager = pagerEl();
    if (!pager) return;
    var total = pager.querySelectorAll('.slide').length;
    page = Math.max(1, Math.min(total, Math.round(page)));
    pager.scrollTo({ left: (page - 1) * pager.clientWidth, behavior: 'smooth' });
  };
  window.__agent1DeckNext = function() { step(1); };
  window.__agent1DeckPrev = function() { step(-1); };
  function step(delta) {
    window.__agent1DeckGoTo(currentPage() + delta);
  }

  var resizePending = null;
  function scheduleFit() {
    if (resizePending != null) return;
    resizePending = setTimeout(function() { resizePending = null; fit(); }, 100);
  }
  window.addEventListener('resize', scheduleFit);
  window.addEventListener('orientationchange', scheduleFit);
  window.__agent1DeckRefit = function() { fit(); };

  build();
  // 沉浸式切换后 WebView 尺寸常晚一拍：多帧 + 短延时重算，避免首屏黑/未缩放。
  try {
    requestAnimationFrame(function() {
      fit();
      requestAnimationFrame(fit);
    });
  } catch (e) { fit(); }
  setTimeout(fit, 50);
  setTimeout(fit, 200);
  setTimeout(fit, 500);
})();
"""

    /** 演示视角下强制重算整页 fit（全屏 insets 变化后由 Kotlin 再调一次）。 */
    const val REFIT_JS: String = "window.__agent1DeckRefit && window.__agent1DeckRefit();"

    /** 还原 `.slide` 到原父节点原位置并移除 pager（记录于安装时的 expando）。 */
    const val UNINSTALL_JS: String = """
(function() {
  try {
    document.querySelectorAll('.slide').forEach(function(slide) {
      var origin = slide.__agent1DeckOrigin;
      slide.style.removeProperty('flex');
      slide.style.removeProperty('transform');
      slide.style.removeProperty('transform-origin');
      if (origin && origin.parent) {
        origin.parent.insertBefore(slide, origin.next || null);
      }
    });
    var pager = document.querySelector('[data-agent1-deck-pager]');
    if (pager) pager.remove();
    document.documentElement.style.removeProperty('overflow');
    window.__agent1DeckPagerInstalled = false;
  } catch (e) {}
})();
"""

    const val NEXT_JS: String = "window.__agent1DeckNext && window.__agent1DeckNext();"
    const val PREV_JS: String = "window.__agent1DeckPrev && window.__agent1DeckPrev();"

    /** 回到第 [page] 页（热更新 reload 后由 Kotlin 侧恢复页位）。 */
    fun goToJs(page: Int): String =
        "window.__agent1DeckGoTo && window.__agent1DeckGoTo($page);"

    /**
     * 桥回传 JSON（`{"page":3,"total":16}`）→ 页码对；容错：缺字段/非正数/坏 JSON → null。
     */
    fun parsePageEvent(json: String): Pair<Int, Int>? {
        if (json.isBlank()) return null
        return runCatching {
            val obj = JSONObject(json)
            val page = obj.optInt("page", 0)
            val total = obj.optInt("total", 0)
            if (page < 1 || total < 1 || page > total) null else page to total
        }.getOrNull()
    }
}
