package com.agent1.javaagent.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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
    void createSkillQueryHitsPromotionDocFirst() {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);

        var hits = CapabilityIndexStore.search(
            agentRoot,
            "create skill skill directory structure",
            List.of(),
            "android",
            5
        );
        assertFalse(hits.isEmpty());
        assertEquals("doc.promotion", hits.get(0).id());
        assertEquals("docs/system/promotion.md", hits.get(0).docPath());
    }

    @Test
    void skillCreatorQueryHitsBundledSkillFirst() {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);

        var hits = CapabilityIndexStore.search(agentRoot, "skill creator", List.of(), "android", 5);
        assertFalse(hits.isEmpty());
        assertEquals("skill.skill-creator", hits.get(0).id());
        assertEquals("skill:skill-creator", hits.get(0).entry());
    }

    @Test
    void promoteSharedLocalQueryHitsPromotionDocFirst() {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);

        var hits = CapabilityIndexStore.search(
            agentRoot,
            "skill promote shared/local",
            List.of(),
            "android",
            5
        );
        assertFalse(hits.isEmpty());
        assertEquals("doc.promotion", hits.get(0).id());
        assertEquals("docs/system/promotion.md", hits.get(0).docPath());
    }

    @Test
    void systemDocsAreIndexed() {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);

        assertEquals(
            "doc.directories",
            CapabilityIndexStore.search(agentRoot, "directories workspace", List.of("doc"), "any", 3).get(0).id()
        );
        assertEquals(
            "doc.catalog-install",
            CapabilityIndexStore.search(agentRoot, "catalog_install", List.of("doc"), "any", 3).get(0).id()
        );
        assertEquals(
            "doc.events-audit",
            CapabilityIndexStore.search(agentRoot, "events.jsonl", List.of("doc"), "any", 3).get(0).id()
        );
        assertEquals(
            "doc.trusted-sources",
            CapabilityIndexStore.search(agentRoot, "trusted source", List.of("doc"), "any", 3).get(0).id()
        );
    }

    @Test
    void staleSeedRevisionRebuildsMissingDocs() throws Exception {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);
        Path db = CapabilityDatabasePaths.databaseFile(agentRoot);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
            Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE meta SET value = '0' WHERE key = 'seed_revision'");
            statement.executeUpdate("DELETE FROM capability WHERE id = 'doc.promotion'");
            statement.executeUpdate("DELETE FROM capability_fts WHERE id = 'doc.promotion'");
        }

        var missing = CapabilityIndexStore.searchLike(
            db,
            "doc.promotion",
            List.of(),
            "any",
            5
        );
        assertTrue(missing.isEmpty());

        CapabilityIndexStore.ensure(agentRoot);
        var hits = CapabilityIndexStore.search(
            agentRoot,
            "create skill skill directory structure",
            List.of(),
            "android",
            5
        );
        assertEquals("doc.promotion", hits.get(0).id());
    }

    @Test
    void seedOmitsPromptCoveredAgentTools() {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);

        var hits = CapabilityIndexStore.search(agentRoot, "execute_script", List.of("agent_tool"), "any", 5);
        assertTrue(hits.isEmpty());
    }
}
