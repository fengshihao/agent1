package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agent1.android.R
import com.agent1.android.productivity.logic.business.UserFileRequestMarkers

/** 助手请用户选文件时的醒目行动条（替代单独一行小回形针）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFilePickFilesCallToAction(
    hint: UserFileRequestMarkers.Hint?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val subtitle = buildPickFilesSubtitle(hint)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = cs.primaryContainer.copy(alpha = if (enabled) 0.55f else 0.25f),
        border = BorderStroke(1.dp, cs.primary.copy(alpha = if (enabled) 0.65f else 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                AgentIcons.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = cs.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_select_file),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onPrimaryContainer,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = cs.primary,
            )
        }
    }
}

@Composable
private fun buildPickFilesSubtitle(hint: UserFileRequestMarkers.Hint?): String {
    if (hint == null) return stringResource(R.string.chat_pick_files_hint_default)
    val parts = mutableListOf<String>()
    hint.kinds?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    hint.maxCount?.let { parts.add(stringResource(R.string.chat_pick_files_hint_max, it)) }
    return if (parts.isEmpty()) {
        stringResource(R.string.chat_pick_files_hint_default)
    } else {
        parts.joinToString(" · ")
    }
}
