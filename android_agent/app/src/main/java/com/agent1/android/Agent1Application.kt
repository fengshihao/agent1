package com.agent1.android

import android.app.Application
import com.agent1.android.llm.CrashReporter

class Agent1Application : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
