package com.agent1.android.productivity.ui.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun CrashLogScreen(
    report: String?,
    filePathHint: String,
    modifier: Modifier = Modifier,
    showContinueToApp: Boolean = false,
    onContinueToApp: (() -> Unit)? = null,
    onClearReports: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val copied = remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("上次崩溃日志", style = MaterialTheme.typography.headlineSmall)
        Text(
            "请点「复制全部」，粘贴发给排查问题的 Agent。\n" +
                "也可在文件管理器中打开：\n$filePathHint",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (report.isNullOrBlank()) {
            Text(
                "暂无记录。若 App 反复闪退，请先再打开一次主 App（让崩溃写入磁盘），或查看上述下载目录。",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (showContinueToApp && onContinueToApp != null) {
                Button(onClick = onContinueToApp, modifier = Modifier.fillMaxWidth()) {
                    Text("进入 App")
                }
            }
        } else {
            Text(
                report,
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    copyCrashToClipboard(context, report)
                    copied.value = true
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (copied.value) "已复制" else "复制全部")
            }
            if (showContinueToApp && onContinueToApp != null) {
                OutlinedButton(onClick = onContinueToApp, modifier = Modifier.fillMaxWidth()) {
                    Text("仍要进入 App（可能再次闪退）")
                }
            }
            if (onClearReports != null) {
                OutlinedButton(onClick = onClearReports, modifier = Modifier.fillMaxWidth()) {
                    Text("清除记录")
                }
            }
        }
    }
}

private fun copyCrashToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("agent1_crash", text))
}
