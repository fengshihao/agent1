package com.agent1.android

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.agent1.android.llm.AppFileLog
import com.agent1.android.llm.BootTrace
import com.agent1.android.llm.CrashReporter
import com.agent1.android.productivity.logic.business.CrashLogAccess
import com.agent1.android.productivity.logic.business.CrashLogSnapshot
import com.agent1.android.productivity.logic.business.platform.HostUiBridge
import com.agent1.android.productivity.logic.business.platform.ProductivityActivityBridge
import com.agent1.android.productivity.ui.view.ProductivityNavHost
import com.agent1.android.productivity.ui.view.ProductivityTheme
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class MainActivity : ComponentActivity(), ProductivityActivityBridge {

    private val pendingPermissions = AtomicReference<CompletableFuture<Map<String, Boolean>>?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        pendingPermissions.getAndSet(null)?.complete(result)
    }

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
            AppFileLog.e(TAG, "onCreate failed", t)
            CrashReporter.recordHandledFailure(this, "MainActivity.onCreate", t)
            val emergency = CrashLogSnapshot(
                displayText = Log.getStackTraceString(t),
                filePathHint = "files/last_crash_report.txt",
            )
            CrashGateUi.bind(this, emergency, showContinue = false)
        }
    }

    override fun onStart() {
        super.onStart()
        HostUiBridge.register(this)
    }

    override fun onStop() {
        HostUiBridge.unregister(this)
        super.onStop()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun confirm(message: String): Boolean {
        if (isFinishing || isDestroyed) {
            return false
        }
        val future = CompletableFuture<Boolean>()
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                future.complete(false)
                return@runOnUiThread
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.host_confirm_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.host_confirm_ok) { _, _ -> future.complete(true) }
                .setNegativeButton(R.string.host_confirm_deny) { _, _ -> future.complete(false) }
                .setOnCancelListener { future.complete(false) }
                .show()
        }
        return try {
            future.get(120, TimeUnit.SECONDS)
        } catch (_: Exception) {
            false
        }
    }

    override fun requestPermissions(permissions: Array<String>): Map<String, Boolean> {
        if (permissions.isEmpty()) {
            return emptyMap()
        }
        val future = CompletableFuture<Map<String, Boolean>>()
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                future.complete(permissions.associateWith { false })
                return@runOnUiThread
            }
            pendingPermissions.set(future)
            permissionLauncher.launch(permissions)
        }
        return try {
            future.get(120, TimeUnit.SECONDS)
        } catch (_: Exception) {
            pendingPermissions.compareAndSet(future, null)
            permissions.associateWith { false }
        }
    }

    private fun openMainCompose() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        BootTrace.mark(this, "MainActivity.openMainCompose")
        setContent {
            ProductivityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
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
