package com.dynamicui.demo.productivity.ui.viewmodel

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicui.demo.productivity.logic.business.DiagnosticExport
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
