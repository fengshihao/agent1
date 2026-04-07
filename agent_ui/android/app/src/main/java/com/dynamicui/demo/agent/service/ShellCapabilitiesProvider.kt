package com.dynamicui.demo.agent.service

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.Callable
import java.util.concurrent.Executors
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
    private const val PROBE_THREADS = 8
    private const val FUTURE_GET_SEC = 3L
    /** 供 get_shell_capabilities 探测并在上下文中列出；run_shell 不再仅限此列表。 */
    private val CANDIDATES = listOf(
        "sh",
        "toybox",
        "toolbox",
        "busybox",
        "getprop",
        "setprop",
        "pm",
        "am",
        "settings",
        "cmd",
        "dumpsys",
        "logcat",
        "log",
        "input",
        "svc",
        "top",
        "ps",
        "ls",
        "cat",
        "echo",
        "printf",
        "grep",
        "egrep",
        "fgrep",
        "find",
        "xargs",
        "sort",
        "uniq",
        "wc",
        "head",
        "tail",
        "cut",
        "tr",
        "sed",
        "awk",
        "mkdir",
        "rmdir",
        "cp",
        "mv",
        "ln",
        "chmod",
        "chown",
        "touch",
        "stat",
        "id",
        "uname",
        "hostname",
        "date",
        "uptime",
        "df",
        "du",
        "mount",
        "umount",
        "which",
        "basename",
        "dirname",
        "readlink",
        "realpath",
        "md5sum",
        "sha1sum",
        "base64",
        "tar",
        "gzip",
        "gunzip",
        "zip",
        "unzip",
        "curl",
        "wget",
        "nc",
        "netstat",
        "ss",
        "ping",
        "iptables",
        "ip",
        "ifconfig",
        "sqlite3",
        "screencap",
        "screenrecord",
        "content",
        "service"
    )

    @Volatile
    private var cache: ShellCapabilities? = null

    fun probeIfNeeded(force: Boolean = false): ShellCapabilities {
        val cached = cache
        if (!force && cached != null) return cached
        val available = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val pool = Executors.newFixedThreadPool(PROBE_THREADS)
        try {
            val futures = CANDIDATES.map { cmd ->
                cmd to pool.submit(Callable { isCommandAvailable(cmd) })
            }
            for ((cmd, future) in futures) {
                val ok = try {
                    future.get(FUTURE_GET_SEC, TimeUnit.SECONDS)
                } catch (_: Exception) {
                    future.cancel(true)
                    false
                }
                if (ok) {
                    available += cmd
                } else {
                    missing += cmd
                }
            }
        } finally {
            pool.shutdownNow()
        }
        val snapshot = ShellCapabilities(
            shellPath = SHELL_PATH,
            availableCommands = available.sorted(),
            missingCommands = missing.sorted(),
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
