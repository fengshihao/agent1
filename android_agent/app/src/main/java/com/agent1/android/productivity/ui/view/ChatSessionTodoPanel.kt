package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.logic.business.SessionTodoItem

@Composable
internal fun ChatSessionTodoPanel(
    todos: List<SessionTodoItem>,
    modifier: Modifier = Modifier,
) {
    if (todos.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "任务清单",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            todos.forEach { item ->
                val statusLabel = todoStatusLabel(item.status)
                val contentStyle = if (item.status == "completed") {
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                    )
                } else {
                    MaterialTheme.typography.bodyMedium
                }
                Text(
                    "$statusLabel · ${item.content}",
                    style = contentStyle,
                    color = if (item.status == "in_progress") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

private fun todoStatusLabel(status: String): String = when (status) {
    "in_progress" -> "进行中"
    "completed" -> "已完成"
    else -> "待办"
}
