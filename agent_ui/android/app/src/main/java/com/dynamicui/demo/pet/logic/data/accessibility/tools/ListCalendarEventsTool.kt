package com.dynamicui.demo.pet.logic.data.accessibility.tools

import android.content.Context
import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.ToolExecutionResult
import com.agent1.javaagent.tool.ToolUpdateListener
import com.dynamicui.demo.pet.logic.data.service.CalendarAccess
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class ListCalendarEventsTool(
    private val context: Context
) : AgentTool {
    override fun name(): String = "list_calendar_events"

    override fun description(): String =
        "读取系统日历库中日程；按事件开始时间落在 [start_ms, end_ms] 区间内筛选（需 READ_CALENDAR）。"

    override fun parametersSchema(): JsonNode {
        val schema = MAPPER.createObjectNode()
        schema.put("type", "object")
        val p = schema.putObject("properties")
        p.putObject("start_ms").put("type", "integer").put("description", "区间开始时间（毫秒时间戳）。")
        p.putObject("end_ms").put("type", "integer").put("description", "区间结束时间（毫秒时间戳）。")
        p.putObject("limit").put("type", "integer").put("description", "最多返回条数，默认 50，最大 200。")
        schema.putArray("required").add("start_ms").add("end_ms")
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
        val startMs = parameters.path("start_ms").asLong(0L)
        val endMs = parameters.path("end_ms").asLong(0L)
        val limit = parameters.path("limit").asInt(50).coerceIn(1, 200)
        val result = CalendarAccess.listEvents(context, startMs, endMs, limit)
        return result.fold(
            onSuccess = { rows ->
                val out = MAPPER.createObjectNode()
                out.put("ok", true)
                out.put("count", rows.size)
                val arr = MAPPER.createArrayNode()
                for (r in rows) {
                    val o = MAPPER.createObjectNode()
                    o.put("event_id", r.id)
                    o.put("title", r.title)
                    o.put("dt_start", r.dtStart)
                    o.put("dt_end", r.dtEnd)
                    o.put("all_day", r.allDay)
                    o.put("calendar_id", r.calendarId)
                    o.put("location", r.location)
                    o.put("description", r.description)
                    arr.add(o)
                }
                out.putArray("items").addAll(arr)
                ToolExecutionResult.text(MAPPER.writeValueAsString(out))
            },
            onFailure = { e ->
                ToolExecutionResult.text(
                    """{"ok":false,"reason":"${e.message?.replace("\"", "'") ?: "查询失败"}"}"""
                )
            }
        )
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
