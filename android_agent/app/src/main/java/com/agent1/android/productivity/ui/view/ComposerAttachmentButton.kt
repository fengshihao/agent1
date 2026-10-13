package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agent1.android.R
import com.agent1.android.productivity.logic.business.AttachmentComposerLabel

private val ComposerBarShape = RoundedCornerShape(8.dp)
private val ComposerBarHorizontalPadding = 4.dp
private val ComposerBarVerticalPadding = 4.dp

/** 底栏右侧模型、左侧文件入口等：同字号、同箭头，无描边。 */
@Composable
internal fun ComposerBarTappableRow(
    text: String,
    contentDescription: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxWidth: Dp = 200.dp,
    showTrailingChevron: Boolean = onClick != null,
) {
    val tint = if (enabled) contentColor else contentColor.copy(alpha = 0.32f)
    Row(
        modifier = modifier
            .widthIn(max = maxWidth)
            .clip(ComposerBarShape)
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .semantics { this.contentDescription = contentDescription }
            .padding(
                horizontal = ComposerBarHorizontalPadding,
                vertical = ComposerBarVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showTrailingChevron && onClick != null && enabled) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = tint,
            )
        }
    }
}

/** 底栏左侧：打开系统文件选择器。 */
@Composable
internal fun ComposerPickAttachmentButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = composerActionContentColor(enabled, hasUnsavedDraft = false)
    val cd = stringResource(R.string.chat_add_attachment)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .padding(vertical = ComposerBarVerticalPadding)
            .size(28.dp)
            .semantics { contentDescription = cd },
    ) {
        Icon(
            AgentIcons.Add,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
    }
}

/** 底栏加号右侧：已选文件（仅显示数量摘要，详情在列表面板）。 */
@Composable
internal fun ComposerSelectedFilesBar(
    label: AttachmentComposerLabel.Resolved,
    hasUnsavedDraft: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = composerActionContentColor(enabled, hasUnsavedDraft)
    val caption = when {
        label.hasSelection ->
            stringResource(R.string.chat_attachments_composer_multi, label.selectedCount)
        else -> stringResource(R.string.chat_file_list_short)
    }
    val cd = if (label.hasSelection) {
        stringResource(R.string.chat_attachments_cd_count, label.selectedCount)
    } else {
        stringResource(R.string.chat_file_list_cd)
    }
    ComposerBarTappableRow(
        text = caption,
        contentDescription = cd,
        onClick = onClick,
        enabled = enabled,
        contentColor = tint,
        modifier = modifier,
        maxWidth = 168.dp,
    )
}

@Composable
private fun composerActionContentColor(
    enabled: Boolean,
    hasUnsavedDraft: Boolean,
): Color {
    val cs = MaterialTheme.colorScheme
    if (!enabled) {
        return cs.onSurface.copy(alpha = 0.32f)
    }
    if (hasUnsavedDraft) {
        return cs.primary.copy(alpha = 0.92f)
    }
    return cs.onSurfaceVariant
}
