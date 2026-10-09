package com.agent1.android.productivity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent1.android.productivity.logic.business.ArtifactLibraryStore
import com.agent1.android.productivity.logic.business.ProductivityAgentGateway
import com.agent1.android.productivity.logic.business.ProductivityGatewayProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 产物库：汇总所有会话 workspace 内 AI 生成的文件，支持搜索 / 多选 / 删除 / 分享 /
 * 插入当前会话（与底部附件按钮同一落点 `workspace/imports/` 与登记逻辑）。
 */
class ArtifactLibraryViewModel(
    private val appContext: Context,
    private val currentSessionId: String,
) : ViewModel() {

    private val gateway: ProductivityAgentGateway
        get() = ProductivityGatewayProvider.get(appContext.applicationContext)

    private val _state = MutableStateFlow(
        ArtifactLibraryUiState(currentSessionId = currentSessionId),
    )
    val state: StateFlow<ArtifactLibraryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, message = null)
            @Suppress("TooGenericExceptionCaught")
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val titles = gateway.listSessions()
                        .associate { it.sessionId to it.title.ifBlank { "新对话" } }
                    titles to ArtifactLibraryStore.listArtifacts(appContext)
                }
                val (titles, entries) = loaded
                _state.value = applyFilter(
                    _state.value.copy(
                        isLoading = false,
                        items = entries.map { entry ->
                            ArtifactUiItem(
                                sessionId = entry.sessionId,
                                sessionTitle = titles[entry.sessionId].orEmpty(),
                                workspaceRelativePath = entry.workspaceRelativePath,
                                fileName = entry.fileName,
                                sizeBytes = entry.sizeBytes,
                                lastModifiedMillis = entry.lastModifiedMillis,
                            )
                        },
                        selectedKeys = emptySet(),
                        selectionMode = false,
                    ),
                )
            } catch (t: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = t.message ?: t.javaClass.simpleName,
                )
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.value = applyFilter(_state.value.copy(query = query))
    }

    fun enterSelection(stableKey: String) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(
            selectionMode = true,
            selectedKeys = setOf(stableKey),
            message = null,
            error = null,
        )
    }

    fun toggleSelection(stableKey: String) {
        val current = _state.value
        if (!current.selectionMode || current.busy) return
        val next = if (stableKey in current.selectedKeys) {
            current.selectedKeys - stableKey
        } else {
            current.selectedKeys + stableKey
        }
        _state.value = current.copy(
            selectedKeys = next,
            selectionMode = next.isNotEmpty(),
        )
    }

    fun selectAllFiltered() {
        val current = _state.value
        if (current.busy) return
        _state.value = current.copy(
            selectionMode = current.filteredItems.isNotEmpty(),
            selectedKeys = current.filteredItems.map { it.stableKey }.toSet(),
        )
    }

    fun clearSelection() {
        if (_state.value.busy) return
        _state.value = _state.value.copy(selectedKeys = emptySet(), selectionMode = false)
    }

    /** 多选删除；完成后刷新列表并提示删除数量。 */
    fun deleteSelected() {
        val selected = selectedItems()
        if (selected.isEmpty() || _state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = null)
            val failed = withContext(Dispatchers.IO) {
                selected.count { item ->
                    !ArtifactLibraryStore.deleteArtifact(
                        appContext,
                        ArtifactLibraryStore.ArtifactRef(item.sessionId, item.workspaceRelativePath),
                    )
                }
            }
            val deletedCount = selected.size - failed
            _state.value = applyFilter(
                _state.value.copy(
                    busy = false,
                    selectedKeys = emptySet(),
                    selectionMode = false,
                    message = when {
                        failed == 0 -> "已删除 $deletedCount 个产物"
                        deletedCount == 0 -> "删除失败，文件可能已被移动"
                        else -> "已删除 $deletedCount 个产物，${failed} 个失败"
                    },
                ),
            )
            reloadListAfterMutation()
        }
    }

    /**
     * 把所选产物插入当前会话（复制进 `workspace/imports/` 并登记可访问文件）。
     * 完成后回调 [onInserted]（主线程），聊天页据此刷新 transcript 与「已选文件」条。
     */
    fun insertSelectedIntoCurrentSession(onInserted: (Int) -> Unit = {}) {
        val current = _state.value
        if (current.busy) return
        if (currentSessionId.isBlank()) {
            _state.value = current.copy(message = "当前没有活动会话，无法插入")
            return
        }
        if (gateway.isRunInProgress()) {
            _state.value = current.copy(message = "当前对话正在运行，结束后再插入")
            return
        }
        val selected = selectedItems()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = null)
            @Suppress("TooGenericExceptionCaught")
            try {
                val inserted = withContext(Dispatchers.IO) {
                    gateway.importArtifactsIntoSession(
                        currentSessionId,
                        selected.map {
                            ArtifactLibraryStore.ArtifactRef(it.sessionId, it.workspaceRelativePath)
                        },
                    )
                }
                _state.value = _state.value.copy(
                    busy = false,
                    selectedKeys = emptySet(),
                    selectionMode = false,
                    message = if (inserted.isEmpty()) {
                        "未能插入所选产物，文件可能已被移动"
                    } else {
                        "已插入 ${inserted.size} 个产物到当前会话"
                    },
                )
                onInserted(inserted.size)
            } catch (t: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    error = "插入失败：${t.message ?: t.javaClass.simpleName}",
                )
            }
        }
    }

    private fun selectedItems(): List<ArtifactUiItem> =
        _state.value.items.filter { it.stableKey in _state.value.selectedKeys }

    /** 删除后重扫磁盘，保持列表与实际文件一致。 */
    private fun reloadListAfterMutation() {
        viewModelScope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                val entries = withContext(Dispatchers.IO) {
                    ArtifactLibraryStore.listArtifacts(appContext)
                }
                // 会话标题来自打开页面时的快照；新出现的会话允许标题为空（仅展示用）
                val knownTitles = _state.value.items.associate { it.sessionId to it.sessionTitle }
                _state.value = applyFilter(
                    _state.value.copy(
                        items = entries.map { entry ->
                            ArtifactUiItem(
                                sessionId = entry.sessionId,
                                sessionTitle = knownTitles[entry.sessionId].orEmpty(),
                                workspaceRelativePath = entry.workspaceRelativePath,
                                fileName = entry.fileName,
                                sizeBytes = entry.sizeBytes,
                                lastModifiedMillis = entry.lastModifiedMillis,
                            )
                        },
                    ),
                )
            } catch (_: Exception) {
                // 保留删除提示；重扫失败下次进入页面会重扫
            }
        }
    }

    private fun applyFilter(s: ArtifactLibraryUiState): ArtifactLibraryUiState {
        val q = s.query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            s.items
        } else {
            s.items.filter { item ->
                item.fileName.lowercase().contains(q) ||
                    item.sessionTitle.lowercase().contains(q) ||
                    item.workspaceRelativePath.lowercase().contains(q)
            }
        }
        return s.copy(filteredItems = filtered)
    }
}