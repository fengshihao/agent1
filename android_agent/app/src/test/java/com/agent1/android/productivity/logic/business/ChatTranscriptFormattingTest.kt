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
    fun formatToolResult_plainTextUnchanged() {
        val display = ChatTranscriptFormatting.formatToolResult("hello tool", null)
        assertEquals("hello tool", display.summary)
        assertNull(display.workspaceImagePath)
    }
}
