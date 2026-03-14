package com.dynamicui.demo.llm

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val PREFS = "llm_ui_debug_prefs"
    private const val KEY_LAST_CRASH = "last_crash_stack"
    private const val CRASH_FILE_NAME = "last_crash_report.txt"
    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                persistCrash(appContext, thread, throwable)
            }.onFailure {
                Log.e(TAG, "persist crash failed", it)
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
        installed = true
        Log.d(TAG, "UncaughtExceptionHandler installed")
    }

    fun getLastCrash(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fromPrefs = prefs.getString(KEY_LAST_CRASH, null)
        if (!fromPrefs.isNullOrBlank()) return fromPrefs
        return readCrashFile(context)
    }

    fun clearLastCrash(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LAST_CRASH).commit()
        runCatching { crashFile(context).delete() }
    }

    private fun persistCrash(context: Context, thread: Thread, throwable: Throwable) {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val stack = Log.getStackTraceString(throwable)
        val report = buildString {
            appendLine("=== App Crash Captured ===")
            appendLine("time: $now")
            appendLine("thread: ${thread.name}")
            appendLine("error: ${throwable::class.java.name}: ${throwable.message.orEmpty()}")
            appendLine("stacktrace:")
            appendLine(stack)
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val committed = prefs.edit().putString(KEY_LAST_CRASH, report).commit()
        val fileSaved = runCatching {
            crashFile(context).writeText(report)
            true
        }.getOrElse {
            Log.e(TAG, "write crash file failed", it)
            false
        }
        Log.e(TAG, "App crashed, report persisted prefs=$committed file=$fileSaved")
    }

    private fun crashFile(context: Context): File {
        return File(context.applicationContext.filesDir, CRASH_FILE_NAME)
    }

    private fun readCrashFile(context: Context): String? {
        return runCatching {
            val file = crashFile(context)
            if (file.exists()) file.readText() else null
        }.getOrNull()
    }
}
