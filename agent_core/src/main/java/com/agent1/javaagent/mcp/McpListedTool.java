package com.agent1.javaagent.mcp;

/** {@code tools/list} 里的一条工具。{@code inputSchema} 是 JSON 对象文本，入库后只在搜索的前两条里展开。 */
public record McpListedTool(String name, String description, String inputSchema) {
    public McpListedTool(String name, String description) {
        this(name, description, "");
    }

    public McpListedTool {
        name = name == null ? "" : name.trim();
        description = description == null ? "" : description;
        inputSchema = inputSchema == null ? "" : inputSchema.trim();
    }
}
