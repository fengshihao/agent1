package com.agent1.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.agent1.android.productivity.logic.business.CrashLogAccess
import com.agent1.android.productivity.ui.view.CrashLogScreen
import com.agent1.android.productivity.ui.view.ProductivityTheme

/**
 * 诊断包专用入口；主包也会在检测到上次崩溃时于 [MainActivity] 先展示同一界面。
 */
class CrashLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val snapshot = CrashLogAccess.load(this)
        setContent {
            ProductivityTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CrashLogScreen(
                        report = snapshot.report,
                        filePathHint = snapshot.filePathHint,
                        showContinueToApp = true,
                        onContinueToApp = {
                            startActivity(
                                Intent(this@CrashLogActivity, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_SKIP_CRASH_GATE, true)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                            )
                        },
                        onClearReports = {
                            CrashLogAccess.clearAll(this@CrashLogActivity)
                            recreate()
                        },
                    )
                }
            }
        }
    }
}
