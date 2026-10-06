package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.ui.viewmodel.CapabilityHitUi
import com.agent1.android.productivity.ui.viewmodel.CapabilitySearchViewModel
import com.agent1.android.productivity.ui.viewmodel.DisabledMcpUi

@Composable
fun CapabilitySearchScreen(
    viewModel: CapabilitySearchViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val kindChoices = CapabilitySearchViewModel.kindChoices()
    Column(modifier = Modifier.fillMaxSize()) {
        AgentTopBar(
            title = "能力检索",
            subtitle = null,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("搜索") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(
                        onClick = viewModel::search,
                        enabled = !state.busy && state.query.isNotBlank(),
                    ) {
                        Text(if (state.busy) "…" else "搜索")
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(kindChoices, key = { _, choice -> choice.first }) { _, (id, label) ->
                        FilterChip(
                            selected = state.selectedKinds.contains(id),
                            onClick = { viewModel.toggleKind(id) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.setLimit(state.limit - 1) },
                        enabled = state.limit > 1 && !state.busy,
                    ) {
                        Text("−")
                    }
                    Text(state.limit.toString(), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        onClick = { viewModel.setLimit(state.limit + 1) },
                        enabled = state.limit < CapabilitySearchViewModel.MAX_LIMIT && !state.busy,
                    ) {
                        Text("+")
                    }
                }
            }
            if (!state.errorMessage.isNullOrBlank()) {
                item {
                    Text(
                        state.errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (state.searched && state.errorMessage.isNullOrBlank()) {
                item {
                    Text(
                        "模型看到的正文" + if (state.hostPlatform.isNotBlank()) {
                            "（平台 ${state.hostPlatform}）"
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    SelectionContainer {
                        Text(
                            state.modelText.ifBlank { "（空）" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                if (state.hiddenByPlatform.isNotEmpty()) {
                    item {
                        Text("当前平台搜不到", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "索引里有这些条目，但宿主平台过滤后不会出现在上面的正文里。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(
                        state.hiddenByPlatform,
                        key = { index, hit -> "h-$index-${hit.id}" },
                    ) { _, hit ->
                        CapabilityHitCard(hit)
                    }
                }
                if (state.disabledMcp.isNotEmpty()) {
                    item {
                        Text("未启用的 MCP", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "未启用的服务器不会写入能力索引，模型的 capability_search 也找不到。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(state.disabledMcp, key = { index, server -> "m-$index-${server.name}" }) { _, server ->
                        DisabledMcpCard(server)
                    }
                }
            }
            item { Text("", modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun CapabilityHitCard(hit: CapabilityHitUi) {
    val unavailable = hit.availability != CapabilitySearchViewModel.VISIBLE
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "[${hit.kind}] ${hit.title.ifBlank { hit.id }}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            CapabilitySearchViewModel.availabilityLabel(hit.availability),
            style = MaterialTheme.typography.labelMedium,
            color = if (unavailable) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        if (hit.id.isNotBlank()) {
            MetaLine("id", hit.id)
        }
        if (hit.entry.isNotBlank()) {
            MetaLine("entry", hit.entry)
        }
        if (hit.docPath.isNotBlank()) {
            MetaLine("doc", hit.docPath)
        }
        if (hit.platforms.isNotBlank()) {
            MetaLine("platforms", hit.platforms)
        }
        if (hit.source.isNotBlank()) {
            MetaLine("source", hit.source)
        }
        if (hit.loaded) {
            MetaLine("loaded", "true")
        }
        if (hit.summary.isNotBlank()) {
            Text(hit.summary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DisabledMcpCard(server: DisabledMcpUi) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(server.name, style = MaterialTheme.typography.titleSmall)
        Text(
            "未启用",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
        MetaLine("url", server.url)
        if (server.description.isNotBlank()) {
            Text(server.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
