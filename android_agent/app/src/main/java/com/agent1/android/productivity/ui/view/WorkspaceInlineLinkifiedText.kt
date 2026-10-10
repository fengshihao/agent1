package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.logic.business.ChatTranscriptFormatting
import com.agent1.android.productivity.logic.business.LinkifiedSegment
import com.agent1.android.productivity.logic.business.WorkspaceFileActions
import java.nio.file.Path
import java.nio.file.Paths

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceInlineLinkifiedText(
    content: String,
    workspaceAbsolutePath: String,
    workspaceFilePaths: List<String>,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    linkStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    markdown: Boolean = false,
    /** 传入时 html 等可内置预览的文件改走 app 内预览路由。 */
    onOpenInApp: ((String) -> Unit)? = null,
) {
    if (workspaceAbsolutePath.isBlank()) {
        Text(content, modifier = modifier, style = textStyle, color = MaterialTheme.colorScheme.onSurface)
        return
    }
    val root = remember(workspaceAbsolutePath) { Paths.get(workspaceAbsolutePath) }
    val paths = remember(content, workspaceFilePaths, root) {
        ChatTranscriptFormatting.mergeWorkspaceFilePaths(content, workspaceFilePaths, root)
    }
    val segments = remember(content, paths, root) {
        ChatTranscriptFormatting.splitLinkifiedSegments(content, paths, root)
    }
    if (markdown) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RenderLinkifiedSegments(
                segments = segments,
                markdown = true,
                workspaceAbsolutePath = workspaceAbsolutePath,
                root = root,
                textStyle = textStyle,
                linkStyle = linkStyle,
                onOpenInApp = onOpenInApp,
            )
        }
    } else {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RenderLinkifiedSegments(
                segments = segments,
                markdown = false,
                workspaceAbsolutePath = workspaceAbsolutePath,
                root = root,
                textStyle = textStyle,
                linkStyle = linkStyle,
                onOpenInApp = onOpenInApp,
            )
        }
    }
}

@Composable
private fun RenderLinkifiedSegments(
    segments: List<LinkifiedSegment>,
    markdown: Boolean,
    workspaceAbsolutePath: String,
    root: Path,
    textStyle: TextStyle,
    linkStyle: TextStyle,
    onOpenInApp: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val shownImagePaths = remember(segments) { mutableSetOf<String>() }
    segments.forEach { segment ->
        val path = segment.workspacePath
        if (path == null) {
            if (segment.text.isNotEmpty()) {
                if (markdown && ChatTranscriptFormatting.shouldRenderAsMarkdown(segment.text)) {
                    WorkspaceMarkdown(
                        content = segment.text,
                        workspaceAbsolutePath = workspaceAbsolutePath,
                    )
                } else {
                    Text(
                        segment.text,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (ChatTranscriptFormatting.isImageWorkspacePath(path) && shownImagePaths.add(path)) {
                    WorkspaceImagePreview(
                        workspaceAbsolutePath = workspaceAbsolutePath,
                        workspaceRelativePath = path,
                    )
                }
                WorkspaceFileLinkRow(
                    relativePath = path,
                    onOpen = { WorkspaceFileActions.openWorkspaceFile(context, root, path) },
                    onShare = { WorkspaceFileActions.shareWorkspaceFile(context, root, path) },
                    onOpenInApp = onOpenInApp,
                    labelStyle = linkStyle,
                    labelOverride = segment.text.ifBlank { null },
                )
            }
        }
    }
}

/**
 * 工作区文件行：一张醒目卡片，单行展示「文件图标 + 文件名 + 打开图标 + 分享图标」。
 * 文件名与打开图标都触发打开；之前 markdown 模式下曾渲染成无名图标行，用户无法辨识。
 */
@Composable
internal fun WorkspaceFileLinkRow(
    relativePath: String,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    labelOverride: String? = null,
    /** 传入且文件可内置预览（html）时，点「打开」走 app 内预览路由。 */
    onOpenInApp: ((String) -> Unit)? = null,
) {
    val label = labelOverride?.takeIf { it.isNotBlank() }
        ?: relativePath.substringAfterLast('/').ifEmpty { relativePath }
    val inAppOpener = onOpenInApp?.takeIf { WorkspaceFileActions.isPreviewableInApp(relativePath) }
    val openAction = { if (inAppOpener != null) inAppOpener(relativePath) else onOpen() }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AgentIcons.Description,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clickable(onClick = openAction),
                style = labelStyle,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = openAction,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    AgentIcons.OpenInNew,
                    contentDescription = "打开 $label",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "分享 $label",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
