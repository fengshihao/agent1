package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DeckPagerScript] 注入脚本内容特征与页码回传解析：
 * 脚本跑在 WebView 里，JVM 侧锁定行为锚点（一屏一页/整页 fit/翻页 API/桥回传），防静默退化。
 */
class DeckPagerScriptTest {

    @Test
    fun installJs_isIdempotent() {
        assertTrue(DeckPagerScript.INSTALL_JS.contains("__agent1DeckPagerInstalled"))
    }

    @Test
    fun installJs_pagesHorizontallyWithSnap() {
        // 一屏一页 + 横向 snap：翻页手势与吸附的载体。
        assertTrue(DeckPagerScript.INSTALL_JS.contains("scroll-snap-type:x mandatory"))
        assertTrue(DeckPagerScript.INSTALL_JS.contains("flex:0 0 100%"))
    }

    @Test
    fun installJs_fitsWholePageBothAxes() {
        // 整页 fit（min 双轴），区别于编辑视角的按宽适配。
        assertTrue(DeckPagerScript.INSTALL_JS.contains("Math.min(vw / w, vh / h)"))
        assertFalse(DeckPagerScript.INSTALL_JS.contains("Math.min(1,"))
        // 全屏布局晚到：visualViewport / 延时 refit 锚点。
        assertTrue(DeckPagerScript.INSTALL_JS.contains("visualViewport"))
        assertTrue(DeckPagerScript.INSTALL_JS.contains("__agent1DeckRefit"))
        assertTrue(DeckPagerScript.REFIT_JS.contains("__agent1DeckRefit"))
    }

    @Test
    fun installJs_reportsPageViaDedicatedBridge() {
        assertTrue(DeckPagerScript.INSTALL_JS.contains(DeckPagerScript.BRIDGE_NAME))
        assertTrue(DeckPagerScript.INSTALL_JS.contains("onPageChanged"))
    }

    @Test
    fun installJs_exposesPagingApis() {
        assertTrue(DeckPagerScript.INSTALL_JS.contains("__agent1DeckGoTo"))
        assertTrue(DeckPagerScript.INSTALL_JS.contains("__agent1DeckNext"))
        assertTrue(DeckPagerScript.INSTALL_JS.contains("__agent1DeckPrev"))
    }

    @Test
    fun installJs_recordsOriginForRestore() {
        // UNINSTALL 依赖安装时记录的原位置 expando。
        assertTrue(DeckPagerScript.INSTALL_JS.contains("__agent1DeckOrigin"))
        assertTrue(DeckPagerScript.UNINSTALL_JS.contains("__agent1DeckOrigin"))
        assertTrue(DeckPagerScript.UNINSTALL_JS.contains("removeProperty('transform')"))
    }

    @Test
    fun installJs_scalesInsideWrapperForFlexCentering() {
        // slide 包在「缩放尺寸 wrapper」（WRAP_ATTR）里、origin '0 0'：
        // 直接在 item 里缩放超宽 slide 时溢出方向 justify-content:center 不做负侧居中（真机黑屏根因）。
        assertTrue(DeckPagerScript.INSTALL_JS.contains("data-agent1-deck-pwrap"))
        assertTrue(DeckPagerScript.INSTALL_JS.contains("hasAttribute(WRAP_ATTR)"))
        assertTrue(DeckPagerScript.INSTALL_JS.contains("transformOrigin = '0 0'"))
    }

    @Test
    fun parsePageEvent_acceptsValidPayload() {
        assertEquals(3 to 16, DeckPagerScript.parsePageEvent("""{"page":3,"total":16}"""))
        assertEquals(1 to 1, DeckPagerScript.parsePageEvent("""{"page":1,"total":1}"""))
    }

    @Test
    fun parsePageEvent_rejectsBadPayload() {
        assertNull(DeckPagerScript.parsePageEvent(""))
        assertNull(DeckPagerScript.parsePageEvent("not json"))
        assertNull(DeckPagerScript.parsePageEvent("""{"page":0,"total":16}"""))
        assertNull(DeckPagerScript.parsePageEvent("""{"page":3,"total":2}"""))
        assertNull(DeckPagerScript.parsePageEvent("""{"total":16}"""))
    }
}
