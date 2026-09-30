package com.agent1.android.productivity.ui.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.agent1.android.productivity.ui.viewmodel.SessionListViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onOpenSession: (SessionMeta) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AgentTopBar(
            title = "会话",
            subtitle = if (state.sessions.isEmpty()) "从这里开始一条对话" else "${state.sessions.size} 条对话",
            leading = null,
            actions = {
                TopBarIconButton(
                    icon = Icons.Filled.Tune,
                    contentDescription = "模型",
                    onClick = onOpenSettings,
                )
                TopBarIconButton(
                    icon = Icons.Filled.IosShare,
                    contentDescription = "导出",
                    onClick = { viewModel.exportDiagnostics(context) },
                    enabled = !state.exportInProgress,
                    busy = state.exportInProgress,
                )
                TopBarIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "新建",
                    onClick = { viewModel.createSession(onCreated = onOpenSession) },
                )
            },
        )
        AgentHairline()
        RuntimeSummaryStrip(
            summary = state.configSummary,
            catalog = state.catalogModels,
            configError = state.configError,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        if (!state.startupError.isNullOrBlank()) {
            Text(
                "Agent 初始化失败：${state.startupError}。请重启 App 查看崩溃页，或 adb 执行 ./pull-crash-report.sh",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!state.exportMessage.isNullOrBlank()) {
            Text(
                state.exportMessage.orEmpty(),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.sessions.isEmpty()) {
            EmptySessionState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.sessions, key = { it.sessionId }) { meta ->
                    SessionCard(
                        meta = meta,
                        onOpen = { onOpenSession(meta) },
                        onDelete = { viewModel.deleteSession(meta.sessionId) },
                    )
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var input by rememberSaveable { mutableStateOf("") }
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
                        title = state.title.ifBlank { "AI 助手" },
                        subtitle = null,
                        leading = {
                            TopBarIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                onClick = onBack,
                            )
                        },
                        actions = {
                            TopBarIconButton(
                                icon = Icons.Filled.ContentCopy,
                                contentDescription = "简报",
                                onClick = { viewModel.exportBriefTranscript(context) },
                                enabled = !state.exportInProgress,
                                busy = state.exportInProgress,
                            )
                            TopBarIconButton(
                                icon = Icons.Filled.BugReport,
                                contentDescription = "诊断",
                                onClick = { viewModel.exportDiagnostics(context) },
                                enabled = !state.exportInProgress,
                            )
                            TopBarIconButton(
                                icon = Icons.Filled.Tune,
                                contentDescription = "模型",
                                onClick = onOpenSettings,
                            )
                            TopBarIconButton(
                                icon = Icons.Filled.Info,
                                contentDescription = "规格",
                                onClick = { viewModel.toggleModelPanel() },
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
                    if (state.isRunning) {
                        RunActivityStrip(
                            label = state.runActivityLabel ?: "等待助手…",
                            showSpinner = state.streamingText.isEmpty() && state.streamingReasoning.isEmpty(),
                        )
                        AgentHairline()
                    }
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
private fun RunActivityStrip(
    label: String,
    showSpinner: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
                    Icons.Filled.AttachFile,
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
                shape = RoundedCornerShape(10.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            AnimatedVisibility(visible = isRunning) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "中断",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Button(
                onClick = onSend,
                enabled = canSend && !isRunning,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    modifier = Modifier.size(18.dp),
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
private fun EmptySessionState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "还没有会话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "点右上角「新建」开始；模型规格收在上方可展开条目中。",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionCard(
    meta: SessionMeta,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 8.dp),
        ) {
            Text(
                meta.title.ifBlank { "未命名会话" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatUpdatedAt(meta.updatedAt),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    meta.sessionId.take(8),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "删除",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
        items(
            items = state.runTimeline,
            key = { it.id },
        ) { item ->
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
                            Icons.Filled.AttachFile,
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
private fun RuntimeSummaryStrip(
    summary: RuntimeConfigSummary?,
    catalog: List<QwenModelInfo>,
    configError: String?,
    modifier: Modifier = Modifier,
    startExpanded: Boolean = false,
) {
    var expanded by rememberSaveable(startExpanded) { mutableStateOf(startExpanded || configError != null) }
    val modelLabel = summary?.modelId ?: "未读取"
    val keyLabel = when {
        configError != null -> "配置异常"
        summary == null -> "读取中"
        summary.isApiKeyConfigured -> "Key 已配置"
        else -> "Key 未配置"
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        tonalElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        modelLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (summary == null) {
                            keyLabel
                        } else {
                            "$keyLabel · 上下文 ${summary.maxContextTurns} 轮 · 工具 ${summary.maxToolCallsPerRun} 次"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (configError != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "详情",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                AgentHairline()
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    ModelAndRuntimePanel(summary, catalog, configError)
                }
            }
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
