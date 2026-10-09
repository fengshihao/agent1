package com.agent1.android.productivity.ui.view

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agent1.android.productivity.ui.viewmodel.HtmlPreviewViewModel

/**
 * 会话 workspace 内 HTML 的 app 内置预览（不外跳外部浏览器）。
 * 设计：doc/规划/自进化Agent/18-内置HTML预览与点选审查.md（Phase A）。
 */
@Composable
fun HtmlPreviewScreen(
    sessionId: String,
    relativePath: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val vm: HtmlPreviewViewModel = viewModel(
        key = "html-preview-$sessionId-$relativePath",
        factory = previewViewModelFactory { HtmlPreviewViewModel(appContext, sessionId, relativePath) },
    )
    val state = vm.state
    var loadError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                AgentTopBar(
                    title = relativePath.substringAfterLast('/').ifEmpty { relativePath },
                    subtitle = relativePath,
                    leading = {
                        TopBarIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回对话",
                            onClick = onBack,
                        )
                    },
                    actions = {},
                )
                AgentHairline()
            }
        },
    ) { innerPadding ->
        val file = state.file
        if (state.error != null) {
            PreviewErrorBox(state.error.orEmpty(), Modifier.fillMaxSize().padding(innerPadding))
        } else if (file != null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // app 私有目录内 file:// 加载需显式允许（API 30+ 默认 false）。
                            settings.allowFileAccess = true
                            // allowFileAccessFromFileURLs / allowUniversalAccessFromFileURLs
                            // 保持默认 false：file:// 页面不得读任意文件与跨源。
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError,
                                ) {
                                    if (request.isForMainFrame) {
                                        loadError = "页面加载失败：${error.description}"
                                    }
                                }
                            }
                            loadUrl(file.toURI().toString())
                        }
                    },
                    onRelease = { it.destroy() },
                )
                loadError?.let { message ->
                    PreviewErrorBox(message, Modifier.fillMaxSize())
                }
            }
        } else {
            // resolve() 只有 error 或 file 两种结果；防御分支。
            PreviewErrorBox("无法预览：$relativePath", Modifier.fillMaxSize().padding(innerPadding))
        }
    }
}

@Composable
private fun PreviewErrorBox(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            message,
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun <T : androidx.lifecycle.ViewModel> previewViewModelFactory(
    create: () -> T,
): androidx.lifecycle.ViewModelProvider.Factory {
    return object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : androidx.lifecycle.ViewModel> create(modelClass: Class<V>): V = create() as V
    }
}