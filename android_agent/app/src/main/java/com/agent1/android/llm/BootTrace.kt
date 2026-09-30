package com.agent1.android.llm

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 启动阶段落盘轨迹（不依赖 CrashReporter 已安装）。 */
object BootTrace {
    private const val TAG = "Agent1Boot"
    private const val FILE_NAME = "boot_trace.txt"

    fun mark(context: Context, step: String) {
        val line = "${timestamp()} $step"
        Log.i(TAG, line)
        runCatching {
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            file.parentFile?.mkdirs()
            file.appendText(line + "\n")
        }.onFailure {
            Log.w(TAG, "boot trace append failed: ${it.message}")
        }
    }

    fun read(context: Context): String? {
        return runCatching {
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            if (file.exists()) file.readText() else null
        }.getOrNull()
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
