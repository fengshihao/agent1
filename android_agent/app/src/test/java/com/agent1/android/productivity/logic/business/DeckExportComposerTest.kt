package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [DeckExportComposer]：导出指令文案带全 AI 执行所需的路径与 mode。 */
class DeckExportComposerTest {

    @Test
    fun compose_editableUsesNativeMode() {
        val text = DeckExportComposer.compose("deck.html", editable = true)
        assertTrue(text.contains("deck.html"))
        assertTrue(text.contains("mode:\"native\""))
        assertTrue(text.contains("renderHtmlDeck"))
        assertTrue(text.contains("deck-native.pptx"))
    }

    @Test
    fun compose_screenshotOmitsNativeMode() {
        val text = DeckExportComposer.compose("out/deck.html", editable = false)
        assertTrue(text.contains("out/deck.html"))
        assertTrue(text.contains("整页截图"))
        assertTrue(text.contains("deck.pptx"))
        assertFalse(text.contains("native"))
    }
}
