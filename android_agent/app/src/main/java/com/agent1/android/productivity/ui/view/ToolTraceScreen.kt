package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agent1.android.R
import com.agent1.android.productivity.ui.viewmodel.ChatRunTimelineItem
import com.agent1.android.productivity.ui.viewmodel.ChatViewModel

@Composable
fun ToolTraceScreen(
    sessionId: String,
    onBack: () -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val viewModel: ChatViewModel = viewModel(
        key = "chat-$sessionId",
        factory = simpleViewModelFactory {
            ChatViewModel(appContext, sessionId, "")
        },
    )
    val state by viewModel.state.collectAsState()
    val scroll = rememberScrollState()
    Scaffold(
        topBar = {
            AgentTopBar(
                title = stringResource(R.string.menu_tool_trace),
                subtitle = null,
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {},
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.tool_trace_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val historyTools = state.lines.filter { it.isTool }
            historyTools.forEach { line ->
                ToolTraceBlock(
                    toolName = line.toolName ?: "tool",
                    argsPreview = line.toolArgsPreview.orEmpty(),
                    resultSummary = line.content,
                    isError = line.toolIsError,
                )
            }
            state.runTimeline.filterIsInstance<ChatRunTimelineItem.ToolPart>().forEach { part ->
                ToolTraceBlock(
                    toolName = part.toolName,
                    argsPreview = part.argsPreview,
                    resultSummary = part.resultSummary,
                    isError = part.isError,
                )
            }
            if (historyTools.isEmpty() &&
                state.runTimeline.none { it is ChatRunTimelineItem.ToolPart }
            ) {
                Text(
                    "（暂无工具记录）",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToolTraceBlock(
    toolName: String,
    argsPreview: String,
    resultSummary: String,
    isError: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            toolName,
            style = MaterialTheme.typography.titleSmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (argsPreview.isNotBlank()) {
            Text(
                argsPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (resultSummary.isNotBlank()) {
            Text(
                resultSummary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun <T : androidx.lifecycle.ViewModel> simpleViewModelFactory(
    create: () -> T,
): androidx.lifecycle.ViewModelProvider.Factory {
    return object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = create() as T
    }
}
