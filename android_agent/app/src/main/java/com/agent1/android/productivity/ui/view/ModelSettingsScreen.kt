package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.logic.business.ProviderOption
import com.agent1.android.productivity.logic.business.RemoteModelOption
import com.agent1.android.productivity.ui.viewmodel.ModelSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsScreen(
    viewModel: ModelSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var revealKey by remember { mutableStateOf(false) }
    val statusLine = when {
        !state.configError.isNullOrBlank() -> state.configError
        state.isSaving -> "正在保存…"
        else -> null
    }

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProviderDropdown(
                options = state.providerOptions,
                selectedId = state.providerId,
                onSelect = viewModel::onProviderSelected,
            )
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Base URL") },
            )
            SecretField(
                value = state.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                placeholder = "API Key",
                revealed = revealKey,
                onToggleReveal = { revealKey = !revealKey },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ModelDropdown(
                    value = state.modelId,
                    options = state.remoteModels,
                    onValueChange = viewModel::onModelIdChange,
                    onSelect = viewModel::onModelSelected,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = viewModel::refreshModelCatalog,
                    enabled = !state.isFetchingModels,
                    modifier = Modifier.size(40.dp),
                ) {
                    if (state.isFetchingModels) {
                        CircularProgressIndicator(
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "更新模型列表",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
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
                    Text("恢复默认")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    options: List<ProviderOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.displayName.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            placeholder = { Text("服务商") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(option.id)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    value: String,
    options: List<RemoteModelOption>,
    onValueChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            singleLine = true,
            placeholder = { Text("模型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                option.title.ifBlank { option.modelId },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (option.modelId != option.title) {
                                Text(
                                    option.modelId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option.modelId)
                    },
                )
            }
        }
    }
}

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
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
                        placeholder = { Text("上下文轮数") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = maxContextMessages,
                        onValueChange = onMaxContextMessagesChange,
                        placeholder = { Text("消息条数上限，空为默认") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = maxTurnsPerRun,
                        onValueChange = onMaxTurnsPerRunChange,
                        placeholder = { Text("每轮 Run 最大回合") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = maxToolCallsPerRun,
                        onValueChange = onMaxToolCallsPerRunChange,
                        placeholder = { Text("每轮 Run 最大工具调用") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        }
    }
}
