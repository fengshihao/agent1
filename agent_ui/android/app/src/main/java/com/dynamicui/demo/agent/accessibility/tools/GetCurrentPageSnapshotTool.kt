package com.dynamicui.demo.pet.logic.data.accessibility.tools

import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.ToolExecutionResult
import com.agent1.javaagent.tool.ToolUpdateListener
import com.dynamicui.demo.pet.logic.data.accessibility.core.PageSnapshotStore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

class GetCurrentPageSnapshotTool : AgentTool {
    override fun name(): String = "get_current_page_snapshot"

    override fun description(): String = "获取当前前台页面的App、标题、文本块和可点击元素摘要。"

    override fun parametersSchema(): JsonNode {
        val schema = MAPPER.createObjectNode()
        schema.put("type", "object")
        schema.putObject("properties")
        schema.putArray("required")
        return schema
    }

    override fun execute(
        toolCallId: String,
        parameters: JsonNode,
        cancellationToken: CancellationToken,
        onUpdate: ToolUpdateListener
    ): ToolExecutionResult {
        val snapshot = PageSnapshotStore.latest()
            ?: return ToolExecutionResult.text(jsonStatus(false, "无可用页面快照"))
        val out = MAPPER.createObjectNode()
        out.put("ok", true)
        out.put("packageName", snapshot.packageName)
        out.put("className", snapshot.className)
        out.put("capturedAtMs", snapshot.capturedAtMs)
        out.putPOJO("titleCandidates", snapshot.titleCandidates)
        out.putPOJO("textBlocks", snapshot.textBlocks.take(120))
        out.putPOJO("interactiveElements", snapshot.interactiveElements.take(64))
        out.putPOJO("scrollableHints", snapshot.scrollableHints)
        out.put("digest", snapshot.digest())
        return ToolExecutionResult.text(MAPPER.writeValueAsString(out))
    }

    private fun jsonStatus(ok: Boolean, reason: String): String {
        val node: ObjectNode = MAPPER.createObjectNode()
        node.put("ok", ok)
        node.put("reason", reason)
        return MAPPER.writeValueAsString(node)
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
