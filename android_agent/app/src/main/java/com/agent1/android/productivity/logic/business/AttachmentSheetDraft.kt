package com.agent1.android.productivity.logic.business

/**
 * 附件面板：选文件在系统选择器返回后即入库；仅「移除」在点确定前为草稿。
 */
object AttachmentSheetDraft {

    sealed class Row {
        abstract val key: String
        abstract val displayName: String

        /** 本会话里已有、草稿中仍保留。 */
        data class Kept(
            val entry: SessionAccessibleFilesStore.Entry,
        ) : Row() {
            override val key: String = "kept:${entry.workspaceRelativePath}"
            override val displayName: String =
                AccessibleFileLabels.primaryLabel(entry.workspaceRelativePath, entry.displayName)
        }

        /** 系统选择器已选、确定后才会 copy 进 imports。 */
        data class NewPick(
            val pending: PendingAttachmentImport,
        ) : Row() {
            override val key: String = "new:${pending.uriString}"
            override val displayName: String = pending.displayName
        }
    }

    fun rows(
        committed: List<SessionAccessibleFilesStore.Entry>,
        pendingImports: List<PendingAttachmentImport>,
        pendingRemovals: Set<String>,
    ): List<Row> {
        val kept = committed
            .filter { it.workspaceRelativePath !in pendingRemovals }
            .map { Row.Kept(it) }
        val added = pendingImports.map { Row.NewPick(it) }
        return kept + added
    }

    fun removeFromDraft(
        row: Row,
        pendingImports: List<PendingAttachmentImport>,
        pendingRemovals: Set<String>,
    ): Pair<List<PendingAttachmentImport>, Set<String>> {
        return when (row) {
            is Row.NewPick -> {
                pendingImports.filter { it.uriString != row.pending.uriString } to pendingRemovals
            }
            is Row.Kept -> {
                pendingImports to (pendingRemovals + row.entry.workspaceRelativePath)
            }
        }
    }
}
