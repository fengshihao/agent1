package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.logic.business.ChatTranscriptFormatting
import com.agent1.android.productivity.ui.viewmodel.ChatLine
import com.agent1.android.productivity.ui.viewmodel.ChatRunTimelineItem
import com.agent1.android.productivity.ui.viewmodel.ChatUiState
import com.agent1.android.productivity.ui.viewmodel.RunTokenSummary
import kotlinx.coroutines.flow.distinctUntilChanged

/** 会话消息区：历史行 + run 时间线 + 流式气泡 + 待处理/汇总行，粘底自动滚动。 */
@Composable
internal fun ChatMessageList(
    state: ChatUiState,
    onPickFiles: () -> Unit,
    pickFilesEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val visibleLines = state.lines.filterNot { it.hideInChat }
    val listState = rememberLazyListState()
    val stickToBottomState = rememberSaveable(state.sessionId) { mutableStateOf(true) }
    val stickToBottom = stickToBottomState.value

    val userScrollConnection = remember(listState, stickToBottomState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 1f) {
                    stickToBottomState.value = false
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && !listState.canScrollForward) {
                    stickToBottomState.value = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState, stickToBottomState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()
            ChatListAutoscroll.isAtBottom(
                totalItems = layout.totalItemsCount,
                lastVisibleIndex = last?.index ?: -1,
                lastVisibleOffset = last?.offset ?: 0,
                lastVisibleSize = last?.size ?: 0,
                viewportEndOffset = layout.viewportEndOffset,
            )
        }.distinctUntilChanged().collect { atBottom ->
            if (atBottom) {
                stickToBottomState.value = true
            }
        }
    }

    LaunchedEffect(
        visibleLines.size,
        state.runTimeline.size,
        state.streamingText.length,
        state.streamingReasoning.length,
        state.isRunning,
        state.lastRunTokenSummary,
        stickToBottom,
    ) {
        if (!stickToBottom) return@LaunchedEffect
        listState.scrollToActualBottom()
    }
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
        modifier = modifier
            .padding(horizontal = 4.dp)
            .nestedScroll(userScrollConnection),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
    ) {
        items(
            count = visibleLines.size,
            key = { index ->
                visibleLines[index].stableKey.ifBlank { "line-$index" }
            },
        ) { index ->
            val line = visibleLines[index]
            val useMarkdown = !line.isTool &&
                line.role != "user" &&
                ChatTranscriptFormatting.shouldRenderAsMarkdown(line.content)
            MessageBubble(
                line = line,
                workspacePath = state.workspacePath,
                markdown = useMarkdown,
                reasoningStateKey = line.stableKey.ifBlank { "line-$index" },
                onPickFiles = onPickFiles,
                pickFilesEnabled = pickFilesEnabled,
            )
        }
        itemsIndexed(
            items = state.runTimeline,
            key = { index, item -> "$index:${item.id}" },
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

private suspend fun LazyListState.scrollToActualBottom() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    // 最后一条已（至少部分）可见时只补滚溢出量；避免流式增量触发的
    // scrollToItem 先把条目顶部对齐视口再翻回底部，造成“跳到开头又跳到末尾”的闪烁。
    val visibleLast = layoutInfo.visibleItemsInfo.lastOrNull()
    if (visibleLast == null || visibleLast.index != lastIndex) {
        scrollToItem(lastIndex)
        withFrameNanos { }
    }
    val last = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    if (last.index != lastIndex) return
    val overflow = (last.offset + last.size) - layoutInfo.viewportEndOffset
    if (overflow > 0) {
        scrollBy(overflow.toFloat())
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

private fun shouldShowAssistantPending(state: ChatUiState): Boolean {
    if (!state.isRunning) return false
    if (state.streamingText.isNotEmpty() || state.streamingReasoning.isNotEmpty()) return false
    if (state.runTimeline.isEmpty()) return true
    return when (val last = state.runTimeline.last()) {
        is ChatRunTimelineItem.AssistantPart -> false
        is ChatRunTimelineItem.ToolPart -> last.finished
    }
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
