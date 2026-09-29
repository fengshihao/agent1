package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun formatToolResult_plainText() {
        val display = ChatTranscriptFormatting.formatToolResult("hello tool", null)
        assertEquals("hello tool", display.summary)
        assertNull(display.workspaceImagePath)
    }
}
