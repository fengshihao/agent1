package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.model.AgentMessage

/** 判断 transcript 末尾是否仍在等待用户对 {@code ask_user} 的回复。 */
object AskUserPendingDetector {

    fun detectPendingRequest(messages: List<AgentMessage>): AskUserFormatting.Request? {
        if (messages.isEmpty()) return null
        for (i in messages.indices.reversed()) {
            when (messages[i].role) {
                AgentMessage.ROLE_USER -> return null
                AgentMessage.ROLE_ASSISTANT -> {
                    for (call in messages[i].toolCalls) {
                        if (call.name != AskUserFormatting.TOOL_NAME) continue
                        return AskUserFormatting.parseRequestFromToolCall(call)
                    }
                }
                AgentMessage.ROLE_TOOL_RESULT -> {
                    if (!AskUserFormatting.isPauseSummary(messages[i].content)) {
                        return null
                    }
                }
                else -> Unit
            }
        }
        return null
    }
}
