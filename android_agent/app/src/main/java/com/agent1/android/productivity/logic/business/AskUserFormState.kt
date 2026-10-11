package com.agent1.android.productivity.logic.business

/** 待回复的 ask_user 表单。答案变更都从这里走，避免面板和 ViewModel 各写一套。 */
data class AskUserFormState(
    val request: AskUserFormatting.Request,
    val textAnswers: Map<String, String> = emptyMap(),
    val singleChoice: Map<String, String> = emptyMap(),
    val multiChoice: Map<String, Set<String>> = emptyMap(),
    /** 与选项或滑杆二选一的自行填写。 */
    val customAnswers: Map<String, String> = emptyMap(),
    val validationError: String? = null,
) {
    fun isAnswered(question: AskUserFormatting.Question): Boolean {
        return AskUserAnswerLogic.isAnswered(
            question,
            textAnswers,
            singleChoice,
            multiChoice,
            customAnswers,
        )
    }

    /** 选填可直接离开；必填必须已答（选项或自行填写）。 */
    fun canAdvance(question: AskUserFormatting.Question): Boolean {
        return !question.required || isAnswered(question)
    }

    fun requiredError(): String? {
        return AskUserReplyFormatter.validateRequired(
            request,
            textAnswers,
            singleChoice,
            multiChoice,
            customAnswers,
        )
    }

    fun firstMissingRequiredIndex(): Int? {
        return AskUserReplyFormatter.firstMissingRequiredIndex(
            request,
            textAnswers,
            singleChoice,
            multiChoice,
            customAnswers,
        )
    }

    fun withText(questionId: String, value: String): AskUserFormState {
        return copy(
            textAnswers = textAnswers + (questionId to value),
            customAnswers = customAnswers - questionId,
            validationError = null,
        )
    }

    fun withSingleChoice(questionId: String, option: String): AskUserFormState {
        return copy(
            singleChoice = singleChoice + (questionId to option),
            customAnswers = customAnswers - questionId,
            validationError = null,
        )
    }

    fun withMultiToggle(questionId: String, option: String): AskUserFormState {
        val current = multiChoice[questionId].orEmpty()
        val next = if (option in current) current - option else current + option
        return copy(
            multiChoice = multiChoice + (questionId to next),
            customAnswers = customAnswers - questionId,
            validationError = null,
        )
    }

    fun withCustomAnswer(questionId: String, value: String): AskUserFormState {
        val next = copy(
            customAnswers = customAnswers + (questionId to value),
            validationError = null,
        )
        if (value.isBlank()) return next
        return next.copy(
            singleChoice = next.singleChoice - questionId,
            multiChoice = next.multiChoice - questionId,
            textAnswers = next.textAnswers - questionId,
        )
    }
}
