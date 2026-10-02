package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.logic.business.WorkspaceFileActions
import java.nio.file.Paths

@Composable
fun WorkspaceFileAttachments(
    workspaceAbsolutePath: String,
    relativePaths: List<String>,
    excludePaths: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    /** Markdown 正文已展示链接时，仅保留打开/分享按钮行。 */
    showPathLabels: Boolean = true,
) {
    val context = LocalContext.current
    if (workspaceAbsolutePath.isBlank()) return
    val paths = relativePaths.filter { it.isNotBlank() && it !in excludePaths }
    if (paths.isEmpty()) return
    val root = Paths.get(workspaceAbsolutePath)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        paths.forEach { relative ->
            WorkspaceFileLinkRow(
                relativePath = relative,
                onOpen = { WorkspaceFileActions.openWorkspaceFile(context, root, relative) },
                onShare = { WorkspaceFileActions.shareWorkspaceFile(context, root, relative) },
                showLabel = showPathLabels,
                modifier = if (showPathLabels) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.padding(end = 4.dp)
                },
            )
        }
    }
}
