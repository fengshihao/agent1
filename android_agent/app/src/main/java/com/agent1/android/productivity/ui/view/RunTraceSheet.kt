package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agent1.android.R
import com.agent1.android.productivity.ui.viewmodel.ChatLine
import com.agent1.android.productivity.ui.viewmodel.ChatRunTimelineItem
import com.agent1.android.productivity.ui.viewmodel.ChatUiState

internal data class RunTraceEntry(
    val title: String,
    val detail: String,
    val isError: Boolean = false,
)

/** 半屏里按时间顺序摆出思考全文和每次工具的参数、成败、回执。 */
internal object RunTrace {
    fun entries(state: ChatUiState): List<RunTraceEntry> = entries(
        lines = state.lines,
        timeline = state.runTimeline,
        streamingReasoning = state.streamingReasoning,
        isRunning = state.isRunning,
    )

    fun entries(
        lines: List<ChatLine>,
        timeline: List<ChatRunTimelineItem>,
        streamingReasoning: String,
        isRunning: Boolean,
    ): List<RunTraceEntry> {
        val out = ArrayList<RunTraceEntry>()
        for (line in lines) {
            if (!line.isTool && line.reasoning.isNotBlank()) {
                out.add(RunTraceEntry("思考", line.reasoning.trim()))
            }
            if (line.isTool) out.add(fromTool(
                name = line.toolName,
                args = line.toolArgsPreview.orEmpty(),
                result = line.content,
                finished = line.toolFinished,
                isError = line.toolIsError,
            ))
        }
        if (!isRunning) return out
        for (item in timeline) {
            when (item) {
                is ChatRunTimelineItem.AssistantPart -> {
                    if (item.reasoning.isNotBlank()) out.add(RunTraceEntry("思考", item.reasoning.trim()))
                }
                is ChatRunTimelineItem.ToolPart -> out.add(fromTool(
                    name = item.toolName,
                    args = item.argsPreview,
                    result = item.resultSummary,
                    finished = item.finished,
                    isError = item.isError,
                ))
            }
        }
        if (streamingReasoning.isNotBlank()) out.add(RunTraceEntry("思考", streamingReasoning.trim()))
        return out
    }

    private fun fromTool(
        name: String?,
        args: String,
        result: String,
        finished: Boolean,
        isError: Boolean,
    ): RunTraceEntry {
        val status = when {
            !finished -> "进行中"
            isError -> "失败"
            else -> "成功"
        }
        val detail = buildString {
            val trimmedArgs = args.trim()
            val trimmedResult = result.trim()
            if (trimmedArgs.isNotEmpty()) append(trimmedArgs)
            if (trimmedResult.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(trimmedResult)
            }
        }
        return RunTraceEntry(
            title = "${name?.trim().orEmpty().ifEmpty { "工具" }} · $status",
            detail = detail,
            isError = isError,
        )
    }
}

/** 盖在当前对话上的半屏。往上拖可以看完，点外面关掉。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunTraceSheet(
    visible: Boolean,
    state: ChatUiState,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val halfHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    val entries = RunTrace.entries(state)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(halfHeight)
                .padding(horizontal = 20.dp),
        ) {
            Text(
                stringResource(R.string.menu_tool_trace),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.tool_trace_hint),
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (entries.isEmpty()) {
                    Text(
                        "（暂无运行记录）",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                entries.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (entry.isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        if (entry.detail.isNotBlank()) {
                            Text(
                                entry.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
