package com.dynamicui.demo.pet.logic.data.accessibility.tools

import android.content.Context
import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.ToolExecutionResult
import com.agent1.javaagent.tool.ToolUpdateListener
import com.dynamicui.demo.pet.logic.data.service.CalendarAccess
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class DeleteCalendarEventTool(
    private val context: Context
) : AgentTool {
    override fun name(): String = "delete_calendar_event"

    override fun description(): String =
        "按 event_id 删除系统日历中的日程（需 WRITE_CALENDAR）；删除前应先 list_calendar_events 确认。"

    override fun parametersSchema(): JsonNode {
        val schema = MAPPER.createObjectNode()
        schema.put("type", "object")
        val p = schema.putObject("properties")
        p.putObject("event_id").put("type", "integer").put("description", "要删除的事件 ID。")
        schema.putArray("required").add("event_id")
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
        val eventId = parameters.path("event_id").asLong(0L)
        val result = CalendarAccess.deleteEvent(context, eventId)
        return result.fold(
            onSuccess = { deleted ->
                val out = MAPPER.createObjectNode()
                out.put("ok", deleted)
                out.put("reason", if (deleted) "已删除" else "未找到或未删除任何行")
                ToolExecutionResult.text(MAPPER.writeValueAsString(out))
            },
            onFailure = { e ->
                ToolExecutionResult.text(
                    """{"ok":false,"reason":"${e.message?.replace("\"", "'") ?: "删除失败"}"}"""
                )
            }
        )
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
