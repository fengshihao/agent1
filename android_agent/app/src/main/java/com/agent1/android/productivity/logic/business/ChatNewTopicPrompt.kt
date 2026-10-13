package com.agent1.android.productivity.logic.business

import com.agent1.android.productivity.ui.viewmodel.ChatLine

/** 「开始新话题」：仅在最新一条助手回复后、用户尚未再发言时展示。 */
object ChatNewTopicPrompt {

    fun shouldOffer(
        lines: List<ChatLine>,
        isRunning: Boolean,
        streamingText: String,
        streamingReasoning: String,
        pendingAskUser: Boolean,
    ): Boolean {
        if (isRunning || pendingAskUser) return false
        if (streamingText.isNotBlank() || streamingReasoning.isNotBlank()) return false
        val last = conversationTail(lines) ?: return false
        return last.role == "assistant"
    }

    fun tailAssistantLine(lines: List<ChatLine>): ChatLine? {
        return conversationTail(lines)?.takeIf { it.role == "assistant" }
    }

    private fun conversationTail(lines: List<ChatLine>): ChatLine? {
        return lines
            .filterNot { it.hideInChat }
            .filter { !it.isTool && !it.isSystemNotice }
            .lastOrNull()
    }
}
