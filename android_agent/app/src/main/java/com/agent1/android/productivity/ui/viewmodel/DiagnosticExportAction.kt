package com.agent1.android.productivity.ui.viewmodel

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent1.android.productivity.logic.business.BriefChatExport
import com.agent1.android.productivity.logic.business.DiagnosticExport
import com.agent1.android.productivity.logic.business.ProductivityGatewayProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun ViewModel.launchDiagnosticExport(
    activity: Context,
    onBusy: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
) {
    onBusy(true)
    onMessage("正在打包日志和聊天记录…")
    viewModelScope.launch {
        val zip = runCatching {
            withContext(Dispatchers.IO) {
                DiagnosticExport.exportZip(activity.applicationContext)
            }
        }
        zip.fold(
            onSuccess = { file ->
                val shared = runCatching { activity.startActivity(diagnosticShareIntent(activity, file)) }
                onBusy(false)
                onMessage(
                    if (shared.isSuccess) {
                        "已打开分享，把压缩包发给诊断 Agent"
                    } else {
                        "已打包，但无法打开分享：${shared.exceptionOrNull()?.message ?: "未知错误"}"
                    },
                )
            },
            onFailure = { error ->
                onBusy(false)
                onMessage("导出失败：${error.message ?: error.javaClass.simpleName}")
            },
        )
    }
}

internal fun ViewModel.launchBriefChatExport(
    activity: Context,
    sessionId: String,
    sessionTitle: String,
    onBusy: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
) {
    onBusy(true)
    onMessage("正在整理简略聊天记录…")
    viewModelScope.launch {
        val text = runCatching {
            withContext(Dispatchers.IO) {
                val gateway = ProductivityGatewayProvider.get(activity.applicationContext)
                val messages = gateway.loadTranscript(sessionId)
                BriefChatExport.format(messages, sessionTitle)
            }
        }
        text.fold(
            onSuccess = { body ->
                val shared = runCatching {
                    activity.startActivity(briefChatShareIntent(activity, body, sessionTitle))
                }
                onBusy(false)
                onMessage(
                    if (shared.isSuccess) {
                        "已打开分享，选「复制」或发给自己即可粘贴到 Cursor"
                    } else {
                        "整理完成，但无法打开分享：${shared.exceptionOrNull()?.message ?: "未知错误"}"
                    },
                )
            },
            onFailure = { error ->
                onBusy(false)
                onMessage("导出失败：${error.message ?: error.javaClass.simpleName}")
            },
        )
    }
}

internal fun briefChatShareIntent(context: Context, text: String, sessionTitle: String): Intent {
    val subject = sessionTitle.ifBlank { "聊天简报" }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, "agent1 $subject")
    }
    return Intent.createChooser(send, "分享聊天简报")
}

internal fun diagnosticShareIntent(context: Context, zip: File): Intent {
    val authority = "${context.packageName}.fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, zip)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "agent1 诊断包 ${zip.name}")
        putExtra(
            Intent.EXTRA_TEXT,
            "手机测试诊断包，内含会话记录、events.jsonl、崩溃报告和 logcat。",
        )
        clipData = ClipData.newRawUri("agent1-diag", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, "发送诊断包").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
