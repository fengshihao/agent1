package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.model.AgentMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefChatExportTest {

    @Test
    fun format_skipsVerboseToolJson_butKeepsUserAssistant() {
        val messages = listOf(
            AgentMessage.user("你好"),
            AgentMessage.toolResult("t1", """{"ok":true,"outputPath":"a.png","outputBytes":100}""", false),
            AgentMessage.assistant("已生成图片", emptyList()),
        )
        val text = BriefChatExport.format(messages, "测试会话")
        assertTrue(text.contains("你好"))
        assertTrue(text.contains("已生成图片"))
        assertFalse(text.contains("outputBytes"))
    }

    @Test
    fun format_includesToolErrors() {
        val messages = listOf(
            AgentMessage.toolResult("t1", """{"ok":false,"resultPreview":"timeout"}""", true),
        )
        val text = BriefChatExport.format(messages, "")
        assertTrue(text.contains("工具异常"))
    }
}
