package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTranscriptFormattingUiTest {

    @Test
    fun shouldRenderAsMarkdown_rejectsLargeTable() {
        val md = "| a | b |\n" + "|---|---|\n".repeat(50)
        assertFalse(ChatTranscriptFormatting.shouldRenderAsMarkdown(md))
    }

    @Test
    fun truncateForUiDisplay_capsLength() {
        val long = "x".repeat(25_000)
        val out = ChatTranscriptFormatting.truncateForUiDisplay(long)
        assertTrue(out.length < long.length)
        assertTrue(out.contains("25000"))
    }
}
