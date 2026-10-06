package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
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
                    linkStyle = linkStyle,
                    labelOverride = segment.text.ifBlank { null },
                )
            }
        }
    }
}

@Composable
internal fun WorkspaceFileLinkRow(
    relativePath: String,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    linkStyle: TextStyle = MaterialTheme.typography.bodySmall,
    showLabel: Boolean = true,
    labelOverride: String? = null,
) {
    val label = labelOverride?.takeIf { it.isNotBlank() }
        ?: relativePath.substringAfterLast('/').ifEmpty { relativePath }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLabel) {
            Text(
                text = label,
                modifier = Modifier.clickable(onClick = onOpen),
                style = linkStyle,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                maxLines = 2,
            )
        }
        IconButton(
            onClick = onOpen,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                AgentIcons.Description,
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
