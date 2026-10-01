package com.agent1.android.productivity.ui.view

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent1.javaagent.modelcatalog.QwenModelInfo
import com.agent1.javaagent.modelcatalog.RuntimeConfigSummary
import com.agent1.javaagent.session.SessionMeta
import com.agent1.android.productivity.logic.business.ChatTranscriptFormatting
import com.agent1.android.productivity.ui.viewmodel.ChatLine
import com.agent1.android.productivity.ui.viewmodel.ChatRunTimelineItem
import com.agent1.android.productivity.ui.viewmodel.ChatUiState
import com.agent1.android.productivity.ui.viewmodel.ChatViewModel
import com.agent1.android.productivity.ui.viewmodel.SessionListUiState
import com.agent1.android.productivity.ui.viewmodel.SessionListViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(300.dp)
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
                    onOpenSettings = {
                        closeDrawer()
                        onOpenSettings()
                    },
                    onOpenMcp = {
                        closeDrawer()
                        onOpenMcp()
                    },
                    onOpenCapabilities = {
                        closeDrawer()
                        onOpenCapabilities()
                    },
                    onOpenSystemPrompt = {
                        closeDrawer()
                        onOpenSystemPrompt(activeSessionId)
                    },
                    onExportDiagnostics = { sessionListViewModel.exportDiagnostics(context) },
                )
            }
        },
    ) {
        val sessionId = activeSessionId
        if (sessionId.isBlank()) {
            ChatLanding(
                startupError = listState.startupError,
                onOpenDrawer = {
                    scope.launch {
                        if (drawerState.isOpen) drawerState.close() else drawerState.open()
                    }
                },
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
    onOpenSettings: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onOpenSystemPrompt: () -> Unit,
    onExportDiagnostics: () -> Unit,
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
            modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (!state.exportMessage.isNullOrBlank()) {
        Text(
            state.exportMessage.orEmpty(),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    DrawerTextButton(
        label = "模型设置",
        icon = Icons.Filled.Settings,
        onClick = onOpenSettings,
    )
    DrawerTextButton(
        label = "MCP",
        icon = Icons.Filled.Add,
        onClick = onOpenMcp,
    )
    DrawerTextButton(
        label = "能力检索",
        icon = Icons.Filled.Search,
        onClick = onOpenCapabilities,
    )
    DrawerTextButton(
        label = "系统提示词",
        icon = Icons.Filled.Info,
        onClick = onOpenSystemPrompt,
    )
    DrawerTextButton(
        label = if (state.exportInProgress) "正在打包…" else "诊断包",
        icon = Icons.Filled.Share,
        onClick = onExportDiagnostics,
        enabled = !state.exportInProgress,
    )
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
private fun DrawerTextButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
        Text(label, style = MaterialTheme.typography.titleSmall, color = tint)
    }
}

@Composable
private fun ChatLanding(
    startupError: String?,
    onOpenDrawer: () -> Unit,
) {
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
                    actions = {},
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
                            Box {
                                TopBarIconButton(
                                    icon = Icons.Filled.MoreVert,
                                    contentDescription = "更多",
                                    onClick = { moreMenu = true },
                                    busy = state.exportInProgress,
                                )
                                DropdownMenu(
                                    expanded = moreMenu,
                                    onDismissRequest = { moreMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("运行规格") },
                                        onClick = {
                                            moreMenu = false
                                            viewModel.toggleModelPanel()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("会话简报") },
                                        enabled = !state.exportInProgress,
                                        onClick = {
                                            moreMenu = false
                                            viewModel.exportBriefTranscript(context)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("诊断包") },
                                        enabled = !state.exportInProgress,
                                        onClick = {
                                            moreMenu = false
                                            viewModel.exportDiagnostics(context)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("模型设置") },
                                        onClick = {
                                            moreMenu = false
                                            onOpenSettings()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("能力检索") },
                                        onClick = {
                                            moreMenu = false
                                            onOpenCapabilities()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("系统提示词") },
                                        onClick = {
                                            moreMenu = false
                                            onOpenSystemPrompt()
                                        },
                                    )
                                }
                            }
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
                    if (!state.showModelPanel && state.configError != null) {
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

        AnimatedVisibility(visible = state.showModelPanel) {
            RuntimeConfigOverlay(
                summary = state.configSummary,
                catalog = ChatViewModel.catalogModels,
                configError = state.configError,
                onDismiss = { viewModel.toggleModelPanel() },
            )
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
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(
                onClick = onPickFiles,
                enabled = pickFilesEnabled,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    AgentIcons.AttachFile,
                    contentDescription = "选择文件",
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
                placeholder = { Text("输入消息…") },
                enabled = enabled,
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    modifier = Modifier.size(if (isRunning) 14.dp else 18.dp),
                )
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

@Composable
private fun ChatMessageList(
    state: ChatUiState,
    onPickFiles: () -> Unit,
    pickFilesEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val visibleLines = state.lines.filterNot { it.hideInChat }
    val listState = rememberLazyListState()
    var stickToBottom by rememberSaveable(state.sessionId) { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val total = layout.totalItemsCount
            if (total == 0) {
                true
            } else {
                val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= total - 2
            }
        }.distinctUntilChanged().collect { atBottom ->
            stickToBottom = atBottom
        }
    }

    LaunchedEffect(
        visibleLines.size,
        state.runTimeline.size,
        state.streamingText.length,
        state.streamingReasoning.length,
        state.isRunning,
    ) {
        if (!stickToBottom) return@LaunchedEffect
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
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
        modifier = modifier.padding(horizontal = 4.dp),
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
                    LiveToolBubble(item)
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
    }
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
private fun LiveToolBubble(tool: ChatRunTimelineItem.ToolPart) {
    val bubbles = chatBubbleColors()
    BubbleShell(
        alignEnd = false,
        wide = true,
        background = bubbles.systemBackground,
        borderColor = bubbles.systemBorder,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                tool.toolName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (tool.argsPreview.isNotBlank() && !tool.finished) {
                Text(
                    tool.argsPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!tool.finished) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    tool.statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        tool.isError -> MaterialTheme.colorScheme.error
                        tool.finished -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            tool.progressLines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                Text(
                    line.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = bubbles.userContent,
                )
            }
        }
        isTool -> {
            BubbleShell(
                alignEnd = false,
                wide = true,
                background = bubbles.systemBackground,
                borderColor = bubbles.systemBorder,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "🔧 ${line.content}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    line.workspaceImagePath?.let { path ->
                        WorkspaceImagePreview(
                            workspaceAbsolutePath = workspacePath,
                            workspaceRelativePath = path,
                            warning = line.imageWarning,
                        )
                    }
                    if (line.workspaceFilePaths.isNotEmpty()) {
                        WorkspaceFileAttachments(
                            workspaceAbsolutePath = workspacePath,
                            relativePaths = line.workspaceFilePaths,
                            excludePaths = line.workspaceImagePath?.let { setOf(it) } ?: emptySet(),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        else -> {
            BubbleShell(
                alignEnd = false,
                wide = true,
                background = bubbles.assistantBackground,
                borderColor = bubbles.assistantBorder,
            ) {
                if (line.reasoning.isNotBlank()) {
                    CollapsibleReasoningBlock(
                        reasoning = line.reasoning,
                        stateKey = reasoningStateKey,
                    )
                }
                if (line.content.isNotBlank()) {
                    if (markdown) {
                        WorkspaceMarkdown(
                            content = line.content,
                            workspaceAbsolutePath = workspacePath,
                        )
                    } else {
                        Text(
                            line.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (line.workspaceFilePaths.isNotEmpty()) {
                    WorkspaceFileAttachments(
                        workspaceAbsolutePath = workspacePath,
                        relativePaths = line.workspaceFilePaths,
                        excludePaths = emptySet(),
                        modifier = Modifier.padding(top = 6.dp),
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

@Composable
private fun CollapsibleReasoningBlock(
    reasoning: String,
    stateKey: String,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val trimmed = reasoning.trim()
    if (trimmed.isEmpty()) return
    val title = if (expanded) {
        "收起思考过程"
    } else {
        "思考过程（${trimmed.length} 字）"
    }
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = title,
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        AnimatedVisibility(visible = expanded) {
            Text(
                text = trimmed,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BubbleShell(
    alignEnd: Boolean,
    wide: Boolean,
    background: Color,
    borderColor: Color,
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
                        Modifier.border(1.dp, borderColor, RoundedCornerShape(16.dp))
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
private fun RuntimeConfigOverlay(
    summary: RuntimeConfigSummary?,
    catalog: List<QwenModelInfo>,
    configError: String?,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 24.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ),
                )
                .clickable(enabled = false) {}
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "模型与运行时",
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            ModelAndRuntimePanel(summary, catalog, configError)
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

@Composable
fun ModelAndRuntimePanel(
    summary: RuntimeConfigSummary?,
    catalog: List<QwenModelInfo>,
    configError: String?,
) {
    val fmt = NumberFormat.getIntegerInstance(Locale.US)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (configError != null) {
            Text(configError, color = MaterialTheme.colorScheme.error)
        } else if (summary != null) {
            DetailLine("当前模型", summary.modelId)
            DetailLine("Base URL", summary.baseUrl)
            DetailLine("API Key", if (summary.isApiKeyConfigured) "已配置" else "未配置")
            DetailLine(
                "Run 限额",
                "上下文 ${summary.maxContextTurns} 轮 · 每 Run ${summary.maxTurnsPerRun} 轮 · 工具 ${summary.maxToolCallsPerRun} 次",
            )
            DetailLine(
                "Agent 工具",
                com.agent1.android.productivity.logic.business.ProductivityToolCapabilities.summaryForUi(),
            )
            val match = summary.catalogMatch.orElse(null)
            if (match != null) {
                DetailLine(
                    "规格",
                    "上下文 ${fmt.format(match.contextWindowTokens)} · " +
                        "输入≤${fmt.format(match.maxInputTokens)} · 输出≤${fmt.format(match.maxOutputTokens)}",
                )
            }
        }
        Text(
            "主要模型",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        catalog.forEach { model ->
            Text(
                "${model.displayName} · ${model.modelId}",
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "上下文 ${fmt.format(model.contextWindowTokens)} · " +
                    "输入≤${fmt.format(model.maxInputTokens)} · 输出≤${fmt.format(model.maxOutputTokens)}" +
                    (model.maxThinkingChainTokens?.let { " · 思考链≤${fmt.format(it)}" } ?: "") +
                    " · 工具${if (model.isFunctionCalling) "支持" else "不支持"} · ${model.thinkingMode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
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
