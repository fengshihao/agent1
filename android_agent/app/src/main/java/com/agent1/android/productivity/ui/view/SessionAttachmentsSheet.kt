package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.agent1.android.productivity.logic.business.AttachmentSheetDraft
import com.agent1.android.productivity.logic.business.UserFileRequestMarkers

/**
 * 本会话附件：单一草稿列表。选文件 / 移除都只改草稿，点「确定」才写入会话。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionAttachmentsSheet(
    visible: Boolean,
    draftRows: List<AttachmentSheetDraft.Row>,
    hasUnsavedDraft: Boolean,
    importInProgress: Boolean,
    assistantHint: UserFileRequestMarkers.Hint?,
    addEnabled: Boolean,
    onDismiss: () -> Unit,
    onRemoveRow: (AttachmentSheetDraft.Row) -> Unit,
    onConfirm: () -> Unit,
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
                text = stringResource(R.string.chat_attachments_sheet_title, draftRows.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (hasUnsavedDraft) {
                    stringResource(R.string.chat_attachments_sheet_draft_hint)
                } else {
                    stringResource(R.string.chat_attachments_sheet_saved_hint)
                },
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (assistantHint != null) {
                AssistantAttachmentBanner(
                    hint = assistantHint,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (draftRows.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_attachments_empty_short),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    draftRows.forEach { row ->
                        AttachmentDraftRow(
                            row = row,
                            onRemove = { onRemoveRow(row) },
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            val confirmEnabled = addEnabled && !importInProgress && hasUnsavedDraft
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    if (importInProgress) {
                        stringResource(R.string.chat_attachments_importing)
                    } else {
                        stringResource(R.string.chat_attachments_confirm)
                    },
                )
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
private fun AttachmentDraftRow(
    row: AttachmentSheetDraft.Row,
    onRemove: () -> Unit,
) {
    val isNew = row is AttachmentSheetDraft.Row.NewPick
    val tint = if (isNew) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isNew) AgentIcons.AttachFile else AgentIcons.Description,
            contentDescription = null,
            tint = tint,
        )
        Text(
            text = row.displayName,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.chat_remove_file_cd, row.displayName),
            )
        }
    }
}
