package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.logic.business.ChatTranscriptFormatting
import com.agent1.android.productivity.ui.viewmodel.ChatLine
import com.agent1.android.productivity.ui.viewmodel.ChatRunTimelineItem
import com.agent1.android.productivity.ui.viewmodel.ChatUiState
import com.agent1.android.productivity.ui.viewmodel.RunTokenSummary
import com.agent1.android.productivity.ui.viewmodel.shouldShowAssistantPending

/** 会话消息区：普通 LazyColumn（无贴底/scrollToItem）；新消息时暂不自动滚到底，避免与浏览历史打架。 */
@Composable
internal fun ChatMessageList(
    state: ChatUiState,
    onPickFiles: () -> Unit,
    pickFilesEnabled: Boolean,
    modifier: Modifier = Modifier,
    onOpenInApp: ((String) -> Unit)? = null,
    /** 系统提示卡（单轮往返上限等）「设置页」链接的跳转。 */
    onOpenSettings: (() -> Unit)? = null,
) {
    val visibleLines = state.lines.filterNot { it.hideInChat }
    val lazyRows = remember(visibleLines, state.workspacePath) {
        ChatLazyRowExpansion.expandTranscriptLines(visibleLines, state.workspacePath)
    }
    val listState = rememberLazyListState()
    if (state.isLoadingTranscript && state.lines.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "加载对话…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val showEmptyHint = visibleLines.isEmpty() &&
        state.runTimeline.isEmpty() &&
        state.streamingText.isEmpty() &&
        state.streamingReasoning.isEmpty() &&
        !state.isRunning
    if (showEmptyHint) {
        EmptyChatHint(modifier)
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
    ) {
        items(
            count = lazyRows.size,
            key = { index -> lazyRows[index].key },
        ) { index ->
            when (val row = lazyRows[index]) {
                is ChatLazyRow.Message -> {
                    val line = row.line
                    val slice = row.contentSlice
                    val bodyForMarkdown = slice ?: line.content
                    val useMarkdown = !line.isTool &&
                        !line.isSystemNotice &&
                        line.role != "user" &&
                        ChatTranscriptFormatting.shouldRenderAsMarkdown(bodyForMarkdown)
                    MessageBubble(
                        line = line,
                        workspacePath = state.workspacePath,
                        markdown = useMarkdown,
                        reasoningStateKey = line.stableKey.ifBlank { "line-$index" },
                        onPickFiles = onPickFiles,
                        pickFilesEnabled = pickFilesEnabled,
                        onOpenInApp = onOpenInApp,
                        onOpenSettings = onOpenSettings,
                        skipTrailingImagePreviews = row.skipTrailingImagePreviews,
                        contentSlice = slice,
                        showReasoning = row.showReasoning,
                        showPickFiles = row.showPickFiles,
                        modifier = if (row.tightTop) {
                            Modifier.padding(top = (-6).dp)
                        } else {
                            Modifier
                        },
                    )
                }
                is ChatLazyRow.DetachedImage -> {
                    DetachedWorkspaceImageBubble(
                        workspacePath = state.workspacePath,
                        relativePath = row.relativePath,
                    )
                }
            }
        }
        itemsIndexed(
            items = state.runTimeline,
            // key 不能含 index：中途插入会让后续所有条目 key 变化，滚动锚点丢失导致跳动/闪烁。
            key = { _, item -> item.id },
        ) { _, item ->
            when (item) {
                is ChatRunTimelineItem.AssistantPart -> {
                    MessageBubble(
                        line = ChatLine(
                            role = "assistant",
                            content = item.content,
                            reasoning = item.reasoning,
                            stableKey = item.id,
                        ),
                        workspacePath = state.workspacePath,
                        markdown = ChatTranscriptFormatting.shouldRenderAsMarkdown(item.content),
                        reasoningStateKey = item.id,
                        onPickFiles = onPickFiles,
                        pickFilesEnabled = false,
                        onOpenInApp = onOpenInApp,
                    )
                }
                is ChatRunTimelineItem.ToolPart -> {
                    CollapsibleToolCallBubble(
                        stateKey = item.id,
                        toolName = item.toolName,
                        argsPreview = item.argsPreview,
                        finished = item.finished,
                        isError = item.isError,
                        resultSummary = item.resultSummary,
                        progressLines = item.progressLines,
                    )
                }
            }
        }
        if (shouldShowAssistantPending(state)) {
            item(key = "assistant-pending") {
                AssistantPendingBubble(
                    label = state.runActivityLabel ?: "等待助手…",
                )
            }
        }
        if (state.streamingText.isNotEmpty() || state.streamingReasoning.isNotEmpty()) {
            item(key = "assistant-streaming") {
                MessageBubble(
                    line = ChatLine(
                        role = "assistant",
                        content = state.streamingText + if (state.streamingText.isNotEmpty()) "▌" else "",
                        reasoning = state.streamingReasoning +
                            if (state.streamingReasoning.isNotEmpty() && state.streamingText.isEmpty()) "▌" else "",
                        stableKey = "assistant-streaming",
                    ),
                    workspacePath = state.workspacePath,
                    markdown = false,
                    reasoningStateKey = "assistant-streaming-${state.sessionId}",
                    onPickFiles = onPickFiles,
                    pickFilesEnabled = false,
                    onOpenInApp = onOpenInApp,
                )
            }
        }
        if (!state.isRunning) {
            state.lastRunTokenSummary?.let { summary ->
                item(key = "run-token-summary") {
                    RunTokenSummaryRow(summary)
                }
            }
        }
    }
}

@Composable
private fun RunTokenSummaryRow(summary: RunTokenSummary) {
    val color = if (summary.failed) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = summary.displayText(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}


@Composable
private fun EmptyChatHint(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "从一条消息开始",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "左上角可以打开已有会话",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
