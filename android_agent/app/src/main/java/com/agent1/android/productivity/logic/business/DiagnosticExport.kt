package com.agent1.android.productivity.logic.business

import android.content.Context
import android.os.Build
import com.agent1.android.BuildConfig
import com.agent1.android.llm.CrashReporter
import com.agent1.android.productivity.logic.data.DiagnosticBundleWriter
import com.agent1.android.productivity.logic.data.LogcatCapture
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 打包会话、事件日志、崩溃和 logcat，供分享给云端诊断。不含 API Key。
 * Zip 仅落在 [cacheDir]/diagnostics/ 临时目录；分享后删除，启动时清理超时残留。
 */
object DiagnosticExport {

    private const val CACHE_DIR = "diagnostics"
    private const val ZIP_SUFFIX = ".zip"

    /** 分享 chooser 返回后，留给目标应用读取 zip 的宽限（毫秒）。 */
    const val SHARE_DELETE_DELAY_MS = 5L * 60L * 1000L

    /** 启动时删除超过该年龄的残留诊断 zip（进程被杀、未走分享回调等）。 */
    private const val STALE_MAX_AGE_MS = 24L * 60L * 60L * 1000L

    fun diagnosticsDir(context: Context): File =
        File(context.applicationContext.cacheDir, CACHE_DIR)

    fun exportZip(context: Context): File {
        val app = context.applicationContext
        val outDir = diagnosticsDir(app).apply { mkdirs() }
        deleteAllDiagnosticZips(outDir)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val zip = File(outDir, "agent1-diag-$stamp$ZIP_SUFFIX")
        val agentRoot = ProductivityGatewayProvider.agentRoot(app).toFile()
        val summary = runCatching { AndroidRuntimeNote.from(app) }.getOrElse {
            "运行时摘要读取失败: ${it.message}"
        }
        DiagnosticBundleWriter().write(
            DiagnosticBundleWriter.Request(
                outputZip = zip,
                trees = listOf(
                    DiagnosticBundleWriter.NamedDir("agent1", agentRoot),
                    DiagnosticBundleWriter.NamedDir("crash-reports", CrashReporter.reportDirectory(app)),
                ),
                texts = listOf(
                    DiagnosticBundleWriter.NamedText("README.txt", readme()),
                    DiagnosticBundleWriter.NamedText("device.txt", deviceNote(app, summary)),
                    DiagnosticBundleWriter.NamedText("logcat.txt", LogcatCapture.capture()),
                ),
            ),
        )
        return zip
    }

    /** 应用启动时调用：删掉过期的诊断 zip，避免 cache 长期堆积。 */
    fun cleanupStaleDiagnosticExports(context: Context) {
        cleanupStaleDiagnosticExports(diagnosticsDir(context), STALE_MAX_AGE_MS)
    }

    fun cleanupStaleDiagnosticExports(dir: File, maxAgeMs: Long) {
        if (!dir.isDirectory) {
            return
        }
        val cutoff = System.currentTimeMillis() - maxAgeMs
        dir.listFiles { file -> file.isFile && file.name.endsWith(ZIP_SUFFIX) }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    fun deleteAllDiagnosticZips(context: Context) {
        deleteAllDiagnosticZips(diagnosticsDir(context))
    }

    fun deleteAllDiagnosticZips(dir: File) {
        if (!dir.isDirectory) {
            return
        }
        dir.listFiles { file -> file.isFile && file.name.endsWith(ZIP_SUFFIX) }
            ?.forEach { it.delete() }
    }

    /** 分享完成后删除本次导出的 zip（路径须在 diagnostics 目录下）。 */
    fun deleteExportZip(zip: File) {
        val parent = zip.parentFile ?: return
        if (parent.name != CACHE_DIR || !zip.name.endsWith(ZIP_SUFFIX)) {
            return
        }
        zip.delete()
    }

    private fun deviceNote(context: Context, runtimeNote: String): String {
        val pkg = context.packageName
        return buildString {
            appendLine("time: ${Date()}")
            appendLine("package: $pkg")
            appendLine("versionName: ${BuildConfig.VERSION_NAME}")
            appendLine("versionCode: ${BuildConfig.VERSION_CODE}")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android: ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine(runtimeNote)
        }
    }

    private fun readme(): String = """
        Agent One 诊断包（手机一键导出）
        把整个 zip 发给负责诊断的 Agent 即可，不必解压后再挑文件。

        agent1/sessions/<sessionId>/transcript.jsonl  聊天记录
        agent1/sessions/<sessionId>/meta.json         会话标题与时间
        agent1/sessions/<sessionId>/runs/            每次 Run 的落盘记录
        agent1/logs/events.jsonl                     结构化事件（模型、工具、用量）
        crash-reports/                               崩溃栈
        logcat.txt                                   本进程最近 logcat
        device.txt                                   机型、系统版本、模型名（不含 API Key）
        skipped.txt                                  因过大未打入的文件（若有）
    """.trimIndent() + "\n"
}

private object AndroidRuntimeNote {
    fun from(context: Context): String {
        val gateway = ProductivityGatewayProvider.get(context)
        val summary = gateway.configurationSummary()
        val error = gateway.configurationError()
        return buildString {
            appendLine("model: ${summary.modelId}")
            appendLine("baseUrl: ${summary.baseUrl}")
            appendLine("apiKeyConfigured: ${summary.isApiKeyConfigured}")
            appendLine(
                "limits: contextTurns=${summary.maxContextTurns} " +
                    "turnsPerRun=${summary.maxTurnsPerRun} toolCalls=${summary.maxToolCallsPerRun}",
            )
            if (!error.isNullOrBlank()) {
                appendLine("configError: $error")
            }
        }
    }
}
