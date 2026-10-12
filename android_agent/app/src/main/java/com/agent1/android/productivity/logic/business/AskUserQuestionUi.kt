package com.agent1.android.productivity.logic.business

/** ask_user 在 Android 上的展示规则。打分只认 {@code type=rating}，避免把数字单选误画成滑杆。 */
object AskUserQuestionUi {

    data class RatingScale(
        val min: Int,
        val max: Int,
    )

    fun ratingScale(question: AskUserFormatting.Question): RatingScale? {
        if (question.type != "rating") return null
        val min = question.ratingMin ?: 1
        val max = question.ratingMax ?: 5
        if (min >= max) return null
        return RatingScale(min, max)
    }

    /** `workspace_files` 题型：options 首项为最大可选数量（缺省不限）。 */
    fun workspaceMaxFiles(question: AskUserFormatting.Question): Int? {
        if (question.type != "workspace_files") return null
        return question.options.firstOrNull()?.toIntOrNull()?.coerceAtLeast(1)
    }
}
