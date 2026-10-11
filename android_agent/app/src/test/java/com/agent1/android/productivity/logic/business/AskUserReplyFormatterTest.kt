package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AskUserReplyFormatterTest {

    @Test
    fun firstMissingRequiredIndex_returnsFirstGap() {
        val request = AskUserFormatting.parseRequest(
            """
            {"questions":[
              {"id":"a","prompt":"A","type":"text","required":true},
              {"id":"b","prompt":"B","type":"text","required":true}
            ]}
            """.trimIndent(),
        )!!
        assertEquals(
            0,
            AskUserReplyFormatter.firstMissingRequiredIndex(request, emptyMap(), emptyMap(), emptyMap()),
        )
        assertEquals(
            1,
            AskUserReplyFormatter.firstMissingRequiredIndex(
                request,
                mapOf("a" to "ok"),
                emptyMap(),
                emptyMap(),
            ),
        )
        assertNull(
            AskUserReplyFormatter.firstMissingRequiredIndex(
                request,
                mapOf("a" to "ok", "b" to "ok"),
                emptyMap(),
                emptyMap(),
            ),
        )
    }

    @Test
    fun format_customAnswerOverridesChoice() {
        val request = AskUserFormatting.parseRequest(
            """{"questions":[{"id":"x","prompt":"偏好？","type":"single_choice","options":["A","B"]}]}""",
        )!!
        val text = AskUserReplyFormatter.format(
            request,
            emptyMap(),
            mapOf("x" to "A"),
            emptyMap(),
            mapOf("x" to "我有别的想法"),
        )
        assert(text.contains("我有别的想法"))
        assert(!text.contains("  A"))
    }
}
