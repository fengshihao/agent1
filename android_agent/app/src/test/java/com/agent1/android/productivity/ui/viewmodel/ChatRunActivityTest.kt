package com.agent1.android.productivity.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRunActivityTest {

    @Test
    fun pendingHiddenWhenIdleOrToolInProgress() {
        assertFalse(shouldShowAssistantPending(ChatUiState(isRunning = false, streamingText = "已说完")))
        assertFalse(
            shouldShowAssistantPending(
                ChatUiState(
                    isRunning = true,
                    runTimeline = listOf(tool(finished = false)),
                ),
            ),
        )
    }

    @Test
    fun pendingShownAfterBodyTextWhileRunContinues() {
        assertTrue(
            shouldShowAssistantPending(
                ChatUiState(isRunning = true, streamingText = "我来为你制作一个精美的火箭原理科普页面。"),
            ),
        )
        assertTrue(
            shouldShowAssistantPending(
                ChatUiState(
                    isRunning = true,
                    runTimeline = listOf(
                        ChatRunTimelineItem.AssistantPart(id = "a", content = "先说明原理。"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun pendingHiddenWhileOnlyReasoningStreams() {
        assertFalse(
            shouldShowAssistantPending(
                ChatUiState(isRunning = true, streamingReasoning = "先想版式"),
            ),
        )
    }

    @Test
    fun bodyTextKeepsGeneratingLabel() {
        assertEquals("正在生成回复…", streamActivityLabel("已经有正文", "", null))
        assertEquals("思考中…", streamActivityLabel("", "还在想", null))
        assertEquals("准备下一步…", streamActivityLabel("", "", "准备下一步…"))
    }

    private fun tool(finished: Boolean) = ChatRunTimelineItem.ToolPart(
        id = "t",
        toolCallId = "c",
        toolName = "run_js",
        finished = finished,
    )
}
