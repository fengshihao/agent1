package com.dynamicui.demo.productivity.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dynamicui.demo.productivity.logic.business.resolveProvider
import com.dynamicui.demo.productivity.ui.viewmodel.ModelSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsScreen(
    viewModel: ModelSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var providerExpanded by remember { mutableStateOf(false) }
    var revealKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        AgentTopBar(
            title = "模型配置",
            subtitle = "Key / Base URL / 模型均保存在本机",
            leading = { TopBarIconButton(label = "←", onClick = onBack) },
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

            ExposedDropdownMenuBox(
                expanded = providerExpanded,
                onExpandedChange = { providerExpanded = !providerExpanded },
            ) {
                OutlinedTextField(
                    value = resolveProvider(state.providerId).displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("服务商") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                )
                ExposedDropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false },
                ) {
                    state.providerOptions.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.displayName) },
                            onClick = {
                                viewModel.onProviderSelected(preset.id)
                                providerExpanded = false
                            },
                        )
                    }
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
                    TextButton(onClick = { revealKey = !revealKey }) {
                        Text(if (revealKey) "隐藏" else "显示")
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::fetchRemoteModels,
                    enabled = !state.isFetchingModels,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isFetchingModels) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("从网络拉取模型")
                }
            }

            Text(
                "选择模型（${state.remoteModels.size} 项）",
                style = MaterialTheme.typography.titleSmall,
            )

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

            TextButton(onClick = viewModel::toggleAdvanced) {
                Text(if (state.showAdvanced) "收起高级参数" else "展开高级参数")
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.isSaving) "保存中…" else "保存并生效")
                }
                OutlinedButton(onClick = viewModel::resetToBuildDefaults) {
                    Text("恢复编译默认")
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
                    }
                }
            }

            Text(
                "说明：配置仅存在本机加密存储，不会上传到 Git。远程拉取模型需有效 Key 与可访问的 Base URL。",
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
