package com.agent1.android.productivity.logic.business

import android.content.Context
import com.agent1.android.llm.CrashReporter
import com.agent1.android.productivity.logic.data.StartupTrace

object StartupDiagnostics {
    fun mark(context: Context, phase: String) {
        StartupTrace.mark(context, phase)
    }

    fun recordFailure(context: Context, source: String, error: Throwable) {
        StartupTrace.mark(context, "$source.fail:${error.message}")
        CrashReporter.recordHandledError(context, source, error)
    }
}
