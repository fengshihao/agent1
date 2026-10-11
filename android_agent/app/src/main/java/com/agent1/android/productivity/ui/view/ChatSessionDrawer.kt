package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.agent1.android.productivity.logic.business.UsageCost
import com.agent1.android.productivity.logic.business.UsageCostEstimate
import com.agent1.android.productivity.logic.business.UsageOverview
import com.agent1.android.productivity.ui.viewmodel.SessionListUiState
import com.agent1.javaagent.session.SessionMeta

/** 会话抽屉：新建对话、两行标题的会话列表、底部累计用量。 */
@Composable
internal fun ColumnScope.SessionDrawer(
    state: SessionListUiState,
    activeSessionId: String,
    onNewChat: () -> Unit,
    onOpenSession: (SessionMeta) -> Unit,
    onDeleteSession: (SessionMeta) -> Unit,
) {
    val sessions = state.sessions.sortedByDescending { it.updatedAt }
    var pendingDelete by remember { mutableStateOf<SessionMeta?>(null) }
    var usageOpen by remember { mutableStateOf(false) }
    Text(
        "Agent One",
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleMedium,
    )
    NewChatButton(onClick = onNewChat)
    if (!state.startupError.isNullOrBlank()) {
        Text(
            state.startupError.orEmpty(),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val fadeHeight = 28.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to surface.copy(alpha = 0.72f),
                        1f to surface,
                        startY = size.height - fadeHeight,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, size.height - fadeHeight),
                    size = Size(size.width, fadeHeight),
                )
            },
    ) {
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.isLoading) "加载中…" else "还没有对话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 20.dp),
            ) {
                itemsIndexed(sessions, key = { _, meta -> meta.sessionId }) { index, meta ->
                    val selected = meta.sessionId == activeSessionId
                    val previousSelected = index > 0 && sessions[index - 1].sessionId == activeSessionId
                    if (index > 0 && !selected && !previousSelected) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    SessionDrawerRow(
                        meta = meta,
                        selected = selected,
                        onOpen = { onOpenSession(meta) },
                        onDelete = { pendingDelete = meta },
                    )
                }
            }
        }
    }
    val estimate = UsageCost.estimate(
        usage = state.usage,
        currentModelId = state.configSummary?.modelId.orEmpty(),
        baseUrl = state.configSummary?.baseUrl.orEmpty(),
    )
    UsageFooter(
        usage = state.usage,
        estimate = estimate,
        onClick = { usageOpen = true },
    )
    pendingDelete?.let { meta ->
        val title = meta.title.ifBlank { "新对话" }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除对话") },
            text = { Text("删除「$title」后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteSession(meta)
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
    if (usageOpen) {
        UsageCostPage(
            usage = state.usage,
            estimate = estimate,
            onDismiss = { usageOpen = false },
        )
    }
}

/** 抽屉右缘的暖色光，让侧栏从对话上浮起来，而不是一条硬边。 */
@Composable
internal fun Modifier.drawerEdgeGlow(): Modifier {
    val glow = MaterialTheme.colorScheme.primary
    return drawWithContent {
        drawContent()
        val band = 10.dp.toPx()
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                1f to glow.copy(alpha = 0.12f),
                startX = size.width - band,
                endX = size.width,
            ),
            topLeft = Offset(size.width - band, 0f),
            size = Size(band, size.height),
        )
    }
}

@Composable
private fun NewChatButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = primary.copy(alpha = 0.28f),
                spotColor = primary.copy(alpha = 0.36f),
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        lerp(primary, Color.White, 0.45f),
                        primary.copy(alpha = 0.72f),
                    ),
                ),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lerp(primary, Color.White, 0.28f),
                            primary,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = onPrimary,
            )
        }
        Text(
            "新建对话",
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SessionDrawerRow(
    meta: SessionMeta,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onOpen)
            .padding(start = 10.dp, end = 0.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            meta.title.ifBlank { "新对话" },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
            style = MaterialTheme.typography.titleSmall.copy(lineHeight = 18.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UsageFooter(
    usage: UsageOverview,
    estimate: UsageCostEstimate,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val headline = estimate.compact
    val detail = if (usage.hasUsage) {
        val hit = usage.cacheHitPercent
        val amount = UsageCost.formatMillions(usage.totalTokens)
        if (hit == null) amount else "$amount · 缓存 $hit%"
    } else {
        "按当前模型估算"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                headline,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "用量统计",
            modifier = Modifier.size(18.dp),
            tint = primary.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun UsageCostPage(
    usage: UsageOverview,
    estimate: UsageCostEstimate,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "用量",
                        modifier = Modifier.weight(1f).padding(top = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "关闭",
                        )
                    }
                }
                Text(
                    estimate.headline,
                    modifier = Modifier.padding(top = 10.dp, end = 14.dp),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    estimate.caption,
                    modifier = Modifier.padding(top = 4.dp, end = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (estimate.lines.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(top = 18.dp, end = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        estimate.lines.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(line.label, style = MaterialTheme.typography.bodyLarge)
                                    if (line.detail.isNotBlank()) {
                                        Text(
                                            line.detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (line.moneyText.isNotBlank()) {
                                    Text(
                                        line.moneyText,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                        }
                    }
                }
                usage.cacheHitPercent?.let { hit ->
                    Text(
                        "缓存命中 $hit%",
                        modifier = Modifier.padding(top = 14.dp, end = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (estimate.caveat.isNotBlank()) {
                    Text(
                        estimate.caveat,
                        modifier = Modifier.padding(top = 12.dp, end = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
