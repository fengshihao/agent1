package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 聊天页共享的「外壳」组件：顶栏、顶栏图标按钮、分隔线、更多菜单与配置错误横幅。
 * 供 ProductivityHome / ChatScreen / ChatLanding 与各设置页共用，故 internal。
 */
@Composable
internal fun AgentTopBar(
    title: String,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    actions: @Composable () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = if (leading == null) 16.dp else 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                actions()
            }
        }
    }
}

@Composable
internal fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.size(36.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
            )
        } else {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}

@Composable
internal fun AgentHairline() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
internal fun ChatMoreMenu(
    expanded: Boolean,
    exportInProgress: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onOpenSystemPrompt: (() -> Unit)? = null,
    onBrief: (() -> Unit)? = null,
) {
    Box {
        TopBarIconButton(
            icon = Icons.Filled.MoreVert,
            contentDescription = "更多",
            onClick = onOpen,
            busy = exportInProgress,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            MoreMenuRow("模型设置", Icons.Filled.Settings) {
                onDismiss()
                onOpenSettings()
            }
            MoreMenuRow("MCP & web搜索", AgentIcons.Hub) {
                onDismiss()
                onOpenMcp()
            }
            MoreMenuRow("能力检索", Icons.Filled.Search) {
                onDismiss()
                onOpenCapabilities()
            }
            if (onOpenSystemPrompt != null) {
                MoreMenuRow("系统提示词", Icons.Filled.Info) {
                    onDismiss()
                    onOpenSystemPrompt()
                }
            }
            if (onBrief != null) {
                MoreMenuRow("会话简报", AgentIcons.Description, enabled = !exportInProgress) {
                    onDismiss()
                    onBrief()
                }
            }
            MoreMenuRow(
                label = if (exportInProgress) "正在打包…" else "诊断包",
                icon = Icons.Filled.Share,
                enabled = !exportInProgress,
            ) {
                onDismiss()
                onExportDiagnostics()
            }
        }
    }
}

@Composable
private fun MoreMenuRow(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
internal fun ConfigErrorBanner(message: String) {
    Text(
        message,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}
