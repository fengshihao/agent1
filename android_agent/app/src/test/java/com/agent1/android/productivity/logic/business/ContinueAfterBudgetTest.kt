package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.core.RunOutcome
import com.agent1.javaagent.model.AgentMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueAfterBudgetTest {

    @Test
    fun offersAfterTurnBudgetSummary() {
        val messages = listOf(
            AgentMessage.user("做个页面"),
            AgentMessage.assistant("开始了", emptyList()),
            AgentMessage.user(RunOutcome.progressSummaryUserPrompt()),
            AgentMessage.assistant("做到图集还没写完", emptyList()),
        )
        assertTrue(ContinueAfterBudget.shouldOffer(messages, running = false, pendingAsk = false))
    }

    @Test
    fun offersWhenContinueHitUnpairedToolError() {
        val messages = listOf(
            AgentMessage.assistant(
                "错误: 工具调用次数超过上限（48），已停止本轮以避免循环重试",
                emptyList(),
            ),
            AgentMessage.user("继续"),
            AgentMessage.assistant(
                "错误: DashScope error [invalid_request_error]: insufficient tool messages following tool_calls message",
                emptyList(),
            ),
        )
        assertTrue(ContinueAfterBudget.shouldOffer(messages, running = false, pendingAsk = false))
    }

    @Test
    fun offersOnToolBudgetNotice() {
        val messages = listOf(
            AgentMessage.user("配图"),
            AgentMessage.assistant(RunOutcome.toolBudgetNotice(48), emptyList()),
        )
        assertTrue(ContinueAfterBudget.shouldOffer(messages, running = false, pendingAsk = false))
    }

    @Test
    fun hidesAfterSuccessfulContinue() {
        val messages = listOf(
            AgentMessage.assistant(RunOutcome.toolBudgetNotice(48), emptyList()),
            AgentMessage.user("继续"),
            AgentMessage.assistant("图集补好了", emptyList()),
        )
        assertFalse(ContinueAfterBudget.shouldOffer(messages, running = false, pendingAsk = false))
    }

    @Test
    fun hidesWhileRunningOrWaitingForAnswer() {
        val messages = listOf(
            AgentMessage.assistant(RunOutcome.toolBudgetNotice(48), emptyList()),
        )
        assertFalse(ContinueAfterBudget.shouldOffer(messages, running = true, pendingAsk = false))
        assertFalse(ContinueAfterBudget.shouldOffer(messages, running = false, pendingAsk = true))
    }

    @Test
    fun hidesOnNormalChat() {
        val messages = listOf(
            AgentMessage.user("你好"),
            AgentMessage.assistant("你好", emptyList()),
        )
        assertFalse(ContinueAfterBudget.shouldOffer(messages, running = false, pendingAsk = false))
    }

    @Test
    fun offersAfterRunFailureWithoutFinalAnswer() {
        // 真机场景：run 因网络 / SSE / API 错误收场，没有产出最终回答
        val messages = listOf(
            AgentMessage.user("做个规划"),
            AgentMessage.assistant("错误: SSE failed: Software caused connection abort", emptyList()),
        )
        assertTrue(ContinueAfterBudget.shouldOffer(messages, running = false, pendingAsk = false))
    }

    @Test
    fun offersAfterNewFormatFailureRecord() {
        // 新版 core 写入的失败记录带 error 标志（内容不以「错误: 」开头也可以识别）
        val failure = AgentMessage(
            AgentMessage.ROLE_ASSISTANT,
            "SSE failed: Software caused connection abort",
            System.currentTimeMillis(),
            null,
            true,
            emptyList(),
        )
        val messages = listOf(
            AgentMessage.user("做个规划"),
            failure,
        )
        assertTrue(ContinueAfterBudget.shouldOffer(messages, running = false, pendingAsk = false))
    }

    @Test
    fun repeatedFailuresKeepOffering() {
        // 连续点继续都失败：只要仍以失败记录收尾，就继续给重试入口
        val messages = listOf(
            AgentMessage.user("做个规划"),
            AgentMessage.assistant("错误: SSE failed", emptyList()),
            AgentMessage.user("继续"),
            AgentMessage.assistant("错误: DashScope error [1113]: 余额不足或无可用资源包", emptyList()),
        )
        assertTrue(ContinueAfterBudget.shouldOffer(messages, running = false, pendingAsk = false))
    }

    @Test
    fun hidesAfterSuccessfulRetryAfterFailure() {
        val messages = listOf(
            AgentMessage.user("做个规划"),
            AgentMessage.assistant("错误: SSE failed", emptyList()),
            AgentMessage.user("继续"),
            AgentMessage.assistant("规划做好了", emptyList()),
        )
        assertFalse(ContinueAfterBudget.shouldOffer(messages, running = false, pendingAsk = false))
    }
}
