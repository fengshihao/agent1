package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DeckFitScript] 注入脚本的内容特征：脚本本体跑在 WebView 里，
 * JVM 侧锁定关键行为锚点（幂等、wrapper 标记、缩放封顶、重算时机），防静默退化。
 */
class DeckFitScriptTest {

    @Test
    fun installJs_isIdempotent() {
        assertTrue(DeckFitScript.INSTALL_JS.contains("__agent1DeckFitInstalled"))
    }

    @Test
    fun installJs_targetsSlidePagesWithWrapper() {
        assertTrue(DeckFitScript.INSTALL_JS.contains("querySelectorAll('.slide')"))
        // wrapper 让 transform 后的布局尺寸正确（直接 scale 会叠页）。
        assertTrue(DeckFitScript.INSTALL_JS.contains("data-agent1-deck-wrap"))
        assertTrue(DeckFitScript.INSTALL_JS.contains("transformOrigin"))
    }

    @Test
    fun installJs_scalesDownOnly() {
        // 桌面宽视口不放大，只缩小适配。
        assertTrue(DeckFitScript.INSTALL_JS.contains("Math.min(1,"))
    }

    @Test
    fun installJs_recalculatesOnResize() {
        assertTrue(DeckFitScript.INSTALL_JS.contains("addEventListener('resize'"))
        assertTrue(DeckFitScript.INSTALL_JS.contains("addEventListener('orientationchange'"))
    }

    @Test
    fun uninstallJs_restoresOriginalLayout() {
        assertTrue(DeckFitScript.UNINSTALL_JS.contains("removeProperty('transform')"))
        assertFalse(DeckFitScript.UNINSTALL_JS.contains("Math.min(1,"))
    }
}
