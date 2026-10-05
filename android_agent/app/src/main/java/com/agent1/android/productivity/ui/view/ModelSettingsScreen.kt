package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.logic.business.PROVIDER_CUSTOM
import com.agent1.android.productivity.logic.business.ProviderOption
import com.agent1.android.productivity.ui.viewmodel.ModelSettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelSettingsScreen(
    viewModel: ModelSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var revealKey by remember { mutableStateOf(false) }
    var revealWebSearchKey by remember { mutableStateOf(false) }

    val statusLine = when {
        !state.configError.isNullOrBlank() -> state.configError
        state.isSaving -> "正在保存…"
        !state.statusMessage.isNullOrBlank() -> state.statusMessage
        else -> "修改后自动保存"
    }
    val statusIsError = !state.configError.isNullOrBlank()

    Column(modifier = Modifier.fillMaxSize()) {
        AgentTopBar(
            title = "模型配置",
            subtitle = statusLine,
            leading = {
                TopBarIconButton(
                    icon = Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
            },
            actions = {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 1.5.dp,
                    )
                }
            },
        )
        AgentHairline()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EffectiveConfigBanner(
                summary = state.effectiveSummary,
                statusIsError = statusIsError,
            )

            SettingsSectionCard(title = "服务商", description = "每个服务商单独保存 API Key") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.providerOptions.forEach { preset ->
                        ProviderChip(
                            preset = preset,
                            selected = state.providerId == preset.id,
                            onSelect = { viewModel.onProviderSelected(preset.id) },
                        )
                    }
                }
            }

            SettingsSectionCard(
                title = "连接",
                description = "OpenAI 兼容端点与鉴权",
            ) {
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = viewModel::onBaseUrlChange,
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        if (state.providerId != PROVIDER_CUSTOM) {
                            Text("切换服务商时会填入默认地址；仍可手动修改")
                        } else {
                            Text("自定义 OpenAI 兼容端点")
                        }
                    },
                )
                SecretField(
                    value = state.apiKey,
                    onValueChange = viewModel::onApiKeyChange,
                    label = "API Key",
                    revealed = revealKey,
                    onToggleReveal = { revealKey = !revealKey },
                )
            }

            SettingsSectionCard(
                title = "模型",
                description = "列表为内置或远程拉取；也可直接填写模型 ID",
                headerAction = {
                    IconButton(
                        onClick = viewModel::fetchRemoteModels,
                        enabled = !state.isFetchingModels,
                        modifier = Modifier.size(36.dp),
                    ) {
                        if (state.isFetchingModels) {
                            CircularProgressIndicator(
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "从网络拉取模型",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
            ) {
                Text(
                    "${state.remoteModels.size} 个可选",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                OutlinedTextField(
                    value = state.modelId,
                    onValueChange = viewModel::onModelIdChange,
                    label = { Text("模型 ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            SettingsSectionCard(
                title = "Web Search",
                description = "填写 Tavily Key 后助手可调用 web_search",
            ) {
                SecretField(
                    value = state.webSearchApiKey,
                    onValueChange = viewModel::onWebSearchApiKeyChange,
                    label = "Tavily API Key",
                    revealed = revealWebSearchKey,
                    onToggleReveal = { revealWebSearchKey = !revealWebSearchKey },
                )
                OutlinedTextField(
                    value = state.webSearchBaseUrl,
                    onValueChange = viewModel::onWebSearchBaseUrlChange,
                    label = { Text("Tavily Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            AdvancedSection(
                expanded = state.showAdvanced,
                onToggle = viewModel::toggleAdvanced,
                maxContextTurns = state.maxContextTurns,
                onMaxContextTurnsChange = viewModel::onMaxContextTurnsChange,
                maxContextMessages = state.maxContextMessages,
                onMaxContextMessagesChange = viewModel::onMaxContextMessagesChange,
                maxTurnsPerRun = state.maxTurnsPerRun,
                onMaxTurnsPerRunChange = viewModel::onMaxTurnsPerRunChange,
                maxToolCallsPerRun = state.maxToolCallsPerRun,
                onMaxToolCallsPerRunChange = viewModel::onMaxToolCallsPerRunChange,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = viewModel::resetToBuildDefaults) {
                    Text("恢复编译期默认")
                }
            }

            Text(
                com.agent1.android.productivity.logic.business.ProductivityToolCapabilities.summaryForUi(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "模型 Key 与 Tavily Key 仅存在本机加密存储。远程拉取模型需有效 Key 与可访问的 Base URL。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EffectiveConfigBanner(
    summary: com.agent1.javaagent.modelcatalog.RuntimeConfigSummary?,
    statusIsError: Boolean,
) {
    if (summary == null) return
    val container = if (statusIsError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val onContainer = if (statusIsError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "当前生效",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = onContainer,
            )
            Text(
                summary.modelId,
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
            )
            Text(
                "Key：${if (summary.isApiKeyConfigured) "已配置" else "未配置"} · " +
                    "Web Search：${if (summary.isWebSearchConfigured) "已配置" else "未配置"}",
                style = MaterialTheme.typography.bodySmall,
                color = onContainer.copy(alpha = 0.9f),
            )
            Text(
                summary.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = onContainer.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    description: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (!description.isNullOrBlank()) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                headerAction?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun ProviderChip(
    preset: ProviderOption,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onSelect,
        label = {
            Text(
                preset.displayName,
                maxLines = 1,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
}

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = onToggleReveal, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (revealed) AgentIcons.VisibilityOff else AgentIcons.Visibility,
                    contentDescription = if (revealed) "隐藏" else "显示",
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}

@Composable
private fun AdvancedSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    maxContextTurns: String,
    onMaxContextTurnsChange: (String) -> Unit,
    maxContextMessages: String,
    onMaxContextMessagesChange: (String) -> Unit,
    maxTurnsPerRun: String,
    onMaxTurnsPerRunChange: (String) -> Unit,
    maxToolCallsPerRun: String,
    onMaxToolCallsPerRunChange: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "高级参数",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = maxContextTurns,
                        onValueChange = onMaxContextTurnsChange,
                        label = { Text("上下文轮数") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = maxContextMessages,
                        onValueChange = onMaxContextMessagesChange,
                        label = { Text("消息条数上限（空=默认）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = maxTurnsPerRun,
                        onValueChange = onMaxTurnsPerRunChange,
                        label = { Text("每轮 Run 最大回合") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = maxToolCallsPerRun,
                        onValueChange = onMaxToolCallsPerRunChange,
                        label = { Text("每轮 Run 最大工具调用") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
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
            .padding(vertical = 2.dp),
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
