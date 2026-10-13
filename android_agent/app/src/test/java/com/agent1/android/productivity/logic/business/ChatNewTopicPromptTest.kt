package com.agent1.android.productivity.logic.business

import com.agent1.android.productivity.ui.viewmodel.ChatLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNewTopicPromptTest {

    @Test
    fun shouldOffer_trueWhenLastMessageIsAssistant() {
        val lines = listOf(
            ChatLine(role = "user", content = "hi"),
            ChatLine(role = "assistant", content = "hello"),
        )
        assertTrue(
            ChatNewTopicPrompt.shouldOffer(
                lines = lines,
                isRunning = false,
                streamingText = "",
                streamingReasoning = "",
                pendingAskUser = false,
            ),
        )
    }

    @Test
    fun shouldOffer_falseAfterUserReplies() {
        val lines = listOf(
            ChatLine(role = "assistant", content = "hello"),
            ChatLine(role = "user", content = "thanks"),
        )
        assertFalse(
            ChatNewTopicPrompt.shouldOffer(
                lines = lines,
                isRunning = false,
                streamingText = "",
                streamingReasoning = "",
                pendingAskUser = false,
            ),
        )
    }
}
