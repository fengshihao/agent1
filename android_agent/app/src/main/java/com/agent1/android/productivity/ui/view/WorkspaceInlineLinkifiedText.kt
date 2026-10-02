package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.agent1.android.productivity.logic.business.WorkspaceFileActions
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
) {
    val context = LocalContext.current
    if (workspaceAbsolutePath.isBlank()) {
        Text(content, modifier = modifier, style = textStyle, color = MaterialTheme.colorScheme.onSurface)
        return
    }
    val paths = remember(content, workspaceFilePaths) {
        ChatTranscriptFormatting.mergeWorkspaceFilePaths(content, workspaceFilePaths)
    }
    val segments = remember(content, paths) {
        ChatTranscriptFormatting.splitLinkifiedSegments(content, paths)
    }
    val root = remember(workspaceAbsolutePath) { Paths.get(workspaceAbsolutePath) }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.forEach { segment ->
            val path = segment.workspacePath
            if (path == null) {
                if (segment.text.isNotEmpty()) {
                    Text(
                        segment.text,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                WorkspaceFileLinkRow(
                    relativePath = path,
                    onOpen = { WorkspaceFileActions.openWorkspaceFile(context, root, path) },
                    onShare = { WorkspaceFileActions.shareWorkspaceFile(context, root, path) },
                    linkStyle = linkStyle,
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
) {
    val label = relativePath.substringAfterLast('/').ifEmpty { relativePath }
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
