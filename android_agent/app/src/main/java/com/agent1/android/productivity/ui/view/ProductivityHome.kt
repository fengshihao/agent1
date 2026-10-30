package com.agent1.android.productivity.ui.view

import android.content.Context
import android.widget.Toast
import com.agent1.android.R
import com.agent1.android.productivity.logic.business.i18n.AppLocalization
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agent1.android.productivity.logic.business.IncomingSharePayload
import com.agent1.android.productivity.logic.business.IncomingShareQueue
import com.agent1.android.productivity.ui.viewmodel.ChatViewModel
import com.agent1.android.productivity.ui.viewmodel.SessionListViewModel
import com.agent1.javaagent.session.SessionMeta
import kotlinx.coroutines.launch

/**
 * 生产力主界面：抽屉（会话列表）+ 当前会话聊天区。
 * 原先与各聊天组件同在 ProductivityScreens.kt，按屏幕拆分后这里只保留宿主与 ChatPane 装配。
 */
@Composable
fun ProductivityHome(
    sessionListViewModel: SessionListViewModel,
    onOpenSettings: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onOpenSystemPrompt: (String) -> Unit,
    /** 打开 app 内置 HTML 预览（sessionId + workspace 相对路径）。 */
    onOpenHtmlPreview: (sessionId: String, relativePath: String) -> Unit,
    /** 打开产物库；sessionId 为空表示当前没有活动会话（落位页）。 */
    onOpenArtifacts: (sessionId: String) -> Unit = { _ -> },
    onOpenAbout: () -> Unit = {},
    onOpenToolTrace: (String) -> Unit = { _ -> },
    onOpenHostCaps: () -> Unit = {},
) {
    val listState by sessionListViewModel.state.collectAsState()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var activeSessionId by rememberSaveable { mutableStateOf("") }
    fun defaultChatTitle(): String = AppLocalization.getString(R.string.chat_new_session)
    var activeTitle by rememberSaveable { mutableStateOf(defaultChatTitle()) }
    var createRequested by rememberSaveable { mutableStateOf(false) }
    var sessionLoading by remember { mutableStateOf(false) }
    var sessionFresh by remember { mutableStateOf(false) }
    var pendingIncomingShare by remember { mutableStateOf<IncomingSharePayload?>(null) }
    val reportSessionFreshness = remember {
        { loading: Boolean, fresh: Boolean ->
            if (sessionLoading != loading) sessionLoading = loading
            if (sessionFresh != fresh) sessionFresh = fresh
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sessionListViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(listState.isLoading, listState.sessions, listState.startupError) {
        if (listState.isLoading) return@LaunchedEffect
        val current = activeSessionId
        if (current.isNotBlank() && listState.sessions.any { it.sessionId == current }) {
            val match = listState.sessions.first { it.sessionId == current }
            val titled = match.title.ifBlank { defaultChatTitle() }
            if (titled != activeTitle) activeTitle = titled
            return@LaunchedEffect
        }
        if (listState.startupError != null && listState.sessions.isEmpty()) return@LaunchedEffect
        val next = listState.sessions.maxByOrNull { it.updatedAt }
        if (next != null) {
            activeSessionId = next.sessionId
            activeTitle = next.title.ifBlank { defaultChatTitle() }
            return@LaunchedEffect
        }
        if (!createRequested) {
            createRequested = true
            sessionListViewModel.createSession { meta ->
                activeSessionId = meta.sessionId
                activeTitle = meta.title.ifBlank { defaultChatTitle() }
            }
        }
    }

    LaunchedEffect(Unit) {
        IncomingShareQueue.updates.collect {
            IncomingShareQueue.poll()?.let { pendingIncomingShare = it }
        }
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    fun toggleDrawer() {
        if (!drawerState.isOpen) {
            sessionListViewModel.refreshUsage()
        }
        scope.launch {
            if (drawerState.isOpen) drawerState.close() else drawerState.open()
        }
    }

    fun openSession(meta: SessionMeta) {
        activeSessionId = meta.sessionId
        activeTitle = meta.title.ifBlank { defaultChatTitle() }
        closeDrawer()
    }

    fun newChat() {
        closeDrawer()
        if (activeSessionId.isNotBlank()) {
            if (sessionLoading) return
            if (sessionFresh) {
                Toast.makeText(
                    context,
                    AppLocalization.getString(R.string.chat_already_new_session),
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
        }
        sessionListViewModel.createSession { meta ->
            activeSessionId = meta.sessionId
            activeTitle = meta.title.ifBlank { defaultChatTitle() }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    val drawerShape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp)
    val drawerGlow = MaterialTheme.colorScheme.primary
    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(252.dp)
                    .fillMaxHeight()
                    .shadow(
                        elevation = 10.dp,
                        shape = drawerShape,
                        clip = false,
                        ambientColor = drawerGlow.copy(alpha = 0.14f),
                        spotColor = drawerGlow.copy(alpha = 0.20f),
                    ),
                drawerShape = drawerShape,
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 0.dp,
            ) {
                Box(modifier = Modifier.fillMaxSize().drawerEdgeGlow()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SessionDrawer(
                            state = listState,
                            activeSessionId = activeSessionId,
                            onNewChat = { newChat() },
                            onOpenSession = { openSession(it) },
                            onDeleteSession = { meta ->
                                sessionListViewModel.deleteSession(meta.sessionId) { remaining ->
                                    if (activeSessionId != meta.sessionId) return@deleteSession
                                    val next = remaining.maxByOrNull { it.updatedAt }
                                    if (next != null) {
                                        activeSessionId = next.sessionId
                                        activeTitle = next.title.ifBlank { defaultChatTitle() }
                                    } else {
                                        createRequested = false
                                        activeSessionId = ""
                                        activeTitle = defaultChatTitle()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
    ) {
        val sessionId = activeSessionId
        if (sessionId.isBlank()) {
            ChatLanding(
                startupError = listState.startupError,
                exportInProgress = listState.exportInProgress,
                onOpenDrawer = { toggleDrawer() },
                onOpenSettings = onOpenSettings,
                onOpenMcp = onOpenMcp,
                onOpenCapabilities = onOpenCapabilities,
                onExportDiagnostics = { sessionListViewModel.exportDiagnostics(context) },
                onOpenArtifacts = { onOpenArtifacts("") },
                onOpenAbout = onOpenAbout,
                onOpenHostCaps = onOpenHostCaps,
                modelLabel = composerModelLabel(listState.configSummary),
            )
        } else {
            ChatPane(
                appContext = appContext,
                sessionId = sessionId,
                title = activeTitle,
                incomingSharePayload = pendingIncomingShare,
                onIncomingShareConsumed = { pendingIncomingShare = null },
                onOpenDrawer = { toggleDrawer() },
                onNewChat = { newChat() },
                onOpenSettings = onOpenSettings,
                onOpenMcp = onOpenMcp,
                onOpenCapabilities = onOpenCapabilities,
                onOpenSystemPrompt = { onOpenSystemPrompt(sessionId) },
                onOpenHtmlPreview = onOpenHtmlPreview,
                onOpenArtifacts = { onOpenArtifacts(sessionId) },
                onOpenAbout = onOpenAbout,
                onOpenToolTrace = { onOpenToolTrace(sessionId) },
                onOpenHostCaps = onOpenHostCaps,
                onSessionFreshness = reportSessionFreshness,
            )
        }
    }
}

@Composable
private fun ChatPane(
    appContext: Context,
    sessionId: String,
    title: String,
    incomingSharePayload: IncomingSharePayload? = null,
    onIncomingShareConsumed: () -> Unit = {},
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onOpenSystemPrompt: () -> Unit,
    onOpenHtmlPreview: (sessionId: String, relativePath: String) -> Unit,
    onOpenArtifacts: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenToolTrace: () -> Unit,
    onOpenHostCaps: () -> Unit,
    onSessionFreshness: (loading: Boolean, fresh: Boolean) -> Unit,
) {
    val viewModel: ChatViewModel = viewModel(
        key = "chat-$sessionId",
        factory = simpleViewModelFactory { ChatViewModel(appContext, sessionId, title) },
    )
    LaunchedEffect(incomingSharePayload) {
        val payload = incomingSharePayload ?: return@LaunchedEffect
        viewModel.importIncomingShareFromHost(payload)
        onIncomingShareConsumed()
    }
    ChatScreen(
        viewModel = viewModel,
        title = title,
        onOpenDrawer = onOpenDrawer,
        onNewChat = onNewChat,
        onOpenSettings = onOpenSettings,
        onOpenMcp = onOpenMcp,
        onOpenCapabilities = onOpenCapabilities,
        onOpenSystemPrompt = onOpenSystemPrompt,
        onOpenArtifacts = onOpenArtifacts,
        onOpenAbout = onOpenAbout,
        onOpenToolTrace = onOpenToolTrace,
        onOpenHostCaps = onOpenHostCaps,
        // 会话工作区路径由 sessionId 在预览路由内解析，这里只需注入当前会话 id。
        onOpenInApp = { relativePath -> onOpenHtmlPreview(sessionId, relativePath) },
        onSessionFreshness = onSessionFreshness,
    )
}

private fun <T : androidx.lifecycle.ViewModel> simpleViewModelFactory(
    create: () -> T,
): androidx.lifecycle.ViewModelProvider.Factory {
    return object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = create() as T
    }
}
