package com.agent1.javaagent.mcp;

import java.util.Map;

/** 与微智 {@code mcp_servers.json} v2 的 {@code servers[i]} 字段对齐。 */
public record McpServerRecord(
    String name,
    String url,
    Map<String, String> headers,
    boolean enabled,
    String description,
    int toolCount,
    String lastListedAt
) {
    public McpServerRecord {
        headers = headers == null || headers.isEmpty() ? Map.of() : Map.copyOf(headers);
        description = description == null ? "" : description;
        lastListedAt = lastListedAt == null ? "" : lastListedAt;
        if (toolCount < 0) {
            toolCount = 0;
        }
    }

    public McpServerRecord withListing(int listedTools, String listedAt) {
        return new McpServerRecord(name, url, headers, enabled, description, listedTools, listedAt);
    }
}
