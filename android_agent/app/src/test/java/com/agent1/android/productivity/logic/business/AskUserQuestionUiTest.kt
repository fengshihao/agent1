package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AskUserQuestionUiTest {

    @Test
    fun ratingScale_fromRatingType() {
        val q = AskUserFormatting.Question("s", "分", "rating", emptyList(), true, "", 0, 10)
        assertEquals(AskUserQuestionUi.RatingScale(0, 10), AskUserQuestionUi.ratingScale(q))
    }

    @Test
    fun ratingScale_ignoresNumericSingleChoice() {
        val q = AskUserFormatting.Question(
            "s",
            "分",
            "single_choice",
            (0..10).map { it.toString() },
            true,
        )
        assertNull(AskUserQuestionUi.ratingScale(q))
    }

    @Test
    fun ratingScale_ignoresNonNumericOptions() {
        val q = AskUserFormatting.Question(
            "s",
            "分",
            "single_choice",
            listOf("高", "低"),
            true,
        )
        assertNull(AskUserQuestionUi.ratingScale(q))
    }
}
