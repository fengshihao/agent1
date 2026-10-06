package com.agent1.android.productivity.ui.view

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agent1.android.productivity.logic.business.ChatTranscriptFormatting
import com.agent1.android.productivity.ui.viewmodel.ChatLine
import com.agent1.android.productivity.ui.viewmodel.ChatRunTimelineItem
import com.agent1.android.productivity.ui.viewmodel.ChatUiState
import com.agent1.android.productivity.ui.viewmodel.ChatViewModel
import com.agent1.android.productivity.ui.viewmodel.RunTokenSummary
import com.agent1.android.productivity.ui.viewmodel.SessionListUiState
import com.agent1.android.productivity.ui.viewmodel.SessionListViewModel
import com.agent1.javaagent.session.SessionMeta
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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

@Composable
private fun ColumnScope.SessionDrawer(
    state: SessionListUiState,
    activeSessionId: String,
    onNewChat: () -> Unit,
    onOpenSession: (SessionMeta) -> Unit,
    onDeleteSession: (SessionMeta) -> Unit,
) {
    val sessions = state.sessions.sortedByDescending { it.updatedAt }
    Text(
            "Agent One",
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onNewChat)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            "新建对话",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (!state.startupError.isNullOrBlank()) {
        Text(
            state.startupError.orEmpty(),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (state.isLoading) "加载中…" else "还没有对话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(sessions, key = { it.sessionId }) { meta ->
                SessionDrawerRow(
                    meta = meta,
                    selected = meta.sessionId == activeSessionId,
                    onOpen = { onOpenSession(meta) },
                    onDelete = { onDeleteSession(meta) },
                )
            }
        }
    }
    AgentHairline()
    state.configSummary?.modelId?.let { modelId ->
        Text(
            modelId,
            modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 16.dp, bottom = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SessionDrawerRow(
    meta: SessionMeta,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Text(
                meta.title.ifBlank { "新对话" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatUpdatedAt(meta.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChatMoreMenu(
    expanded: Boolean,
    exportInProgress: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onOpenSystemPrompt: (() -> Unit)? = null,
    onBrief: (() -> Unit)? = null,
) {
    Box {
        TopBarIconButton(
            icon = Icons.Filled.MoreVert,
            contentDescription = "更多",
            onClick = onOpen,
            busy = exportInProgress,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            MoreMenuRow("模型设置", Icons.Filled.Settings) {
                onDismiss()
                onOpenSettings()
            }
            MoreMenuRow("MCP & web搜索", AgentIcons.Hub) {
                onDismiss()
                onOpenMcp()
            }
            MoreMenuRow("能力检索", Icons.Filled.Search) {
                onDismiss()
                onOpenCapabilities()
            }
            if (onOpenSystemPrompt != null) {
                MoreMenuRow("系统提示词", Icons.Filled.Info) {
                    onDismiss()
                    onOpenSystemPrompt()
                }
            }
            if (onBrief != null) {
                MoreMenuRow("会话简报", AgentIcons.Description, enabled = !exportInProgress) {
                    onDismiss()
                    onBrief()
                }
            }
            MoreMenuRow(
                label = if (exportInProgress) "正在打包…" else "诊断包",
                icon = Icons.Filled.Share,
                enabled = !exportInProgress,
            ) {
                onDismiss()
                onExportDiagnostics()
            }
        }
    }
}

@Composable
private fun MoreMenuRow(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun ChatLanding(
    startupError: String?,
    exportInProgress: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onExportDiagnostics: () -> Unit,
) {
    var moreMenu by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                AgentTopBar(
                    title = "新对话",
                    subtitle = null,
                    leading = {
                        TopBarIconButton(
                            icon = Icons.Filled.Menu,
                            contentDescription = "会话列表",
                            onClick = onOpenDrawer,
                        )
                    },
                    actions = {
                        ChatMoreMenu(
                            expanded = moreMenu,
                            exportInProgress = exportInProgress,
                            onOpen = { moreMenu = true },
                            onDismiss = { moreMenu = false },
                            onOpenSettings = onOpenSettings,
                            onOpenMcp = onOpenMcp,
                            onOpenCapabilities = onOpenCapabilities,
                            onExportDiagnostics = onExportDiagnostics,
                        )
                    },
                )
                AgentHairline()
            }
        },
        bottomBar = {
            ChatComposer(
                value = "",
                onValueChange = {},
                isRunning = false,
                canSend = false,
                enabled = false,
                onPickFiles = {},
                pickFilesEnabled = false,
                onSend = {},
                onStop = {},
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (startupError.isNullOrBlank()) {
                Text(
                    "正在打开对话…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "暂时无法开始对话：$startupError",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    title: String,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onOpenSystemPrompt: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var input by rememberSaveable { mutableStateOf("") }
    var moreMenu by rememberSaveable { mutableStateOf(false) }
    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.onUserPickedFiles(uris)
    }
    val launchPickFiles = {
        pickFilesLauncher.launch(arrayOf("*/*"))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshConfigSummary()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    AgentTopBar(
                        title = title.ifBlank { state.title }.ifBlank { "新对话" },
                        subtitle = null,
                        leading = {
                            TopBarIconButton(
                                icon = Icons.Filled.Menu,
                                contentDescription = "会话列表",
                                onClick = onOpenDrawer,
                            )
                        },
                        actions = {
                            ChatMoreMenu(
                                expanded = moreMenu,
                                exportInProgress = state.exportInProgress,
                                onOpen = { moreMenu = true },
                                onDismiss = { moreMenu = false },
                                onOpenSettings = onOpenSettings,
                                onOpenMcp = onOpenMcp,
                                onOpenCapabilities = onOpenCapabilities,
                                onOpenSystemPrompt = onOpenSystemPrompt,
                                onBrief = { viewModel.exportBriefTranscript(context) },
                                onExportDiagnostics = { viewModel.exportDiagnostics(context) },
                            )
                            TopBarIconButton(
                                icon = Icons.Filled.Add,
                                contentDescription = "新建对话",
                                onClick = onNewChat,
                            )
                        },
                    )
                    AgentHairline()
                    if (!state.exportMessage.isNullOrBlank()) {
                        Text(
                            state.exportMessage.orEmpty(),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.configError != null) {
                        ConfigErrorBanner(state.configError.orEmpty())
                    }
                    if (!state.transcriptLoadError.isNullOrBlank()) {
                        ConfigErrorBanner("对话加载失败：${state.transcriptLoadError}")
                    }
                    if (!state.fileImportMessage.isNullOrBlank()) {
                        Text(
                            state.fileImportMessage.orEmpty(),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            bottomBar = {
                // 非 edge-to-edge（decorFitsSystemWindows）下由 adjustResize 抬升窗口；勿再叠 imePadding。
                Column {
                    if (state.accessibleFilePaths.isNotEmpty()) {
                        AccessibleFilesStrip(paths = state.accessibleFilePaths)
                        AgentHairline()
                    }
                    ChatComposer(
                        value = input,
                        onValueChange = { input = it },
                        isRunning = state.isRunning,
                        canSend = state.configError == null && input.isNotBlank() && !state.isRunning,
                        enabled = state.configError == null,
                        onPickFiles = launchPickFiles,
                        pickFilesEnabled = state.configError == null && !state.isRunning,
                        onSend = {
                            viewModel.sendMessage(input)
                            input = ""
                        },
                        onStop = { viewModel.stopRun() },
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                ChatMessageList(
                    state = state,
                    onPickFiles = launchPickFiles,
                    pickFilesEnabled = state.configError == null && !state.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                state.pendingAskUser?.let { form ->
                    AskUserFormPanel(
                        form = form,
                        enabled = state.configError == null && !state.isRunning,
                        validationError = form.validationError,
                        onTextChange = viewModel::onAskUserTextChange,
                        onSingleSelect = viewModel::onAskUserSingleSelect,
                        onMultiToggle = viewModel::onAskUserMultiToggle,
                        onSubmit = viewModel::submitAskUserForm,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AgentTopBar(
    title: String,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    actions: @Composable () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = if (leading == null) 16.dp else 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                actions()
            }
        }
    }
}

@Composable
internal fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.size(36.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
            )
        } else {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}

@Composable
internal fun AgentHairline() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun AccessibleFilesStrip(paths: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            "已选文件（本会话）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            paths.joinToString(" · "),
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    isRunning: Boolean,
    canSend: Boolean,
    enabled: Boolean,
    onPickFiles: () -> Unit,
    pickFilesEnabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val fieldShape = RoundedCornerShape(22.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        AgentHairline()
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onPickFiles,
                    enabled = pickFilesEnabled,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        AgentIcons.AttachFile,
                        contentDescription = "选择文件",
                        modifier = Modifier.size(22.dp),
                        tint = if (pickFilesEnabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "输入消息…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    enabled = enabled,
                    shape = fieldShape,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                FilledIconButton(
                    onClick = { if (isRunning) onStop() else onSend() },
                    enabled = isRunning || canSend,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                    ),
                ) {
                    Icon(
                        if (isRunning) AgentIcons.Stop else Icons.Filled.Send,
                        contentDescription = if (isRunning) "停止" else "发送",
                        modifier = Modifier.size(if (isRunning) 16.dp else 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigErrorBanner(message: String) {
    Text(
        message,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

private suspend fun LazyListState.scrollToActualBottom() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    scrollToItem(lastIndex)
    withFrameNanos { }
    val last = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    val overflow = (last.offset + last.size) - layoutInfo.viewportEndOffset
    if (overflow > 0) {
        scrollBy(overflow.toFloat())
    }
}

@Composable
private fun ChatMessageList(
    state: ChatUiState,
    onPickFiles: () -> Unit,
    pickFilesEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val visibleLines = state.lines.filterNot { it.hideInChat }
    val listState = rememberLazyListState()
    val stickToBottomState = rememberSaveable(state.sessionId) { mutableStateOf(true) }
    val stickToBottom = stickToBottomState.value

    val userScrollConnection = remember(listState, stickToBottomState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 1f) {
                    stickToBottomState.value = false
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && !listState.canScrollForward) {
                    stickToBottomState.value = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState, stickToBottomState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()
            ChatListAutoscroll.isAtBottom(
                totalItems = layout.totalItemsCount,
                lastVisibleIndex = last?.index ?: -1,
                lastVisibleOffset = last?.offset ?: 0,
                lastVisibleSize = last?.size ?: 0,
                viewportEndOffset = layout.viewportEndOffset,
            )
        }.distinctUntilChanged().collect { atBottom ->
            if (atBottom) {
                stickToBottomState.value = true
            }
        }
    }

    LaunchedEffect(
        visibleLines.size,
        state.runTimeline.size,
        state.streamingText.length,
        state.streamingReasoning.length,
        state.isRunning,
        state.lastRunTokenSummary,
        stickToBottom,
    ) {
        if (!stickToBottom) return@LaunchedEffect
        listState.scrollToActualBottom()
    }
    if (state.isLoadingTranscript && state.lines.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "加载对话…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val showEmptyHint = visibleLines.isEmpty() &&
        state.runTimeline.isEmpty() &&
        state.streamingText.isEmpty() &&
        state.streamingReasoning.isEmpty() &&
        !state.isRunning
    if (showEmptyHint) {
        EmptyChatHint(modifier)
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .nestedScroll(userScrollConnection),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
    ) {
        items(
            count = visibleLines.size,
            key = { index ->
                visibleLines[index].stableKey.ifBlank { "line-$index" }
            },
        ) { index ->
            val line = visibleLines[index]
            val useMarkdown = !line.isTool &&
                line.role != "user" &&
                ChatTranscriptFormatting.shouldRenderAsMarkdown(line.content)
            MessageBubble(
                line = line,
                workspacePath = state.workspacePath,
                markdown = useMarkdown,
                reasoningStateKey = line.stableKey.ifBlank { "line-$index" },
                onPickFiles = onPickFiles,
                pickFilesEnabled = pickFilesEnabled,
            )
        }
        itemsIndexed(
            items = state.runTimeline,
            key = { index, item -> "$index:${item.id}" },
        ) { _, item ->
            when (item) {
                is ChatRunTimelineItem.AssistantPart -> {
                    MessageBubble(
                        line = ChatLine(
                            role = "assistant",
                            content = item.content,
                            reasoning = item.reasoning,
                            stableKey = item.id,
                        ),
                        workspacePath = state.workspacePath,
                        markdown = ChatTranscriptFormatting.shouldRenderAsMarkdown(item.content),
                        reasoningStateKey = item.id,
                        onPickFiles = onPickFiles,
                        pickFilesEnabled = false,
                    )
                }
                is ChatRunTimelineItem.ToolPart -> {
                    CollapsibleToolCallBubble(
                        stateKey = item.id,
                        toolName = item.toolName,
                        argsPreview = item.argsPreview,
                        finished = item.finished,
                        isError = item.isError,
                        resultSummary = item.resultSummary,
                        progressLines = item.progressLines,
                        workspacePath = state.workspacePath,
                        workspaceImagePath = item.workspaceImagePath,
                        imageWarning = item.imageWarning,
                        workspaceFilePaths = item.workspaceFilePaths,
                    )
                }
            }
        }
        if (shouldShowAssistantPending(state)) {
            item(key = "assistant-pending") {
                AssistantPendingBubble(
                    label = state.runActivityLabel ?: "等待助手…",
                )
            }
        }
        if (state.streamingText.isNotEmpty() || state.streamingReasoning.isNotEmpty()) {
            item(key = "assistant-streaming") {
                MessageBubble(
                    line = ChatLine(
                        role = "assistant",
                        content = state.streamingText + if (state.streamingText.isNotEmpty()) "▌" else "",
                        reasoning = state.streamingReasoning +
                            if (state.streamingReasoning.isNotEmpty() && state.streamingText.isEmpty()) "▌" else "",
                        stableKey = "assistant-streaming",
                    ),
                    workspacePath = state.workspacePath,
                    markdown = false,
                    reasoningStateKey = "assistant-streaming-${state.sessionId}",
                    onPickFiles = onPickFiles,
                    pickFilesEnabled = false,
                )
            }
        }
        if (!state.isRunning) {
            state.lastRunTokenSummary?.let { summary ->
                item(key = "run-token-summary") {
                    RunTokenSummaryRow(summary)
                }
            }
        }
    }
}

@Composable
private fun RunTokenSummaryRow(summary: RunTokenSummary) {
    val color = if (summary.failed) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = summary.displayText(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

private fun shouldShowAssistantPending(state: ChatUiState): Boolean {
    if (!state.isRunning) return false
    if (state.streamingText.isNotEmpty() || state.streamingReasoning.isNotEmpty()) return false
    if (state.runTimeline.isEmpty()) return true
    return when (val last = state.runTimeline.last()) {
        is ChatRunTimelineItem.AssistantPart -> false
        is ChatRunTimelineItem.ToolPart -> last.finished
    }
}

@Composable
private fun AssistantPendingBubble(label: String) {
    val bubbles = chatBubbleColors()
    BubbleShell(
        alignEnd = false,
        wide = true,
        background = bubbles.assistantBackground,
        borderColor = bubbles.assistantBorder,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CollapsibleToolCallBubble(
    stateKey: String,
    toolName: String,
    argsPreview: String,
    finished: Boolean,
    isError: Boolean,
    resultSummary: String,
    progressLines: List<String>,
    workspacePath: String,
    workspaceImagePath: String?,
    imageWarning: String?,
    workspaceFilePaths: List<String>,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val bubbles = chatBubbleColors()
    val failed = finished && isError
    CollapsibleMetaBubble(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        title = toolName,
        borderColor = if (failed) MaterialTheme.colorScheme.error else bubbles.systemBorder,
        showSpinner = !finished,
        expandContentDescription = "展开工具结果",
        collapseContentDescription = "收起工具结果",
    ) {
        if (argsPreview.isNotBlank()) {
            Text(
                argsPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!finished && progressLines.isNotEmpty()) {
            progressLines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (resultSummary.isNotBlank()) {
            WorkspaceChatBody(
                content = resultSummary,
                workspaceAbsolutePath = workspacePath,
                markdown = false,
                workspaceFilePaths = workspaceFilePaths,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        } else if (finished && !isError) {
            Text(
                "（无输出）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        workspaceImagePath?.let { path ->
            WorkspaceImagePreview(
                workspaceAbsolutePath = workspacePath,
                workspaceRelativePath = path,
                warning = imageWarning,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    line: ChatLine,
    workspacePath: String,
    markdown: Boolean,
    reasoningStateKey: String,
    onPickFiles: () -> Unit,
    pickFilesEnabled: Boolean,
) {
    val bubbles = chatBubbleColors()
    val isUser = line.role == "user" && !line.isTool
    val isTool = line.isTool
    when {
        isUser -> {
            BubbleShell(
                alignEnd = true,
                wide = false,
                background = bubbles.userBackground,
                borderColor = Color.Transparent,
            ) {
                SelectionContainer {
                    Text(
                        line.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = bubbles.userContent,
                    )
                }
            }
        }
        isTool -> {
            CollapsibleToolCallBubble(
                stateKey = reasoningStateKey,
                toolName = line.toolName ?: "工具",
                argsPreview = line.toolArgsPreview.orEmpty(),
                finished = line.toolFinished,
                isError = line.toolIsError,
                resultSummary = line.content,
                progressLines = emptyList(),
                workspacePath = workspacePath,
                workspaceImagePath = line.workspaceImagePath,
                imageWarning = line.imageWarning,
                workspaceFilePaths = line.workspaceFilePaths,
            )
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (line.reasoning.isNotBlank()) {
                    CollapsibleReasoningBlock(
                        reasoning = line.reasoning,
                        stateKey = reasoningStateKey,
                        inProgress = line.stableKey == "assistant-streaming" &&
                            line.content.isBlank(),
                    )
                }
                if (line.content.isNotBlank() || line.requestUserPickFiles) {
                    BubbleShell(
                        alignEnd = false,
                        wide = true,
                        background = bubbles.assistantBackground,
                        borderColor = bubbles.assistantBorder,
                    ) {
                        SelectionContainer {
                            Column {
                                if (line.content.isNotBlank()) {
                                    WorkspaceChatBody(
                                        content = line.content,
                                        workspaceAbsolutePath = workspacePath,
                                        markdown = markdown,
                                        workspaceFilePaths = line.workspaceFilePaths,
                                    )
                                }
                                if (line.requestUserPickFiles) {
                                    IconButton(
                                        onClick = onPickFiles,
                                        enabled = pickFilesEnabled,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .size(32.dp),
                                    ) {
                                        Icon(
                                            AgentIcons.AttachFile,
                                            contentDescription = "选择文件",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleReasoningBlock(
    reasoning: String,
    stateKey: String,
    inProgress: Boolean = false,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val trimmed = reasoning.trim().trimEnd('▌', ' ').trim()
    if (trimmed.isEmpty()) return
    val bubbles = chatBubbleColors()
    CollapsibleMetaBubble(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        title = "思考过程",
        borderColor = bubbles.systemBorder,
        showSpinner = inProgress,
        expandContentDescription = "展开思考过程",
        collapseContentDescription = "收起思考过程",
    ) {
        Text(
            text = trimmed,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CollapsibleMetaBubble(
    expanded: Boolean,
    onToggle: () -> Unit,
    title: String,
    borderColor: Color,
    showSpinner: Boolean,
    expandContentDescription: String,
    collapseContentDescription: String,
    expandedContent: @Composable ColumnScope.() -> Unit,
) {
    val bubbles = chatBubbleColors()
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    BubbleShell(
        alignEnd = false,
        wide = true,
        background = bubbles.systemBackground,
        borderColor = borderColor,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) collapseContentDescription else expandContentDescription,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = iconTint,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                SelectionContainer {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        content = expandedContent,
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleShell(
    alignEnd: Boolean,
    wide: Boolean,
    background: Color,
    borderColor: Color,
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (wide) 340.dp else 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .then(
                    if (borderColor.alpha > 0f) {
                        Modifier.border(borderWidth, borderColor, RoundedCornerShape(16.dp))
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun EmptyChatHint(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "从一条消息开始",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "左上角可以打开已有会话",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatUpdatedAt(raw: String): String {
    return try {
        val zoned = Instant.parse(raw).atZone(ZoneId.systemDefault())
        val date = zoned.toLocalDate()
        val today = LocalDate.now()
        val clock = zoned.format(DateTimeFormatter.ofPattern("HH:mm"))
        when {
            date == today -> "今天 $clock"
            date == today.minusDays(1) -> "昨天 $clock"
            date.year == today.year -> zoned.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
            else -> zoned.format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
        }
    } catch (_: Exception) {
        raw
    }
}
