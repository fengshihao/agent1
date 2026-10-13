package com.agent1.android.productivity.logic.business

/** 用户已在系统选择器确认、尚未复制进 workspace/imports/ 的条目。 */
data class PendingAttachmentImport(
    val uriString: String,
    val displayName: String,
)

object PendingAttachmentImports {
    fun merge(
        existing: List<PendingAttachmentImport>,
        incoming: List<PendingAttachmentImport>,
    ): List<PendingAttachmentImport> {
        if (incoming.isEmpty()) return existing
        val seen = existing.map { it.uriString }.toMutableSet()
        val out = existing.toMutableList()
        for (item in incoming) {
            if (seen.add(item.uriString)) {
                out.add(item)
            }
        }
        return out
    }
}
