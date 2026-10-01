package com.agent1.javaagent.mcp;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.capability.CapabilityRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把已启用 MCP server 的工具写进能力索引（kind=mcp）。
 * 搜索结果只给名称、短描述和 {@code $mcp} 调用形式，不把参数 schema 放进模型工具列表。
 */
public final class McpCapabilitySync {

    private McpCapabilitySync() {
    }

    public static SyncResult ensureIndexed(Path agentRoot) {
        return ensureIndexed(agentRoot, new HttpMcpToolLister(), false);
    }

    public static SyncResult ensureIndexed(Path agentRoot, McpToolLister lister, boolean forceNetwork) {
        if (!Files.isRegularFile(McpServersFile.configFile(agentRoot))) {
            return new SyncResult(0, 0, List.of());
        }
        List<McpServerRecord> servers = McpServersFile.load(agentRoot);
        List<McpServerRecord> updated = new ArrayList<>();
        List<CapabilityRecord> records = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean rewriteConfig = false;
        int enabled = 0;
        int toolCount = 0;
        for (McpServerRecord server : servers) {
            if (!server.enabled()) {
                updated.add(server);
                continue;
            }
            enabled++;
            boolean cacheOk = McpServersFile.cacheMatches(agentRoot, server);
            List<McpListedTool> cached = cacheOk
                ? McpServersFile.readToolCache(agentRoot, server.name())
                : null;
            List<McpListedTool> tools = !forceNetwork && cached != null ? cached : null;
            boolean listedNow = false;
            if (tools == null) {
                try {
                    tools = lister.listTools(server);
                    McpServersFile.writeToolCache(agentRoot, server, tools);
                    listedNow = true;
                } catch (IOException e) {
                    warnings.add(clip(e.getMessage()));
                    tools = cached;
                } catch (RuntimeException e) {
                    warnings.add(server.name() + ": " + clip(e.getMessage()));
                    tools = cached;
                }
            }
            if (tools == null) {
                records.add(unavailable(server));
                updated.add(server);
                continue;
            }
            Map<String, McpListedTool> unique = new LinkedHashMap<>();
            for (McpListedTool tool : tools) {
                if (!tool.name().isBlank()) {
                    unique.putIfAbsent(tool.name(), tool);
                }
            }
            toolCount += unique.size();
            if (unique.isEmpty()) {
                records.add(emptyServer(server));
            } else {
                for (McpListedTool tool : unique.values()) {
                    records.add(toRecord(server, tool));
                }
            }
            if (listedNow) {
                rewriteConfig = true;
                updated.add(server.withListing(unique.size(), Instant.now().toString()));
            } else {
                updated.add(server);
            }
        }
        if (rewriteConfig) {
            McpServersFile.save(agentRoot, updated);
        }
        CapabilityIndexStore.replaceKind(agentRoot, "mcp", records);
        return new SyncResult(enabled, toolCount, List.copyOf(warnings));
    }

    private static CapabilityRecord toRecord(McpServerRecord server, McpListedTool tool) {
        String entry = McpCallForm.entry(server.name(), tool.name());
        String summary = tool.description().trim().replace('\n', ' ');
        if (summary.isBlank()) {
            summary = "在脚本里调用 " + entry;
        }
        return new CapabilityRecord(
            "mcp:" + server.name() + "/" + tool.name(),
            "mcp",
            clip(server.name() + " / " + tool.name(), 160),
            clip(summary, 280),
            server.name() + " " + tool.name() + " mcp",
            "any",
            entry,
            "",
            "",
            "",
            "mcp",
            1.0
        );
    }

    private static CapabilityRecord unavailable(McpServerRecord server) {
        String entry = callHint(server.name());
        return new CapabilityRecord(
            "mcp:" + server.name() + "/",
            "mcp",
            server.name(),
            clip(server.url() + " 暂时列不出工具。连通后在脚本里调用 " + entry, 280),
            server.name() + " mcp",
            "any",
            entry,
            "",
            "",
            "",
            "mcp",
            1.0
        );
    }

    private static CapabilityRecord emptyServer(McpServerRecord server) {
        String entry = callHint(server.name());
        return new CapabilityRecord(
            "mcp:" + server.name() + "/",
            "mcp",
            server.name(),
            clip("该服务器当前没有工具。有工具后在脚本里调用 " + entry, 280),
            server.name() + " mcp",
            "any",
            entry,
            "",
            "",
            "",
            "mcp",
            1.0
        );
    }

    private static String callHint(String server) {
        if (server.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return "$mcp." + server + ".<tool>";
        }
        return "$mcp[\"" + server.replace("\\", "\\\\").replace("\"", "\\\"") + "\"][\"<tool>\"]";
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String clip(String text) {
        return clip(text, 180);
    }

    public record SyncResult(int enabledServers, int toolCount, List<String> warnings) {
    }
}
