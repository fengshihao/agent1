package com.agent1.android.productivity.logic.business

import android.content.Context
import com.agent1.android.llm.BootTrace
import com.agent1.android.llm.CrashReporter

data class CrashLogSnapshot(
    val displayText: String?,
    val filePathHint: String,
)

object CrashLogAccess {
    private fun filePathHint(): String =
        "files/last_crash_report.txt（adb: ./pull-crash-report.sh）"

    fun load(context: Context): CrashLogSnapshot {
        val crash = CrashReporter.getLastCrash(context)
        val boot = BootTrace.read(context)
        val display = buildString {
            if (!crash.isNullOrBlank()) {
                append(crash.trim())
            }
            if (!boot.isNullOrBlank()) {
                if (isNotEmpty()) appendLine().appendLine()
                appendLine("=== boot_trace ===")
                append(boot.trim())
            }
        }.trim().ifBlank { null }
        return CrashLogSnapshot(
            displayText = display,
            filePathHint = filePathHint(),
        )
    }

    fun clearAll(context: Context) {
        CrashReporter.clearAllReports(context)
    }

    fun shouldShowGate(context: Context, skipGate: Boolean): Boolean {
        if (skipGate) return false
        return !CrashReporter.getLastCrash(context).isNullOrBlank()
    }
}
