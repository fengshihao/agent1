package com.dynamicui.demo.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agent1.javaagent.modelcatalog.QwenModelInfo
import com.agent1.javaagent.modelcatalog.RuntimeConfigSummary
import com.agent1.javaagent.session.SessionMeta
import com.dynamicui.demo.productivity.ui.viewmodel.ChatLine
import com.dynamicui.demo.productivity.ui.viewmodel.ChatUiState
import com.dynamicui.demo.productivity.ui.viewmodel.ChatViewModel
import com.dynamicui.demo.productivity.ui.viewmodel.SessionListViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onOpenSession: (SessionMeta) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        ModelAndRuntimePanel(
            summary = state.configSummary,
            catalog = state.catalogModels,
            configError = state.configError,
        )
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("会话", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { viewModel.exportDiagnostics(context) },
                    enabled = !state.exportInProgress,
                ) {
                    Text(if (state.exportInProgress) "打包中…" else "导出日志")
                }
                Button(onClick = {
                    onOpenSession(viewModel.createSession())
                }) {
                    Text("新建")
                }
            }
        }
        if (!state.exportMessage.isNullOrBlank()) {
            Text(
                state.exportMessage.orEmpty(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.sessions.isEmpty()) {
            Text(
                "暂无会话，点新建开始",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.sessions, key = { it.sessionId }) { meta ->
                    SessionRow(meta, onOpen = { onOpenSession(meta) }, onDelete = {
                        viewModel.deleteSession(meta.sessionId)
                    })
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    meta: SessionMeta,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(meta.title, fontWeight = FontWeight.Medium)
            Text(meta.sessionId, style = MaterialTheme.typography.bodySmall)
            Text("更新 ${meta.updatedAt}", style = MaterialTheme.typography.bodySmall)
        }
        Row {
            TextButton(onClick = onOpen) { Text("打开") }
            TextButton(onClick = onDelete) { Text("删除") }
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("← 列表") }
            Text(state.title, style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(
                    onClick = { viewModel.exportDiagnostics(context) },
                    enabled = !state.exportInProgress,
                ) {
                    Text(if (state.exportInProgress) "打包中…" else "导出")
                }
                TextButton(onClick = { viewModel.toggleModelPanel() }) {
                    Text(if (state.showModelPanel) "隐藏模型" else "模型参数")
                }
            }
        }
        if (!state.exportMessage.isNullOrBlank()) {
            Text(
                state.exportMessage.orEmpty(),
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.showModelPanel) {
            ModelAndRuntimePanel(
                summary = state.configSummary,
                catalog = ChatViewModel.catalogModels,
                configError = state.configError,
            )
        }
        ChatMessageList(state, modifier = Modifier.weight(1f))
        if (state.isRunning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("运行中…", color = MaterialTheme.colorScheme.primary)
                Button(onClick = { viewModel.stopRun() }) { Text("停止") }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息") },
                enabled = !state.isRunning && state.configError == null,
            )
            Button(
                onClick = {
                    viewModel.sendMessage(input)
                    input = ""
                },
                enabled = !state.isRunning && state.configError == null,
            ) {
                Text("发送")
            }
        }
    }
}

@Composable
private fun ChatMessageList(state: ChatUiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.lines) { line ->
            MessageBubble(line)
        }
        if (state.toolTrail.isNotEmpty()) {
            item {
                Text("工具轨迹", fontWeight = FontWeight.SemiBold)
                state.toolTrail.forEach { t ->
                    Text(t, style = MaterialTheme.typography.bodySmall)
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
    val label = when {
        line.isTool -> "工具"
        line.role == "user" -> "你"
        else -> "助手"
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (line.role == "user") {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(line.content)
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
    Column(modifier = Modifier.padding(12.dp)) {
        Text("Qwen 运行时", style = MaterialTheme.typography.titleSmall)
        if (configError != null) {
            Text(configError, color = MaterialTheme.colorScheme.error)
        } else if (summary != null) {
            Text("当前模型: ${summary.modelId}")
            Text("Base URL: ${summary.baseUrl}")
            Text("API Key: ${if (summary.isApiKeyConfigured) "已配置" else "未配置"}")
            Text(
                "Run 限额: 上下文 ${summary.maxContextTurns} 轮 · " +
                    "每 Run ${summary.maxTurnsPerRun} 轮 · 工具 ${summary.maxToolCallsPerRun} 次",
            )
            val match = summary.catalogMatch.orElse(null)
            if (match != null) {
                Text(
                    "规格: 上下文 ${fmt.format(match.contextWindowTokens)} · " +
                        "输入≤${fmt.format(match.maxInputTokens)} · 输出≤${fmt.format(match.maxOutputTokens)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("主要模型（DashScope 兼容名）", style = MaterialTheme.typography.titleSmall)
        catalog.forEach { model ->
            Text(
                "${model.displayName} (${model.modelId}) · ${model.tier}",
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "上下文 ${fmt.format(model.contextWindowTokens)} · " +
                    "输入≤${fmt.format(model.maxInputTokens)} · 输出≤${fmt.format(model.maxOutputTokens)}" +
                    (model.maxThinkingChainTokens?.let { " · 思考链≤${fmt.format(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "输入: ${model.inputModalities.joinToString()} · 输出: ${model.outputModalities.joinToString()} · " +
                    "工具调用: ${if (model.isFunctionCalling) "支持" else "否"} · ${model.thinkingMode}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
