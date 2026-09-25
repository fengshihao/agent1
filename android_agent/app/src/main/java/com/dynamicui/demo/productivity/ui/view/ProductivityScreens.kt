package com.dynamicui.demo.productivity.ui.view

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.dynamicui.demo.productivity.ui.viewmodel.ChatLine
import com.dynamicui.demo.productivity.ui.viewmodel.ChatUiState
import com.dynamicui.demo.productivity.ui.viewmodel.ChatViewModel
import com.dynamicui.demo.productivity.ui.viewmodel.SessionListViewModel
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
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
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        AgentTopBar(
            title = "会话",
            subtitle = if (state.sessions.isEmpty()) "从这里开始一条对话" else "${state.sessions.size} 条对话",
            leading = null,
            actions = {
                TopBarIconButton(label = "模型", onClick = onOpenSettings)
                TopBarIconButton(
                    label = if (state.exportInProgress) "…" else "导出",
                    onClick = { viewModel.exportDiagnostics(context) },
                    enabled = !state.exportInProgress,
                )
                Button(
                    onClick = { viewModel.createSession(onCreated = onOpenSession) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("新建")
                }
            },
        )
        AgentHairline()
        RuntimeSummaryStrip(
            summary = state.configSummary,
            catalog = state.catalogModels,
            configError = state.configError,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
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
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                Column {
                    AgentTopBar(
                        title = state.title.ifBlank { "AI 助手" },
                        subtitle = null,
                        leading = {
                            TopBarIconButton(label = "←", onClick = onBack)
                        },
                        actions = {
                            TopBarIconButton(
                                label = if (state.exportInProgress) "…" else "导出",
                                onClick = { viewModel.exportDiagnostics(context) },
                                enabled = !state.exportInProgress,
                            )
                            TopBarIconButton(label = "模型", onClick = onOpenSettings)
                            TopBarIconButton(
                                label = "规格",
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
                }
            },
            bottomBar = {
                // edge-to-edge 下用 adjustNothing + 仅 bottomBar 消费 IME，避免 adjustResize 与 imePadding 叠加把输入条顶得过高。
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    if (state.isRunning) {
                        RunActivityStrip(
                            label = state.runActivityLabel ?: "等待助手…",
                            showSpinner = state.streamingText.isEmpty(),
                        )
                        AgentHairline()
                    }
                    ChatComposer(
                        value = input,
                        onValueChange = { input = it },
                        isRunning = state.isRunning,
                        canSend = state.configError == null && input.isNotBlank() && !state.isRunning,
                        enabled = state.configError == null,
                        onSend = {
                            viewModel.sendMessage(input)
                            input = ""
                        },
                        onStop = { viewModel.stopRun() },
                    )
                }
            },
        ) { innerPadding ->
            ChatMessageList(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
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
                    fontWeight = FontWeight.Bold,
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
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    isRunning: Boolean,
    canSend: Boolean,
    enabled: Boolean,
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
                OutlinedButton(
                    onClick = onStop,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text("中断")
                }
            }
            Button(
                onClick = onSend,
                enabled = canSend && !isRunning,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text("发送")
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
            .shadow(2.dp, RoundedCornerShape(14.dp), clip = false)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
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
                TextButton(onClick = onOpen) { Text("打开") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@Composable
private fun ChatMessageList(state: ChatUiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(
        state.lines.size,
        state.toolTrail.size,
        state.streamingText.length,
        state.isRunning,
        state.runActivityLabel,
    ) {
        if (!state.isRunning && state.streamingText.isEmpty()) return@LaunchedEffect
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) {
            listState.scrollToItem(total - 1)
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
        items(state.lines.size) { index ->
            val line = state.lines[index]
            val useMarkdown = !line.isTool && line.role != "user" && line.content.length <= 6_000
            MessageBubble(line, markdown = useMarkdown)
        }
        if (state.toolTrail.isNotEmpty()) {
            item {
                ToolTrailBubble(state.toolTrail)
            }
        }
        if (state.isRunning && state.streamingText.isEmpty() && state.toolTrail.isEmpty()) {
            item {
                AssistantPendingBubble(
                    label = state.runActivityLabel ?: "等待助手…",
                )
            }
        }
        if (state.streamingText.isNotEmpty()) {
            item {
                MessageBubble(
                    ChatLine("assistant", state.streamingText + "▌"),
                    markdown = false,
                )
            }
        }
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
private fun ToolTrailBubble(trail: List<String>) {
    val bubbles = chatBubbleColors()
    BubbleShell(
        alignEnd = false,
        wide = true,
        background = bubbles.systemBackground,
        borderColor = bubbles.systemBorder,
    ) {
        trail.forEach { line ->
            Text(
                line,
                modifier = Modifier.padding(vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageBubble(line: ChatLine, markdown: Boolean) {
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
                Text(
                    "🔧 ${line.content}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            BubbleShell(
                alignEnd = false,
                wide = true,
                background = bubbles.assistantBackground,
                borderColor = bubbles.assistantBorder,
            ) {
                if (markdown) {
                    Markdown(
                        content = line.content,
                        colors = markdownColor(
                            text = MaterialTheme.colorScheme.onSurface,
                            codeBackground = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                } else {
                    Text(
                        line.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
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
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (wide) 340.dp else 300.dp)
                .shadow(if (alignEnd) 0.dp else 1.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(background)
                .then(
                    if (borderColor.alpha > 0f) {
                        Modifier.border(1.dp, borderColor, RoundedCornerShape(12.dp))
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onDismiss) { Text("关闭") }
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
                Text(
                    if (expanded) "收起" else "详情",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
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
