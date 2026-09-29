package com.agent1.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.agent1.android.productivity.logic.business.CrashLogAccess
import com.agent1.android.productivity.logic.data.StartupTrace
import com.agent1.android.productivity.ui.view.ProductivityNavHost
import com.agent1.android.productivity.ui.view.ProductivityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.mark(this, "MainActivity.onCreate.begin")
        super.onCreate(savedInstanceState)
        val skipCrashGate = intent.getBooleanExtra(EXTRA_SKIP_CRASH_GATE, false)
        val snapshot = runCatching { CrashLogAccess.load(this) }.getOrElse {
            CrashLogAccess.load(applicationContext)
        }
        if (CrashLogAccess.shouldShowGate(this, skipCrashGate) && snapshot.displayText != null) {
            StartupTrace.mark(this, "MainActivity.showCrashGate")
            CrashGateUi.bind(this, snapshot, showContinue = true)
            return
        }
        StartupTrace.mark(this, "MainActivity.showMainUi")
        enableEdgeToEdge()
        setContent {
            ProductivityTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProductivityNavHost()
                }
            }
        }
        StartupTrace.mark(this, "MainActivity.onCreate.end")
    }

    companion object {
        const val EXTRA_SKIP_CRASH_GATE = "com.agent1.android.skip_crash_gate"
    }
}
