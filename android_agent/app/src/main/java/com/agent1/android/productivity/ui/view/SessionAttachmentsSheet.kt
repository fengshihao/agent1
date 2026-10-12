package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent1.android.R
import com.agent1.android.productivity.logic.business.AccessibleFileLabels
import com.agent1.android.productivity.logic.business.SessionAccessibleFilesStore
import com.agent1.android.productivity.logic.business.UserFileRequestMarkers

/**
 * 本会话附件托盘：列表、移除、从本机添加。助手请选文件时顶部展示说明条。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionAttachmentsSheet(
    visible: Boolean,
    entries: List<SessionAccessibleFilesStore.Entry>,
    assistantHint: UserFileRequestMarkers.Hint?,
    addEnabled: Boolean,
    onDismiss: () -> Unit,
    onRemove: (String) -> Unit,
    onAddFromDevice: () -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_attachments_sheet_title, entries.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (assistantHint != null) {
                AssistantAttachmentBanner(
                    hint = assistantHint,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_attachments_empty),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(entries, key = { it.workspaceRelativePath }) { entry ->
                        AttachmentSheetRow(
                            entry = entry,
                            onRemove = { onRemove(entry.workspaceRelativePath) },
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Button(
                onClick = {
                    onAddFromDevice()
                },
                enabled = addEnabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.chat_attachments_add_from_device))
            }
        }
    }
}

@Composable
private fun AssistantAttachmentBanner(
    hint: UserFileRequestMarkers.Hint,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val detail = buildAssistantBannerDetail(hint)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = cs.primaryContainer.copy(alpha = 0.65f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                AgentIcons.AttachFile,
                contentDescription = null,
                tint = cs.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_attachments_assistant_banner),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onPrimaryContainer,
                )
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onPrimaryContainer.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

@Composable
private fun buildAssistantBannerDetail(hint: UserFileRequestMarkers.Hint): String {
    val parts = mutableListOf<String>()
    hint.kinds?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    hint.maxCount?.let { parts.add(stringResource(R.string.chat_pick_files_hint_max, it)) }
    return parts.joinToString(" · ")
}

@Composable
private fun AttachmentSheetRow(
    entry: SessionAccessibleFilesStore.Entry,
    onRemove: () -> Unit,
) {
    val label = AccessibleFileLabels.primaryLabel(entry.workspaceRelativePath, entry.displayName)
    val parent = AccessibleFileLabels.parentHint(entry.workspaceRelativePath)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            AgentIcons.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!parent.isNullOrBlank()) {
                Text(
                    text = parent,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.chat_remove_file_cd, label),
            )
        }
    }
}
