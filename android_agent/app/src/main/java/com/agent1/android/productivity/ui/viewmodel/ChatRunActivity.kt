package com.agent1.android.productivity.ui.viewmodel

/**
 * 运行还没结束时，列表底部要不要再放一条进行中提示。
 *
 * 工具进行中时，折叠的工具气泡自己有转圈。只有思考、还没有正文时，思考折叠块也有转圈。
 * 正文是直接展开的，说完一段后如果把提示藏掉，会看起来像停住了。
 */
internal fun shouldShowAssistantPending(state: ChatUiState): Boolean {
    if (!state.isRunning) return false
    val last = state.runTimeline.lastOrNull()
    if (last is ChatRunTimelineItem.ToolPart && !last.finished) return false
    if (state.streamingText.isNotEmpty()) return true
    if (state.streamingReasoning.isNotEmpty()) return false
    if (last == null) return true
    return when (last) {
        is ChatRunTimelineItem.AssistantPart -> true
        is ChatRunTimelineItem.ToolPart -> last.finished
    }
}

/** 正文已经出现时仍保留文案，供底部进行中提示使用。 */
internal fun streamActivityLabel(content: String, reasoning: String, current: String?): String? {
    if (content.isNotEmpty()) return "正在生成回复…"
    if (reasoning.isNotEmpty()) return "思考中…"
    return current
}
