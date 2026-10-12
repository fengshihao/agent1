package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent1.android.R
import com.agent1.android.productivity.logic.business.AccessibleFileLabels
import com.agent1.android.productivity.logic.business.SessionAccessibleFilesStore

/**
 * 已选文件区：横向芯片列表，主标题为文件名（不暴露 `imports/`），可单独移除。
 */
@Composable
fun AccessibleFilesPanel(
    entries: List<SessionAccessibleFilesStore.Entry>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    if (entries.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = title ?: stringResource(R.string.chat_selected_files_count, entries.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEach { entry ->
                AccessibleFileChip(
                    entry = entry,
                    onRemove = { onRemove(entry.workspaceRelativePath) },
                )
            }
        }
    }
}

@Composable
private fun AccessibleFileChip(
    entry: SessionAccessibleFilesStore.Entry,
    onRemove: () -> Unit,
) {
    val label = AccessibleFileLabels.primaryLabel(entry.workspaceRelativePath, entry.displayName)
    val hint = AccessibleFileLabels.parentHint(entry.workspaceRelativePath)
    val removeCd = stringResource(R.string.chat_remove_file_cd, label)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.padding(end = 2.dp)) {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (!hint.isNullOrBlank()) {
                    Text(
                        text = hint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = removeCd,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
