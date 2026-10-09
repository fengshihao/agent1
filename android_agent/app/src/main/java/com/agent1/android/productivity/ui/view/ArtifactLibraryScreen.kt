package com.agent1.android.productivity.ui.view

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.logic.business.ArtifactLibraryStore
import com.agent1.android.productivity.logic.business.SessionWorkspacePaths
import com.agent1.android.productivity.logic.business.WorkspaceFileActions
import com.agent1.android.productivity.ui.viewmodel.ArtifactLibraryViewModel
import com.agent1.android.productivity.ui.viewmodel.ArtifactUiItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 产物库页面：右上角文件夹入口打开。集中展示所有会话里 AI 生成的文件，
 * 支持搜索、多选、删除、分享与「插入当前会话」（与附件按钮同效）。
 */
@Composable
fun ArtifactLibraryScreen(
    viewModel: ArtifactLibraryViewModel,
    onBack: () -> Unit,
    /** 当前活动会话；为空（落位页进入）时隐藏「插入会话」。 */
    currentSessionId: String,
    /** 插入完成后回调（主线程），聊天页据此刷新 transcript 与「已选文件」。 */
    onInserted: (Int) -> Unit = {},
    /** html 产物点开走 app 内预览路由（sessionId + workspace 相对路径）。 */
    onOpenHtmlPreview: (sessionId: String, relativePath: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    fun selectedRefs(): List<ArtifactLibraryStore.ArtifactRef> = state.items
        .filter { it.stableKey in state.selectedKeys }
        .map { item -> ArtifactLibraryStore.ArtifactRef(item.sessionId, item.workspaceRelativePath) }

    Column(modifier = Modifier.fillMaxSize()) {
        AgentTopBar(
            title = "产物库",
            subtitle = when {
                state.isLoading -> "加载中…"
                state.selectionMode -> "已选 ${state.selectedKeys.size} 项"
                else -> "${state.filteredItems.size} 个产物"
            },
            leading = {
                TopBarIconButton(
                    icon = Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
            },
            actions = {
                if (state.selectionMode && !state.busy) {
                    TopBarIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "取消选择",
                        onClick = viewModel::clearSelection,
                    )
                }
            },
        )
        AgentHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("搜索文件名或会话") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空搜索", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        state.message?.let { Text(
            it,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        ) }
        state.error?.let { Text(
            it,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        ) }
        if (state.selectionMode) {
            ArtifactSelectionBar(
                selectedCount = state.selectedKeys.size,
                canInsert = currentSessionId.isNotBlank(),
                busy = state.busy,
                onSelectAll = viewModel::selectAllFiltered,
                onShare = { WorkspaceFileActions.shareArtifactFiles(context, selectedRefs()) },
                onInsert = { viewModel.insertSelectedIntoCurrentSession(onInserted) },
                onDelete = { confirmDelete = true },
            )
            AgentHairline()
        }
        when {
            state.isLoading -> Text(
                "正在扫描产物…",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.filteredItems.isEmpty() -> Text(
                if (state.query.isBlank()) "暂无产物。AI 在会话里生成的文件会出现在这里。" else "没有匹配「${state.query}」的产物",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(
                    state.filteredItems,
                    key = { _, item -> item.stableKey },
                ) { _, item ->
                    ArtifactRow(
                        item = item,
                        selected = item.stableKey in state.selectedKeys,
                        selectionMode = state.selectionMode,
                        busy = state.busy,
                        dateFormat = dateFormat,
                        onOpen = { openArtifact(context, item, onOpenHtmlPreview) },
                        onShare = { shareArtifact(context, item) },
                        onLongClick = { viewModel.enterSelection(item.stableKey) },
                        onToggle = { viewModel.toggleSelection(item.stableKey) },
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                item { Spacer(modifier = Modifier.padding(bottom = 24.dp)) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除产物") },
            text = { Text("将删除所选 ${state.selectedKeys.size} 个文件，删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteSelected()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ArtifactSelectionBar(
    selectedCount: Int,
    canInsert: Boolean,
    busy: Boolean,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onInsert: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSelectAll, enabled = !busy) { Text("全选") }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onShare, enabled = !busy && selectedCount > 0) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "分享所选产物",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canInsert) {
                Button(onClick = onInsert, enabled = !busy && selectedCount > 0) {
                    Text(if (busy) "…" else "插入会话")
                }
            }
            IconButton(onClick = onDelete, enabled = !busy && selectedCount > 0) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除所选产物",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtifactRow(
    item: ArtifactUiItem,
    selected: Boolean,
    selectionMode: Boolean,
    busy: Boolean,
    dateFormat: SimpleDateFormat,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = !busy,
                onClick = { if (selectionMode) onToggle() else onOpen() },
                onLongClick = onLongClick,
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() }, enabled = !busy)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.fileName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(item.sessionTitle.ifBlank { "会话 ${item.sessionId.takeLast(6)}" }, item.workspaceRelativePath)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${Formatter.formatFileSize(context, item.sizeBytes)} · " +
                    dateFormat.format(Date(item.lastModifiedMillis)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!selectionMode) {
            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "分享 ${item.fileName}",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun openArtifact(
    context: android.content.Context,
    item: ArtifactUiItem,
    onOpenHtmlPreview: (sessionId: String, relativePath: String) -> Unit,
) {
    if (WorkspaceFileActions.isPreviewableInApp(item.workspaceRelativePath)) {
        onOpenHtmlPreview(item.sessionId, item.workspaceRelativePath)
        return
    }
    val root = SessionWorkspacePaths.workspaceRoot(context, item.sessionId) ?: return
    WorkspaceFileActions.openWorkspaceFile(context, root, item.workspaceRelativePath)
}

private fun shareArtifact(context: android.content.Context, item: ArtifactUiItem) {
    val root = SessionWorkspacePaths.workspaceRoot(context, item.sessionId) ?: return
    WorkspaceFileActions.shareWorkspaceFile(context, root, item.workspaceRelativePath)
}