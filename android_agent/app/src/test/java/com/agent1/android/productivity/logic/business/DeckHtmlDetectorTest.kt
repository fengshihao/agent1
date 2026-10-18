package com.agent1.android.productivity.logic.business

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [DeckHtmlDetector]：deck.html（html2pptx `.slide` 约定）检测的命中与误报面。 */
class DeckHtmlDetectorTest {

    @Test
    fun isDeckHtml_matchesSlideSection() {
        assertTrue(
            DeckHtmlDetector.isDeckHtml("<html><body><section class=\"slide\">p1</section></body></html>"),
        )
    }

    @Test
    fun isDeckHtml_matchesMultiClassAndSingleQuotes() {
        assertTrue(DeckHtmlDetector.isDeckHtml("<section class=\"slide cover\">c</section>"))
        assertTrue(DeckHtmlDetector.isDeckHtml("<section class='slide'>c</section>"))
        assertTrue(DeckHtmlDetector.isDeckHtml("<div class=\"page slide dark\">c</div>"))
    }

    @Test
    fun isDeckHtml_rejectsPlainHtml() {
        assertFalse(DeckHtmlDetector.isDeckHtml("<html><body class=\"container\"><p>hi</p></body></html>"))
        assertFalse(DeckHtmlDetector.isDeckHtml(""))
        assertFalse(DeckHtmlDetector.isDeckHtml("plain text without any attribute"))
    }

    @Test
    fun isDeckHtml_rejectsCssRuleWithoutClassAttr() {
        // 只有 CSS 规则没有元素时不视为 deck（避免只定义了样式的普通页误入 deck 模式）。
        assertFalse(
            DeckHtmlDetector.isDeckHtml("<html><head><style>.slide{width:1280px;height:720px}</style></head></html>"),
        )
    }

    @Test
    fun isDeckHtml_rejectsSimilarWords() {
        assertFalse(DeckHtmlDetector.isDeckHtml("<div class=\"slideshow\">c</div>"))
        assertFalse(DeckHtmlDetector.isDeckHtml("<div class=\"myslide\">c</div>"))
    }

    @Test
    fun scanFile_readsHeadOnly() {
        val dir = Files.createTempDirectory("deck-detect")
        // 头部即有 slide（真实 deck 的形态：head CSS 后紧跟第一页）。
        val deck = dir.resolve("deck.html").toFile()
        deck.writeText(
            "<html><head><style>.slide{width:1280px}</style></head><body><section class=\"slide\">p1</section></body></html>",
        )
        assertTrue(DeckHtmlDetector.scanFile(deck))
    }

    @Test
    fun scanFile_falsyForMissingOrPlain() {
        assertFalse(DeckHtmlDetector.scanFile(null))
        assertFalse(DeckHtmlDetector.scanFile(Files.createTempDirectory("deck-detect").resolve("a.html").toFile()))
        val dir = Files.createTempDirectory("deck-detect")
        val plain = dir.resolve("index.html").toFile()
        plain.writeText("<html><body class=\"container\">hi</body></html>")
        assertFalse(DeckHtmlDetector.scanFile(plain))
    }

    @Test
    fun scanFile_capsScanAtHeadBytes() {
        // 超过扫描窗口的 slide 不可见：deck 首页必然在头部，此用例锁定截断语义与不 OOM。
        val dir = Files.createTempDirectory("deck-detect")
        val big = dir.resolve("big.html").toFile()
        big.outputStream().use { stream ->
            stream.write(ByteArray(DeckHtmlDetector.MAX_SCAN_BYTES + 1024) { ' '.code.toByte() })
        }
        assertFalse(DeckHtmlDetector.scanFile(big))
        assertEquals((DeckHtmlDetector.MAX_SCAN_BYTES + 1024).toLong(), big.length())
    }
}
