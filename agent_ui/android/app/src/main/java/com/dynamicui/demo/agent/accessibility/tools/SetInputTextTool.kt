package com.dynamicui.demo.agent.accessibility.tools

import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.ToolExecutionResult
import com.agent1.javaagent.tool.ToolUpdateListener
import com.dynamicui.demo.agent.accessibility.core.PageSnapshotStore
import com.dynamicui.demo.agent.accessibility.service.InputSelector
import com.dynamicui.demo.agent.accessibility.service.PetAccessibilityService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class SetInputTextTool : AgentTool {
    override fun name(): String = "set_input_text"

    override fun description(): String = "定位输入框并填入文本，优先 ACTION_SET_TEXT，失败时尝试粘贴兜底。"

    override fun parametersSchema(): JsonNode {
        val schema = MAPPER.createObjectNode()
        schema.put("type", "object")
        val p = schema.putObject("properties")
        p.putObject("input_text").put("type", "string").put("description", "要写入输入框的文本。")
        p.putObject("text").put("type", "string").put("description", "输入框文本/描述/提示词匹配。")
        p.putObject("resource_id").put("type", "string").put("description", "输入框resourceId匹配。")
        p.putObject("class_name").put("type", "string").put("description", "输入框类名匹配，如 EditText。")
        p.putObject("index").put("type", "integer").put("description", "多个命中时选择索引，默认0。")
        p.putObject("auto_submit").put("type", "boolean").put("description", "输入后是否自动点击发送/提交按钮。")
        val submitTexts = p.putObject("submit_texts")
        submitTexts.put("type", "array")
        submitTexts.put("description", "自动发送时优先匹配的按钮文本列表，如 [\"发送\",\"搜索\"]。")
        submitTexts.putObject("items").put("type", "string")
        schema.putArray("required").add("input_text")
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
        val inputText = parameters.path("input_text").asText("").trim()
        val selector = InputSelector(
            text = parameters.path("text").asText(""),
            resourceId = parameters.path("resource_id").asText(""),
            className = parameters.path("class_name").asText(""),
            index = parameters.path("index").asInt(0)
        )
        val autoSubmit = parameters.path("auto_submit").asBoolean(false)
        val submitTexts = mutableListOf<String>()
        val submitTextsNode = parameters.path("submit_texts")
        if (submitTextsNode.isArray) {
            submitTextsNode.forEach { n ->
                val label = n.asText("").trim()
                if (label.isNotEmpty()) submitTexts.add(label)
            }
        }
        val result = svc.setInputText(selector, inputText, autoSubmit, submitTexts)
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
