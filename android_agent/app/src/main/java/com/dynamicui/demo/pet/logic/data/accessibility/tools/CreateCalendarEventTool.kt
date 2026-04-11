package com.dynamicui.demo.pet.logic.data.accessibility.tools

import android.content.Context
import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.ToolExecutionResult
import com.agent1.javaagent.tool.ToolUpdateListener
import com.dynamicui.demo.pet.logic.data.service.CalendarAccess
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class CreateCalendarEventTool(
    private val context: Context
) : AgentTool {
    override fun name(): String = "create_calendar_event"

    override fun description(): String =
        "在系统日历中创建日程（需 WRITE_CALENDAR）；未指定 calendar_id 时使用首个可写日历。"

    override fun parametersSchema(): JsonNode {
        val schema = MAPPER.createObjectNode()
        schema.put("type", "object")
        val p = schema.putObject("properties")
        p.putObject("title").put("type", "string").put("description", "标题。")
        p.putObject("start_ms").put("type", "integer").put("description", "开始时间毫秒。")
        p.putObject("end_ms").put("type", "integer").put("description", "结束时间毫秒。")
        p.putObject("description_text").put("type", "string").put("description", "备注说明。")
        p.putObject("calendar_id").put("type", "integer").put("description", "可选，指定日历 ID。")
        p.putObject("all_day").put("type", "boolean").put("description", "是否全天，默认 false。")
        schema.putArray("required").add("title").add("start_ms").add("end_ms")
        return schema
    }

    override fun execute(
        toolCallId: String,
        parameters: JsonNode,
        cancellationToken: CancellationToken,
        onUpdate: ToolUpdateListener
    ): ToolExecutionResult {
        if (cancellationToken.isCancelled) {
            return ToolExecutionResult.text("""{"ok":false,"reason":"执行已取消"}""")
        }
        val title = parameters.path("title").asText("").trim()
        val startMs = parameters.path("start_ms").asLong(0L)
        val endMs = parameters.path("end_ms").asLong(0L)
        val desc = parameters.path("description_text").asText("").trim()
        val calIdNode = parameters.path("calendar_id")
        val calendarId = if (calIdNode.isMissingNode || calIdNode.isNull) null else calIdNode.asLong()
        val allDay = parameters.path("all_day").asBoolean(false)
        val result = CalendarAccess.createEvent(
            context,
            calendarId,
            title,
            startMs,
            endMs,
            desc,
            allDay
        )
        return result.fold(
            onSuccess = { id ->
                val out = MAPPER.createObjectNode()
                out.put("ok", true)
                out.put("event_id", id)
                out.put("reason", "已创建")
                ToolExecutionResult.text(MAPPER.writeValueAsString(out))
            },
            onFailure = { e ->
                ToolExecutionResult.text(
                    """{"ok":false,"reason":"${e.message?.replace("\"", "'") ?: "创建失败"}"}"""
                )
            }
        )
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
