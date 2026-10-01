package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.ui.viewmodel.ModelSettingsViewModel

@Composable
fun ModelSettingsScreen(
    viewModel: ModelSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var revealKey by remember { mutableStateOf(false) }
    var revealWebSearchKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        AgentTopBar(
            title = "模型配置",
            subtitle = "模型与 Web Search 的 Key 均保存在本机",
            leading = {
                TopBarIconButton(
                    icon = Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
            },
            actions = {},
        )
        AgentHairline()

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.statusMessage.isNullOrBlank()) {
                Text(state.statusMessage.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
            if (!state.configError.isNullOrBlank()) {
                Text(
                    state.configError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                com.agent1.android.productivity.logic.business.ProductivityToolCapabilities.summaryForUi(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("服务商", style = MaterialTheme.typography.titleSmall)
            state.providerOptions.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onProviderSelected(preset.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.providerId == preset.id,
                        onClick = { viewModel.onProviderSelected(preset.id) },
                    )
                    Text(preset.displayName, style = MaterialTheme.typography.bodyMedium)
                }
            }

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (revealKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { revealKey = !revealKey }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (revealKey) AgentIcons.VisibilityOff else AgentIcons.Visibility,
                            contentDescription = if (revealKey) "隐藏" else "显示",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "选择模型（${state.remoteModels.size} 项）",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(
                    onClick = viewModel::fetchRemoteModels,
                    enabled = !state.isFetchingModels,
                    modifier = Modifier.size(36.dp),
                ) {
                    if (state.isFetchingModels) {
                        CircularProgressIndicator(strokeWidth = 1.5.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "从网络拉取模型",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.remoteModels, key = { it.modelId }) { option ->
                    ModelOptionRow(
                        selected = state.modelId == option.modelId,
                        title = option.title,
                        subtitle = option.subtitle,
                        modelId = option.modelId,
                        onSelect = { viewModel.onModelSelected(option.modelId) },
                    )
                }
            }

            OutlinedTextField(
                value = state.modelId,
                onValueChange = viewModel::onModelIdChange,
                label = { Text("模型 ID（可手填）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("Web Search", style = MaterialTheme.typography.titleSmall)
            Text(
                "Tavily 免费搜索。填写 Key 后，助手可调用 web_search。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.webSearchApiKey,
                onValueChange = viewModel::onWebSearchApiKeyChange,
                label = { Text("Tavily API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (revealWebSearchKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = { revealWebSearchKey = !revealWebSearchKey },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            if (revealWebSearchKey) AgentIcons.VisibilityOff else AgentIcons.Visibility,
                            contentDescription = if (revealWebSearchKey) "隐藏 Web Search Key" else "显示 Web Search Key",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
            OutlinedTextField(
                value = state.webSearchBaseUrl,
                onValueChange = viewModel::onWebSearchBaseUrlChange,
                label = { Text("Tavily Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::toggleAdvanced),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "高级参数",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    if (state.showAdvanced) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (state.showAdvanced) "收起高级参数" else "展开高级参数",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.showAdvanced) {
                OutlinedTextField(
                    value = state.maxContextTurns,
                    onValueChange = viewModel::onMaxContextTurnsChange,
                    label = { Text("上下文轮数 maxContextTurns") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.maxContextMessages,
                    onValueChange = viewModel::onMaxContextMessagesChange,
                    label = { Text("消息条数上限（空=默认）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.maxTurnsPerRun,
                    onValueChange = viewModel::onMaxTurnsPerRunChange,
                    label = { Text("每轮 Run 最大回合") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.maxToolCallsPerRun,
                    onValueChange = viewModel::onMaxToolCallsPerRunChange,
                    label = { Text("每轮 Run 最大工具调用") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = viewModel::resetToBuildDefaults,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.Build,
                        contentDescription = "恢复编译默认",
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.size(36.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(strokeWidth = 1.5.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "保存并生效",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            state.effectiveSummary?.let { summary ->
                Surface(
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("当前生效", style = MaterialTheme.typography.titleSmall)
                        Text("模型：${summary.modelId}", style = MaterialTheme.typography.bodyMedium)
                        Text("Base URL：${summary.baseUrl}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Key：${if (summary.isApiKeyConfigured) "已配置" else "未配置"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Web Search：${if (summary.isWebSearchConfigured) "已配置" else "未配置"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Text(
                "说明：模型 Key 与 Tavily Key 仅存在本机加密存储，不会上传到 Git。远程拉取模型需有效 Key 与可访问的 Base URL。未填 Tavily Key 时不注册 web_search。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelOptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    modelId: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                "$modelId · $subtitle",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
