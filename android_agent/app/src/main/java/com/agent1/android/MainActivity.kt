package com.agent1.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.agent1.android.productivity.logic.business.CrashLogAccess
import com.agent1.android.productivity.ui.view.CrashLogScreen
import com.agent1.android.productivity.ui.view.ProductivityNavHost
import com.agent1.android.productivity.ui.view.ProductivityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val skipCrashGate = intent.getBooleanExtra(EXTRA_SKIP_CRASH_GATE, false)
        val crashSnapshot = if (skipCrashGate) null else CrashLogAccess.load(this)
        setContent {
            ProductivityTheme {
                var showMain by rememberSaveable {
                    mutableStateOf(skipCrashGate || crashSnapshot?.report.isNullOrBlank() == true)
                }
                if (!showMain && crashSnapshot != null) {
                    CrashLogScreen(
                        report = crashSnapshot.report,
                        filePathHint = crashSnapshot.filePathHint,
                        showContinueToApp = true,
                        onContinueToApp = { showMain = true },
                        onClearReports = {
                            CrashLogAccess.clearAll(this@MainActivity)
                            showMain = true
                        },
                    )
                } else {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ProductivityNavHost()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_SKIP_CRASH_GATE = "com.agent1.android.skip_crash_gate"
    }
}
