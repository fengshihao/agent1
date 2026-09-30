package com.agent1.android

import android.app.Application
import android.content.Context
import com.agent1.android.llm.CrashReporter

class Agent1Application : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashReporter.install(base)
    }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
