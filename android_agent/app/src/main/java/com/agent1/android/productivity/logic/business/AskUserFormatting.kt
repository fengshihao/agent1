package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.model.ToolCall
import org.json.JSONArray
import org.json.JSONObject

/** 解析与展示 {@code ask_user} 工具调用（结构化提问）。 */
object AskUserFormatting {

    const val TOOL_NAME = "ask_user"

    data class Question(
        val id: String,
        val prompt: String,
        val type: String,
        val options: List<String>,
    )

    data class Request(
        val title: String?,
        val allowFreeformReply: Boolean,
        val questions: List<Question>,
    )

    fun isPauseSummary(text: String): Boolean {
        val t = text.trim()
        return t.contains("Run 已暂停等待回复") || t.contains("已向用户提出")
    }

    fun parseRequest(argumentsJson: String): Request? {
        return try {
            parseRequest(JSONObject(argumentsJson.ifBlank { "{}" }))
        } catch (_: Exception) {
            null
        }
    }

    fun parseRequestFromToolCall(call: ToolCall): Request? {
        if (call.name != TOOL_NAME) return null
        return parseRequest(call.argumentsJson)
    }

    fun parseRequest(root: JSONObject): Request? {
        val questionsNode = root.optJSONArray("questions") ?: return null
        if (questionsNode.length() == 0) return null
        val questions = buildList {
            for (i in 0 until questionsNode.length()) {
                val q = questionsNode.optJSONObject(i) ?: continue
                val id = q.optString("id", "").trim()
                val prompt = q.optString("prompt", "").trim()
                if (id.isEmpty() || prompt.isEmpty()) continue
                val type = q.optString("type", "text").ifBlank { "text" }
                val options = q.optJSONArray("options").toStringList()
                add(Question(id, prompt, type, options))
            }
        }
        if (questions.isEmpty()) return null
        val title = root.optString("title", "").trim().ifEmpty { null }
        val allowFreeform = root.optBoolean("allow_freeform_reply", true)
        return Request(title, allowFreeform, questions)
    }

    fun formatForChat(request: Request): String {
        return buildString {
            val heading = request.title?.ifBlank { null } ?: "需要您补充以下信息"
            appendLine("**$heading**")
            appendLine()
            request.questions.forEachIndexed { index, q ->
                appendLine("${index + 1}. ${q.prompt}")
                if (q.options.isNotEmpty()) {
                    appendLine("   选项：${q.options.joinToString(" / ")}")
                }
            }
            if (request.allowFreeformReply) {
                appendLine()
                append("请在下方输入框回复（可逐条回答，也可自由说明）。")
            }
        }.trim()
    }

    fun formatForBrief(request: Request, toolResultSummary: String?): String {
        return buildString {
            appendLine("**ask_user（等待用户回复）**")
            request.title?.let { appendLine("- 标题：$it") }
            appendLine("- 问题数：${request.questions.size}")
            request.questions.forEachIndexed { index, q ->
                append("  ${index + 1}. [${q.id}] ${q.prompt}")
                if (q.type != "text") append(" (${q.type})")
                appendLine()
                if (q.options.isNotEmpty()) {
                    appendLine("     选项：${q.options.joinToString(", ")}")
                }
            }
            toolResultSummary?.trim()?.takeIf { it.isNotEmpty() }?.let {
                appendLine("- 工具摘要：$it")
            }
        }.trim()
    }

    fun briefToolCallLine(call: ToolCall): String? {
        if (call.name == TOOL_NAME) {
            val req = parseRequestFromToolCall(call) ?: return "ask_user（参数解析失败）"
            return formatForBrief(req, null)
        }
        val args = call.argumentsJson.trim().replace('\n', ' ')
        val preview = if (args.length <= 160) args else args.take(160) + "…"
        return "${call.name}($preview)"
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val s = optString(i, "").trim()
                if (s.isNotEmpty()) add(s)
            }
        }
    }
}
