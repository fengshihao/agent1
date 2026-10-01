package com.agent1.android.productivity.logic.business

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTranscriptFormattingTest {

    @Test
    fun formatToolResult_parsesWebviewImageJson() {
        val raw = """
            {"ok":true,"elapsedMs":608,"outputPath":"cat.png","outputBytes":30534}
        """.trimIndent()
        val display = ChatTranscriptFormatting.formatToolResult(raw, null)
        assertEquals("cat.png", display.workspaceImagePath)
        assert(display.summary.contains("cat.png"))
        assert(display.summary.contains("30534"))
    }

    @Test
    fun extractMarkdownImagePaths_findsRelativePath() {
        val paths = ChatTranscriptFormatting.extractMarkdownImagePaths("看图 ![小猫](cat.png) 结束")
        assertEquals(listOf("cat.png"), paths)
    }

    @Test
    fun formatToolResult_parsesDocxOutputPath() {
        val raw = """{"ok":true,"path":"out/report.docx","bytes":12000}"""
        val display = ChatTranscriptFormatting.formatToolResult(raw, null)
        assertEquals(listOf("out/report.docx"), display.workspaceFilePaths)
        assert(display.summary.contains("report.docx"))
    }

    @Test
    fun extractMarkdownFileLinks_findsDocxLink() {
        val paths = ChatTranscriptFormatting.extractMarkdownFileLinks("见 [报告](out/a.docx) 和 ![图](x.png)")
        assertEquals(listOf("out/a.docx", "x.png"), paths)
    }

    @Test
    fun extractMarkdownFileLinks_skipsHttp() {
        val paths = ChatTranscriptFormatting.extractMarkdownFileLinks("[外](https://example.com/x.docx)")
        assertEquals(emptyList<String>(), paths)
    }

    @Test
    fun formatToolResult_nullSpillErrorIsNotAnImage() {
        val raw = """{"ok":false,"error":"没有可落盘的返回值:脚本返回了 null 或 undefined。"}"""
        val display = ChatTranscriptFormatting.formatToolResult(raw, null)
        assertNull(display.workspaceImagePath)
        assertTrue(display.summary.contains("没有可落盘的返回值"))
        assertFalse(display.summary.contains("已生成图片"))
    }

    @Test
    fun formatToolResult_includesResultType() {
        val raw = """{"ok":true,"outputPath":"cat.png","outputBytes":10,"resultType":"string","elapsedMs":12}"""
        val display = ChatTranscriptFormatting.formatToolResult(raw, null)
        assertEquals("cat.png", display.workspaceImagePath)
        assertTrue(display.summary.contains("string"))
        assertTrue(display.summary.contains("已生成图片"))
    }

    @Test
    fun workspaceImageBytes_decodesBase64PngAndRejectsNullText() {
        val dir = Files.createTempDirectory("webview-img")
        val base64 = dir.resolve("puppy.png").toFile()
        base64.writeText(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )
        assertTrue(WorkspaceImageBytes.isBase64ImageText(base64))
        val decoded = checkNotNull(WorkspaceImageBytes.decodeBase64Payload(base64))
        assertTrue(decoded.size > 8)
        assertEquals(0x89.toByte(), decoded[0])
        assertEquals('P'.code.toByte(), decoded[1])

        val nullText = dir.resolve("empty.png").toFile()
        nullText.writeText("null")
        assertFalse(WorkspaceImageBytes.isBase64ImageText(nullText))
        assertNull(WorkspaceImageBytes.decodeBase64Payload(nullText))
    }

    @Test
    fun formatToolResult_plainText() {
        val display = ChatTranscriptFormatting.formatToolResult("hello tool", null)
        assertEquals("hello tool", display.summary)
        assertNull(display.workspaceImagePath)
    }
}
