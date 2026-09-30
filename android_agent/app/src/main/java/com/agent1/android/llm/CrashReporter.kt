package com.agent1.android.llm

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.agent1.android.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val PREFS = "llm_ui_debug_prefs"
    private const val KEY_LAST_CRASH = "last_crash_stack"
    private const val CRASH_FILE_NAME = "last_crash_report.txt"
    private const val CRASH_DIR_NAME = "crash-reports"
    @Volatile
    private var handlerInstalled = false

    /** 仅注册 JVM 未捕获处理器；可在 ContentProvider 早期调用，不做磁盘读。 */
    fun installUncaughtHandler(context: Context) {
        if (handlerInstalled) return
        runCatching {
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
            handlerInstalled = true
            Log.i(TAG, "UncaughtExceptionHandler installed")
        }.onFailure {
            Log.e(TAG, "installUncaughtHandler failed", it)
        }
    }

    /** Application.onCreate 及以后：确保 handler + 打印上次崩溃摘要。 */
    fun install(context: Context) {
        installUncaughtHandler(context)
        logPreviousCrashIfAny(context)
    }

    fun logPreviousCrashIfAny(context: Context) {
        runCatching {
            getLastCrash(context.applicationContext)?.let { report ->
                Log.e(TAG, "previous crash on disk (see files/last_crash_report.txt):\n$report")
            }
        }.onFailure {
            Log.w(TAG, "logPreviousCrashIfAny skipped: ${it.message}")
        }
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

    /** 已捕获的启动/编排失败（非 uncaught），同样落盘便于下次启动展示。 */
    fun recordHandledFailure(context: Context, where: String, throwable: Throwable) {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val stack = Log.getStackTraceString(throwable)
        val report = buildString {
            appendLine("=== Handled Failure (persisted) ===")
            appendLine("time: $now")
            appendLine("where: $where")
            appendLine("error: ${throwable::class.java.name}: ${throwable.message.orEmpty()}")
            appendLine("stacktrace:")
            appendLine(stack)
        }
        persistReport(context, report)
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
        persistReport(context, report)
    }

    private fun persistReport(context: Context, report: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val committed = prefs.edit().putString(KEY_LAST_CRASH, report).commit()
        val fileSaved = runCatching {
            val file = crashFile(context)
            file.parentFile?.mkdirs()
            file.writeText(report)
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
        Log.e(TAG, "Crash report persisted prefs=$committed file=$fileSaved archive=$archiveSaved")
        if (BuildConfig.DEBUG) {
            runCatching {
                Handler(Looper.getMainLooper()).post {
                    runCatching {
                        Toast.makeText(
                            context.applicationContext,
                            "崩溃/错误已写入 files/last_crash_report.txt",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
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
