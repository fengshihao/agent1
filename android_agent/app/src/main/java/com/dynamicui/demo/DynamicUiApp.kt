package com.dynamicui.demo

import android.app.Application
import com.dynamicui.demo.llm.CrashReporter

class DynamicUiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
