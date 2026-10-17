package com.agent1.android.productivity.logic.data

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 应用自己写入的运行日志。不读系统 logcat。
 * 当前文件写满后改名保留，启动和写入间隙删掉过期或超量的旧文件。
 */
object AppLogFiles {

    const val DIR_NAME = "app-logs"
    const val ACTIVE_NAME = "app.log"

    /** 单个当前文件超过该大小就滚动。 */
    const val MAX_FILE_BYTES = 256L * 1024L

    /** 目录里日志合计超过该大小时，从最旧的滚动文件开始删。 */
    const val MAX_TOTAL_BYTES = 2L * 1024L * 1024L

    /** 滚动文件保留这么久。当前正在写的 [ACTIVE_NAME] 不按年龄删。 */
    const val MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L

    private val lock = Any()
    private val rotatedNameTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault())

    fun directory(filesDir: File): File = File(filesDir, DIR_NAME)

    fun append(dir: File, text: String, maxFileBytes: Long = MAX_FILE_BYTES) {
        if (text.isEmpty()) {
            return
        }
        synchronized(lock) {
            dir.mkdirs()
            val active = File(dir, ACTIVE_NAME)
            if (active.isFile && active.length() >= maxFileBytes) {
                rotate(active)
            }
            val payload = if (text.endsWith("\n")) text else text + "\n"
            active.appendText(payload)
        }
    }

    fun cleanup(
        dir: File,
        nowMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = MAX_AGE_MS,
        maxTotalBytes: Long = MAX_TOTAL_BYTES,
    ) {
        synchronized(lock) {
            if (!dir.isDirectory) {
                return
            }
            val cutoff = nowMs - maxAgeMs
            rotatedFiles(dir)
                .filter { it.lastModified() < cutoff }
                .forEach { it.delete() }
            val activeLength = File(dir, ACTIVE_NAME).takeIf { it.isFile }?.length() ?: 0L
            var total = activeLength + rotatedFiles(dir).sumOf { it.length() }
            val oldestFirst = rotatedFiles(dir).sortedBy { it.lastModified() }
            for (file in oldestFirst) {
                if (total <= maxTotalBytes) {
                    break
                }
                val length = file.length()
                if (file.delete()) {
                    total -= length
                }
            }
        }
    }

    private fun rotate(active: File) {
        val parent = active.parentFile ?: return
        val stamp = rotatedNameTime.format(Instant.now())
        var dest = File(parent, "app-$stamp.log")
        var suffix = 1
        while (dest.exists()) {
            dest = File(parent, "app-$stamp-$suffix.log")
            suffix++
        }
        if (!active.renameTo(dest)) {
            dest.writeBytes(active.readBytes())
            active.writeText("")
        }
    }

    private fun rotatedFiles(dir: File): List<File> {
        return dir.listFiles { file ->
            file.isFile && file.name.startsWith("app-") && file.name.endsWith(".log")
        }?.toList() ?: emptyList()
    }
}
