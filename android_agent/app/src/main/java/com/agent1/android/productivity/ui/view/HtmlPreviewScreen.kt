package com.agent1.android.productivity.ui.view

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agent1.android.productivity.logic.business.ElementSelectorBuilder
import com.agent1.android.productivity.logic.business.HtmlInspectParser
import com.agent1.android.productivity.logic.business.HtmlInspectScript
import com.agent1.android.productivity.logic.business.InspectFeedbackComposer
import com.agent1.android.productivity.logic.business.InspectedElement
import com.agent1.android.productivity.ui.viewmodel.HtmlPreviewViewModel

/**
 * 会话 workspace 内 HTML 的 app 内置预览（不外跳外部浏览器）。
 * 审查模式（Phase B / REQ-121）：开关注入 [HtmlInspectScript]，tap 元素高亮并回传结构化信息；
 * 点「反馈给 AI」组装草稿经 [onSendFeedback] 回填聊天输入框（不自动发送）。
 */
@Composable
fun HtmlPreviewScreen(
    sessionId: String,
    relativePath: String,
    onBack: () -> Unit,
    onSendFeedback: (draft: String) -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val vm: HtmlPreviewViewModel = viewModel(
        key = "html-preview-$sessionId-$relativePath",
        factory = previewViewModelFactory { HtmlPreviewViewModel(appContext, sessionId, relativePath) },
    )
    val state = vm.state
    var loadError by remember { mutableStateOf<String?>(null) }
    var inspectEnabled by remember { mutableStateOf(false) }
    var selectedElement by remember { mutableStateOf<InspectedElement?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

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
                    actions = {
                        TopBarIconButton(
                            icon = if (inspectEnabled) {
                                AgentIcons.VisibilityOff
                            } else {
                                AgentIcons.Visibility
                            },
                            contentDescription = if (inspectEnabled) "关闭审查模式" else "开启审查模式",
                            onClick = {
                                val enable = !inspectEnabled
                                inspectEnabled = enable
                                selectedElement = null
                                webView?.evaluateJavascript(
                                    if (enable) HtmlInspectScript.ENABLE_JS else HtmlInspectScript.DISABLE_JS,
                                    null,
                                )
                            },
                        )
                    },
                )
                AgentHairline()
            }
        },
        bottomBar = {
            val element = selectedElement
            if (element != null) {
                Column {
                    AgentHairline()
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                "已选中 <${element.tag}>",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val selector = ElementSelectorBuilder.build(element)
                            Text(
                                selector,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            if (element.outerHtml.isNotEmpty()) {
                                Text(
                                    element.outerHtml,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = {
                                        selectedElement = null
                                        webView?.evaluateJavascript(HtmlInspectScript.CLEAR_JS, null)
                                    },
                                ) {
                                    Text("清除")
                                }
                                Button(
                                    onClick = {
                                        onSendFeedback(InspectFeedbackComposer.compose(relativePath, element))
                                        selectedElement = null
                                    },
                                ) {
                                    Text("反馈给 AI")
                                }
                            }
                        }
                    }
                }
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
                            addJavascriptInterface(
                                object : Any() {
                                    // JS 桥线程回调 → 主线程更新 Compose 状态。
                                    // 只回传元素描述，不提供 eval / 文件 / 网络能力。
                                    @JavascriptInterface
                                    fun onElementPicked(json: String) {
                                        val parsed = HtmlInspectParser.parse(json)
                                        mainHandler.post {
                                            selectedElement = parsed
                                        }
                                    }
                                },
                                HtmlInspectScript.BRIDGE_NAME,
                            )
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    // 页面（含跳转/刷新）就绪后重装脚本；开关状态保持。
                                    view.evaluateJavascript(HtmlInspectScript.INSTALL_JS, null)
                                    if (inspectEnabled) {
                                        view.evaluateJavascript(HtmlInspectScript.ENABLE_JS, null)
                                    }
                                }

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
                            webView = this
                        }
                    },
                    onRelease = {
                        webView = null
                        it.destroy()
                    },
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