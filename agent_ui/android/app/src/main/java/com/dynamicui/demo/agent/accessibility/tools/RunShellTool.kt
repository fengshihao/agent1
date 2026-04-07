package com.dynamicui.demo.agent.accessibility.tools

import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.ToolExecutionResult
import com.agent1.javaagent.tool.ToolUpdateListener
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

class RunShellTool : AgentTool {
    override fun name(): String = "run_shell"

    override fun description(): String = "在 Android 设备上执行 shell 脚本，返回 stdout/stderr、退出码和耗时。"

    override fun parametersSchema(): JsonNode {
        val schema = MAPPER.createObjectNode()
        schema.put("type", "object")
        val p = schema.putObject("properties")
        p.putObject("script").put("type", "string").put("description", "要执行的 shell 脚本。")
        p.putObject("timeout_ms").put("type", "integer").put("description", "超时毫秒，默认10000，范围1000-30000。")
        p.putObject("allow_risky").put("type", "boolean").put("description", "是否允许高风险命令，默认 false。")
        schema.putArray("required").add("script")
        return schema
    }

    override fun execute(
        toolCallId: String,
        parameters: JsonNode,
        cancellationToken: CancellationToken,
        onUpdate: ToolUpdateListener
    ): ToolExecutionResult {
        val script = parameters.path("script").asText("").trim()
        if (script.isEmpty()) return ToolExecutionResult.text(jsonFailure(stderr = "script 不能为空"))
        if (cancellationToken.isCancelled) return ToolExecutionResult.text(jsonFailure(stderr = "执行已取消"))

        val allowRisky = parameters.path("allow_risky").asBoolean(false)
        if (!allowRisky) {
            val blocked = blockedReason(script)
            if (blocked.isNotEmpty()) {
                return ToolExecutionResult.text(
                    jsonFailure(
                        blockedReason = blocked,
                        stderr = "命中高风险策略，已阻止执行"
                    )
                )
            }
        }

        val timeoutMs = parameters.path("timeout_ms").asLong(DEFAULT_TIMEOUT_MS).coerceIn(1000L, 30000L)
        val startAt = System.currentTimeMillis()
        return try {
            val process = ProcessBuilder("/system/bin/sh", "-c", script)
                .redirectErrorStream(false)
                .start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ToolExecutionResult.text(
                    jsonFailure(
                        stderr = "执行超时(${timeoutMs}ms)",
                        elapsedMs = System.currentTimeMillis() - startAt
                    )
                )
            }
            val stdoutRaw = String(process.inputStream.readBytes(), Charsets.UTF_8)
            val stderrRaw = String(process.errorStream.readBytes(), Charsets.UTF_8)
            val (stdout, outTrunc) = truncate(stdoutRaw, OUTPUT_LIMIT)
            val (stderr, errTrunc) = truncate(stderrRaw, OUTPUT_LIMIT)
            val out = MAPPER.createObjectNode()
            out.put("ok", process.exitValue() == 0)
            out.put("exitCode", process.exitValue())
            out.put("stdout", stdout)
            out.put("stderr", stderr)
            out.put("elapsedMs", System.currentTimeMillis() - startAt)
            out.put("truncated", outTrunc || errTrunc)
            out.put("blockedReason", "")
            ToolExecutionResult.text(MAPPER.writeValueAsString(out))
        } catch (e: Exception) {
            ToolExecutionResult.text(
                jsonFailure(
                    stderr = e.message ?: "执行失败",
                    elapsedMs = System.currentTimeMillis() - startAt
                )
            )
        }
    }

    private fun blockedReason(script: String): String {
        val low = script.lowercase()
        for (pattern in DANGEROUS_PATTERNS) {
            if (low.contains(pattern)) return pattern
        }
        return ""
    }

    private fun truncate(text: String, limit: Int): Pair<String, Boolean> {
        if (text.length <= limit) return text to false
        return text.take(limit) + "\n...<truncated>" to true
    }

    private fun jsonFailure(
        blockedReason: String = "",
        stderr: String,
        elapsedMs: Long = 0L
    ): String {
        val out = MAPPER.createObjectNode()
        out.put("ok", false)
        out.put("exitCode", -1)
        out.put("stdout", "")
        out.put("stderr", stderr)
        out.put("elapsedMs", elapsedMs)
        out.put("truncated", false)
        out.put("blockedReason", blockedReason)
        return MAPPER.writeValueAsString(out)
    }

    companion object {
        private val MAPPER = ObjectMapper()
        private const val DEFAULT_TIMEOUT_MS = 10000L
        private const val OUTPUT_LIMIT = 8000
        // 默认拦截极危险子串；allow_risky=true 时放行。避免过宽匹配（如普通英文里的 "stop"）。
        private val DANGEROUS_PATTERNS = listOf(
            "su ",
            "sudo ",
            "rm -rf /",
            "reboot",
            "shutdown",
            "setenforce ",
            "mkfs",
            "dd if=",
            "am force-stop",
            "am kill",
            "svc power",
            "svc reboot"
        )
    }
}
