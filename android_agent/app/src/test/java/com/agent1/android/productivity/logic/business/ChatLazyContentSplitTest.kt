package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLazyContentSplitTest {

    @Test
    fun splitAssistantBody_shortContent_staysSingle() {
        val text = "hello\n\nworld"
        assertEquals(listOf(text), ChatLazyContentSplit.splitAssistantBody(text))
    }

    @Test
    fun splitAssistantBody_longContent_producesMultipleBlocks() {
        val para = "段落".repeat(400)
        val text = (1..6).joinToString("\n\n") { "$it $para" }
        assertTrue(text.length > ChatLazyContentSplit.SPLIT_WHEN_CHARS_EXCEED)
        val blocks = ChatLazyContentSplit.splitAssistantBody(text)
        assertTrue(blocks.size >= 2)
        assertEquals(text, blocks.joinToString("\n\n"))
    }

    @Test
    fun splitParagraphs_keepsCodeFenceTogether() {
        val md = "intro\n\n```\nline1\n\nline2\n```\n\noutro"
        val paras = ChatLazyContentSplit.splitParagraphsRespectingCodeFences(md)
        assertTrue(paras.any { it.contains("line1") && it.contains("line2") })
    }
}
