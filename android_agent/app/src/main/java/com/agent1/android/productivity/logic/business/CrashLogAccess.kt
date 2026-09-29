package com.agent1.android.productivity.logic.business

import android.content.Context
import com.agent1.android.llm.CrashReporter
import com.agent1.android.productivity.logic.data.PublicCrashExport
import com.agent1.android.productivity.logic.data.StartupTrace

data class CrashLogSnapshot(
    val displayText: String?,
    val filePathHint: String,
)

object CrashLogAccess {
    fun load(context: Context): CrashLogSnapshot {
        val crash = CrashReporter.getLastCrash(context) ?: PublicCrashExport.readMainAppMirror(context)
        val trace = StartupTrace.readCombined(context)
        val display = buildString {
            if (!crash.isNullOrBlank()) {
                appendLine(crash.trim())
            }
            if (!trace.isNullOrBlank()) {
                if (isNotEmpty()) appendLine()
                appendLine("=== startup trace ===")
                append(trace.trim())
            }
        }.trim().ifBlank { null }
        return CrashLogSnapshot(
            displayText = display,
            filePathHint = PublicCrashExport.userVisiblePathHint(),
        )
    }

    fun clearAll(context: Context) {
        CrashReporter.clearAllReports(context)
        StartupTrace.clear(context)
    }

    fun shouldShowGate(context: Context, skipGate: Boolean): Boolean {
        if (skipGate) return false
        val crash = CrashReporter.getLastCrash(context) ?: PublicCrashExport.readMainAppMirror(context)
        if (!crash.isNullOrBlank()) return true
        val trace = StartupTrace.readCombined(context).orEmpty()
        return trace.contains(".fail:", ignoreCase = true) ||
            trace.contains("App Crash Captured", ignoreCase = true)
    }
}
