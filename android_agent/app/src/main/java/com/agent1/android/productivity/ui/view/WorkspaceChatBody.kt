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
import java.nio.file.Paths

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
    val root = remember(workspaceAbsolutePath) {
        if (workspaceAbsolutePath.isBlank()) null else Paths.get(workspaceAbsolutePath)
    }
    val paths = remember(content, workspaceFilePaths, root) {
        ChatTranscriptFormatting.mergeWorkspaceFilePaths(content, workspaceFilePaths, root)
    }
    val useMarkdown = markdown && ChatTranscriptFormatting.shouldRenderAsMarkdown(content)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (paths.isNotEmpty()) {
            WorkspaceInlineLinkifiedText(
                content = content,
                workspaceAbsolutePath = workspaceAbsolutePath,
                workspaceFilePaths = paths,
                textStyle = textStyle,
                linkStyle = textStyle.copy(color = MaterialTheme.colorScheme.primary),
                markdown = useMarkdown,
            )
        } else if (useMarkdown) {
            WorkspaceMarkdown(
                content = ChatTranscriptFormatting.rewriteWorkspaceMarkdownHrefs(
                    ChatTranscriptFormatting.linkifyBareWorkspacePaths(content, paths),
                ),
                workspaceAbsolutePath = workspaceAbsolutePath,
            )
        } else {
            Text(
                content,
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
