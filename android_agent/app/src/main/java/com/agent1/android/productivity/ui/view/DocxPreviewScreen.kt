package com.agent1.android.productivity.ui.view

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agent1.android.productivity.logic.business.DocxPreviewAssets
import com.agent1.android.productivity.logic.business.ProductivityToolCapabilities
import com.agent1.android.productivity.logic.business.DocxPreviewDocxOps
import com.agent1.android.productivity.logic.business.DocxPreviewFileReader
import com.agent1.android.productivity.logic.business.SessionWorkspacePaths
import com.agent1.android.productivity.logic.business.UserFacingError
import com.agent1.android.productivity.logic.business.WorkspaceFileWatcher
import com.agent1.android.productivity.ui.viewmodel.DocxPreviewViewModel
import com.agent1.javaagent.html.inspect.DocxInspectFeedbackComposer
import com.agent1.javaagent.html.inspect.DocxInspectScript
import com.agent1.javaagent.html.inspect.DocxPreviewAnnotateScript
import com.agent1.javaagent.html.inspect.ElementSelectorBuilder
import com.agent1.javaagent.html.inspect.HtmlInspectParser
import com.agent1.javaagent.html.inspect.HtmlInspectScript
import com.agent1.javaagent.html.inspect.InspectedElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * docx 内置预览：CDN shell + P1 审查草稿 + P2 块级写回（readDocx）。
 */
@Composable
fun DocxPreviewScreen(
    sessionId: String,
    relativePath: String,
    onBack: () -> Unit,
    onSendFeedback: (draft: String) -> Boolean = { false },
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val vm: DocxPreviewViewModel = viewModel(
        key = "docx-preview-$sessionId-$relativePath",
        factory = previewViewModelFactory { DocxPreviewViewModel(appContext, sessionId, relativePath) },
    )
    val state = vm.state
    val workspaceRoot = remember(sessionId) { SessionWorkspacePaths.workspaceRoot(appContext, sessionId) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var rendering by remember { mutableStateOf(true) }
    var inspectEnabled by remember { mutableStateOf(false) }
    var selectedElement by remember { mutableStateOf<InspectedElement?>(null) }
    var opinion by remember { mutableStateOf("") }
    var blockEditText by remember { mutableStateOf("") }
    var blockBold by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webViewHost by remember { mutableStateOf<HtmlPreviewWebViewLifecycle.Host?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    fun afterPageReady(view: WebView) {
        view.evaluateJavascript(DocxPreviewAnnotateScript.ANNOTATE_JS, null)
        view.evaluateJavascript(DocxInspectScript.INSTALL_JS, null)
        if (inspectEnabled) {
            view.evaluateJavascript(HtmlInspectScript.ENABLE_JS, null)
        }
        rendering = false
    }

    fun rerender(view: WebView?) {
        val target = view ?: return
        rendering = true
        loadError = null
        selectedElement = null
        opinion = ""
        blockEditText = ""
        target.evaluateJavascript(
            "window.__agent1DocxRender && window.Agent1Docx && " +
                "window.__agent1DocxRender(window.Agent1Docx.readDocxBase64());",
            null,
        )
    }

    DisposableEffect(webViewHost, lifecycleOwner) {
        val host = webViewHost ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> HtmlPreviewWebViewLifecycle.pause(host)
                Lifecycle.Event.ON_RESUME -> HtmlPreviewWebViewLifecycle.resume(host)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            HtmlPreviewWebViewLifecycle.pause(host)
        }
    }

    Scaffold(
        topBar = {
            Column {
                AgentTopBar(
                    title = relativePath.substringAfterLast('/').ifEmpty { relativePath },
                    subtitle = relativePath,
                    leading = {
                        TopBarIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            onClick = onBack,
                        )
                    },
                    actions = {
                        TopBarIconButton(
                            icon = if (inspectEnabled) AgentIcons.VisibilityOff else AgentIcons.Visibility,
                            contentDescription = if (inspectEnabled) "关闭审查" else "审查模式",
                            onClick = {
                                inspectEnabled = !inspectEnabled
                                selectedElement = null
                                webView?.evaluateJavascript(
                                    if (inspectEnabled) HtmlInspectScript.ENABLE_JS else HtmlInspectScript.DISABLE_JS,
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
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        val blockLabel = element.blockIndex?.let { "块 #$it · " } ?: ""
                        Text(
                            "${blockLabel}已选中 <${element.tag}> · ${ElementSelectorBuilder.build(element)}",
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        OutlinedTextField(
                            value = opinion,
                            onValueChange = { opinion = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            placeholder = { Text("描述问题，反馈给 AI…") },
                            maxLines = 3,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = {
                                selectedElement = null
                                opinion = ""
                                blockEditText = ""
                                webView?.evaluateJavascript(HtmlInspectScript.CLEAR_JS, null)
                            }) { Text("取消") }
                            Button(
                                enabled = opinion.isNotBlank(),
                                onClick = {
                                    val draft = DocxInspectFeedbackComposer.compose(relativePath, element, opinion)
                                    val ok = onSendFeedback(draft)
                                    Toast.makeText(
                                        context,
                                        if (ok) "已发送给 AI" else "发送失败",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    if (ok) {
                                        opinion = ""
                                        selectedElement = null
                                        webView?.evaluateJavascript(HtmlInspectScript.CLEAR_JS, null)
                                    }
                                },
                            ) { Text("反馈给 AI") }
                        }
                        if (element.blockIndex != null && ProductivityToolCapabilities.weizhiIntegrated) {
                            OutlinedTextField(
                                value = blockEditText,
                                onValueChange = { blockEditText = it },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                label = { Text("块正文（P2 直接保存）") },
                                maxLines = 4,
                            )
                            Row(
                                Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = blockBold,
                                    onClick = { blockBold = !blockBold },
                                    label = { Text("加粗") },
                                )
                            }
                            Button(
                                enabled = blockEditText.isNotBlank() || blockBold,
                                modifier = Modifier.padding(top = 8.dp),
                                onClick = {
                                    val idx = element.blockIndex ?: return@Button
                                    val root = workspaceRoot ?: return@Button
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            var last: String? = null
                                            if (blockEditText.isNotBlank()) {
                                                last = DocxPreviewDocxOps.setBlockText(
                                                    root,
                                                    relativePath,
                                                    idx,
                                                    blockEditText,
                                                )
                                            }
                                            if (blockBold) {
                                                last = DocxPreviewDocxOps.setBlockStyle(
                                                    root,
                                                    relativePath,
                                                    idx,
                                                    true,
                                                    null,
                                                )
                                            }
                                            last
                                        }
                                        val ok = result != null && result.contains("\"ok\"")
                                        Toast.makeText(
                                            context,
                                            if (ok) "已保存，正在刷新" else "保存失败（需联网安装 weizhi:docx）",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        if (ok) rerender(webView)
                                    }
                                },
                            ) { Text("应用并保存") }
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
            DisposableEffect(file, scope) {
                val watcher = WorkspaceFileWatcher(
                    file = file,
                    scope = scope,
                    onChanged = { rerender(webView) },
                )
                watcher.start()
                onDispose { watcher.stop() }
            }
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val docAbsolutePath = file.absolutePath
                        val wsRoot = workspaceRoot
                        val rel = relativePath
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            addJavascriptInterface(
                                object : Any() {
                                    @JavascriptInterface
                                    fun readDocxBase64(): String {
                                        val bytes = DocxPreviewFileReader.readBytes(docAbsolutePath)
                                        return Base64.encodeToString(bytes, Base64.NO_WRAP)
                                    }

                                    @JavascriptInterface
                                    fun getBlockTextViewJson(): String {
                                        val root = wsRoot ?: return "[]"
                                        return DocxPreviewDocxOps.textViewJson(root, rel) ?: "[]"
                                    }
                                },
                                DocxPreviewAssets.DOCX_BRIDGE_NAME,
                            )
                            addJavascriptInterface(
                                object : Any() {
                                    @JavascriptInterface
                                    fun onElementPicked(json: String) {
                                        val parsed = HtmlInspectParser.parse(json)
                                        mainHandler.post {
                                            selectedElement = parsed
                                            blockEditText = parsed?.textPreview ?: ""
                                        }
                                    }
                                },
                                HtmlInspectScript.BRIDGE_NAME,
                            )
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    afterPageReady(view)
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError,
                                ) {
                                    if (request.isForMainFrame) {
                                        loadError = UserFacingError.explain(error.description?.toString())
                                        rendering = false
                                    }
                                }
                            }
                            loadUrl(state.shellUrl)
                            webView = this
                            webViewHost = HtmlPreviewWebViewHost(this)
                        }
                    },
                    onRelease = { view ->
                        webView = null
                        val host = webViewHost ?: HtmlPreviewWebViewHost(view)
                        webViewHost = null
                        HtmlPreviewWebViewLifecycle.release(host)
                    },
                )
                loadError?.let { PreviewErrorBox(it, Modifier.fillMaxSize()) }
                if (rendering && loadError == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
