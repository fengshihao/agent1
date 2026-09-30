package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.model.ToolCall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskUserFormattingTest {

    @Test
    fun parseRequest_readsQuestions() {
        val json = """
            {"title":"PPT 需求","questions":[
              {"id":"pages","prompt":"页数？","type":"single_choice","options":["5","10"]},
              {"id":"style","prompt":"风格说明","type":"text"}
            ]}
        """.trimIndent()
        val req = AskUserFormatting.parseRequest(json)!!
        assertTrue(req.title == "PPT 需求")
        assertTrue(req.questions.size == 2)
        assertTrue(req.questions[0].options == listOf("5", "10"))
    }

    @Test
    fun formatForBrief_includesQuestionPrompts() {
        val call = ToolCall(
            "c1",
            AskUserFormatting.TOOL_NAME,
            """{"questions":[{"id":"a","prompt":"主题是什么？","type":"text"}]}""",
        )
        val brief = AskUserFormatting.briefToolCallLine(call)!!
        assertTrue(brief.contains("主题是什么"))
        assertTrue(brief.contains("[a]"))
    }

    @Test
    fun isPauseSummary_detectsAskUserResultText() {
        assertTrue(AskUserFormatting.isPauseSummary("已向用户提出 2 个问题，Run 已暂停等待回复。"))
        assertFalse(AskUserFormatting.isPauseSummary("ok"))
    }
}
