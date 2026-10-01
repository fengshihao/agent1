package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.ui.viewmodel.McpSettingsViewModel

@Composable
fun McpSettingsScreen(
    viewModel: McpSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        AgentTopBar(
            title = "MCP",
            subtitle = "仅 http / https",
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
            Text(
                "添加服务器后，助手用 capability_search 查找接口，再在脚本里调用 \$mcp.名称.工具。工具参数不会写进模型的工具列表。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.statusMessage.isNullOrBlank()) {
                Text(state.statusMessage.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
            if (!state.errorMessage.isNullOrBlank()) {
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.servers.forEach { server ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(server.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                server.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (server.toolCount > 0) "${server.toolCount} 个工具" else "尚未列出工具",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = server.enabled,
                            onCheckedChange = { enabled -> viewModel.setEnabled(server.name, enabled) },
                            enabled = !state.busy,
                        )
                    }
                    TextButton(
                        onClick = { viewModel.remove(server.name) },
                        enabled = !state.busy,
                    ) {
                        Text("删除")
                    }
                }
            }
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("字母、数字、下划线或连字符，例如 github") },
            )
            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::onUrlChange,
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("以 http:// 或 https:// 开头") },
            )
            OutlinedTextField(
                value = state.authorization,
                onValueChange = viewModel::onAuthorizationChange,
                label = { Text("Authorization（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("例如 Bearer token") },
            )
            Button(
                onClick = viewModel::addServer,
                enabled = !state.busy,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (state.busy) "正在保存…" else "添加")
            }
        }
    }
}
