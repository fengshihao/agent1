package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.agent1.android.productivity.logic.business.SessionWorkspacePaths
import java.nio.file.Paths

@Composable
fun WorkspaceImagePreview(
    workspaceAbsolutePath: String,
    workspaceRelativePath: String,
    warning: String? = null,
    modifier: Modifier = Modifier,
) {
    if (warning != null) {
        Text(
            text = warning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
    }
    if (workspaceAbsolutePath.isBlank()) {
        Text(
            text = "工作区路径不可用",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val file = SessionWorkspacePaths.resolveFile(Paths.get(workspaceAbsolutePath), workspaceRelativePath)
    if (file == null) {
        Text(
            text = "找不到图片：$workspaceRelativePath",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    AsyncImage(
        model = file,
        contentDescription = workspaceRelativePath,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        contentScale = ContentScale.Fit,
    )
}
