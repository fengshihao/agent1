package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskUserAnswerLogicTest {

    @Test
    fun isAnswered_customOrChoice() {
        val q = AskUserFormatting.Question("x", "?", "single_choice", listOf("A"), required = true)
        assertFalse(
            AskUserAnswerLogic.isAnswered(q, emptyMap(), emptyMap(), emptyMap(), emptyMap()),
        )
        assertTrue(
            AskUserAnswerLogic.isAnswered(q, emptyMap(), mapOf("x" to "A"), emptyMap(), emptyMap()),
        )
        assertTrue(
            AskUserAnswerLogic.isAnswered(
                q,
                emptyMap(),
                emptyMap(),
                emptyMap(),
                mapOf("x" to "自定义"),
            ),
        )
    }
}
