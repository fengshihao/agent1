package com.agent1.android.productivity.logic.data

import com.agent1.javaagent.mcp.McpServerRecord
import com.agent1.javaagent.mcp.McpServersFile
import java.nio.file.Path

/** 持久化 HTTP MCP 服务器列表。路径为 {@code agentRoot/mcp_servers.json}。 */
class McpServerFileStore(
    private val agentRoot: Path,
) {
    fun load(): List<McpServerRecord> = McpServersFile.load(agentRoot)

    fun save(servers: List<McpServerRecord>) {
        McpServersFile.save(agentRoot, servers)
    }
}
