package com.agent1.android.productivity.logic.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 启动阶段轨迹：即使未捕获 JVM 崩溃，也能在下载目录看到最后执行到哪一步。 */
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
        runCatching { PublicCrashExport.mirrorStartupTrace(app, body) }
    }

    fun readPrivate(context: Context): String? {
        val file = traceFile(context.applicationContext)
        return if (file.exists()) file.readText() else null
    }

    fun readCombined(context: Context): String? {
        readPrivate(context)?.takeIf { it.isNotBlank() }?.let { return it }
        return PublicCrashExport.readStartupTraceMirror(context)
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        runCatching { traceFile(app).delete() }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LINES).commit()
        runCatching { PublicCrashExport.mirrorStartupTrace(app, "") }
    }

    private fun traceFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
    }
}
