package com.agent1.android.productivity.logic.business

import android.content.Context
import com.agent1.android.llm.CrashReporter
import com.agent1.android.productivity.logic.data.PublicCrashExport

data class CrashLogSnapshot(
    val report: String?,
    val filePathHint: String,
)

object CrashLogAccess {
    fun load(context: Context): CrashLogSnapshot {
        val report = CrashReporter.getLastCrash(context) ?: PublicCrashExport.readMainAppMirror(context)
        return CrashLogSnapshot(
            report = report,
            filePathHint = PublicCrashExport.userVisiblePathHint(),
        )
    }

    fun clearAll(context: Context) {
        CrashReporter.clearAllReports(context)
    }
}
