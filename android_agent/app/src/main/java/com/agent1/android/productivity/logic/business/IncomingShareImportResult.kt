package com.agent1.android.productivity.logic.business

/** [ProductivityAgentGateway.importIncomingShare] 结果。 */
data class IncomingShareImportResult(
    val workspaceRelativePaths: List<String>,
    val rejectedOversizedCount: Int,
    val sharedTextPath: String? = null,
)
