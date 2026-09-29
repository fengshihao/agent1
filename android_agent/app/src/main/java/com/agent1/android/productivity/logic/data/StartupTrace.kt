package com.agent1.android.productivity.logic.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 启动阶段轨迹：仅同步写应用私有目录；下载目录镜像异步。 */
object StartupTrace {
    private const val FILE_NAME = "startup_trace.txt"
    private const val PREFS = "startup_trace_prefs"
    private const val KEY_LINES = "lines"

    fun mark(context: Context, phase: String) {
        val app = context.applicationContext
        val line = "${timestamp()} $phase"
        runCatching {
            val file = traceFile(app)
            file.parentFile?.mkdirs()
            file.appendText(line + "\n")
        }
        runCatching {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val merged = buildString {
                append(prefs.getString(KEY_LINES, "").orEmpty())
                append(line)
                append('\n')
            }.takeLast(32_000)
            prefs.edit().putString(KEY_LINES, merged).commit()
        }
        val body = readPrivate(app).orEmpty()
        PublicCrashExport.mirrorStartupTraceAsync(app, body)
    }

    fun readPrivate(context: Context): String? {
        val file = traceFile(context.applicationContext)
        return runCatching { if (file.exists()) file.readText() else null }.getOrNull()
    }

    /** UI / 诊断页优先读私有文件，避免在 Android 16 上阻塞读 MediaStore。 */
    fun readCombined(context: Context): String? {
        return readPrivate(context)?.takeIf { it.isNotBlank() }
            ?: PublicCrashExport.readStartupTraceMirror(context)?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        runCatching { traceFile(app).delete() }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LINES).commit()
        PublicCrashExport.mirrorStartupTraceAsync(app, "")
    }

    private fun traceFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
    }
}
