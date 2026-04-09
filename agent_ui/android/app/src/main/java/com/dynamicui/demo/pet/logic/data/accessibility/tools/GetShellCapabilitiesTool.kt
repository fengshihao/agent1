package com.dynamicui.demo.pet.logic.data.accessibility.tools

import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.ToolExecutionResult
import com.agent1.javaagent.tool.ToolUpdateListener
import com.dynamicui.demo.pet.logic.data.service.ShellCapabilitiesProvider
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class GetShellCapabilitiesTool : AgentTool {
    override fun name(): String = "get_shell_capabilities"

    override fun description(): String = "返回当前设备 shell 可用命令能力表。"

    override fun parametersSchema(): JsonNode {
        val schema = MAPPER.createObjectNode()
        schema.put("type", "object")
        val properties = schema.putObject("properties")
        properties.putObject("force_refresh")
            .put("type", "boolean")
            .put("description", "是否强制重新探测命令能力，默认 false。")
        schema.putArray("required")
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
        val force = parameters.path("force_refresh").asBoolean(false)
        val cap = ShellCapabilitiesProvider.probeIfNeeded(force)
        val out = MAPPER.createObjectNode()
        out.put("ok", true)
        out.put("shellPath", cap.shellPath)
        out.putPOJO("availableCommands", cap.availableCommands)
        out.putPOJO("missingCommands", cap.missingCommands)
        out.put("detectedAtMs", cap.detectedAtMs)
        return ToolExecutionResult.text(MAPPER.writeValueAsString(out))
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
