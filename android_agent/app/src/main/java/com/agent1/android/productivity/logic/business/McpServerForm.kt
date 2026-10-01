package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.mcp.McpServerRecord

/** 设置页里的一条 HTTP MCP 服务器。密钥只放在 Authorization，不写进 URL。 */
data class McpServerForm(
    val name: String,
    val url: String,
    val authorization: String = "",
    val enabled: Boolean = true,
    val description: String = "",
    val toolCount: Int = 0,
)

fun McpServerRecord.toForm(): McpServerForm {
    return McpServerForm(
        name = name,
        url = url,
        authorization = headers["Authorization"].orEmpty(),
        enabled = enabled,
        description = description,
        toolCount = toolCount,
    )
}

fun McpServerForm.toRecord(): McpServerRecord {
    val headers = if (authorization.isBlank()) {
        emptyMap()
    } else {
        mapOf("Authorization" to authorization.trim())
    }
    return McpServerRecord(
        name.trim(),
        url.trim(),
        headers,
        enabled,
        description,
        toolCount,
        "",
    )
}
