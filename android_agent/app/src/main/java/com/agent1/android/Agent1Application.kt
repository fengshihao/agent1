package com.agent1.android

import android.app.Application
import com.agent1.android.llm.AppFileLog
import com.agent1.android.llm.BootTrace
import com.agent1.android.llm.CrashReporter
import com.agent1.android.productivity.logic.business.DiagnosticExport
import com.agent1.android.productivity.logic.business.i18n.AppLocalization
import com.agent1.android.productivity.logic.data.AndroidCapabilityDatabase
import com.agent1.javaagent.capability.CapabilityIndexStore

class Agent1Application : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLocalization.attach(this)
        CapabilityIndexStore.setDatabaseOpener { path -> AndroidCapabilityDatabase.open(path) }
        AppFileLog.install(this)
        BootTrace.mark(this, "Application.onCreate")
        CrashReporter.install(this)
        DiagnosticExport.cleanupStaleDiagnosticExports(this)
    }
}
