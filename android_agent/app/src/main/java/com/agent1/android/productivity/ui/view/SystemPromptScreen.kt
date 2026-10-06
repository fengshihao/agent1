package com.agent1.android.productivity.ui.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.ui.viewmodel.RegisteredToolUi
import com.agent1.android.productivity.ui.viewmodel.SystemPromptViewModel
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

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
            subtitle = state.copyMessage,
            leading = {
                TopBarIconButton(
                    icon = Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
            },
            actions = {
                TopBarIconButton(
                    icon = AgentIcons.ContentCopy,
                    contentDescription = "复制系统提示词",
                    onClick = {
                        clipboard.setText(AnnotatedString(state.prompt))
                        viewModel.onCopied()
                    },
                    enabled = state.prompt.isNotBlank(),
                )
            },
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
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (!state.errorMessage.isNullOrBlank()) {
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.prompt.isNotBlank()) {
                PromptMarkdown(state.prompt)
            }
            if (state.tools.isNotEmpty()) {
                Text(
                    "工具 ${state.tools.size}",
                    style = MaterialTheme.typography.titleSmall,
                )
                state.tools.forEach { tool ->
                    CollapsibleToolSpec(tool)
                }
            }
        }
    }
}

@Composable
private fun PromptMarkdown(content: String) {
    val body = MaterialTheme.typography.bodyMedium
    val heading = body.copy(fontWeight = FontWeight.SemiBold)
    Markdown(
        content = content,
        modifier = Modifier.fillMaxWidth(),
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurface,
            codeBackground = MaterialTheme.colorScheme.surfaceVariant,
        ),
        typography = markdownTypography(
            h1 = heading,
            h2 = heading,
            h3 = heading,
            h4 = body,
            h5 = body,
            h6 = body,
            text = body,
            paragraph = body,
            code = body.copy(fontFamily = FontFamily.Monospace),
            table = body,
        ),
    )
}

@Composable
private fun CollapsibleToolSpec(tool: RegisteredToolUi) {
    var expanded by rememberSaveable(tool.name) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowDown
                } else {
                    Icons.Filled.KeyboardArrowRight
                },
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(tool.name, style = MaterialTheme.typography.titleSmall)
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tool.description.isNotBlank()) {
                    Text(tool.description, style = MaterialTheme.typography.bodySmall)
                }
                if (tool.parametersSchema.isNotBlank()) {
                    Text(
                        tool.parametersSchema,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }
}
