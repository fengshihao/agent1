package com.agent1.javaagent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpServersFileTest {

    @TempDir
    Path temp;

    @Test
    void roundTripV2AndRejectsNonHttp() {
        McpServerRecord server = new McpServerRecord(
            "github",
            "https://example.com/mcp",
            Map.of("Authorization", "Bearer secret"),
            true,
            "issues",
            0,
            ""
        );
        McpServersFile.save(temp, List.of(server));
        List<McpServerRecord> loaded = McpServersFile.load(temp);
        assertEquals(1, loaded.size());
        assertEquals("github", loaded.get(0).name());
        assertEquals("Bearer secret", loaded.get(0).headers().get("Authorization"));
        assertTrue(loaded.get(0).enabled());

        assertThrows(IllegalArgumentException.class, () -> McpServersFile.validate(List.of(
            new McpServerRecord("github", "stdio://local", Map.of(), true, "", 0, "")
        )));
        assertThrows(IllegalArgumentException.class, () -> McpServersFile.validate(List.of(
            new McpServerRecord("bad name", "https://example.com/mcp", Map.of(), true, "", 0, "")
        )));
    }

    @Test
    void scriptServersJsonOmitsDisabledServers() {
        McpServersFile.save(temp, List.of(
            new McpServerRecord(
                "github",
                "https://example.com/mcp",
                Map.of("Authorization", "Bearer secret"),
                true,
                "",
                0,
                ""
            ),
            new McpServerRecord("off", "https://example.com/off", Map.of(), false, "", 0, "")
        ));
        String json = McpServersFile.scriptServersJson(temp);
        assertTrue(json.contains("\"github\""));
        assertTrue(json.contains("https://example.com/mcp"));
        assertTrue(json.contains("Bearer secret"));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("\"off\""));
        assertEquals("{}", McpServersFile.scriptServersJson(null));
    }
}
