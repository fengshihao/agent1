package com.dynamicui.demo.agent.accessibility.tools

import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.ToolExecutionResult
import com.agent1.javaagent.tool.ToolUpdateListener
import com.dynamicui.demo.agent.accessibility.core.PageSnapshotStore
import com.dynamicui.demo.agent.accessibility.service.PetAccessibilityService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class ScrollPageTool : AgentTool {
    override fun name(): String = "scroll_page"

    override fun description(): String = "滚动当前页面，direction 支持 forward/backward/up/down。"

    override fun parametersSchema(): JsonNode {
        val schema = MAPPER.createObjectNode()
        schema.put("type", "object")
        val props = schema.putObject("properties")
        props.putObject("direction")
            .put("type", "string")
            .put("description", "滚动方向：forward/backward/up/down，默认forward。")
        schema.putArray("required")
        return schema
    }

    override fun execute(
        toolCallId: String,
        parameters: JsonNode,
        cancellationToken: CancellationToken,
        onUpdate: ToolUpdateListener
    ): ToolExecutionResult {
        val svc = PetAccessibilityService.current()
            ?: return ToolExecutionResult.text("""{"ok":false,"reason":"无障碍服务未连接"}""")
        val direction = parameters.path("direction").asText("forward")
        val result = svc.scrollPage(direction)
        val out = MAPPER.createObjectNode()
        out.put("ok", result.ok)
        out.put("reason", result.reason)
        out.put("matchedElement", result.matchedElement)
        out.put("postActionSnapshotDigest", PageSnapshotStore.latestDigest())
        return ToolExecutionResult.text(MAPPER.writeValueAsString(out))
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
