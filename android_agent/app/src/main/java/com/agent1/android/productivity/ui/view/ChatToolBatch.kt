package com.agent1.android.productivity.ui.view

import com.agent1.android.productivity.ui.viewmodel.ChatLine
import com.agent1.android.productivity.ui.viewmodel.ChatRunTimelineItem

/** 一轮里的一段思考或一次工具调用，按发生顺序放进同一张折叠卡。 */
internal sealed class TurnPiece {
    data class Reasoning(
        val key: String,
        val text: String,
        val inProgress: Boolean,
    ) : TurnPiece()

    data class Tool(
        val entry: ToolBatchEntry,
    ) : TurnPiece()
}

/** 一轮里连续的工具调用，折叠成一张卡片后再逐条展开。 */
internal data class ToolBatchEntry(
    val key: String,
    val toolName: String,
    val argsPreview: String,
    val finished: Boolean,
    val isError: Boolean,
    val resultSummary: String,
    val progressLines: List<String>,
)

internal sealed class RunTimelineRow {
    abstract val key: String

    data class Activity(
        val pieces: List<TurnPiece>,
    ) : RunTimelineRow() {
        override val key: String = "live-turn-activity"
    }

    data class Assistant(
        val part: ChatRunTimelineItem.AssistantPart,
    ) : RunTimelineRow() {
        override val key: String = part.id
    }
}

internal object ChatToolBatch {
    fun fromLine(line: ChatLine): ToolBatchEntry = ToolBatchEntry(
        key = line.stableKey.ifBlank { line.toolName ?: "tool" },
        toolName = line.toolName ?: "工具",
        argsPreview = line.toolArgsPreview.orEmpty(),
        finished = line.toolFinished,
        isError = line.toolIsError,
        resultSummary = line.content,
        progressLines = emptyList(),
    )

    fun fromPart(part: ChatRunTimelineItem.ToolPart): ToolBatchEntry = ToolBatchEntry(
        key = part.id,
        toolName = part.toolName,
        argsPreview = part.argsPreview,
        finished = part.finished,
        isError = part.isError,
        resultSummary = part.resultSummary,
        progressLines = part.progressLines,
    )

    /** 用户消息和系统提示切开轮次。一轮里的思考与工具调用收成一张卡，正文留在卡后面。 */
    fun isTurnBoundary(line: ChatLine): Boolean =
        line.isSystemNotice || (line.role == "user" && !line.isTool)

    fun piecesFromLines(lines: List<ChatLine>): List<TurnPiece> {
        val pieces = ArrayList<TurnPiece>()
        for (line in lines) {
            if (line.isTool) {
                pieces.add(TurnPiece.Tool(fromLine(line)))
                continue
            }
            val reasoning = line.reasoning.trim()
            if (line.role == "assistant" && reasoning.isNotEmpty()) {
                pieces.add(
                    TurnPiece.Reasoning(
                        key = "${line.stableKey.ifBlank { "line" }}::reasoning",
                        text = reasoning,
                        inProgress = false,
                    ),
                )
            }
        }
        return pieces
    }

    /**
     * 进行中的时间线：思考和工具全部进一张卡。
     * 助手正文按原顺序排在卡后面，不再各自带思考折叠。
     */
    fun groupTimeline(
        items: List<ChatRunTimelineItem>,
        streamingReasoning: String = "",
        isRunning: Boolean = false,
    ): List<RunTimelineRow> {
        val pieces = ArrayList<TurnPiece>()
        val assistants = ArrayList<ChatRunTimelineItem.AssistantPart>()
        for (item in items) {
            when (item) {
                is ChatRunTimelineItem.AssistantPart -> {
                    val reasoning = item.reasoning.trim()
                    if (reasoning.isNotEmpty()) {
                        pieces.add(
                            TurnPiece.Reasoning(
                                key = "${item.id}::reasoning",
                                text = reasoning,
                                inProgress = false,
                            ),
                        )
                    }
                    if (item.content.isNotBlank()) {
                        assistants.add(item.copy(reasoning = ""))
                    }
                }
                is ChatRunTimelineItem.ToolPart -> pieces.add(TurnPiece.Tool(fromPart(item)))
            }
        }
        val liveReasoning = streamingReasoning.trim().trimEnd('▌', ' ').trim()
        if (liveReasoning.isNotEmpty()) {
            pieces.add(
                TurnPiece.Reasoning(
                    key = "streaming-reasoning",
                    text = liveReasoning,
                    inProgress = true,
                ),
            )
        }
        val out = ArrayList<RunTimelineRow>(assistants.size + 1)
        if (pieces.isNotEmpty() || isRunning) out.add(RunTimelineRow.Activity(pieces))
        assistants.forEach { out.add(RunTimelineRow.Assistant(it)) }
        return out
    }

    fun title(pieces: List<TurnPiece>): String {
        if (pieces.isEmpty()) return "思考与执行"
        val tools = pieces.mapNotNull { (it as? TurnPiece.Tool)?.entry }
        val thoughts = pieces.filterIsInstance<TurnPiece.Reasoning>()
        val runningTools = tools.count { !it.finished }
        val failed = tools.count { showsFailure(it) }
        val names = tools.map { it.toolName }.distinct()
        val shown = names.take(3).joinToString("、")
        val suffix = if (names.size > 3) " 等" else ""
        val active = pieces.lastOrNull {
            when (it) {
                is TurnPiece.Reasoning -> it.inProgress
                is TurnPiece.Tool -> !it.entry.finished
            }
        }
        val head = when (active) {
            is TurnPiece.Tool -> "正在调用 ${active.entry.toolName}"
            is TurnPiece.Reasoning -> "正在思考"
            else -> when {
                tools.isEmpty() -> "思考过程"
                thoughts.isNotEmpty() -> "思考并调用了 $shown$suffix"
                else -> "调用了 $shown$suffix"
            }
        }
        val count = if (tools.isNotEmpty()) " · 共 ${tools.size} 次" else ""
        val progress = if (runningTools > 0) " · 进行中 $runningTools" else ""
        val fail = if (failed > 0) " · ${failed} 次失败" else ""
        return head + count + progress + fail
    }

    /** 展开后每一行的标题，带上这一步自己的进度或结果。 */
    fun pieceLabel(piece: TurnPiece): String = when (piece) {
        is TurnPiece.Reasoning -> {
            val line = piece.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            val head = if (piece.inProgress) "正在思考" else "思考过程"
            val shown = if (line.length <= 80) line else line.take(80) + "…"
            if (shown.isEmpty()) head else "$head · $shown"
        }
        is TurnPiece.Tool -> {
            val entry = piece.entry
            val line = entry.resultSummary.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            val shown = if (line.length <= 80) line else line.take(80) + "…"
            when {
                !entry.finished && entry.progressLines.isEmpty() -> "正在调用 ${entry.toolName}"
                !entry.finished -> {
                    val progress = entry.progressLines.last()
                    val clipped = if (progress.length <= 80) progress else progress.take(80) + "…"
                    "${entry.toolName} · $clipped"
                }
                showsFailure(entry) && shown.isEmpty() -> "${entry.toolName} 失败"
                showsFailure(entry) -> "${entry.toolName} 失败 · $shown"
                shown.isEmpty() -> "${entry.toolName} 已完成"
                else -> "${entry.toolName} · $shown"
            }
        }
    }

    /** 折叠态下一行：最近一段思考或最近一次工具结果。 */
    fun detail(pieces: List<TurnPiece>): String {
        val last = pieces.lastOrNull() ?: return ""
        return when (last) {
            is TurnPiece.Reasoning -> {
                val line = last.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                when {
                    line.isEmpty() && last.inProgress -> "思考中…"
                    line.length <= 80 -> line.ifEmpty { "思考过程" }
                    else -> line.take(80) + "…"
                }
            }
            is TurnPiece.Tool -> toolDetail(last.entry)
        }
    }

    fun isRunning(pieces: List<TurnPiece>): Boolean = pieces.any { piece ->
        when (piece) {
            is TurnPiece.Reasoning -> piece.inProgress
            is TurnPiece.Tool -> !piece.entry.finished
        }
    }

    fun showsFailure(entry: ToolBatchEntry): Boolean = entry.finished && entry.isError

    private fun toolDetail(last: ToolBatchEntry): String {
        if (!last.finished) {
            return last.progressLines.lastOrNull()?.take(80) ?: "${last.toolName} 执行中…"
        }
        val firstLine = last.resultSummary.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (firstLine.isEmpty()) {
            return if (showsFailure(last)) "${last.toolName} 失败" else "${last.toolName} 已完成"
        }
        return if (firstLine.length <= 80) firstLine else firstLine.take(80) + "…"
    }
}
