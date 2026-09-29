package com.agent1.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.agent1.android.productivity.logic.business.CrashLogAccess
import com.agent1.android.productivity.logic.data.StartupTrace

class CrashLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.mark(this, "CrashLogActivity.onCreate")
        super.onCreate(savedInstanceState)
        val snapshot = CrashLogAccess.load(this)
        CrashGateUi.bind(this, snapshot, showContinue = true)
    }
}
