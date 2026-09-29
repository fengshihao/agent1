package com.agent1.android

import android.app.Application
import android.content.Context
import com.agent1.android.llm.CrashReporter
import com.agent1.android.productivity.logic.data.StartupTrace

class Agent1Application : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashReporter.install(this)
        StartupTrace.mark(this, "Application.attachBaseContext")
    }

    override fun onCreate() {
        StartupTrace.mark(this, "Application.onCreate.begin")
        super.onCreate()
        CrashReporter.install(this)
        StartupTrace.mark(this, "Application.onCreate.end")
    }
}
