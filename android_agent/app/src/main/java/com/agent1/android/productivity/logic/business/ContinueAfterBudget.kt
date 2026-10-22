package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.core.RunOutcome
import com.agent1.javaagent.model.AgentMessage

/**
 * 轮次或工具次数用尽后，对话还没做完。底部「继续」只在这种停住的状态出现。
 * 用户已经接着说完、并且模型给出了正常回复之后就收起。
 */
object ContinueAfterBudget {

    fun shouldOffer(
        messages: List<AgentMessage>,
        running: Boolean,
        pendingAsk: Boolean,
    ): Boolean {
        if (running || pendingAsk) return false
        val marker = messages.indexOfLast { isBudgetMarker(it) }
        if (marker < 0) return false
        var sawUserAfter = false
        for (index in marker + 1 until messages.size) {
            val message = messages[index]
            if (message.role == AgentMessage.ROLE_USER && !isProgressPrompt(message)) {
                sawUserAfter = true
            }
            if (
                sawUserAfter &&
                message.role == AgentMessage.ROLE_ASSISTANT &&
                message.content.isNotBlank() &&
                !RunOutcome.isContinuableStop(message.content)
            ) {
                return false
            }
        }
        return true
    }

    private fun isBudgetMarker(message: AgentMessage): Boolean {
        if (isProgressPrompt(message)) return true
        return RunOutcome.isContinuableStop(message.content)
    }

    private fun isProgressPrompt(message: AgentMessage): Boolean {
        return message.role == AgentMessage.ROLE_USER &&
            message.content.trim() == RunOutcome.progressSummaryUserPrompt()
    }
}
