package com.agent1.android

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import com.agent1.android.productivity.logic.business.CrashLogAccess
import com.agent1.android.productivity.logic.business.CrashLogSnapshot
import com.agent1.android.productivity.logic.data.StartupTrace

/** 诊断包入口：纯 [Activity]，不加载 Compose。 */
class CrashLogActivity : Activity() {
    @Suppress("TooGenericExceptionCaught")
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(
                TextView(this).apply {
                    text = "Agent1 诊断启动中…"
                    textSize = 16f
                    setPadding(48, 48, 48, 48)
                },
            )
            StartupTrace.mark(this, "CrashLogActivity.onCreate")
            val snapshot = runCatching { CrashLogAccess.load(this, includePublicMirror = true) }
                .getOrElse { CrashLogAccess.load(applicationContext, includePublicMirror = true) }
            val traceOnly = snapshot.displayText ?: StartupTrace.readPrivate(this)?.let {
                "=== startup trace（诊断包）===\n$it"
            } ?: "（暂无日志；若主 App 闪退，请先打开主 App 一次再回来看本页）"
            CrashGateUi.bind(
                this,
                snapshot.copy(displayText = snapshot.displayText ?: traceOnly),
                showContinue = false,
            )
            StartupTrace.mark(this, "CrashLogActivity.onCreate.end")
        } catch (t: Exception) {
            Log.e("CrashLogActivity", "fatal in onCreate", t)
            showEmergency(t)
        }
    }

    private fun showEmergency(error: Throwable) {
        val scroll = ScrollView(this)
        val text = TextView(this).apply {
            text = buildString {
                appendLine("诊断页启动失败（Emergency）")
                appendLine(Log.getStackTraceString(error))
            }
            textSize = 12f
            setPadding(32, 32, 32, 32)
        }
        scroll.addView(text)
        setContentView(scroll)
    }
}
