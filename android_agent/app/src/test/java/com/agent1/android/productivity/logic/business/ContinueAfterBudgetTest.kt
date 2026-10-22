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
}
