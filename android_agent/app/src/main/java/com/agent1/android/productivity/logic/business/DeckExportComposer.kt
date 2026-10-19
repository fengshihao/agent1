package com.agent1.android.productivity.logic.business

/**
 * deck 导出指令组装（21 号规划 T3：导出决策从 AI 端挪到用户端）。
 *
 * 用户在预览页点「导出」后，以**用户消息**回填对话，AI 收到明确指令才调
 * `renderHtmlDeck`——AI 不再在用户未确认时擅自导出（配合 SKILL.md 工作流）。
 * 纯函数便于 JVM 单测与文案统一。
 */
object DeckExportComposer {

    /**
     * @param relativePath deck.html 的 workspace 相对路径（同会话消息里的引用路径）
     * @param editable true=可编辑版（mode:"native"，接收方要在 PowerPoint/WPS 改字）；
     *   false=原样版（整页截图，观感 100%）
     */
    fun compose(relativePath: String, editable: Boolean): String {
        val mode = if (editable) {
            "可编辑版（mode:\"native\"，文字在 PowerPoint/WPS 里可改）"
        } else {
            "原样版（默认整页截图，观感 100%）"
        }
        val output = if (editable) "deck-native.pptx" else "deck.pptx"
        return "请把 $relativePath 导出为 PPTX $mode：用 weizhi:html2pptx 的 renderHtmlDeck，" +
            "输出 $output，完成后告诉我产物路径。"
    }
}
