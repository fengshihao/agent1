package com.agent1.javaagent.mcp;

/** {@code tools/list} 里的一条工具。参数 schema 不进入能力索引。 */
public record McpListedTool(String name, String description) {
    public McpListedTool {
        name = name == null ? "" : name.trim();
        description = description == null ? "" : description;
    }
}
