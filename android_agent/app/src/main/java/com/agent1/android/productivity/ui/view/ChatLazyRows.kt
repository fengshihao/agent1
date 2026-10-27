package com.agent1.android.productivity.ui.view

import com.agent1.android.productivity.logic.business.ChatLazyContentSplit
import com.agent1.android.productivity.logic.business.ChatTranscriptFormatting
import com.agent1.android.productivity.ui.viewmodel.ChatLine
import java.nio.file.Paths

/** 将一条消息拆成多行 Lazy item，降低单条过高导致的滚动锚点跳变。 */
internal sealed class ChatLazyRow {
    abstract val key: String

    data class Message(
        val line: ChatLine,
        /** 非 null 时只渲染该片段（同一条消息的连续 Lazy item）。 */
        val contentSlice: String? = null,
        val segmentIndex: Int = 0,
        val segmentCount: Int = 1,
        val skipTrailingImagePreviews: Boolean = false,
        val showReasoning: Boolean = true,
        val showPickFiles: Boolean = true,
        /** 与上一段同属一条消息，上面不再留列表间距。 */
        val tightTop: Boolean = false,
    ) : ChatLazyRow() {
        private val stable: String = line.stableKey.ifBlank { "line" }
        override val key: String = when {
            segmentCount > 1 -> "$stable::seg::$segmentIndex"
            skipTrailingImagePreviews -> "$stable::body"
            else -> stable
        }
    }

    data class DetachedImage(
        val parentStableKey: String,
        val relativePath: String,
    ) : ChatLazyRow() {
        override val key: String = "$parentStableKey::img::$relativePath"
    }

    /** 一轮的思考和工具调用合成一条 Lazy item。 */
    data class TurnActivity(
        val activityKey: String,
        val pieces: List<TurnPiece>,
    ) : ChatLazyRow() {
        override val key: String = "turn::$activityKey"
    }
}

internal object ChatLazyRowExpansion {
    /** 预览图 ≥2 时拆成独立 Lazy item；仍是一条消息，只是列表结构拆开。 */
    private const val SPLIT_IMAGE_PREVIEW_MIN = 2

    fun expandTranscriptLines(
        lines: List<ChatLine>,
        workspaceAbsolutePath: String,
    ): List<ChatLazyRow> {
        if (lines.isEmpty()) return emptyList()
        val root = if (workspaceAbsolutePath.isBlank()) null else Paths.get(workspaceAbsolutePath)
        val out = ArrayList<ChatLazyRow>(lines.size + 8)
        var index = 0
        while (index < lines.size) {
            if (ChatToolBatch.isTurnBoundary(lines[index])) {
                appendMessage(out, lines[index], root, showReasoning = true)
                index++
                continue
            }
            val turn = ArrayList<ChatLine>()
            while (index < lines.size && !ChatToolBatch.isTurnBoundary(lines[index])) {
                turn.add(lines[index])
                index++
            }
            val pieces = ChatToolBatch.piecesFromLines(turn)
            if (pieces.isNotEmpty()) {
                val anchor = turn.first().stableKey.ifBlank { "turn" }
                out.add(ChatLazyRow.TurnActivity(activityKey = anchor, pieces = pieces))
            }
            for (line in turn) {
                if (line.isTool) continue
                if (line.content.isBlank() && !line.requestUserPickFiles) continue
                appendMessage(out, line, root, showReasoning = false)
            }
        }
        return out
    }

    private fun appendMessage(
        out: MutableList<ChatLazyRow>,
        line: ChatLine,
        root: java.nio.file.Path?,
        showReasoning: Boolean,
    ) {
            val stable = line.stableKey.ifBlank { "line-${line.role}" }
            val assistantBody = line.role == "assistant" && !line.isTool && !line.isSystemNotice
            val useMarkdown = assistantBody &&
                ChatTranscriptFormatting.shouldRenderAsMarkdown(line.content)
            val bodyBlocks = if (assistantBody &&
                ChatLazyContentSplit.shouldSplitAssistantBody(line.content, useMarkdown)
            ) {
                ChatLazyContentSplit.splitAssistantBody(line.content)
            } else {
                listOf(line.content)
            }
            val previews = if (assistantBody) {
                ChatTranscriptFormatting.trailingImagePreviewPaths(
                    line.content,
                    line.workspaceFilePaths,
                    root,
                )
            } else {
                emptyList()
            }
            val splitImages = previews.size >= SPLIT_IMAGE_PREVIEW_MIN
            bodyBlocks.forEachIndexed { index, block ->
                val isLast = index == bodyBlocks.lastIndex
                out.add(
                    ChatLazyRow.Message(
                        line = line,
                        contentSlice = if (bodyBlocks.size == 1) null else block,
                        segmentIndex = index,
                        segmentCount = bodyBlocks.size,
                        skipTrailingImagePreviews = !isLast || splitImages,
                        showReasoning = showReasoning && index == 0,
                        showPickFiles = isLast,
                        tightTop = index > 0,
                    ),
                )
            }
            if (splitImages) {
                for (path in previews) {
                    out.add(ChatLazyRow.DetachedImage(parentStableKey = stable, relativePath = path))
                }
            }
    }
}
