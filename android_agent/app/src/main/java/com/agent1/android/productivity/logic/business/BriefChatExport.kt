package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.model.AgentMessage
import com.agent1.javaagent.model.ToolCall

/** 供粘贴到 Cursor / 云端 Agent 的简略会话文本（不含完整工具 JSON 与诊断日志）。 */
object BriefChatExport {

    /** 单条助手回复压缩后上限（字符）；用户消息保持全文便于还原意图。 */
    private const val ASSISTANT_MAX_CHARS = 1_600
    private const val ASSISTANT_HEAD_CHARS = 900
    private const val ASSISTANT_TAIL_CHARS = 450
    private const val REASONING_MAX_CHARS = 400
    private const val TOOL_ARG_CHARS = 360
    private const val TOOL_ERROR_RAW_CHARS = 500

    private val codeFence = Regex("```[\\s\\S]*?```")
    private val markdownTable = Regex("(?m)^\\|.+\\|\\s*$")

    data class Context(
        val sessionId: String = "",
        val modelLabel: String = "",
        val appVersion: String = "",
        val deviceLabel: String = "",
        val limits: String = "",
        val configError: String = "",
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
            appendLine("以下是 agent1 手机端会话简报，按时间顺序排列，供调试 AI 直接阅读。")
            appendLine("工具失败、参数和结果摘要都在正文里；大段代码和表格已压缩。请根据异常摘要和对应工具 id 定位问题。")
            appendLine()
            appendContextHeader(context)
            appendFailureSummary(messages, toolCallsById)
            val title = sessionTitle.trim()
            if (title.isNotEmpty()) {
                appendLine("# $title")
                appendLine()
            }
            for (msg in messages) {
                when (msg.role) {
                    AgentMessage.ROLE_USER -> appendUser(msg)
                    AgentMessage.ROLE_ASSISTANT -> appendAssistant(msg)
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
        val lines = buildList {
            if (context.sessionId.isNotBlank()) add("session: ${context.sessionId}")
            if (context.modelLabel.isNotBlank()) add("model: ${context.modelLabel}")
            if (context.appVersion.isNotBlank()) add("app: ${context.appVersion}")
            if (context.deviceLabel.isNotBlank()) add("device: ${context.deviceLabel}")
            if (context.limits.isNotBlank()) add("limits: ${context.limits}")
            if (context.configError.isNotBlank()) add("configError: ${context.configError}")
        }
        if (lines.isEmpty()) return
        appendLine("## 环境")
        lines.forEach { appendLine(it) }
        appendLine()
    }

    private fun StringBuilder.appendFailureSummary(
        messages: List<AgentMessage>,
        toolCallsById: Map<String, ToolCall>,
    ) {
        val errors = messages.filter { it.role == AgentMessage.ROLE_TOOL_RESULT && it.isError }
        if (errors.isEmpty()) return
        appendLine("## 异常摘要")
        appendLine("工具失败 ${errors.size} 次。")
        val last = errors.last()
        val name = last.toolCallId?.let { toolCallsById[it]?.name } ?: "tool"
        val summary = ChatTranscriptFormatting.formatToolResult(last.content, null).summary
        appendLine("最近一次：$name id=${last.toolCallId ?: "-"} — $summary")
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

    private fun StringBuilder.appendAssistant(msg: AgentMessage) {
        val text = compressAssistantContent(msg.content)
        val reasoning = compressAssistantContent(msg.reasoningContent).take(REASONING_MAX_CHARS).trim()
        val toolDigests = msg.toolCalls.map { debugToolCallLine(it) }
        if (text.isEmpty() && reasoning.isEmpty() && toolDigests.isEmpty()) return
        appendLine("**助手**")
        if (text.isNotEmpty()) {
            appendLine(text)
        }
        if (reasoning.isNotEmpty()) {
            if (text.isNotEmpty()) appendLine()
            appendLine("思考：$reasoning")
        }
        for (digest in toolDigests) {
            appendLine()
            appendLine(digest)
        }
        appendLine()
    }

    private fun debugToolCallLine(call: ToolCall): String {
        if (call.name == AskUserFormatting.TOOL_NAME) {
            val count = AskUserFormatting.parseRequestFromToolCall(call)?.questions?.size ?: 0
            return "工具调用 ask_user id=${call.id} 问题数=$count"
        }
        val args = call.argumentsJson.trim().replace('\n', ' ')
        val preview = if (args.length <= TOOL_ARG_CHARS) args else args.take(TOOL_ARG_CHARS) + "…"
        return "工具调用 ${call.name} id=${call.id} args=$preview"
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
            appendLine("**工具异常 · $toolName** id=${msg.toolCallId ?: "-"} $summary")
            val raw = msg.content.trim().replace('\n', ' ')
            val clipped = if (raw.length <= TOOL_ERROR_RAW_CHARS) raw else raw.take(TOOL_ERROR_RAW_CHARS) + "…"
            if (clipped.isNotEmpty() && summary != clipped && !summary.contains(clipped.take(80))) {
                appendLine("原始：$clipped")
            }
            appendLine()
            return
        }
        if (summary.isBlank()) return
        appendLine("_[工具 · $toolName]_ $summary")
        appendLine()
    }
}
