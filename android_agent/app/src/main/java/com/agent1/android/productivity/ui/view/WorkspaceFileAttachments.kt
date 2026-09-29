package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
) {
    val context = LocalContext.current
    if (workspaceAbsolutePath.isBlank()) return
    val paths = relativePaths.filter { it.isNotBlank() && it !in excludePaths }
    if (paths.isEmpty()) return
    val root = Paths.get(workspaceAbsolutePath)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        paths.forEach { relative ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = { WorkspaceFileActions.openWorkspaceFile(context, root, relative) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = relative,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                    )
                }
                IconButton(
                    onClick = { WorkspaceFileActions.shareWorkspaceFile(context, root, relative) },
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "分享",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
