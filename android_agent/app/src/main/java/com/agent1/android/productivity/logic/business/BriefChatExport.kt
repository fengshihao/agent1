package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.model.AgentMessage

/** 供粘贴到 Cursor / 云端 Agent 的简略会话文本（不含完整工具 JSON 与诊断日志）。 */
object BriefChatExport {

    fun format(messages: List<AgentMessage>, sessionTitle: String): String {
        if (messages.isEmpty()) {
            return "（空会话）"
        }
        return buildString {
            val title = sessionTitle.trim()
            if (title.isNotEmpty()) {
                appendLine("# $title")
                appendLine()
            }
            for (msg in messages) {
                when (msg.role) {
                    AgentMessage.ROLE_USER -> appendUser(msg)
                    AgentMessage.ROLE_ASSISTANT -> appendAssistant(msg)
                    AgentMessage.ROLE_TOOL_RESULT -> appendTool(msg)
                    else -> Unit
                }
            }
        }.trimEnd()
    }

    private fun StringBuilder.appendUser(msg: AgentMessage) {
        appendLine("**用户**")
        appendLine(msg.content.trim())
        appendLine()
    }

    private fun StringBuilder.appendAssistant(msg: AgentMessage) {
        val text = msg.content.trim()
        if (text.isEmpty()) return
        appendLine("**助手**")
        appendLine(text)
        appendLine()
    }

    private fun StringBuilder.appendTool(msg: AgentMessage) {
        val summary = ChatTranscriptFormatting.formatToolResult(msg.content, null).summary
        if (msg.isError) {
            appendLine("**工具异常** $summary")
            appendLine()
            return
        }
        // 正常工具结果在 UI 已展示摘要，简略导出只保留一行以免刷屏
        if (summary.length <= 120) {
            appendLine("_[工具]_ $summary")
            appendLine()
        }
    }
}
