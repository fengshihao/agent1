package com.agent1.android

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.agent1.android.llm.CrashReporter
import com.agent1.android.productivity.logic.data.PublicCrashExport
import com.agent1.android.productivity.ui.view.ProductivityTheme

/**
 * 轻量入口：不加载聊天/Weizhi，用于主界面启动即崩时查看并复制上次崩溃栈。
 */
class CrashLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val privateReport = CrashReporter.getLastCrash(this)
        val publicReport = PublicCrashExport.readMainAppMirror(this)
        val report = privateReport ?: publicReport
        val hint = PublicCrashExport.userVisiblePathHint()
        setContent {
            ProductivityTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val copied = remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("崩溃日志", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "主 App 若一打开就闪退，可在此复制日志发给诊断 Agent。\n" +
                                "文件副本：$hint",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (report.isNullOrBlank()) {
                            Text(
                                "暂无记录。请先打开主 App 触发一次崩溃，或从电脑执行 pull-crash-report.sh。",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        } else {
                            Text(
                                report,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = {
                                    copyToClipboard(report)
                                    copied.value = true
                                    Toast.makeText(this@CrashLogActivity, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (copied.value) "已复制" else "复制全部")
                            }
                            OutlinedButton(
                                onClick = {
                                    CrashReporter.clearAllReports(this@CrashLogActivity)
                                    recreate()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("清除记录")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("agent1_crash", text))
    }
}
