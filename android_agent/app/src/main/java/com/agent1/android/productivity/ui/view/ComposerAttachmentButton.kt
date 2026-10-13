package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent1.android.R
import com.agent1.android.productivity.logic.business.AttachmentComposerLabel

/** 底栏左侧：打开系统文件选择器。 */
@Composable
internal fun ComposerPickAttachmentButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(36.dp),
    ) {
        Icon(
            AgentIcons.Add,
            contentDescription = stringResource(R.string.chat_add_attachment),
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
    }
}

/** 输入框上方：已选文件入口，点开附件列表面板。 */
@Composable
internal fun ComposerSelectedFilesBar(
    label: AttachmentComposerLabel.Resolved,
    hasUnsavedDraft: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tint = if (enabled) cs.onSurfaceVariant else cs.onSurface.copy(alpha = 0.38f)
    val caption = when {
        label.isMulti -> stringResource(R.string.chat_attachments_composer_multi, label.selectedCount)
        label.hasSelection -> AttachmentComposerLabel.ellipsizeStart(label.singleFileName.orEmpty())
        else -> stringResource(R.string.chat_file_list_short)
    }
    val barColor = if (hasUnsavedDraft) {
        cs.primaryContainer.copy(alpha = 0.55f)
    } else {
        cs.surfaceContainerHighest.copy(alpha = 0.9f)
    }
    val cd = stringResource(R.string.chat_file_list_cd)
    Surface(
        modifier = modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = cd },
        color = barColor,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                AgentIcons.Description,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (hasUnsavedDraft) cs.primary else tint,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelLarge,
                color = if (hasUnsavedDraft) cs.primary else tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (hasUnsavedDraft) {
                Text(
                    text = stringResource(R.string.chat_attachments_composer_unsaved),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.primary,
                )
            }
        }
    }
}
