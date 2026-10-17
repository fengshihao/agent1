package com.agent1.android.llm

import android.content.Context
import android.util.Log
import com.agent1.android.productivity.logic.data.AppLogFiles
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * 把本进程日志写进 files/app-logs，同时仍打到 logcat。
 * java.util.logging 也接到同一份文件，这样核心库的警告不依赖系统日志缓冲。
 */
object AppFileLog {

    private const val CLEANUP_INTERVAL_MS = 60L * 60L * 1000L

    private val time: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    @Volatile
    private var dir: File? = null

    private var lastCleanupMs = 0L
    private var julInstalled = false

    fun install(context: Context) {
        runCatching {
            val logDir = AppLogFiles.directory(context.applicationContext.filesDir)
            val first = dir == null
            dir = logDir
            AppLogFiles.cleanup(logDir)
            lastCleanupMs = System.currentTimeMillis()
            if (first) {
                installJulHandler()
                i("AppFileLog", "file log ready ${logDir.absolutePath}")
            }
        }
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        write("I", tag, message, null)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (error == null) Log.w(tag, message) else Log.w(tag, message, error)
        write("W", tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (error == null) Log.e(tag, message) else Log.e(tag, message, error)
        write("E", tag, message, error)
    }

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val directory = dir ?: return
        AppLogFiles.append(directory, format(level, tag, message, error))
        maybeCleanup(directory)
    }

    private fun maybeCleanup(directory: File) {
        val now = System.currentTimeMillis()
        if (now - lastCleanupMs < CLEANUP_INTERVAL_MS) {
            return
        }
        lastCleanupMs = now
        AppLogFiles.cleanup(directory)
    }

    private fun installJulHandler() {
        if (julInstalled) {
            return
        }
        val root = Logger.getLogger("")
        if (root.handlers.none { it is FileLogHandler }) {
            val handler = FileLogHandler()
            handler.level = Level.INFO
            root.addHandler(handler)
        }
        val current = root.level
        if (current == null || current.intValue() > Level.INFO.intValue()) {
            root.level = Level.INFO
        }
        julInstalled = true
    }

    private fun format(level: String, tag: String, message: String, error: Throwable?): String {
        return buildString {
            append(time.format(Instant.now()))
            append(' ')
            append(level)
            append('/')
            append(tag)
            append(' ')
            append(message)
            if (error != null) {
                append('\n')
                append(error.stackTraceToString())
            }
        }
    }

    private class FileLogHandler : Handler() {
        override fun publish(record: LogRecord?) {
            if (record == null || !isLoggable(record)) {
                return
            }
            val directory = dir ?: return
            val level = when {
                record.level.intValue() >= Level.SEVERE.intValue() -> "E"
                record.level.intValue() >= Level.WARNING.intValue() -> "W"
                else -> "I"
            }
            val tag = record.loggerName.substringAfterLast('.')
            val message = record.message ?: ""
            runCatching {
                AppLogFiles.append(directory, format(level, tag, message, record.thrown))
                maybeCleanup(directory)
            }
        }

        override fun flush() {
            // 逐条直写日志文件，无缓冲，无需 flush
        }

        override fun close() {
            // 持续追加写，没有需要关闭的资源
        }
    }
}
