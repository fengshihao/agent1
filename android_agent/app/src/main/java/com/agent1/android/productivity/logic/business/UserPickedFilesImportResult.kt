package com.agent1.android.productivity.logic.business

/** [ProductivityAgentGateway.importUserPickedFiles] 结果。 */
data class UserPickedFilesImportResult(
    val workspaceRelativePaths: List<String>,
    val rejectedOversizedCount: Int,
)
