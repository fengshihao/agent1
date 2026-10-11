package com.agent1.android.productivity.logic.business

/** ask_user 结构化选项与「自行填写」二选一时的答案判定与合并。 */
object AskUserAnswerLogic {

    fun customText(questionId: String, customAnswers: Map<String, String>): String {
        return customAnswers[questionId]?.trim().orEmpty()
    }

    fun hasCustomAnswer(questionId: String, customAnswers: Map<String, String>): Boolean {
        return customText(questionId, customAnswers).isNotEmpty()
    }

    fun resolveAnswer(
        question: AskUserFormatting.Question,
        textAnswers: Map<String, String>,
        singleChoice: Map<String, String>,
        multiChoice: Map<String, Set<String>>,
        customAnswers: Map<String, String>,
    ): String {
        val custom = customText(question.id, customAnswers)
        if (custom.isNotEmpty()) return custom
        return when (question.type) {
            "single_choice" -> singleChoice[question.id]?.trim().orEmpty()
            "multi_choice" -> multiChoice[question.id]?.joinToString("、")?.trim().orEmpty()
            else -> textAnswers[question.id]?.trim().orEmpty()
        }
    }

    fun isAnswered(
        question: AskUserFormatting.Question,
        textAnswers: Map<String, String>,
        singleChoice: Map<String, String>,
        multiChoice: Map<String, Set<String>>,
        customAnswers: Map<String, String>,
    ): Boolean {
        return resolveAnswer(question, textAnswers, singleChoice, multiChoice, customAnswers).isNotBlank()
    }
}
