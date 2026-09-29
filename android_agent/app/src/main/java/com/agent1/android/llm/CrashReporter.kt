package com.agent1.android.llm

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.agent1.android.BuildConfig
import com.agent1.android.CrashBriefActivity
import com.agent1.android.productivity.logic.data.PublicCrashExport
import com.agent1.android.productivity.logic.data.StartupTrace
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val PREFS = "llm_ui_debug_prefs"
    private const val KEY_LAST_CRASH = "last_crash_stack"
    private const val CRASH_FILE_NAME = "last_crash_report.txt"
    private const val CRASH_DIR_NAME = "crash-reports"
    private const val USER_VISIBLE_PAUSE_MS = 4_500L
    @Volatile
    private var installed = false

    fun recordHandledError(context: Context, source: String, throwable: Throwable) {
        val thread = Thread.currentThread()
        val wrapped = RuntimeException("$source: ${throwable.message}", throwable)
        persistCrash(context.applicationContext, thread, wrapped)
        if (BuildConfig.DEBUG) {
            notifyUserBriefly(context.applicationContext, thread, wrapped)
        }
    }

    fun install(context: Context) {
        if (installed) return
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                StartupTrace.mark(appContext, "UncaughtExceptionHandler.${throwable.javaClass.simpleName}")
                persistCrash(appContext, thread, throwable)
                if (BuildConfig.DEBUG) {
                    notifyUserBriefly(appContext, thread, throwable)
                }
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

    fun clearAllReports(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LAST_CRASH).commit()
        runCatching { crashFile(context).delete() }
        runCatching {
            val dir = crashReportDir(context)
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
                dir.delete()
            }
        }
    }

    private fun formatBrief(throwable: Throwable): String {
        val head = throwable::class.java.simpleName
        val msg = throwable.message?.trim().orEmpty().take(160)
        return if (msg.isEmpty()) "Agent1 崩溃: $head" else "Agent1 崩溃: $head — $msg"
    }

    private fun notifyUserBriefly(context: Context, thread: Thread, throwable: Throwable) {
        val brief = formatBrief(throwable)
        val app = context.applicationContext
        val onMain = thread === Looper.getMainLooper().thread
        if (onMain) {
            Toast.makeText(app, brief, Toast.LENGTH_LONG).show()
            launchBriefActivity(app, brief, throwable)
            pauseBriefly()
        } else {
            val latch = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(app, brief, Toast.LENGTH_LONG).show()
                launchBriefActivity(app, brief, throwable)
                latch.countDown()
            }
            runCatching { latch.await(800, TimeUnit.MILLISECONDS) }
            pauseBriefly()
        }
    }

    private fun launchBriefActivity(context: Context, brief: String, throwable: Throwable) {
        val detail = buildString {
            appendLine(brief)
            appendLine()
            append(Log.getStackTraceString(throwable).take(6_000))
        }
        val intent = Intent(context, CrashBriefActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(CrashBriefActivity.EXTRA_MESSAGE, detail)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "CrashBriefActivity start failed", it) }
    }

    private fun pauseBriefly() {
        try {
            Thread.sleep(USER_VISIBLE_PAUSE_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
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
        val archiveSaved = runCatching {
            val dir = crashReportDir(context)
            if (!dir.exists()) dir.mkdirs()
            val fileName = "crash-${System.currentTimeMillis()}.txt"
            File(dir, fileName).writeText(report)
            true
        }.getOrElse {
            Log.e(TAG, "write crash archive failed", it)
            false
        }
        PublicCrashExport.mirrorFromMainAppAsync(context.applicationContext, report)
        Log.e(TAG, "App crashed, report persisted prefs=$committed file=$fileSaved archive=$archiveSaved")
    }

    private fun crashFile(context: Context): File {
        return File(context.applicationContext.filesDir, CRASH_FILE_NAME)
    }

    fun reportDirectory(context: Context): File = crashReportDir(context)

    private fun crashReportDir(context: Context): File {
        return File(context.applicationContext.filesDir, CRASH_DIR_NAME)
    }

    private fun readCrashFile(context: Context): String? {
        return runCatching {
            val file = crashFile(context)
            if (file.exists()) file.readText() else null
        }.getOrNull()
    }
}
