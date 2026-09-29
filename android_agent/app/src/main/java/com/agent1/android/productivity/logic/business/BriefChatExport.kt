package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.model.AgentMessage

/** 供粘贴到 Cursor / 云端 Agent 的简略会话文本（不含完整工具 JSON 与诊断日志）。 */
object BriefChatExport {

    /** 单条助手回复压缩后上限（字符）；用户消息保持全文便于还原意图。 */
    private const val ASSISTANT_MAX_CHARS = 1_600
    private const val ASSISTANT_HEAD_CHARS = 900
    private const val ASSISTANT_TAIL_CHARS = 450

    private val codeFence = Regex("```[\\s\\S]*?```")
    private val markdownTable = Regex("(?m)^\\|.+\\|\\s*$")

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

    /** 压缩助手长文：去掉大块代码/表格细节，必要时保留首尾摘要。 */
    internal fun compressAssistantContent(raw: String): String {
        var text = raw.trim()
        if (text.isEmpty()) return text

        text = codeFence.replace(text) { match ->
            val lines = match.value.count { it == '\n' } + 1
            "[省略代码块 · ${lines}行]"
        }

        val tableLines = markdownTable.findAll(text).count()
        if (tableLines >= 4) {
            text = markdownTable.replace(text, "")
            text = text.replace(Regex("\n{3,}"), "\n\n").trim()
            text += "\n[表格 ${tableLines} 行已省略，保留上文说明]"
        }

        if (text.length <= ASSISTANT_MAX_CHARS) {
            return text.trim()
        }

        val head = text.take(ASSISTANT_HEAD_CHARS).trimEnd()
        val tail = text.takeLast(ASSISTANT_TAIL_CHARS).trimStart()
        val omitted = text.length - ASSISTANT_HEAD_CHARS - ASSISTANT_TAIL_CHARS
        return buildString {
            append(head)
            append("\n\n…（助手回复略去约 ")
            append(omitted.coerceAtLeast(0))
            append(" 字）\n\n")
            append(tail)
        }.trim()
    }

    private fun StringBuilder.appendUser(msg: AgentMessage) {
        appendLine("**用户**")
        appendLine(msg.content.trim())
        appendLine()
    }

    private fun StringBuilder.appendAssistant(msg: AgentMessage) {
        val text = compressAssistantContent(msg.content)
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
        if (summary.length <= 120) {
            appendLine("_[工具]_ $summary")
            appendLine()
        }
    }
}
