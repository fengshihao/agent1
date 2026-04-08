package com.dynamicui.demo.pet.logic.data.accessibility.core

object PageContextPromptBuilder {
    fun build(snapshot: PageSnapshot?): String {
        if (snapshot == null) {
            return "当前未检测到可访问页面。若问题和当前页面有关，请先让用户打开并保持目标页面在前台。"
        }
        val title = snapshot.titleCandidates.firstOrNull().orEmpty()
        val texts = snapshot.textBlocks.take(18).joinToString("\n- ", prefix = "- ")
        val actions = snapshot.interactiveElements
            .take(16)
            .mapIndexed { i, it ->
                val label = it.text.ifBlank { it.contentDescription }.ifBlank { it.resourceId }
                "${i + 1}. ${label.ifBlank { "(无标签)" }} [${it.className}]"
            }
            .joinToString("\n")
        return buildString {
            appendLine("当前手机页面环境（来自无障碍快照）:")
            appendLine("- app: ${snapshot.packageName.ifBlank { "(未知)" }}")
            appendLine("- pageClass: ${snapshot.className.ifBlank { "(未知)" }}")
            appendLine("- title: ${title.ifBlank { "(无明显标题)" }}")
            appendLine("- scrollable: ${if (snapshot.scrollableHints.isNotEmpty()) "yes" else "no"}")
            appendLine("- texts:")
            appendLine(if (texts.isBlank()) "- (无可见文本)" else texts)
            appendLine("- interactiveElements:")
            appendLine(if (actions.isBlank()) "- (无可交互元素)" else actions)
            appendLine()
            appendLine("要求: 页面相关问题先读取页面信息再回答；执行点击/滚动前先确认元素存在；高风险操作先征求用户确认。")
        }.trim()
    }
}
