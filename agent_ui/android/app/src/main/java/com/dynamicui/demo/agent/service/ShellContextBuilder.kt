package com.dynamicui.demo.agent.service

import android.os.Build

object ShellContextBuilder {
    fun build(): String {
        val cap = ShellCapabilitiesProvider.getCachedOrProbe()
        val path = System.getenv("PATH").orEmpty()
        return buildString {
            appendLine("当前 shell 环境:")
            appendLine("- shellPath: ${cap.shellPath}")
            appendLine("- sdkInt: ${Build.VERSION.SDK_INT}")
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
            appendLine("  - blockedRiskyPatterns: su, rm -rf /, reboot, shutdown, setenforce, mkfs, dd if=, stop/start")
            appendLine("  - use allow_risky=true 才可尝试绕过风险拦截")
        }.trim()
    }
}
