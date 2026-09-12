package com.dynamicui.demo.productivity.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
    Column(modifier = Modifier.statusBarsPadding()) {
        SessionListHeader(
            count = state.sessions.size,
            exportInProgress = state.exportInProgress,
            onExport = { viewModel.exportDiagnostics(context) },
            onCreate = { onOpenSession(viewModel.createSession()) },
        )
        RuntimeSummaryStrip(
            summary = state.configSummary,
            catalog = state.catalogModels,
            configError = state.configError,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (!state.exportMessage.isNullOrBlank()) {
            Text(
                state.exportMessage.orEmpty(),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.sessions.isEmpty()) {
            EmptySessionState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 24.dp,
                ),
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
}

@Composable
private fun SessionListHeader(
    count: Int,
    exportInProgress: Boolean,
    onExport: () -> Unit,
    onCreate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "会话",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (count == 0) "从这里开始一条对话" else "$count 条对话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onExport, enabled = !exportInProgress) {
            Text(if (exportInProgress) "打包中…" else "导出日志")
        }
        Button(
            onClick = onCreate,
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("新建")
        }
    }
}

@Composable
private fun EmptySessionState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "还没有会话",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "点右上角「新建」开始。模型规格收在上方那一行里。",
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 6.dp),
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
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                TextButton(onClick = onOpen) { Text("打开") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
    Column(modifier = Modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 列表") }
            Text(
                state.title.ifBlank { "新对话" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = { viewModel.exportDiagnostics(context) },
                enabled = !state.exportInProgress,
            ) {
                Text(if (state.exportInProgress) "打包中…" else "导出")
            }
            TextButton(onClick = { viewModel.toggleModelPanel() }) {
                Text(if (state.showModelPanel) "收起" else "运行时")
            }
        }
        if (!state.exportMessage.isNullOrBlank()) {
            Text(
                state.exportMessage.orEmpty(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.showModelPanel) {
            RuntimeSummaryStrip(
                summary = state.configSummary,
                catalog = ChatViewModel.catalogModels,
                configError = state.configError,
                startExpanded = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        } else if (state.configError != null) {
            Text(
                state.configError.orEmpty(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        ChatMessageList(state, modifier = Modifier.weight(1f))
        if (state.isRunning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("正在回复", color = MaterialTheme.colorScheme.primary)
                Button(onClick = { viewModel.stopRun() }, shape = RoundedCornerShape(12.dp)) {
                    Text("停止")
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息") },
                    enabled = !state.isRunning && state.configError == null,
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 4,
                )
                Button(
                    onClick = {
                        viewModel.sendMessage(input)
                        input = ""
                    },
                    enabled = !state.isRunning && state.configError == null && input.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("发送")
                }
            }
        }
    }
    }
}

@Composable
private fun ChatMessageList(state: ChatUiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.lines.size, state.toolTrail.size, state.streamingText.length, state.isRunning) {
        if (!state.isRunning && state.streamingText.isEmpty()) return@LaunchedEffect
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) {
            listState.scrollToItem(total - 1)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(state.lines) { line ->
            MessageBubble(line)
        }
        if (state.toolTrail.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("工具轨迹", fontWeight = FontWeight.SemiBold)
                        state.toolTrail.forEach { trail ->
                            Text(
                                trail,
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        if (state.streamingText.isNotEmpty()) {
            item {
                MessageBubble(ChatLine("assistant", state.streamingText + "▌"))
            }
        }
    }
}

@Composable
private fun MessageBubble(line: ChatLine) {
    val isUser = line.role == "user" && !line.isTool
    val container = when {
        line.isTool -> MaterialTheme.colorScheme.secondaryContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val label = when {
        line.isTool -> "工具"
        isUser -> "你"
        else -> "助手"
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = container),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(if (line.isTool) 1f else 0.86f),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    line.content,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
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
                        maxLines = 1,
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
