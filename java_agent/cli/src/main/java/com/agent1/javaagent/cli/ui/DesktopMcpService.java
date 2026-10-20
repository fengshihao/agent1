package com.agent1.javaagent.cli.ui;

import com.agent1.javaagent.mcp.HttpMcpToolLister;
import com.agent1.javaagent.mcp.McpCapabilitySync;
import com.agent1.javaagent.mcp.McpServerRecord;
import com.agent1.javaagent.mcp.McpServersFile;
import java.nio.file.Path;
import java.util.List;

/** 桌面 Web UI：读写 {@code mcp_servers.json} 并刷新能力索引。 */
public final class DesktopMcpService {

    public record SaveResult(int toolCount, List<String> warnings) {
    }

    private DesktopMcpService() {
    }

    public static List<McpServerRecord> load(Path agentRoot) {
        return McpServersFile.load(agentRoot);
    }

    public static SaveResult save(Path agentRoot, List<McpServerRecord> servers) {
        McpServersFile.save(agentRoot, servers);
        try {
            McpCapabilitySync.SyncResult sync =
                McpCapabilitySync.ensureIndexed(agentRoot, new HttpMcpToolLister(), true);
            return new SaveResult(sync.toolCount(), sync.warnings());
        } catch (IllegalStateException e) {
            return new SaveResult(0, List.of(e.getMessage() == null ? "索引 MCP 工具失败" : e.getMessage()));
        }
    }
}
