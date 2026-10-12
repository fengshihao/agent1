package com.agent1.android.productivity.ui.viewmodel

/** 正文已经出现时仍保留文案，供预览页状态条使用。 */
internal fun streamActivityLabel(content: String, reasoning: String, current: String?): String? {
    if (content.isNotEmpty()) return "正在生成回复…"
    if (reasoning.isNotEmpty()) return "思考中…"
    return current
}
