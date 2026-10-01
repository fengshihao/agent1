package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.ui.viewmodel.RegisteredToolUi
import com.agent1.android.productivity.ui.viewmodel.SystemPromptViewModel
import com.agent1.android.productivity.ui.viewmodel.countIgnoreCase

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemPromptScreen(
    viewModel: SystemPromptViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxSize()) {
        AgentTopBar(
            title = "系统提示词",
            subtitle = "当前会话发给模型的原文",
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
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "这里是下一次 Run 的系统提示词，以及已经注册的工具名称、说明和参数。目录、权限、QuickJS、WebView 如果写进了提示词或工具说明，会原样出现在下面。能力索引里的 Skill 和 MCP 用能力检索查看。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.busy) {
                CircularProgressIndicator()
            }
            if (!state.errorMessage.isNullOrBlank()) {
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!state.copyMessage.isNullOrBlank()) {
                Text(state.copyMessage.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
            if (state.prompt.isNotBlank() || state.tools.isNotEmpty()) {
                Text("关键词", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.glances.forEach { glance ->
                        val empty = glance.promptHits == 0 && glance.toolHits == 0
                        FilterChip(
                            selected = state.find.equals(glance.word, ignoreCase = true),
                            onClick = { viewModel.useFind(glance.word) },
                            label = {
                                Text(
                                    if (empty) {
                                        "${glance.word}：没有"
                                    } else {
                                        "${glance.word}：提示词 ${glance.promptHits}，工具 ${glance.toolHits}"
                                    },
                                )
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = state.find,
                    onValueChange = viewModel::onFindChange,
                    label = { Text("在提示词和工具说明里查找") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (state.find.isNotBlank()) {
                    val promptHits = countIgnoreCase(state.prompt, state.find)
                    val toolHits = state.tools.sumOf { tool ->
                        countIgnoreCase(tool.name, state.find) +
                            countIgnoreCase(tool.description, state.find) +
                            countIgnoreCase(tool.parametersSchema, state.find)
                    }
                    Text(
                        if (promptHits == 0 && toolHits == 0) {
                            "提示词和已注册工具说明里都没有「${state.find}」。"
                        } else {
                            "提示词 $promptHits 处，工具说明 $toolHits 处。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (promptHits == 0 && toolHits == 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(state.prompt))
                        viewModel.onCopied()
                    },
                    enabled = state.prompt.isNotBlank(),
                ) {
                    Text("复制系统提示词")
                }
                Text("系统提示词", style = MaterialTheme.typography.titleSmall)
                SelectionContainer {
                    Text(
                        highlight(state.prompt, state.find),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                Text(
                    "已注册工具（${state.tools.size}）",
                    style = MaterialTheme.typography.titleSmall,
                )
                val shownTools = if (state.find.isBlank()) {
                    state.tools
                } else {
                    state.tools.filter { tool -> tool.matches(state.find) }
                }
                if (state.find.isNotBlank() && shownTools.size != state.tools.size) {
                    Text(
                        "只显示说明里含「${state.find}」的工具，其余 ${state.tools.size - shownTools.size} 个已折叠。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (shownTools.isEmpty()) {
                    Text(
                        "没有匹配的工具。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                shownTools.forEach { tool ->
                    RegisteredToolBlock(tool, state.find)
                }
            }
        }
    }
}

@Composable
private fun RegisteredToolBlock(tool: RegisteredToolUi, find: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(highlight(tool.name, find), style = MaterialTheme.typography.titleSmall)
        if (tool.description.isNotBlank()) {
            SelectionContainer {
                Text(highlight(tool.description, find), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (tool.parametersSchema.isNotBlank()) {
            Text("参数", style = MaterialTheme.typography.labelMedium)
            SelectionContainer {
                Text(
                    highlight(tool.parametersSchema, find),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

private fun RegisteredToolUi.matches(find: String): Boolean {
    return countIgnoreCase(name, find) +
        countIgnoreCase(description, find) +
        countIgnoreCase(parametersSchema, find) > 0
}

@Composable
private fun highlight(text: String, query: String): AnnotatedString {
    if (query.isBlank() || text.isEmpty() || query.length > text.length) {
        return AnnotatedString(text)
    }
    val background = MaterialTheme.colorScheme.primaryContainer
    val haystack = text.lowercase()
    val needle = query.lowercase()
    return buildAnnotatedString {
        var start = 0
        while (start <= text.length - needle.length) {
            val index = haystack.indexOf(needle, start)
            if (index < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, index))
            pushStyle(SpanStyle(background = background, fontWeight = FontWeight.SemiBold))
            append(text.substring(index, index + needle.length))
            pop()
            start = index + needle.length
        }
    }
}
