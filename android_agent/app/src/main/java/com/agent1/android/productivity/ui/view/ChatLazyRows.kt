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
        /** 与上一段同属一条消息，抵消 LazyColumn item 间距。 */
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
        for (line in lines) {
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
                        showReasoning = index == 0,
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
        return out
    }
}
