package com.agent1.android.productivity.ui.view

import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import com.agent1.android.productivity.logic.business.HtmlPreviewNavigation
import com.agent1.android.productivity.logic.business.MolanPreviewFileReader
import com.agent1.android.productivity.logic.business.UserFacingError
import com.agent1.android.productivity.logic.business.WorkspaceFileWatcher
import com.agent1.android.productivity.ui.viewmodel.ChatViewModel
import com.agent1.android.productivity.ui.viewmodel.MolanPreviewViewModel
import org.json.JSONObject
import org.json.JSONTokener

/** 壳内注入桥名称（壳 HTML 侧 window.Agent1Molan）。 */
private const val MOLAN_BRIDGE_NAME = "Agent1Molan"

private data class MolanInjectDiag(
    val shellErr: String,
    val r: String,
    val ready: Boolean,
    val htmlLen: Int,
    val bodyH: Int,
)

/** [WebView.evaluateJavascript] 回调里是 JSON 编码字符串，不能直接用 contains 匹配。 */
private fun parseMolanInjectDiag(raw: String?): MolanInjectDiag? {
    if (raw.isNullOrBlank() || raw == "null") return null
    val body = try {
        val tok = JSONTokener(raw.trim())
        when (val first = tok.nextValue()) {
            is String -> first
            is JSONObject -> first.toString()
            else -> return null
        }
    } catch (_: Exception) {
        return null
    }
    return try {
        val o = JSONObject(body)
        MolanInjectDiag(
            shellErr = o.optString("shellErr"),
            r = o.optString("r"),
            ready = o.optBoolean("ready"),
            htmlLen = o.optInt("htmlLen"),
            bodyH = o.optInt("bodyH"),
        )
    } catch (_: Exception) {
        null
    }
}

private val MOLAN_INJECT_DIAG_JS =
    "(function(){var el=document.getElementById('agent1MdError');" +
        "var shellErr=(el&&!el.hidden)?String(el.textContent||''):'';" +
        "var body=document.getElementById('molanPreviewBody');" +
        "var rb=document.getElementById('readerBody');" +
        "var htmlLen=body?body.innerHTML.length:0;" +
        "var bodyH=rb?Math.round(rb.getBoundingClientRect().height):" +
        "(body?Math.round(body.getBoundingClientRect().height):0);" +
        "var ready=!!(window.MolanHost&&window.MolanHost.isReady&&window.MolanHost.isReady());" +
        "return JSON.stringify({shellErr:shellErr,r:'diag',ready:ready,htmlLen:htmlLen,bodyH:bodyH});})()"

/**
 * 墨览 Markdown 内置预览：embed shell 走官方 CDN（{@code OfficialCdn}）；
 * md 文本经 [MOLAN_BRIDGE_NAME] 桥注入 [window.MolanHost]。
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
    var loadError by remember { mutableStateOf<String?>(null) }
    var rendering by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webViewHost by remember { mutableStateOf<HtmlPreviewWebViewLifecycle.Host?>(null) }
    var lastShellLoadKey by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun rerender(view: WebView?) {
        view?.evaluateJavascript("window.__agent1MolanRenderFromBridge && window.__agent1MolanRenderFromBridge()", null)
    }

    fun applyInjectDiag(raw: String?) {
        val diag = parseMolanInjectDiag(raw) ?: return
        mainHandler.post {
            if (diag.shellErr.isNotBlank() && loadError == null) {
                loadError = diag.shellErr
                rendering = false
                return@post
            }
            val painted = diag.htmlLen > 20 && diag.bodyH > 120
            if (painted) {
                AppFileLog.i(
                    "MolanPreview",
                    "dismiss spinner htmlLen=${diag.htmlLen} bodyH=${diag.bodyH}",
                )
                rendering = false
            }
        }
    }

    fun logMolanInject(view: WebView, delayMs: Long) {
        mainHandler.postDelayed({
            view.evaluateJavascript(MOLAN_INJECT_DIAG_JS) { raw ->
                AppFileLog.i("MolanPreview", "inject=$raw")
                applyInjectDiag(raw)
            }
        }, delayMs)
    }

    fun afterPageReady(view: WebView) {
        rerender(view)
        logMolanInject(view, 1_500L)
        logMolanInject(view, 4_000L)
        mainHandler.postDelayed({
            if (rendering && loadError == null) {
                rendering = false
            }
        }, 8_000L)
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
                LaunchedEffect(webView, state.shellUrl, file.absolutePath) {
                    val view = webView ?: return@LaunchedEffect
                    val loadKey = "${view.hashCode()}:${file.absolutePath}:${state.shellUrl}"
                    if (lastShellLoadKey == loadKey) {
                        return@LaunchedEffect
                    }
                    lastShellLoadKey = loadKey
                    rendering = true
                    loadError = null
                    AppFileLog.i("MolanPreview", "loadUrl ${state.shellUrl} file=${file.name}")
                    view.loadUrl(state.shellUrl)
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
                                // host-web 目录 immutable 长缓存；壳 ?v= 与资源 ?v= 负责刷新，
                                // 开发迭代时与 docx 预览一致禁用 HTTP 缓存，避免旧 CSS 粘住。
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

                                        @JavascriptInterface
                                        fun onContentReady() {
                                            mainHandler.post {
                                                AppFileLog.i("MolanPreview", "onContentReady")
                                                webView?.let { logMolanInject(it, 400L) }
                                            }
                                        }
                                    },
                                    MOLAN_BRIDGE_NAME,
                                )
                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                                        AppFileLog.i(
                                            "MolanPreview",
                                            "js: ${message?.message()} @ ${message?.sourceId()}:${message?.lineNumber()}",
                                        )
                                        return true
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        return HtmlPreviewNavigation.consume(view.context, request.url?.toString())
                                    }

                                    @Deprecated("API 24+")
                                    override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
                                        return HtmlPreviewNavigation.consume(view.context, url)
                                    }

                                    override fun onPageFinished(view: WebView, url: String?) {
                                        afterPageReady(view)
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        error: WebResourceError,
                                    ) {
                                        val url = request.url?.toString() ?: ""
                                        if (request.isForMainFrame || url.contains("/molan-embed/")) {
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
                            lastShellLoadKey = null
                            val host = webViewHost ?: HtmlPreviewWebViewHost(view)
                            webViewHost = null
                            HtmlPreviewWebViewLifecycle.release(host)
                        },
                    )
                    loadError?.let { PreviewErrorBox(it, Modifier.fillMaxSize()) }
                    if (rendering && loadError == null) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
