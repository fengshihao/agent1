package com.agent1.javaagent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpCapabilitySyncTest {

    @TempDir
    Path temp;

    @Test
    void indexesToolCallFormWithoutSchema() {
        McpServersFile.save(temp, List.of(new McpServerRecord(
            "github",
            "https://example.com/mcp",
            Map.of(),
            true,
            "",
            0,
            ""
        )));
        String schema = "{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\",\"description\":\"query\"}},\"required\":[\"q\"]}";
        McpToolLister lister = server -> List.of(
            new McpListedTool("search", "find issues", schema),
            new McpListedTool("create-issue", "open an issue")
        );

        McpCapabilitySync.SyncResult result = McpCapabilitySync.ensureIndexed(temp, lister, true);

        assertEquals(1, result.enabledServers());
        assertEquals(2, result.toolCount());
        var hits = CapabilityIndexStore.search(temp, "find issues", List.of("mcp"), "any", 5);
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().anyMatch(hit -> "$mcp.github.search".equals(hit.entry())));
        assertTrue(hits.stream().anyMatch(hit -> hit.requiresJson() != null && hit.requiresJson().contains("\"q\"")));
        assertTrue(hits.stream().noneMatch(hit -> hit.summary().contains("inputSchema")));
        var hyphen = CapabilityIndexStore.search(temp, "create-issue", List.of("mcp"), "any", 5);
        assertTrue(hyphen.stream().anyMatch(hit -> hit.entry().contains("create-issue")));

        McpCapabilitySync.SyncResult cached = McpCapabilitySync.ensureIndexed(
            temp,
            server -> {
                throw new IllegalStateException("should use cache");
            },
            false
        );
        assertEquals(2, cached.toolCount());
        assertTrue(cached.warnings().isEmpty());
        var fromCache = CapabilityIndexStore.search(temp, "find issues", List.of("mcp"), "any", 5);
        assertTrue(fromCache.stream().anyMatch(hit -> hit.requiresJson() != null && hit.requiresJson().contains("\"q\"")));
    }
}
