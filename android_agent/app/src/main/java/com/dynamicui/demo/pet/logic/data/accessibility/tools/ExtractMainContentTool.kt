package com.dynamicui.demo.pet.logic.data.accessibility.tools

import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.ToolExecutionResult
import com.agent1.javaagent.tool.ToolUpdateListener
import com.dynamicui.demo.pet.logic.data.accessibility.core.PageSnapshotStore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class ExtractMainContentTool : AgentTool {
    override fun name(): String = "extract_main_content"

    override fun description(): String = "提取当前页面正文候选文本，适合做总结。"

    override fun parametersSchema(): JsonNode {
        val schema = MAPPER.createObjectNode()
        schema.put("type", "object")
        val properties = schema.putObject("properties")
        properties.putObject("max_chars")
            .put("type", "integer")
            .put("description", "返回内容的最大字符数，默认2000，上限6000。")
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
            ?: return ToolExecutionResult.text("""{"ok":false,"reason":"无可用页面快照"}""")
        val maxChars = parameters.path("max_chars").asInt(2000).coerceIn(200, 6000)
        val filtered = snapshot.textBlocks
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 2 }
            .filterNot { it.startsWith("http", true) }
            .distinct()
            .toList()
        val joined = filtered.joinToString("\n")
        val content = if (joined.length > maxChars) joined.take(maxChars) else joined

        val out = MAPPER.createObjectNode()
        out.put("ok", true)
        out.put("packageName", snapshot.packageName)
        out.put("className", snapshot.className)
        out.put("content", content)
        out.put("contentLength", content.length)
        out.put("truncated", joined.length > maxChars)
        out.put("digest", snapshot.digest())
        return ToolExecutionResult.text(MAPPER.writeValueAsString(out))
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
