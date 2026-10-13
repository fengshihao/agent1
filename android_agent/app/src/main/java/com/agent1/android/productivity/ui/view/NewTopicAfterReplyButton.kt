package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agent1.android.R

@Composable
internal fun NewTopicAfterReplyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.padding(start = 4.dp, top = 0.dp, bottom = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.chat_new_topic_after_reply),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
