package com.agent1.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.agent1.android.llm.BootTrace
import com.agent1.android.llm.CrashReporter
import com.agent1.android.productivity.logic.business.CrashLogAccess
import com.agent1.android.productivity.logic.business.CrashLogSnapshot
import com.agent1.android.productivity.ui.view.ProductivityNavHost
import com.agent1.android.productivity.ui.view.ProductivityTheme

class MainActivity : ComponentActivity() {
    @Suppress("TooGenericExceptionCaught")
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            BootTrace.mark(this, "MainActivity.onCreate.beforeSuper")
            super.onCreate(savedInstanceState)
            BootTrace.mark(this, "MainActivity.onCreate.afterSuper")
            val skipCrashGate = intent.getBooleanExtra(EXTRA_SKIP_CRASH_GATE, false)
            val snapshot = runCatching { CrashLogAccess.load(this) }.getOrElse {
                CrashLogSnapshot(null, "files/last_crash_report.txt")
            }
            if (CrashLogAccess.shouldShowGate(this, skipCrashGate) && snapshot.displayText != null) {
                CrashGateUi.bind(this, snapshot, showContinue = true)
                return
            }
            openMainCompose()
        } catch (t: Throwable) {
            Log.e(TAG, "onCreate failed", t)
            CrashReporter.recordHandledFailure(this, "MainActivity.onCreate", t)
            val emergency = CrashLogSnapshot(
                displayText = Log.getStackTraceString(t),
                filePathHint = "files/last_crash_report.txt",
            )
            CrashGateUi.bind(this, emergency, showContinue = false)
        }
    }

    private fun openMainCompose() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        BootTrace.mark(this, "MainActivity.openMainCompose")
        setContent {
            ProductivityTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProductivityNavHost()
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_SKIP_CRASH_GATE = "com.agent1.android.skip_crash_gate"
    }
}
