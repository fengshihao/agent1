package com.dynamicui.demo.agent.accessibility.tools

import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.ToolExecutionResult
import com.agent1.javaagent.tool.ToolUpdateListener
import com.dynamicui.demo.agent.accessibility.core.PageSnapshotStore
import com.dynamicui.demo.agent.accessibility.service.PetAccessibilityService
import com.dynamicui.demo.agent.accessibility.service.TapSelector
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class TapElementTool : AgentTool {
    override fun name(): String = "tap_element"

    override fun description(): String = "按文本、resourceId或类名定位当前页面元素并点击。"

    override fun parametersSchema(): JsonNode {
        val schema = MAPPER.createObjectNode()
        schema.put("type", "object")
        val p = schema.putObject("properties")
        p.putObject("text").put("type", "string").put("description", "目标文本，支持模糊匹配。")
        p.putObject("resource_id").put("type", "string").put("description", "目标控件resourceId，支持包含匹配。")
        p.putObject("class_name").put("type", "string").put("description", "控件类名，如 Button。")
        p.putObject("index").put("type", "integer").put("description", "命中多个元素时选择索引，默认0。")
        p.putObject("fallback_x").put("type", "number").put("description", "找不到节点时可选的兜底点击X坐标。")
        p.putObject("fallback_y").put("type", "number").put("description", "找不到节点时可选的兜底点击Y坐标。")
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
        val selector = TapSelector(
            text = parameters.path("text").asText(""),
            resourceId = parameters.path("resource_id").asText(""),
            className = parameters.path("class_name").asText(""),
            index = parameters.path("index").asInt(0),
            fallbackX = parameters.path("fallback_x").takeIf { !it.isMissingNode && !it.isNull }?.asDouble()?.toFloat(),
            fallbackY = parameters.path("fallback_y").takeIf { !it.isMissingNode && !it.isNull }?.asDouble()?.toFloat()
        )
        if (selector.text.isBlank() && selector.resourceId.isBlank() && selector.className.isBlank() &&
            (selector.fallbackX == null || selector.fallbackY == null)
        ) {
            return ToolExecutionResult.text("""{"ok":false,"reason":"至少提供 text/resource_id/class_name 或 fallback 坐标"}""")
        }
        val result = svc.tapBySelector(selector)
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
