package com.dynamicui.demo.pet.logic.data.service

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object AgentFileLogger {
    private const val LOG_DIR = "agent_logs"
    private const val LOG_FILE = "agent_runtime.log"
    private val tsFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        ensureLogFile()
    }

    fun clear() {
        val ctx = appContext ?: return
        try {
            val f = ensureLogFile(ctx)
            synchronized(this) {
                f.writeText("")
            }
        } catch (_: Exception) {
        }
    }

    fun log(tag: String, message: String) {
        val ctx = appContext ?: return
        val line = "${LocalDateTime.now().format(tsFormatter)} [$tag] $message\n"
        try {
            val f = ensureLogFile(ctx)
            synchronized(this) {
                f.appendText(line)
            }
        } catch (_: Exception) {
        }
    }

    private fun ensureLogFile(): File? {
        val ctx = appContext ?: return null
        return ensureLogFile(ctx)
    }

    private fun ensureLogFile(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, LOG_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val f = File(dir, LOG_FILE)
        if (!f.exists()) {
            f.createNewFile()
        }
        return f
    }
}
