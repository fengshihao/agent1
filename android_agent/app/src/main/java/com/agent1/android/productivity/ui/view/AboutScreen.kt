package com.agent1.android.productivity.ui.view

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.agent1.android.BuildConfig

/** 关于：版本、作者，以及随包分发的开源库协议。 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        AgentTopBar(
            title = "关于",
            subtitle = null,
            leading = {
                TopBarIconButton(
                    icon = Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
            },
            actions = {},
        )
        AgentHairline()
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Text("Agent1", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                AboutRow("作者", "冯世浩 fengshihao")
                AboutLink("官网", "https://fengshihao.github.io/agent1/") {
                    openLink(context, "https://fengshihao.github.io/agent1/")
                }
                AboutLink("开源地址", "https://github.com/fengshihao/agent1") {
                    openLink(context, "https://github.com/fengshihao/agent1")
                }
                AboutRow("本软件", "MIT")
                Text(
                    "开源依赖",
                    modifier = Modifier.padding(top = 28.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "这些库随安装包分发。Apache-2.0 与 MIT 都要求保留版权声明和许可证名称。",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                dependencyNotices(BuildConfig.WEIZHI_INTEGRATED).forEach { notice ->
                    AboutRow(notice.name, notice.license)
                }
            }
        }
    }
}

private data class DependencyNotice(
    val name: String,
    val license: String,
)

/** 只列打进 APK 的库，不列单测依赖。协议以各库公开发布的许可证为准。 */
private fun dependencyNotices(weizhiIntegrated: Boolean): List<DependencyNotice> {
    val notices = mutableListOf(
        DependencyNotice("AndroidX（Compose、Lifecycle、Navigation 等）", "Apache-2.0"),
        DependencyNotice("Kotlin / kotlinx", "Apache-2.0"),
        DependencyNotice("OkHttp", "Apache-2.0"),
        DependencyNotice("Jackson", "Apache-2.0"),
        DependencyNotice("RxJava", "Apache-2.0"),
        DependencyNotice("Gson", "Apache-2.0"),
        DependencyNotice("Coil", "Apache-2.0"),
        DependencyNotice("multiplatform-markdown-renderer", "Apache-2.0"),
        DependencyNotice("desugar_jdk_libs", "Apache-2.0"),
    )
    if (weizhiIntegrated) {
        notices += DependencyNotice("微智 Weizhi", "Apache-2.0")
    }
    return notices
}

@Composable
private fun AboutRow(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AboutLink(label: String, url: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 16.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            url,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun openLink(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
}
