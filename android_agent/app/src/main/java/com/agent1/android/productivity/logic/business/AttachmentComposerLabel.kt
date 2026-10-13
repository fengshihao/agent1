package com.agent1.android.productivity.logic.business

/** 底部输入条附件按钮上的文件名 / 多选摘要（纯文本，多选文案由 UI 注入）。 */
object AttachmentComposerLabel {

    const val MAX_FILENAME_CHARS = 14

    data class Resolved(
        val singleFileName: String?,
        val selectedCount: Int,
        val unsavedDraft: Boolean = false,
    ) {
        val hasSelection: Boolean get() = selectedCount > 0
        val isMulti: Boolean get() = selectedCount > 1
    }

    fun resolve(
        pending: List<PendingAttachmentImport>,
        committed: List<SessionAccessibleFilesStore.Entry>,
        pendingRemovalPaths: Set<String> = emptySet(),
    ): Resolved {
        val draftRows = AttachmentSheetDraft.rows(committed, pending, pendingRemovalPaths)
        val unsaved = hasUnsavedDraft(pending, pendingRemovalPaths)
        if (draftRows.isEmpty()) {
            return Resolved(singleFileName = null, selectedCount = 0, unsavedDraft = unsaved)
        }
        if (draftRows.size == 1) {
            return Resolved(
                singleFileName = draftRows.first().displayName,
                selectedCount = 1,
                unsavedDraft = unsaved,
            )
        }
        return Resolved(singleFileName = null, selectedCount = draftRows.size, unsavedDraft = unsaved)
    }

    /** 底部按钮：有未保存草稿时为 true（文案可强调待确定）。 */
    fun hasUnsavedDraft(
        pending: List<PendingAttachmentImport>,
        pendingRemovalPaths: Set<String>,
    ): Boolean = pending.isNotEmpty() || pendingRemovalPaths.isNotEmpty()

    /** 过长时在开头加 `...`，保留末尾（含扩展名）。 */
    fun ellipsizeStart(name: String, maxChars: Int = MAX_FILENAME_CHARS): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.length <= maxChars) return trimmed
        val tailLen = (maxChars - 3).coerceAtLeast(1)
        return "..." + trimmed.takeLast(tailLen)
    }
}
