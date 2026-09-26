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

/** 打包会话、事件日志、崩溃和 logcat，供分享给云端诊断。不含 API Key。 */
object DiagnosticExport {

    private const val CACHE_DIR = "diagnostics"
    private const val KEEP_ZIPS = 3

    fun exportZip(context: Context): File {
        val app = context.applicationContext
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val outDir = File(app.cacheDir, CACHE_DIR)
        pruneOldZips(outDir)
        val zip = File(outDir, "agent1-diag-$stamp.zip")
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

    private fun pruneOldZips(dir: File) {
        val zips = dir.listFiles { file -> file.isFile && file.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        zips.drop(KEEP_ZIPS - 1).forEach { it.delete() }
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
        生产力助手诊断包（手机一键导出）
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
