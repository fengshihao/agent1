package com.dynamicui.demo.agent.accessibility.tools

import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.ToolExecutionResult
import com.agent1.javaagent.tool.ToolUpdateListener
import com.dynamicui.demo.agent.accessibility.core.PageSnapshotStore
import com.dynamicui.demo.agent.accessibility.service.PetAccessibilityService
import com.dynamicui.demo.agent.accessibility.service.UiActionRequest
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class ActOnUiTool : AgentTool {
    override fun name(): String = "act_on_ui"

    override fun description(): String = "统一执行 UI 动作：tap/scroll/input/back/home。"

    override fun parametersSchema(): JsonNode {
        val schema = MAPPER.createObjectNode()
        schema.put("type", "object")
        val p = schema.putObject("properties")
        p.putObject("action").put("type", "string").put("description", "支持 tap/scroll/input/back/home。")
        p.putObject("text").put("type", "string").put("description", "匹配文本。")
        p.putObject("resource_id").put("type", "string").put("description", "匹配 resourceId。")
        p.putObject("class_name").put("type", "string").put("description", "匹配 className。")
        p.putObject("index").put("type", "integer").put("description", "命中多个元素时索引。")
        p.putObject("input_text").put("type", "string").put("description", "input 动作写入文本。")
        p.putObject("direction").put("type", "string").put("description", "scroll 方向 forward/backward/up/down。")
        p.putObject("auto_submit").put("type", "boolean").put("description", "input 后自动发送。")
        p.putObject("submit_texts").put("type", "array").put("description", "自动发送优先按钮文案列表。")
            .putObject("items").put("type", "string")
        p.putObject("fallback_x").put("type", "number").put("description", "tap 兜底坐标 x。")
        p.putObject("fallback_y").put("type", "number").put("description", "tap 兜底坐标 y。")
        schema.putArray("required").add("action")
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
        val submitTexts = mutableListOf<String>()
        val node = parameters.path("submit_texts")
        if (node.isArray) {
            node.forEach {
                val v = it.asText("").trim()
                if (v.isNotEmpty()) submitTexts += v
            }
        }
        val req = UiActionRequest(
            action = parameters.path("action").asText(""),
            text = parameters.path("text").asText(""),
            resourceId = parameters.path("resource_id").asText(""),
            className = parameters.path("class_name").asText(""),
            index = parameters.path("index").asInt(0),
            inputText = parameters.path("input_text").asText(""),
            direction = parameters.path("direction").asText("forward"),
            autoSubmit = parameters.path("auto_submit").asBoolean(false),
            submitTexts = submitTexts,
            fallbackX = parameters.path("fallback_x").takeIf { !it.isMissingNode && !it.isNull }?.asDouble()?.toFloat(),
            fallbackY = parameters.path("fallback_y").takeIf { !it.isMissingNode && !it.isNull }?.asDouble()?.toFloat()
        )
        val result = svc.actOnUi(req)
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
