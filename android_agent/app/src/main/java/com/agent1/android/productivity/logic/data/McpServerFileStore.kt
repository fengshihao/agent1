package com.agent1.android.productivity.logic.data

import com.agent1.javaagent.mcp.McpServerRecord
import com.agent1.javaagent.mcp.McpServersFile
import java.nio.file.Path

/** 持久化 HTTP MCP 服务器列表。路径与微智 {@code McpAgentExtension} 的 agentRoot 相同。 */
class McpServerFileStore(
    private val agentRoot: Path,
) {
    fun load(): List<McpServerRecord> = McpServersFile.load(agentRoot)

    fun save(servers: List<McpServerRecord>) {
        McpServersFile.save(agentRoot, servers)
    }
}
