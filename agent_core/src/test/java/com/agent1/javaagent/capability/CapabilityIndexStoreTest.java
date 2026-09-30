package com.agent1.javaagent.capability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CapabilityIndexStoreTest {

    @TempDir
    Path temp;

    @Test
    void ensureCreatesDatabaseAndFindsDocx() {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);

        Path db = CapabilityDatabasePaths.databaseFile(agentRoot);
        assertTrue(Files.isRegularFile(db));

        var hits = CapabilityIndexStore.search(agentRoot, "docx word", List.of(), "any", 5);
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().anyMatch(h -> h.id().contains("docx")));
    }

    @Test
    void platformFilterAndroidShare() {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);

        var hits = CapabilityIndexStore.search(agentRoot, "微信 share", List.of("caps"), "android", 5);
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().anyMatch(h -> h.entry().contains("android.share")));
    }

    @Test
    void likeFallbackFindsBridgeTool() {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);

        var hits = CapabilityIndexStore.search(agentRoot, "webview_exec", List.of(), "any", 3);
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().anyMatch(h -> "webview_exec".equals(h.entry())));
    }

    @Test
    void seedOmitsPromptCoveredAgentTools() {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);

        var hits = CapabilityIndexStore.search(agentRoot, "execute_script", List.of("agent_tool"), "any", 5);
        assertTrue(hits.isEmpty());
    }
}
