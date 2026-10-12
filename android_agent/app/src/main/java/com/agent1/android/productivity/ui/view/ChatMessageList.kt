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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.logic.business.ChatTranscriptFormatting
import com.agent1.android.productivity.logic.business.UserFileRequestMarkers
import com.agent1.android.productivity.ui.viewmodel.ChatLine
import com.agent1.android.productivity.ui.viewmodel.ChatUiState
import com.agent1.android.productivity.ui.viewmodel.RunTokenSummary

/** 会话消息区。跟随输出时只 scrollBy 补底边，用户上滑看历史会立刻松开。 */
@Composable
internal fun ChatMessageList(
    state: ChatUiState,
    onOpenAttachments: (UserFileRequestMarkers.Hint?) -> Unit,
    attachmentsActionEnabled: Boolean,
    modifier: Modifier = Modifier,
    onOpenInApp: ((String) -> Unit)? = null,
    /** 系统提示卡（单轮往返上限等）「设置页」链接的跳转。 */
    onOpenSettings: (() -> Unit)? = null,
    /** 列表顶部额外留白，给顶栏渐变让出位置。 */
    topContentPadding: Dp = 8.dp,
    onSetupKeyDraftChange: (String) -> Unit = {},
    onSubmitSetupKey: () -> Unit = {},
) {
    val visibleLines = state.lines.filterNot { it.hideInChat }
    val lazyRows = remember(visibleLines, state.workspacePath) {
        ChatLazyRowExpansion.expandTranscriptLines(visibleLines, state.workspacePath)
    }
    val timelineRows = remember(state.runTimeline, state.streamingReasoning, state.isRunning) {
        ChatToolBatch.groupTimeline(
            items = state.runTimeline,
            streamingReasoning = state.streamingReasoning,
            isRunning = state.isRunning,
        )
    }
    val listState = rememberLazyListState()
    val followTail = remember(state.sessionId) { mutableStateOf(true) }
    val didAnchor = remember(state.sessionId) { mutableStateOf(false) }
    val followConnection = remember(listState, followTail) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (ChatListAutoscroll.shouldReleaseStickToBottom(available.y)) {
                    followTail.value = false
                } else if (
                    ChatListAutoscroll.shouldRestickToBottom(
                        consumedY = consumed.y,
                        canScrollForward = listState.canScrollForward,
                        atBottom = ChatListAutoscroll.layoutIsAtBottom(listState.layoutInfo),
                    )
                ) {
                    followTail.value = true
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(state.isRunning) {
        if (state.isRunning) followTail.value = true
    }
    LaunchedEffect(state.sessionId, state.isLoadingTranscript, lazyRows.size) {
        if (state.isLoadingTranscript || didAnchor.value || lazyRows.isEmpty()) return@LaunchedEffect
        repeat(8) {
            val count = listState.layoutInfo.totalItemsCount
            if (count > 0) {
                didAnchor.value = true
                listState.scrollToItem(count - 1)
                withFrameNanos { }
                listState.followBottomByScroll()
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }
    LaunchedEffect(
        followTail.value,
        didAnchor.value,
        lazyRows.size,
        state.runTimeline.size,
        state.streamingText,
        state.streamingReasoning,
        state.isRunning,
        state.inlineApiSetupVisible,
    ) {
        if (!followTail.value || !didAnchor.value) return@LaunchedEffect
        listState.followBottomByScroll()
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
        !state.isRunning &&
        !state.inlineApiSetupVisible
    if (showEmptyHint) {
        EmptyChatHint(modifier)
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .nestedScroll(followConnection),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(top = topContentPadding, bottom = 8.dp, start = 8.dp, end = 8.dp),
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
                        onOpenAttachments = onOpenAttachments,
                        attachmentsActionEnabled = attachmentsActionEnabled,
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
                is ChatLazyRow.TurnActivity -> {
                    TurnActivityBubble(
                        stateKey = row.key,
                        pieces = row.pieces,
                    )
                }
            }
        }
        items(
            items = timelineRows,
            key = { row -> row.key },
        ) { row ->
            when (row) {
                is RunTimelineRow.Assistant -> {
                    val item = row.part
                    MessageBubble(
                        line = ChatLine(
                            role = "assistant",
                            content = item.content,
                            stableKey = item.id,
                        ),
                        workspacePath = state.workspacePath,
                        markdown = ChatTranscriptFormatting.shouldRenderAsMarkdown(item.content),
                        reasoningStateKey = item.id,
                        onOpenAttachments = onOpenAttachments,
                        attachmentsActionEnabled = false,
                        onOpenInApp = onOpenInApp,
                        showReasoning = false,
                    )
                }
                is RunTimelineRow.Activity -> {
                    TurnActivityBubble(
                        stateKey = row.key,
                        pieces = row.pieces,
                        runInProgress = state.isRunning,
                    )
                }
            }
        }
        if (state.streamingText.isNotEmpty()) {
            item(key = "assistant-streaming") {
                MessageBubble(
                    line = ChatLine(
                        role = "assistant",
                        content = state.streamingText + if (state.streamingText.isNotEmpty()) "▌" else "",
                        stableKey = "assistant-streaming",
                    ),
                    workspacePath = state.workspacePath,
                    markdown = false,
                    reasoningStateKey = "assistant-streaming-${state.sessionId}",
                    onOpenAttachments = onOpenAttachments,
                    attachmentsActionEnabled = false,
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
        if (state.inlineApiSetupVisible) {
            item(key = "inline-api-setup") {
                ApiSetupInlineCard(
                    draft = state.setupKeyDraft,
                    isSaving = state.setupKeySaving,
                    fieldHighlight = state.setupKeyFieldHighlight,
                    fieldErrorText = state.setupKeyFieldError,
                    onDraftChange = onSetupKeyDraftChange,
                    onSubmitDraft = onSubmitSetupKey,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private suspend fun LazyListState.followBottomByScroll() {
    repeat(6) {
        val info = layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return
        val px = ChatListAutoscroll.followScrollByPx(
            totalItems = info.totalItemsCount,
            lastVisibleIndex = last.index,
            lastVisibleOffset = last.offset,
            lastVisibleSize = last.size,
            viewportEndOffset = info.viewportEndOffset,
        )
        if (px <= 0) return
        scrollBy(px.toFloat())
        withFrameNanos { }
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
