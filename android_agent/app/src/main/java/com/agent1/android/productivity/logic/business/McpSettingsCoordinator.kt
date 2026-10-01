package com.agent1.android.productivity.logic.business

import android.content.Context
import com.agent1.android.productivity.logic.data.McpServerFileStore
import com.agent1.javaagent.mcp.HttpMcpToolLister
import com.agent1.javaagent.mcp.McpCapabilitySync

data class McpSaveResult(
    val toolCount: Int,
    val warnings: List<String>,
)

class McpSettingsCoordinator(context: Context) {

    private val agentRoot = SessionWorkspacePaths.agentRoot(context.applicationContext)
    private val store = McpServerFileStore(agentRoot)

    fun load(): List<McpServerForm> = store.load().map { it.toForm() }

    fun save(forms: List<McpServerForm>): McpSaveResult {
        store.save(forms.map { it.toRecord() })
        return try {
            val sync = McpCapabilitySync.ensureIndexed(agentRoot, HttpMcpToolLister(), true)
            McpSaveResult(sync.toolCount(), sync.warnings())
        } catch (error: RuntimeException) {
            McpSaveResult(0, listOf(error.message ?: "索引 MCP 工具失败"))
        }
    }
}
