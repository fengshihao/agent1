package com.agent1.android.productivity.logic.business

/** 将表单答案格式化为发给模型的用户消息。 */
object AskUserReplyFormatter {

    fun format(
        request: AskUserFormatting.Request,
        textAnswers: Map<String, String>,
        singleChoice: Map<String, String>,
        multiChoice: Map<String, Set<String>>,
    ): String {
        return buildString {
            appendLine("【ask_user 回复】")
            request.title?.takeIf { it.isNotBlank() }?.let { appendLine("主题：$it") }
            appendLine()
            for (q in request.questions) {
                val answer = when (q.type) {
                    "single_choice" -> singleChoice[q.id]?.trim().orEmpty()
                    "multi_choice" -> multiChoice[q.id]?.joinToString("、")?.trim().orEmpty()
                    else -> textAnswers[q.id]?.trim().orEmpty()
                }
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
    ): String? {
        for (q in request.questions) {
            if (!q.required) continue
            val ok = when (q.type) {
                "single_choice" -> !singleChoice[q.id].isNullOrBlank()
                "multi_choice" -> !multiChoice[q.id].isNullOrEmpty()
                else -> !textAnswers[q.id].isNullOrBlank()
            }
            if (!ok) {
                return "请完成必答题：${q.prompt}"
            }
        }
        return null
    }
}
