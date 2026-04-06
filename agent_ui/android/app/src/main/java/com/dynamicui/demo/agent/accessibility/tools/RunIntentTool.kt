package com.dynamicui.demo.agent.accessibility.tools

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.ToolExecutionResult
import com.agent1.javaagent.tool.ToolUpdateListener
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class RunIntentTool(
    private val context: Context
) : AgentTool {
    override fun name(): String = "run_intent"

    override fun description(): String = "通过 Intent 执行跨 App 跳转或系统能力入口。"

    override fun parametersSchema(): JsonNode {
        val schema = MAPPER.createObjectNode()
        schema.put("type", "object")
        val p = schema.putObject("properties")
        p.putObject("action").put("type", "string").put("description", "Intent action，如 android.intent.action.VIEW。")
        p.putObject("data_uri").put("type", "string").put("description", "可选 data Uri。")
        p.putObject("package_name").put("type", "string").put("description", "可选目标包名。")
        p.putObject("component").put("type", "string").put("description", "可选组件名，格式 package/class。")
        p.putObject("flags").put("type", "integer").put("description", "额外 flags，默认 NEW_TASK。")
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
        return try {
            val action = parameters.path("action").asText(Intent.ACTION_VIEW)
            val intent = Intent(action)
            val dataUri = parameters.path("data_uri").asText("").trim()
            if (dataUri.isNotEmpty()) intent.data = Uri.parse(dataUri)
            val packageName = parameters.path("package_name").asText("").trim()
            if (packageName.isNotEmpty()) intent.setPackage(packageName)
            val component = parameters.path("component").asText("").trim()
            if (component.contains("/")) {
                val parts = component.split("/", limit = 2)
                intent.component = ComponentName(parts[0], parts[1])
            }
            val flags = parameters.path("flags").asInt(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(flags or Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolver = context.packageManager.resolveActivity(intent, 0)
            if (resolver == null) {
                return ToolExecutionResult.text("""{"ok":false,"reason":"未找到可处理该 Intent 的应用"}""")
            }
            context.startActivity(intent)
            val out = MAPPER.createObjectNode()
            out.put("ok", true)
            out.put("reason", "Intent 已发送")
            out.put("resolvedPackage", resolver.activityInfo?.packageName ?: "")
            ToolExecutionResult.text(MAPPER.writeValueAsString(out))
        } catch (e: Exception) {
            ToolExecutionResult.text("""{"ok":false,"reason":"${escape(e.message ?: "执行失败")}"}""")
        }
    }

    private fun escape(text: String): String {
        return text.replace("\"", "'")
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
