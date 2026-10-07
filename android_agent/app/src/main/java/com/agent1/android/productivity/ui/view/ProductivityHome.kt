package com.agent1.android.productivity.ui.view

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
) {
    val listState by sessionListViewModel.state.collectAsState()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var activeSessionId by rememberSaveable { mutableStateOf("") }
    var activeTitle by rememberSaveable { mutableStateOf("新对话") }
    var createRequested by rememberSaveable { mutableStateOf(false) }

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
            val titled = match.title.ifBlank { "新对话" }
            if (titled != activeTitle) activeTitle = titled
            return@LaunchedEffect
        }
        if (listState.startupError != null && listState.sessions.isEmpty()) return@LaunchedEffect
        val next = listState.sessions.maxByOrNull { it.updatedAt }
        if (next != null) {
            activeSessionId = next.sessionId
            activeTitle = next.title.ifBlank { "新对话" }
            return@LaunchedEffect
        }
        if (!createRequested) {
            createRequested = true
            sessionListViewModel.createSession { meta ->
                activeSessionId = meta.sessionId
                activeTitle = meta.title.ifBlank { "新对话" }
            }
        }
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    fun openSession(meta: SessionMeta) {
        activeSessionId = meta.sessionId
        activeTitle = meta.title.ifBlank { "新对话" }
        closeDrawer()
    }

    fun newChat() {
        closeDrawer()
        sessionListViewModel.createSession { meta ->
            activeSessionId = meta.sessionId
            activeTitle = meta.title.ifBlank { "新对话" }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight(),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
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
                                activeTitle = next.title.ifBlank { "新对话" }
                            } else {
                                createRequested = false
                                activeSessionId = ""
                                activeTitle = "新对话"
                            }
                        }
                    },
                )
            }
        },
    ) {
        val sessionId = activeSessionId
        if (sessionId.isBlank()) {
            ChatLanding(
                startupError = listState.startupError,
                exportInProgress = listState.exportInProgress,
                onOpenDrawer = {
                    scope.launch {
                        if (drawerState.isOpen) drawerState.close() else drawerState.open()
                    }
                },
                onOpenSettings = onOpenSettings,
                onOpenMcp = onOpenMcp,
                onOpenCapabilities = onOpenCapabilities,
                onExportDiagnostics = { sessionListViewModel.exportDiagnostics(context) },
            )
        } else {
            ChatPane(
                appContext = appContext,
                sessionId = sessionId,
                title = activeTitle,
                onOpenDrawer = {
                    scope.launch {
                        if (drawerState.isOpen) drawerState.close() else drawerState.open()
                    }
                },
                onNewChat = { newChat() },
                onOpenSettings = onOpenSettings,
                onOpenMcp = onOpenMcp,
                onOpenCapabilities = onOpenCapabilities,
                onOpenSystemPrompt = { onOpenSystemPrompt(sessionId) },
            )
        }
    }
}

@Composable
private fun ChatPane(
    appContext: Context,
    sessionId: String,
    title: String,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onOpenSystemPrompt: () -> Unit,
) {
    val viewModel: ChatViewModel = viewModel(
        key = "chat-$sessionId",
        factory = simpleViewModelFactory { ChatViewModel(appContext, sessionId, title) },
    )
    ChatScreen(
        viewModel = viewModel,
        title = title,
        onOpenDrawer = onOpenDrawer,
        onNewChat = onNewChat,
        onOpenSettings = onOpenSettings,
        onOpenMcp = onOpenMcp,
        onOpenCapabilities = onOpenCapabilities,
        onOpenSystemPrompt = onOpenSystemPrompt,
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
