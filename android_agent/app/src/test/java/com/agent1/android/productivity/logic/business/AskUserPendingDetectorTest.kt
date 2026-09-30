package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.model.AgentMessage
import com.agent1.javaagent.model.ToolCall
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AskUserPendingDetectorTest {

    @Test
    fun detectPending_whenAskUserAtEnd() {
        val args = """{"questions":[{"id":"a","prompt":"主题？","type":"text"}]}"""
        val messages = listOf(
            AgentMessage.user("做 PPT"),
            AgentMessage.assistant("", listOf(ToolCall("c1", "ask_user", args))),
            AgentMessage.toolResult("c1", "已向用户提出 1 个问题，Run 已暂停等待回复。", false),
        )
        assertNotNull(AskUserPendingDetector.detectPendingRequest(messages))
    }

    @Test
    fun detectPending_nullAfterUserReply() {
        val args = """{"questions":[{"id":"a","prompt":"主题？","type":"text"}]}"""
        val messages = listOf(
            AgentMessage.assistant("", listOf(ToolCall("c1", "ask_user", args))),
            AgentMessage.toolResult("c1", "已向用户提出 1 个问题，Run 已暂停等待回复。", false),
            AgentMessage.user("React 入门"),
        )
        assertNull(AskUserPendingDetector.detectPendingRequest(messages))
    }
}
