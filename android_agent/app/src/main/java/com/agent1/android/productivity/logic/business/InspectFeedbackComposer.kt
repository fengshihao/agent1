package com.agent1.android.productivity.logic.business

/**
 * 审查模式反馈消息组装（18 号规划 Phase B / REQ-122）。
 * 预览页内直接发送给 AI（不回填输入框、不自动跳转），
 * 用户意见在预览面板输入，随结构化元素信息一并发出。
 */
object InspectFeedbackComposer {

    /** 发送消息里 outerHtml 的截断上限。 */
    private const val OUTER_HTML_DRAFT_MAX_CHARS = 200

    /**
     * 组装发送给 AI 的消息：文件路径 + selector + 元素片段 + 用户意见，
     * 供 AI 直接定位源码修改。
     */
    fun compose(relativePath: String, element: InspectedElement, problem: String): String {
        val selector = ElementSelectorBuilder.build(element)
        val outerHtml = element.outerHtml.take(OUTER_HTML_DRAFT_MAX_CHARS).trimEnd()
        val outerSuffix = if (element.outerHtml.length > OUTER_HTML_DRAFT_MAX_CHARS) "…" else ""
        val opinion = problem.trim()
        return buildString {
            append("请修改工作区文件 ").append(relativePath).append(" 中这个元素：\n")
            append("- 选择器: ").append(selector).append('\n')
            if (outerHtml.isNotEmpty()) {
                append("- 元素: ").append(outerHtml).append(outerSuffix).append('\n')
            }
            if (element.textPreview.isNotEmpty()) {
                append("- 可见文本: ").append(element.textPreview).append('\n')
            }
            append("- 问题: ").append(opinion)
        }.trimEnd()
    }
}