package com.agent1.android.productivity.logic.data

import android.os.Process
import java.util.concurrent.TimeUnit

/** 抓取本进程最近的 logcat，失败时返回说明文本而不是抛错。 */
object LogcatCapture {

    fun capture(maxLines: Int = 6000): String {
        val pid = Process.myPid()
        val command = listOf(
            "logcat",
            "-d",
            "-v",
            "threadtime",
            "-t",
            maxLines.toString(),
            "--pid=$pid",
        )
        return runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroy()
                return "logcat 超时，已中断。\n$text"
            }
            if (text.isBlank()) "logcat 为空（pid=$pid）" else text
        }.getOrElse { error ->
            "logcat 抓取失败: ${error.message ?: error.javaClass.simpleName}"
        }
    }
}
