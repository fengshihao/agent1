package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.model.AgentMessage
import com.agent1.javaagent.model.ToolCall

/** 供粘贴到 Cursor / 云端 Agent 的简略会话文本（不含完整工具 JSON 与诊断日志）。 */
object BriefChatExport {

    /** 单条助手回复压缩后上限（字符）；用户消息保持全文便于还原意图。 */
    private const val ASSISTANT_MAX_CHARS = 1_600
    private const val ASSISTANT_HEAD_CHARS = 900
    private const val ASSISTANT_TAIL_CHARS = 450

    private val codeFence = Regex("```[\\s\\S]*?```")
    private val markdownTable = Regex("(?m)^\\|.+\\|\\s*$")

    data class Context(
        val sessionId: String = "",
        val modelLabel: String = "",
        val appVersion: String = "",
    )

    fun format(
        messages: List<AgentMessage>,
        sessionTitle: String,
        context: Context = Context(),
    ): String {
        if (messages.isEmpty()) {
            return "（空会话）"
        }
        val toolCallsById = indexToolCalls(messages)
        return buildString {
            appendContextHeader(context)
            val title = sessionTitle.trim()
            if (title.isNotEmpty()) {
                appendLine("# $title")
                appendLine()
            }
            for (msg in messages) {
                when (msg.role) {
                    AgentMessage.ROLE_USER -> appendUser(msg)
                    AgentMessage.ROLE_ASSISTANT -> appendAssistant(msg, toolCallsById)
                    AgentMessage.ROLE_TOOL_RESULT -> appendTool(msg, toolCallsById)
                    else -> Unit
                }
            }
        }.trimEnd()
    }

    private fun indexToolCalls(messages: List<AgentMessage>): Map<String, ToolCall> {
        val map = linkedMapOf<String, ToolCall>()
        for (msg in messages) {
            if (msg.role != AgentMessage.ROLE_ASSISTANT) continue
            for (call in msg.toolCalls) {
                map[call.id] = call
            }
        }
        return map
    }

    private fun StringBuilder.appendContextHeader(context: Context) {
        val parts = buildList {
            if (context.sessionId.isNotBlank()) add("session=${context.sessionId}")
            if (context.modelLabel.isNotBlank()) add("model=${context.modelLabel}")
            if (context.appVersion.isNotBlank()) add("app=${context.appVersion}")
        }
        if (parts.isEmpty()) return
        appendLine("<!-- agent1 brief · ${parts.joinToString(" · ")} -->")
        appendLine()
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

    private fun StringBuilder.appendAssistant(
        msg: AgentMessage,
        toolCallsById: Map<String, ToolCall>,
    ) {
        val text = compressAssistantContent(msg.content)
        val toolDigests = msg.toolCalls.mapNotNull { call ->
            if (call.name == AskUserFormatting.TOOL_NAME) return@mapNotNull null
            AskUserFormatting.briefToolCallLine(call)
        }
        if (text.isEmpty() && toolDigests.isEmpty()) return
        appendLine("**助手**")
        if (text.isNotEmpty()) {
            appendLine(text)
        }
        for (digest in toolDigests) {
            appendLine()
            appendLine(digest)
        }
        appendLine()
    }

    private fun StringBuilder.appendTool(
        msg: AgentMessage,
        toolCallsById: Map<String, ToolCall>,
    ) {
        val call = msg.toolCallId?.let { toolCallsById[it] }
        val toolName = call?.name ?: "tool"
        if (call?.name == AskUserFormatting.TOOL_NAME) {
            val req = call?.let { AskUserFormatting.parseRequestFromToolCall(it) }
            if (req != null) {
                appendLine(AskUserFormatting.formatForBrief(req, msg.content.trim()))
                appendLine()
                return
            }
        }
        val summary = ChatTranscriptFormatting.formatToolResult(msg.content, null).summary
        if (msg.isError) {
            appendLine("**工具异常 · $toolName** $summary")
            appendLine()
            return
        }
        if (summary.isBlank()) return
        appendLine("_[工具 · $toolName]_ $summary")
        appendLine()
    }
}
