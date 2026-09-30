package com.agent1.android

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.agent1.android.productivity.logic.business.CrashLogAccess
import com.agent1.android.productivity.logic.business.CrashLogSnapshot

/** 纯 View 诊断页，避免 Compose 崩溃时无法查看日志。 */
object CrashGateUi {

    fun bind(activity: Activity, snapshot: CrashLogSnapshot, showContinue: Boolean) {
        runCatching {
            bindInternal(activity, snapshot, showContinue)
        }.onFailure { error ->
            activity.setContentView(
                android.widget.TextView(activity).apply {
                    text = "CrashGateUi.bind failed:\n${android.util.Log.getStackTraceString(error)}"
                    setPadding(32, 32, 32, 32)
                },
            )
        }
    }

    private fun bindInternal(activity: Activity, snapshot: CrashLogSnapshot, showContinue: Boolean) {
        activity.setContentView(R.layout.activity_crash_gate)
        val body = snapshot.displayText.orEmpty()
        activity.findViewById<TextView>(R.id.crash_gate_hint).text =
            buildString {
                append("请复制下方内容发给排查方，或在本机执行：./pull-crash-report.sh\n")
                append("私有文件：${snapshot.filePathHint}\n")
                append("若为 native 闪退（无 Java 栈），请同时抓 adb logcat。")
            }
        activity.findViewById<TextView>(R.id.crash_gate_body).text =
            body.ifBlank { "（暂无 Java 崩溃栈；可能是 JNI 闪退或进程被系统杀死，请用 logcat。）" }

        activity.findViewById<Button>(R.id.crash_gate_copy).setOnClickListener {
            copyToClipboard(activity, body)
            Toast.makeText(activity, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
        val continueBtn = activity.findViewById<Button>(R.id.crash_gate_continue)
        if (showContinue) {
            continueBtn.setOnClickListener {
                activity.startActivity(
                    Intent(activity, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_SKIP_CRASH_GATE, true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
                activity.finish()
            }
        } else {
            continueBtn.isEnabled = false
        }
        activity.findViewById<Button>(R.id.crash_gate_clear).setOnClickListener {
            CrashLogAccess.clearAll(activity)
            Toast.makeText(activity, "已清除", Toast.LENGTH_SHORT).show()
            activity.recreate()
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("agent1_crash", text))
    }
}
