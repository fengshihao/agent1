package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.agent1.android.productivity.logic.business.ChatTranscriptFormatting

@Composable
fun WorkspaceChatBody(
    content: String,
    workspaceAbsolutePath: String,
    markdown: Boolean,
    workspaceFilePaths: List<String>,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    if (content.isBlank()) return
    val paths = remember(content, workspaceFilePaths) {
        ChatTranscriptFormatting.mergeWorkspaceFilePaths(content, workspaceFilePaths)
    }
    val prepared = remember(content, paths) {
        ChatTranscriptFormatting.linkifyBareWorkspacePaths(content, paths)
    }
    val useMarkdown = markdown && ChatTranscriptFormatting.shouldRenderAsMarkdown(prepared)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (useMarkdown) {
            WorkspaceMarkdown(
                content = prepared,
                workspaceAbsolutePath = workspaceAbsolutePath,
            )
            if (paths.isNotEmpty()) {
                WorkspaceFileAttachments(
                    workspaceAbsolutePath = workspaceAbsolutePath,
                    relativePaths = paths,
                    showPathLabels = false,
                )
            }
        } else {
            if (paths.isEmpty()) {
                Text(
                    prepared,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                WorkspaceInlineLinkifiedText(
                    content = prepared,
                    workspaceAbsolutePath = workspaceAbsolutePath,
                    workspaceFilePaths = paths,
                    textStyle = textStyle,
                    linkStyle = textStyle.copy(color = MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
