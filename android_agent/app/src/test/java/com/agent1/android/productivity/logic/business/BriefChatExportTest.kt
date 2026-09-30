package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.model.AgentMessage
import com.agent1.javaagent.model.ToolCall
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
    fun format_leadsWithLatestToolFailureForDebug() {
        val messages = listOf(
            AgentMessage.user("写个文件"),
            AgentMessage.assistant(
                "",
                "先看目录",
                listOf(ToolCall("t9", "write_file", """{"path":"a.txt"}""")),
            ),
            AgentMessage.toolResult("t9", "denied: read only", true),
        )
        val text = BriefChatExport.format(
            messages,
            "调试",
            BriefChatExport.Context(sessionId = "s1", modelLabel = "qwen", configError = "no key"),
        )
        assertTrue(text.contains("异常摘要"))
        assertTrue(text.contains("write_file"))
        assertTrue(text.contains("t9"))
        assertTrue(text.contains("denied: read only"))
        assertTrue(text.contains("思考：先看目录"))
        assertTrue(text.contains("configError: no key"))
        assertTrue(text.indexOf("异常摘要") < text.indexOf("**用户**"))
    }

    @Test
    fun format_includesToolErrors() {
        val messages = listOf(
            AgentMessage.toolResult("t1", """{"ok":false,"resultPreview":"timeout"}""", true),
        )
        val text = BriefChatExport.format(messages, "")
        assertTrue(text.contains("工具异常"))
    }

    @Test
    fun compressAssistantContent_stripsCodeFence() {
        val long = "结论如下。\n```kotlin\n" + "x\n".repeat(80) + "```\n收尾。"
        val out = BriefChatExport.compressAssistantContent(long)
        assertFalse(out.contains("```"))
        assertTrue(out.contains("省略代码块"))
        assertTrue(out.contains("结论如下"))
        assertTrue(out.contains("收尾"))
    }

    @Test
    fun format_includesAskUserQuestionsFromAssistantToolCall() {
        val askArgs = """
            {"title":"React PPT","questions":[
              {"id":"audience","prompt":"听众是谁？","type":"text"},
              {"id":"length","prompt":"页数","type":"single_choice","options":["5","10"]}
            ]}
        """.trimIndent()
        val messages = listOf(
            AgentMessage.user("给我生成一个PPT，介绍React模型。"),
            AgentMessage.assistant(
                "",
                listOf(ToolCall("tc1", "ask_user", askArgs)),
            ),
            AgentMessage.toolResult(
                "tc1",
                "已向用户提出 2 个问题，Run 已暂停等待回复。",
                false,
            ),
        )
        val text = BriefChatExport.format(messages, "React PPT")
        assertTrue(text.contains("听众是谁"))
        assertTrue(text.contains("ask_user"))
        assertTrue(text.contains("给我生成一个PPT"))
    }

    @Test
    fun compressAssistantContent_truncatesVeryLongPlainText() {
        val body = "开头说明。" + "中".repeat(3_000) + "结尾总结。"
        val out = BriefChatExport.compressAssistantContent(body)
        assertTrue(out.contains("略去约"))
        assertTrue(out.contains("开头说明"))
        assertTrue(out.contains("结尾总结"))
        assertTrue(out.length < body.length)
    }
}
