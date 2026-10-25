package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.core.RunOutcome
import com.agent1.javaagent.model.AgentMessage

/**
 * 对话停住、任务还没做完时，底部展示「继续」。两种停住的状态：
 * 1. 轮次或工具次数用尽（预算标记）；
 * 2. 上一轮 run 异常收场（网络 / SSE / API / 工具环崩溃），transcript 以「错误: …」失败记录结尾，
 *    没有产出最终回答——不管什么原因，都让用户一键重试。
 * 用户已经接着说完、并且模型给出了正常回复之后就收起。
 */
object ContinueAfterBudget {

    fun shouldOffer(
        messages: List<AgentMessage>,
        running: Boolean,
        pendingAsk: Boolean,
    ): Boolean {
        if (running || pendingAsk) return false
        val marker = messages.indexOfLast { isStopMarker(it) }
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

    /** 停住标记：系统注入的进度总结提示、预算用尽通知，或 run 失败记录。 */
    private fun isStopMarker(message: AgentMessage): Boolean {
        if (isProgressPrompt(message)) return true
        if (message.role != AgentMessage.ROLE_ASSISTANT || message.toolCalls.isNotEmpty()) return false
        if (RunOutcome.isContinuableStop(message.content)) return true
        return isRunFailureRecord(message)
    }

    /**
     * 本地合成的运行失败记录（AgentRuntime 捕获异常时写入）。
     * 新记录带 error 标志；旧 transcript 只能按「错误: 」前缀识别。
     * 与 core 的 RunOutcome.isRunFailureRecord 语义对齐（Android 依赖发布件，避免发布顺序耦合）。
     */
    private fun isRunFailureRecord(message: AgentMessage): Boolean {
        return message.isError || message.content.startsWith("错误: ")
    }

    private fun isProgressPrompt(message: AgentMessage): Boolean {
        return message.role == AgentMessage.ROLE_USER &&
            message.content.trim() == RunOutcome.progressSummaryUserPrompt()
    }
}
