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
    void ensureCreatesDatabaseAndFindsHostSeed() {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);

        Path db = CapabilityDatabasePaths.databaseFile(agentRoot);
        assertTrue(Files.isRegularFile(db));

        var hits = CapabilityIndexStore.search(agentRoot, "skill creator", List.of(), "any", 5);
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().anyMatch(h -> "skill.skill-creator".equals(h.id())));
    }

    /** weizhi-api-cards.jsonl 无 kind 字段：android.* 仍按前缀推断 caps。云端 office 卡不进 seed。 */
    @Test
    void weizhiCardsInferCapsAndOfficeCardsAreNotSeeded() throws Exception {
        var records = CapabilitySeedLoader.loadBundledSeed();
        assertTrue(records.stream().noneMatch(r -> r.id().equals("html2pptx.render")
            || r.id().equals("pptx.render")
            || r.id().startsWith("docx.")),
            "cloud package cards must not be classpath seed");
        assertTrue(records.stream().anyMatch(r -> r.id().equals("android.share.send")
            && "caps".equals(r.kind()) && "weizhi".equals(r.source())),
            "weizhi 卡未显式 kind 时按前缀推断 caps");

        CapabilityRecord parsed = CapabilitySeedLoader.parseWeizhiCard(
            "{\"id\":\"html2pptx.render\",\"kind\":\"weizhi_package\",\"title\":\"t\",\"summary\":\"s\","
                + "\"entry\":\"import\",\"weight\":1.2,\"source\":\"agent1\"}");
        assertEquals("weizhi_package", parsed.kind());
        assertEquals(1.2, parsed.weight());
        assertEquals("agent1", parsed.source());
    }

    @Test
    void staleSeedHashRebuildsEvenWhenRevisionMatches() throws Exception {
        Path agentRoot = temp.resolve("agentRootStaleHash");
        CapabilityIndexStore.ensure(agentRoot);
        Path db = CapabilityDatabasePaths.databaseFile(agentRoot);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
            Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM meta WHERE key = 'seed_hash'");
            statement.executeUpdate("DELETE FROM capability WHERE id = 'agent.promotion'");
            statement.executeUpdate("DELETE FROM capability_fts WHERE id = 'agent.promotion'");
        }

        var missing = CapabilityIndexStore.searchLike(db, "agent.promotion", List.of(), "any", 5);
        assertTrue(missing.isEmpty());

        CapabilityIndexStore.ensure(agentRoot);
        var hits = CapabilityIndexStore.search(agentRoot, "create skill skill directory structure", List.of(), "android", 5);
        assertEquals("agent.promotion", hits.get(0).id());
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
    void bundledSeedKeepsWebLibRoutingWithoutLibraryRows() {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);

        var libraries = CapabilityIndexStore.search(agentRoot, "流程图 mermaid three markmap", List.of("web_lib"), "any", 5);
        assertTrue(libraries.isEmpty());
        var routing = CapabilityIndexStore.search(agentRoot, "稳定 CDN", List.of(), "any", 5);
        assertTrue(routing.stream().anyMatch(h -> "agent.web_cdn".equals(h.id())));
    }

    @Test
    void svgQueryDoesNotHitCloudPackageFromSeed() {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);

        var hits = CapabilityIndexStore.search(agentRoot, "svg png", List.of("weizhi_package"), "any", 5);
        assertTrue(hits.stream().noneMatch(h -> "svg.svgToImage".equals(h.id())));
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
        assertEquals("agent.promotion", hits.get(0).id());
        assertEquals("", hits.get(0).docPath());
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
        assertEquals("agent.promotion", hits.get(0).id());
        assertEquals("", hits.get(0).docPath());
    }

    @Test
    void systemDocsAreIndexed() {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);

        assertEquals(
            "agent.directories",
            CapabilityIndexStore.search(agentRoot, "directories workspace", List.of("builtin"), "any", 3).get(0).id()
        );
        assertTrue(
            CapabilityIndexStore.search(agentRoot, "catalog_install", List.of("builtin"), "any", 3).stream()
                .noneMatch(hit -> "agent.catalog-install".equals(hit.id()))
        );
        assertEquals(
            "agent.events-audit",
            CapabilityIndexStore.search(agentRoot, "events.jsonl", List.of("builtin"), "any", 3).get(0).id()
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
            statement.executeUpdate("DELETE FROM capability WHERE id = 'agent.promotion'");
            statement.executeUpdate("DELETE FROM capability_fts WHERE id = 'agent.promotion'");
        }

        var missing = CapabilityIndexStore.searchLike(
            db,
            "agent.promotion",
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
        assertEquals("agent.promotion", hits.get(0).id());
    }

    @Test
    void aarVideoCardGuidesTimelineV2Tracks() {
        var records = CapabilitySeedLoader.loadBundledSeed();
        var card = records.stream()
            .filter(r -> r.id().equals("media.video"))
            .findFirst()
            .orElseThrow();
        assertTrue(card.summary().contains("AAR"), card.summary());
        assertTrue(card.entry().contains("tracks"), card.entry());
        assertTrue(card.tags().contains("关键帧"), card.tags());

        Path agentRoot = temp.resolve("agentRootVideoV2");
        CapabilityIndexStore.ensure(agentRoot);
        var hits = CapabilityIndexStore.search(agentRoot, "关键帧 crossfade 时间线", List.of(), "android", 8);
        assertTrue(hits.stream().anyMatch(h -> h.id().equals("media.video")));
        assertTrue(hits.stream().anyMatch(h -> h.id().equals("media.video") && h.entry().contains("tracks")));
    }

    @Test
    void seedOmitsPromptCoveredAgentTools() {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilityIndexStore.ensure(agentRoot);

        var hits = CapabilityIndexStore.search(agentRoot, "run_js", List.of("agent_tool"), "any", 5);
        assertTrue(hits.isEmpty());
    }
}
