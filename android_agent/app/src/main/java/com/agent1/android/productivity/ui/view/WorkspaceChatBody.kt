package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.logic.business.ChatTranscriptFormatting
import com.agent1.android.productivity.logic.business.SessionWorkspacePaths
import java.nio.file.Paths

@Composable
fun WorkspaceChatBody(
    content: String,
    workspaceAbsolutePath: String,
    markdown: Boolean,
    workspaceFilePaths: List<String>,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    /** 传入且文件可内置预览（html）时，点「打开」走 app 内预览路由。 */
    onOpenInApp: ((String) -> Unit)? = null,
) {
    if (content.isBlank()) return
    val root = remember(workspaceAbsolutePath) {
        if (workspaceAbsolutePath.isBlank()) null else Paths.get(workspaceAbsolutePath)
    }
    val logicalContent = remember(content, root) {
        SessionWorkspacePaths.scrubWorkspaceAbsolute(content, root)
    }
    val paths = remember(logicalContent, workspaceFilePaths, root) {
        ChatTranscriptFormatting.mergeWorkspaceFilePaths(logicalContent, workspaceFilePaths, root)
    }
    val useMarkdown = markdown && ChatTranscriptFormatting.shouldRenderAsMarkdown(logicalContent)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            useMarkdown -> {
                val inlineImagePaths = remember(logicalContent, root) {
                    ChatTranscriptFormatting.extractMarkdownImagePaths(logicalContent)
                        .map { ChatTranscriptFormatting.normalizeWorkspacePath(it, root) }
                        .toSet()
                }
                WorkspaceMarkdown(
                    content = ChatTranscriptFormatting.rewriteWorkspaceMarkdownHrefs(
                        ChatTranscriptFormatting.linkifyBareWorkspacePaths(logicalContent, paths),
                    ),
                    workspaceAbsolutePath = workspaceAbsolutePath,
                    onOpenInApp = onOpenInApp,
                )
                // 流式阶段（非 markdown 渲染）会对图片路径显示预览；完成后切到 markdown
                // 渲染时只有超链接，图片会“消失”。这里补渲染正文引用的图片预览，保持一致。
                // ![](path) 内联图已由 WorkspaceMarkdown 的 ImageTransformer 渲染，跳过避免重复。
                if (workspaceAbsolutePath.isNotBlank()) {
                    paths.filter {
                        ChatTranscriptFormatting.isImageWorkspacePath(it) && it !in inlineImagePaths
                    }.forEach { rel ->
                        WorkspaceImagePreview(
                            workspaceAbsolutePath = workspaceAbsolutePath,
                            workspaceRelativePath = rel,
                        )
                    }
                    // Markdown 正文已是可点链接；另起一行仅保留打开/分享（54ee3b3 整段 Markdown 后曾丢失）。
                    val actionPaths = paths.filter { rel ->
                        !ChatTranscriptFormatting.isImageWorkspacePath(rel) || rel !in inlineImagePaths
                    }
                    if (actionPaths.isNotEmpty()) {
                        WorkspaceFileAttachments(
                            workspaceAbsolutePath = workspaceAbsolutePath,
                            relativePaths = actionPaths,
                            showPathLabels = false,
                            onOpenInApp = onOpenInApp,
                        )
                    }
                }
            }
            paths.isNotEmpty() -> {
                WorkspaceInlineLinkifiedText(
                    content = logicalContent,
                    workspaceAbsolutePath = workspaceAbsolutePath,
                    workspaceFilePaths = paths,
                    textStyle = textStyle,
                    linkStyle = textStyle.copy(color = MaterialTheme.colorScheme.primary),
                    markdown = false,
                    onOpenInApp = onOpenInApp,
                )
            }
            else -> {
                Text(
                    logicalContent,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
