package com.agent1.android.productivity.ui.view

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agent1.android.productivity.logic.business.DeckExportComposer
import com.agent1.android.productivity.logic.business.DeckFitScript
import com.agent1.android.productivity.logic.business.DeckPagerScript
import com.agent1.javaagent.html.inspect.ElementSelectorBuilder
import com.agent1.javaagent.html.inspect.HtmlInspectParser
import com.agent1.javaagent.html.inspect.HtmlInspectScript
import com.agent1.javaagent.html.inspect.InspectFeedbackComposer
import com.agent1.javaagent.html.inspect.InspectedElement
import com.agent1.android.productivity.logic.business.UserFacingError
import com.agent1.android.productivity.logic.business.WorkspaceFileWatcher
import com.agent1.android.productivity.ui.viewmodel.ChatRunTimelineItem
import com.agent1.android.productivity.ui.viewmodel.ChatUiState
import com.agent1.android.productivity.ui.viewmodel.ChatViewModel
import com.agent1.android.productivity.ui.viewmodel.HtmlPreviewViewModel

/**
 * 会话 workspace 内 HTML 的 app 内置预览（不外跳外部浏览器）。
 *
 * deck 模式（21 号规划）：检测到 html2pptx 约定的 `.slide` 页时**默认进演示视角**
 * （[DeckPagerScript] 全屏翻页 + 整页 fit）；退出后为编辑视角（[DeckFitScript]
 * 垂直滚动适配 + 双导出按钮）。非 deck HTML 行为不变。
 *
 * 审查模式（REQ-121）：开关注入 [HtmlInspectScript]，tap 元素高亮并回传结构化信息；
 * 面板内直接输入修改意见，「反馈给 AI」**立即发送**（不跳回聊天页）。
 * 发送后留在预览页，AI 修改文件后经 [WorkspaceFileWatcher] 防抖 reload（REQ-123），
 * 形成「预览 → 点选 → 反馈 → AI 修改 → 热更新」闭环。
 *
 * 离开前台（返回对话 / 进后台）时经 [HtmlPreviewWebViewLifecycle] 先停 audio/video，
 * 再 blank 并 destroy，避免 WebView 被 Compose 短暂缓存时音乐继续播。
 */
@Composable
fun HtmlPreviewScreen(
    sessionId: String,
    relativePath: String,
    onBack: () -> Unit,
    /** 当前会话的 ChatViewModel（与聊天页同实例）；null 时无 AI 活动条、发送会失败。 */
    chatViewModel: ChatViewModel? = null,
    /** 直接发送草稿给当前会话 AI；返回是否成功（被拒如 isRunning/配置错误）。 */
    onSendFeedback: (draft: String) -> Boolean = { false },
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val vm: HtmlPreviewViewModel = viewModel(
        key = "html-preview-$sessionId-$relativePath",
        factory = previewViewModelFactory { HtmlPreviewViewModel(appContext, sessionId, relativePath) },
    )
    val state = vm.state
    val deckMode = state.isDeck
    // deck 默认演示视角（契约：打开即投屏级预览）；非 deck 恒 false。
    var fullscreen by remember { mutableStateOf(deckMode) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var inspectEnabled by remember { mutableStateOf(false) }
    var selectedElement by remember { mutableStateOf<InspectedElement?>(null) }
    var opinion by remember { mutableStateOf("") }
    var deckPage by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webViewHost by remember { mutableStateOf<HtmlPreviewWebViewHost?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()
    val chatState = chatViewModel?.state?.collectAsState()?.value
    val lifecycleOwner = LocalLifecycleOwner.current

    fun requestExport(editable: Boolean) {
        val draft = DeckExportComposer.compose(relativePath, editable)
        val ok = onSendFeedback(draft)
        Toast.makeText(
            context,
            if (ok) {
                "已请求导出，生成后产物会出现在对话里"
            } else {
                "发送失败：模型忙碌或配置有误，稍后再试"
            },
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun schedulePagerRefit(view: WebView?) {
        if (view == null) return
        // 沉浸式 insets 到位后再 fit，避免首帧黑屏/未缩放。
        view.postDelayed({ view.evaluateJavascript(DeckPagerScript.REFIT_JS, null) }, 120)
        view.postDelayed({ view.evaluateJavascript(DeckPagerScript.REFIT_JS, null) }, 360)
    }

    // 进入演示视角（21 号 T2）：关审查（编辑/演示互斥）→ 撤垂直适配 → 装横向翻页 pager。
    fun enterPresentation() {
        fullscreen = true
        deckPage = null
        if (inspectEnabled) {
            inspectEnabled = false
            webView?.evaluateJavascript(HtmlInspectScript.DISABLE_JS, null)
        }
        selectedElement = null
        webView?.evaluateJavascript(DeckFitScript.UNINSTALL_JS, null)
        webView?.evaluateJavascript(DeckPagerScript.INSTALL_JS, null)
        schedulePagerRefit(webView)
    }

    // 退回编辑视角：撤 pager → 恢复垂直滚动适配。
    fun exitPresentation() {
        fullscreen = false
        webView?.evaluateJavascript(DeckPagerScript.UNINSTALL_JS, null)
        webView?.evaluateJavascript(DeckFitScript.INSTALL_JS, null)
    }

    // 全屏演示时返回键先退全屏（再按才离预览页）。
    BackHandler(enabled = fullscreen) { exitPresentation() }

    // 全屏隐藏系统栏（沉浸演示）+ 屏幕常亮（投屏不熄屏）；退出或离开组合时恢复。
    val rootView = LocalView.current
    DisposableEffect(fullscreen, rootView) {
        val window = (rootView.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, rootView) }
        if (fullscreen) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        rootView.keepScreenOn = fullscreen
        onDispose {
            rootView.keepScreenOn = false
            if (fullscreen) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
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
        containerColor = MaterialTheme.colorScheme.background,
        // 全屏演示：内容顶到屏幕物理边缘（topBar/bottomBar 也一并隐藏）。
        contentWindowInsets = if (fullscreen) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            if (!fullscreen) {
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
                            if (deckMode) {
                                TopBarIconButton(
                                    icon = AgentIcons.Fullscreen,
                                    contentDescription = "全屏演示",
                                    onClick = ::enterPresentation,
                                )
                            }
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
                                    opinion = ""
                                    webView?.evaluateJavascript(
                                        if (enable) HtmlInspectScript.ENABLE_JS else HtmlInspectScript.DISABLE_JS,
                                        null,
                                    )
                                },
                            )
                        },
                    )
                    AgentHairline()
                    // AI 工作过程可见：运行中显示当前动作（工具名/连接模型/思考），
                    // 点击状态条跳回聊天页看完整过程。
                    AnimatedVisibility(visible = chatState?.isRunning == true) {
                        val running = chatState
                        if (running != null) {
                            Column {
                                AgentHairline()
                                AiActivityStrip(state = running, onOpenChat = onBack)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            val element = selectedElement
            if (element != null && !fullscreen) {
                Column {
                    AgentHairline()
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                "已选中 <${element.tag}> · ${ElementSelectorBuilder.build(element)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            OutlinedTextField(
                                value = opinion,
                                onValueChange = { opinion = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                placeholder = {
                                    Text(
                                        "描述要修改的问题…",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                enabled = true,
                                maxLines = 4,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = {
                                        opinion = ""
                                        selectedElement = null
                                        webView?.evaluateJavascript(HtmlInspectScript.CLEAR_JS, null)
                                    },
                                ) {
                                    Text("取消")
                                }
                                Button(
                                    enabled = opinion.isNotBlank(),
                                    onClick = {
                                        val draft = InspectFeedbackComposer.compose(relativePath, element, opinion)
                                        val ok = onSendFeedback(draft)
                                        Toast.makeText(
                                            context,
                                            if (ok) {
                                                "已发送给 AI，修改后页面自动刷新"
                                            } else {
                                                "发送失败：模型忙碌或配置有误，稍后再试"
                                            },
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        if (ok) {
                                            opinion = ""
                                            selectedElement = null
                                            webView?.evaluateJavascript(HtmlInspectScript.CLEAR_JS, null)
                                        }
                                    },
                                ) {
                                    Text("反馈给 AI")
                                }
                            }
                        }
                    }
                }
            } else if (deckMode && !fullscreen) {
                // 编辑视角：双导出并排（可编辑 / 原样），决策在用户手里。
                Column {
                    AgentHairline()
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { requestExport(editable = true) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("导出可编辑版", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                onClick = { requestExport(editable = false) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("导出原样版", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            // 热更新（REQ-123）：AI 在会话中改写该文件 → 防抖后自动 reload，无需返回重进。
            DisposableEffect(file, scope) {
                val watcher = WorkspaceFileWatcher(
                    file = file,
                    scope = scope,
                    onChanged = {
                        loadError = null
                        webView?.reload()
                    },
                )
                watcher.start()
                onDispose { watcher.stop() }
            }
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
                            if (deckMode) {
                                // deck 演示桥：只回传页码（page/total），无 eval 能力。
                                addJavascriptInterface(
                                    object : Any() {
                                        @JavascriptInterface
                                        fun onPageChanged(json: String) {
                                            val parsed = DeckPagerScript.parsePageEvent(json)
                                            if (parsed != null) {
                                                mainHandler.post { deckPage = parsed }
                                            }
                                        }
                                    },
                                    DeckPagerScript.BRIDGE_NAME,
                                )
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    // 页面（含跳转/刷新/热更新 reload）就绪后重装脚本；开关状态保持。
                                    view.evaluateJavascript(HtmlInspectScript.INSTALL_JS, null)
                                    if (inspectEnabled) {
                                        view.evaluateJavascript(HtmlInspectScript.ENABLE_JS, null)
                                    }
                                    if (deckMode && fullscreen) {
                                        // 演示视角：重建 pager 并回到当前页（热更新不打断观看）。
                                        view.evaluateJavascript(DeckPagerScript.INSTALL_JS, null)
                                        deckPage?.first?.let { page ->
                                            view.evaluateJavascript(DeckPagerScript.goToJs(page), null)
                                        }
                                        schedulePagerRefit(view)
                                    } else if (deckMode) {
                                        // 编辑视角：缩放适配（.slide 包 wrapper + scale，幂等）。
                                        view.evaluateJavascript(DeckFitScript.INSTALL_JS, null)
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError,
                                ) {
                                    if (request.isForMainFrame) {
                                        loadError = UserFacingError.explain(error.description?.toString())
                                    }
                                }
                            }
                            loadUrl(file.toURI().toString())
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
                loadError?.let { message ->
                    PreviewErrorBox(message, Modifier.fillMaxSize())
                }
                // 全屏演示：左右点击区翻页 + 底部页码 + 右上角半透明退出（返回键同效）。
                if (fullscreen) {
                    // 左右各 1/3 屏点击翻页；中间不消费触摸，穿透给 WebView（滑动本就翻页）。
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { webView?.evaluateJavascript(DeckPagerScript.PREV_JS, null) },
                        )
                        Box(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { webView?.evaluateJavascript(DeckPagerScript.NEXT_JS, null) },
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        deckPage?.let { (page, total) ->
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.45f),
                            ) {
                                Text(
                                    "$page/$total",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                )
                            }
                        }
                        // 演示中也可导出，不必先退出。
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1f),
                                onClick = { requestExport(editable = true) },
                            ) {
                                Text(
                                    "导出可编辑版",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1f),
                                onClick = { requestExport(editable = false) },
                            ) {
                                Text(
                                    "导出原样版",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.35f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                    ) {
                        IconButton(onClick = ::exitPresentation) {
                            Icon(
                                imageVector = AgentIcons.FullscreenExit,
                                contentDescription = "退出演示",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        } else {
            // resolve() 只有 error 或 file 两种结果；防御分支。
            PreviewErrorBox("无法预览：$relativePath", Modifier.fillMaxSize().padding(innerPadding))
        }
    }
}

@Composable
private fun AiActivityStrip(state: ChatUiState, onOpenChat: () -> Unit, modifier: Modifier = Modifier) {
    val tool = state.runTimeline.lastOrNull { it is ChatRunTimelineItem.ToolPart } as? ChatRunTimelineItem.ToolPart
    val label = when {
        tool != null && !tool.finished -> "正在执行 ${tool.toolName}…"
        !state.runActivityLabel.isNullOrBlank() -> state.runActivityLabel
        tool != null -> "正在执行 ${tool.toolName}…"
        state.streamingReasoning.isNotEmpty() -> "AI 正在思考…"
        else -> "AI 正在工作中…"
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenChat)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
            )
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "查看对话",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
