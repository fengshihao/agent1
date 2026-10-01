package com.agent1.javaagent.mcp;

import java.io.IOException;
import java.util.List;

/** 向一个 HTTP MCP server 拉取工具名和描述。 */
public interface McpToolLister {

    List<McpListedTool> listTools(McpServerRecord server) throws IOException;
}
