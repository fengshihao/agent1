package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agent1.android.R

@Composable
internal fun ComposerAttachmentButton(
    attachmentCount: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val cd = if (attachmentCount > 0) {
        stringResource(R.string.chat_attachments_cd_count, attachmentCount)
    } else {
        stringResource(R.string.chat_add_attachment)
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(36.dp),
    ) {
        BadgedBox(
            badge = {
                if (attachmentCount > 0) {
                    Badge(
                        modifier = Modifier.offset(x = (-4).dp, y = 4.dp),
                    ) {
                        Text(
                            text = if (attachmentCount > 9) "9+" else attachmentCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            },
        ) {
            Icon(
                AgentIcons.AttachFile,
                contentDescription = cd,
                modifier = Modifier.size(18.dp),
                tint = tint,
            )
        }
    }
}
