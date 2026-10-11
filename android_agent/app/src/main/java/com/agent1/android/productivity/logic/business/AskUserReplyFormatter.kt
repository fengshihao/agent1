package com.agent1.android.productivity.logic.business

/** 将表单答案格式化为发给模型的用户消息。 */
object AskUserReplyFormatter {

    fun format(form: AskUserFormState): String {
        return format(
            form.request,
            form.textAnswers,
            form.singleChoice,
            form.multiChoice,
            form.customAnswers,
        )
    }

    fun format(
        request: AskUserFormatting.Request,
        textAnswers: Map<String, String>,
        singleChoice: Map<String, String>,
        multiChoice: Map<String, Set<String>>,
        customAnswers: Map<String, String> = emptyMap(),
    ): String {
        return buildString {
            appendLine("【ask_user 回复】")
            request.title?.takeIf { it.isNotBlank() }?.let { appendLine("主题：$it") }
            appendLine()
            for (q in request.questions) {
                val answer = AskUserAnswerLogic.resolveAnswer(
                    q,
                    textAnswers,
                    singleChoice,
                    multiChoice,
                    customAnswers,
                )
                appendLine("- [${q.id}] ${q.prompt}")
                appendLine("  ${answer.ifBlank { "（未填写）" }}")
                appendLine()
            }
        }.trim()
    }

    fun validateRequired(
        request: AskUserFormatting.Request,
        textAnswers: Map<String, String>,
        singleChoice: Map<String, String>,
        multiChoice: Map<String, Set<String>>,
        customAnswers: Map<String, String> = emptyMap(),
    ): String? {
        for (q in request.questions) {
            if (!q.required) continue
            val ok = AskUserAnswerLogic.isAnswered(q, textAnswers, singleChoice, multiChoice, customAnswers)
            if (!ok) {
                return "请完成必答题：${q.prompt}"
            }
        }
        return null
    }

    /** 第一个未完成的必答题下标；全部完成时返回 null。 */
    fun firstMissingRequiredIndex(
        request: AskUserFormatting.Request,
        textAnswers: Map<String, String>,
        singleChoice: Map<String, String>,
        multiChoice: Map<String, Set<String>>,
        customAnswers: Map<String, String> = emptyMap(),
    ): Int? {
        request.questions.forEachIndexed { index, q ->
            if (!q.required) return@forEachIndexed
            if (!AskUserAnswerLogic.isAnswered(q, textAnswers, singleChoice, multiChoice, customAnswers)) {
                return index
            }
        }
        return null
    }
}
