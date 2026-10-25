package com.agent1.android.productivity.ui.view

import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agent1.android.llm.AppFileLog
import com.agent1.android.productivity.logic.business.MolanPreviewFileReader
import com.agent1.android.productivity.logic.business.MolanPreviewShellLoader
import com.agent1.android.productivity.logic.business.SessionWorkspacePaths
import com.agent1.android.productivity.logic.business.UserFacingError
import com.agent1.android.productivity.logic.business.WorkspaceFileWatcher
import com.agent1.android.productivity.ui.viewmodel.ChatViewModel
import com.agent1.android.productivity.ui.viewmodel.MolanPreviewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 壳内注入桥名称（壳 HTML 侧 window.Agent1Molan）。 */
private const val MOLAN_BRIDGE_NAME = "Agent1Molan"

/**
 * 墨览（molan）Markdown 内置预览：壳 HTML 在 APK（assets），编辑器与
 * vditor 大库引用线上 try 页（与 docx 壳模式一致）；md 文本经
 * [MOLAN_BRIDGE_NAME] 桥注入 [window.MolanHost]（molan#18）。
 *
 * 打开即阅读（纸面视图）；文件变更后经 [WorkspaceFileWatcher] 自动重渲染。
 * 预览为只读语义：墨览内的保存是下载副本，不写回 workspace。
 */
@Composable
fun MolanPreviewScreen(
    sessionId: String,
    relativePath: String,
    onBack: () -> Unit,
    /** 与聊天页同实例；用于顶栏 AI 状态条。 */
    chatViewModel: ChatViewModel? = null,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val vm: MolanPreviewViewModel = viewModel(
        key = "molan-preview-$sessionId-$relativePath",
        factory = previewViewModelFactory { MolanPreviewViewModel(appContext, sessionId, relativePath) },
    )
    val state = vm.state
    val workspaceRoot = remember(sessionId) { SessionWorkspacePaths.workspaceRoot(appContext, sessionId) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var rendering by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webViewHost by remember { mutableStateOf<HtmlPreviewWebViewLifecycle.Host?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    fun rerender(view: WebView?) {
        view?.evaluateJavascript("window.__agent1MolanRenderFromBridge && window.__agent1MolanRenderFromBridge()", null)
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
                    actions = { },
                )
                AgentHairline()
                val chatState = chatViewModel?.state?.collectAsState()?.value
                androidx.compose.animation.AnimatedVisibility(visible = chatState?.isRunning == true) {
                    if (chatState != null) {
                        Column {
                            AgentHairline()
                            PreviewAiActivityStrip(state = chatState, onOpenChat = onBack)
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val file = state.file
        when {
            state.error != null -> PreviewErrorBox(state.error.orEmpty(), Modifier.fillMaxSize().padding(innerPadding))
            file != null -> {
                DisposableEffect(file) {
                    val watcher = WorkspaceFileWatcher(
                        file = file,
                        scope = scope,
                        onChanged = { rerender(webView) },
                    )
                    watcher.start()
                    onDispose { watcher.stop() }
                }
                LaunchedEffect(webView, file) {
                    val view = webView ?: return@LaunchedEffect
                    rendering = true
                    loadError = null
                    val html = withContext(Dispatchers.IO) { MolanPreviewShellLoader.loadHtml(appContext) }
                    if (html.isNullOrBlank()) {
                        loadError = "无法加载墨览预览壳"
                        rendering = false
                        AppFileLog.i("MolanPreview", "shell asset missing")
                        return@LaunchedEffect
                    }
                    AppFileLog.i("MolanPreview", "loadDataWithBaseURL base=${MolanPreviewShellLoader.BASE_URL} bytes=${html.length}")
                    view.loadDataWithBaseURL(MolanPreviewShellLoader.BASE_URL, html, "text/html", "UTF-8", null)
                }
                Box(Modifier.fillMaxSize().padding(innerPadding)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val docAbsolutePath = file.absolutePath
                            val displayName = relativePath.substringAfterLast('/').ifEmpty { "document.md" }
                            WebView(ctx).apply {
                                setBackgroundColor(Color.parseColor("#141C18"))
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = true
                                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                settings.loadsImagesAutomatically = true
                                settings.useWideViewPort = false
                                settings.loadWithOverviewMode = false
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    @Suppress("DEPRECATION")
                                    settings.forceDark = WebSettings.FORCE_DARK_OFF
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    settings.isAlgorithmicDarkeningAllowed = false
                                }
                                addJavascriptInterface(
                                    object : Any() {
                                        @JavascriptInterface
                                        fun readMdJson(): String {
                                            return MolanPreviewFileReader.readMdJson(docAbsolutePath, displayName)
                                        }
                                    },
                                    MOLAN_BRIDGE_NAME,
                                )
                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                                        Log.d(
                                            "MolanPreview",
                                            "${message?.message()} -- ${message?.sourceId()}:${message?.lineNumber()}",
                                        )
                                        return true
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView, url: String?) {
                                        // 壳脚本自身轮询 MolanHost 就绪后注入，这里只收尾 loading 态。
                                        view.postDelayed({ rendering = false }, 1_500L)
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        error: WebResourceError,
                                    ) {
                                        val url = request.url?.toString() ?: ""
                                        if (request.isForMainFrame || url.contains("/try/vendor/")) {
                                            loadError = UserFacingError.explain(error.description?.toString())
                                            rendering = false
                                        }
                                    }
                                }
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
}
