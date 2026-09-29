package com.agent1.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.agent1.android.productivity.logic.business.CrashLogAccess
import com.agent1.android.productivity.logic.business.CrashLogSnapshot
import com.agent1.android.productivity.logic.data.StartupTrace
import com.agent1.android.productivity.ui.view.ProductivityNavHost
import com.agent1.android.productivity.ui.view.ProductivityTheme

class MainActivity : ComponentActivity() {
    @Suppress("TooGenericExceptionCaught")
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            StartupTrace.mark(this, "MainActivity.onCreate.begin")
            super.onCreate(savedInstanceState)
            val skipCrashGate = intent.getBooleanExtra(EXTRA_SKIP_CRASH_GATE, false)
            val snapshot = runCatching { CrashLogAccess.load(this) }.getOrNull()
                ?: CrashLogSnapshot(null, com.agent1.android.productivity.logic.data.PublicCrashExport.userVisiblePathHint())
            if (CrashLogAccess.shouldShowGate(this, skipCrashGate) && snapshot.displayText != null) {
                StartupTrace.mark(this, "MainActivity.showCrashGate")
                CrashGateUi.bind(this, snapshot, showContinue = true)
                return
            }
            openMainCompose()
        } catch (t: Exception) {
            Log.e("MainActivity", "onCreate failed", t)
            StartupTrace.mark(this, "MainActivity.onCreate.fail:${t.message}")
            val emergency = CrashLogSnapshot(
                displayText = Log.getStackTraceString(t),
                filePathHint = com.agent1.android.productivity.logic.data.PublicCrashExport.userVisiblePathHint(),
            )
            CrashGateUi.bind(this, emergency, showContinue = false)
        }
    }

    private fun openMainCompose() {
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
