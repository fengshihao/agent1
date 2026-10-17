package com.agent1.javaagent.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebLibRegistrySyncTest {

    @TempDir
    Path temp;

    @Test
    void manifestCardsReplacePreviousWebLibRows() throws Exception {
        Path agentRoot = temp.resolve("agent");
        CapabilityIndexStore.ensure(agentRoot);
        var before = CapabilityIndexStore.search(agentRoot, "流程图 mermaid", List.of("web_lib"), "any", 5);
        assertTrue(before.isEmpty());

        String json = """
            {"schema_version":1,"channel":"stable","cards":[{
              "id":"web_lib.mermaid",
              "kind":"web_lib",
              "title":"Mermaid 图表",
              "summary":"流程图",
              "tags":["mermaid","流程图"],
              "platforms":["any"],
              "entry":"URL https://cdn.jsdelivr.net/npm/mermaid@11.4.0/dist/mermaid.min.js",
              "weight":1.25
            }]}
            """;
        WebLibRegistrySync.applyManifest(agentRoot, json);

        var hits = CapabilityIndexStore.search(agentRoot, "流程图", List.of(), "any", 5);
        assertEquals("web_lib.mermaid", hits.get(0).id());
        assertTrue(hits.get(0).entry().contains("cdn.jsdelivr.net/npm/mermaid@"));

        WebLibRegistrySync.applyManifest(agentRoot, "{\"schema_version\":1,\"cards\":[]}");
        var after = CapabilityIndexStore.search(agentRoot, "流程图 mermaid", List.of("web_lib"), "any", 5);
        assertTrue(after.isEmpty());
        var remaining = CapabilityIndexStore.search(agentRoot, "链式生成 PPT", List.of(), "any", 5);
        assertTrue(remaining.stream().anyMatch(hit -> hit.id().startsWith("pptx.")));
    }

    @Test
    void rejectsCardsThatAreNotWebLibs() {
        assertThrows(Exception.class, () -> WebLibRegistrySync.cardsFromManifest("""
            {"schema_version":1,"cards":[{"id":"docx.markdownToDocx","kind":"weizhi_package","entry":"import"}]}
            """));
    }

    @Test
    void officialUrlIsHttpsOnTheOfficialHost() {
        assertEquals("https", WebLibRegistrySync.DEFAULT_REGISTRY_URL.startsWith("https://") ? "https" : "other");
        assertTrue(WebLibRegistrySync.DEFAULT_REGISTRY_URL.contains(WebLibRegistrySync.OFFICIAL_HOST));
        assertTrue(WebLibRegistrySync.DEFAULT_REGISTRY_URL.endsWith("/web-libs/registry/stable.json"));
    }
}
