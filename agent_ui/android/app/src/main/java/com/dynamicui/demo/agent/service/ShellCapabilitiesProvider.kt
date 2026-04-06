package com.dynamicui.demo.agent.service

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

data class ShellCapabilities(
    val shellPath: String,
    val availableCommands: List<String>,
    val missingCommands: List<String>,
    val detectedAtMs: Long
)

object ShellCapabilitiesProvider {
    private const val SHELL_PATH = "/system/bin/sh"
    private const val COMMAND_TIMEOUT_MS = 1200L
    private val CANDIDATES = listOf(
        "sh", "toybox", "toolbox", "getprop", "pm", "am", "settings",
        "dumpsys", "logcat", "input", "cmd", "svc", "top", "ps"
    )

    @Volatile
    private var cache: ShellCapabilities? = null

    fun probeIfNeeded(force: Boolean = false): ShellCapabilities {
        val cached = cache
        if (!force && cached != null) return cached
        val available = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for (cmd in CANDIDATES) {
            if (isCommandAvailable(cmd)) {
                available += cmd
            } else {
                missing += cmd
            }
        }
        val snapshot = ShellCapabilities(
            shellPath = SHELL_PATH,
            availableCommands = available,
            missingCommands = missing,
            detectedAtMs = System.currentTimeMillis()
        )
        cache = snapshot
        return snapshot
    }

    fun getCachedOrProbe(): ShellCapabilities = cache ?: probeIfNeeded()

    fun asJson(cap: ShellCapabilities = getCachedOrProbe()): String {
        val node = MAPPER.createObjectNode()
        node.put("shellPath", cap.shellPath)
        node.putPOJO("availableCommands", cap.availableCommands)
        node.putPOJO("missingCommands", cap.missingCommands)
        node.put("detectedAtMs", cap.detectedAtMs)
        return MAPPER.writeValueAsString(node)
    }

    private fun isCommandAvailable(command: String): Boolean {
        return try {
            val process = ProcessBuilder(SHELL_PATH, "-c", "command -v $command").start()
            val finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return false
            }
            process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    private val MAPPER = ObjectMapper()
}
