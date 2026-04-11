package com.dynamicui.demo.pet.logic.data.service

import android.os.Build
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ShellContextBuilder {
    fun build(): String {
        val cap = ShellCapabilitiesProvider.getCachedOrProbe()
        val path = System.getenv("PATH").orEmpty()
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val humanTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone).format(now)
        return buildString {
            appendLine("当前 shell 环境:")
            appendLine("- nowLocal: $humanTime")
            appendLine("- timezone: ${zone.id}")
            appendLine("- epochMs: ${System.currentTimeMillis()}")
            appendLine("- os: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("- shellPath: ${cap.shellPath}")
            appendLine("- brand: ${Build.BRAND}")
            appendLine("- model: ${Build.MODEL}")
            appendLine("- abi: ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}")
            appendLine("- path: ${if (path.isBlank()) "(empty)" else path}")
            appendLine("- detectedAtMs: ${cap.detectedAtMs}")
            appendLine("- availableCommands: ${if (cap.availableCommands.isEmpty()) "(none)" else cap.availableCommands.joinToString(",")}")
            appendLine("- missingCommands: ${if (cap.missingCommands.isEmpty()) "(none)" else cap.missingCommands.joinToString(",")}")
            appendLine("- run_shell constraints:")
            appendLine("  - timeoutMs: 1000-30000 (default 10000)")
            appendLine("  - outputLimit: stdout/stderr 各 8000 chars")
            appendLine("  - 任意 PATH 内命令均可尝试；不存在则由 shell 报错")
            appendLine("  - blockedRiskyPatterns(默认): su/sudo, rm -rf /, reboot/shutdown, setenforce, mkfs, dd if=, am force-stop/kill, svc power/reboot")
            appendLine("  - allow_risky=true 可绕过上述风险拦截（仍受系统权限限制）")
        }.trim()
    }
}
