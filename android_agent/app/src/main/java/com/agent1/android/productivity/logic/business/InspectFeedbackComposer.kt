package com.agent1.android.productivity.logic.business

/**
 * 审查模式反馈草稿组装（18 号规划 Phase B / REQ-122）。
 * 草稿回填输入框但不自动发送：用户补充问题描述后手动发送。
 */
object InspectFeedbackComposer {

    /** 面板/草稿里 outerHtml 的展示与回传上限。 */
    private const val OUTER_HTML_DRAFT_MAX_CHARS = 200

    /**
     * 组装反馈草稿：文件路径 + selector + 元素片段，供 AI 直接定位源码修改。
     * 「问题:」行留空由用户补充。
     */
    fun compose(relativePath: String, element: InspectedElement): String {
        val selector = ElementSelectorBuilder.build(element)
        val outerHtml = element.outerHtml.take(OUTER_HTML_DRAFT_MAX_CHARS).trimEnd()
        val outerSuffix = if (element.outerHtml.length > OUTER_HTML_DRAFT_MAX_CHARS) "…" else ""
        return buildString {
            append("请修改工作区文件 ").append(relativePath).append(" 中这个元素：\n")
            append("- 选择器: ").append(selector).append('\n')
            if (outerHtml.isNotEmpty()) {
                append("- 元素: ").append(outerHtml).append(outerSuffix).append('\n')
            }
            if (element.textPreview.isNotEmpty()) {
                append("- 可见文本: ").append(element.textPreview).append('\n')
            }
            append("- 问题: ")
        }.trimEnd()
    }

    /**
     * 草稿回填输入框：保留用户已输入内容（避免覆盖打断中的输入），空则直接回填。
     */
    fun mergeDraft(currentInput: String, draft: String): String {
        if (draft.isBlank()) return currentInput
        if (currentInput.isBlank()) return draft
        return currentInput.trimEnd() + "\n" + draft
    }
}