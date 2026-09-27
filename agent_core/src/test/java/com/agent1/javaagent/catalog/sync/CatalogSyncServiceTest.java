package com.agent1.javaagent.catalog.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.catalog.CatalogDigest;
import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogSyncServiceTest {

    private static final byte[] SCRIPT = "console.log(\"catalog-demo\");\n".getBytes();

    @Test
    void checkAndApplyInstallsScriptAndCapabilities(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            String base = server.url("v1/").toString();
            String manifestUrl = server.url("/catalog-index.json").toString();
            byte[] jsLib = "function inc(x) { return x + 1; }\n".getBytes();
            String digest = CatalogDigest.sha256Prefix(SCRIPT);
            String digestLib = CatalogDigest.sha256Prefix(jsLib);
            String manifest = """
                {
                  "schemaVersion": 1,
                  "catalogId": "test-official",
                  "baseUrl": "%s",
                  "items": [
                    {
                      "id": "script.demo",
                      "kind": "script",
                      "version": "1",
                      "digest": "%s",
                      "path": "scripts/demo.js"
                    },
                    {
                      "id": "lib.demo",
                      "kind": "js_lib",
                      "version": "1",
                      "digest": "%s",
                      "path": "libs/js/demo-lib.js"
                    }
                  ]
                }
                """.formatted(base, digest, digestLib);
            server.enqueue(new MockResponse().setBody(manifest));

            Path manifestFile = agentRoot.resolve("agent.manifest.json");
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = (ObjectNode) mapper.readTree(PathIo.readString(manifestFile));
            ObjectNode catalog = mapper.createObjectNode();
            catalog.put("manifestUrl", manifestUrl);
            root.set("catalog", catalog);
            PathIo.writeString(manifestFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));

            OkHttpClient http = new OkHttpClient();
            CatalogSyncService service = new CatalogSyncService(agentRoot, http);

            CatalogSyncService.SyncCheckResult check = service.check();
            assertEquals(2, check.pending().size());

            server.enqueue(new MockResponse().setBody(manifest));
            server.enqueue(new MockResponse().setBody(new String(SCRIPT)));
            server.enqueue(new MockResponse().setBody(new String(jsLib)));

            CatalogSyncService.SyncApplyResult apply = service.apply(
                java.util.List.of("script.demo", "lib.demo")
            );
            assertEquals(2, apply.appliedIds().size());
            assertTrue(apply.errors().isEmpty());

            Path installed = agentRoot.resolve("shared/catalog/scripts/demo.js");
            assertTrue(Files.isRegularFile(installed));
            assertTrue(Files.isRegularFile(agentRoot.resolve("shared/catalog/libs/js/demo-lib.js")));
            assertTrue(Files.isRegularFile(agentRoot.resolve("shared/catalog/scripts/demo-lib.js")));
            assertTrue(Files.isRegularFile(agentRoot.resolve("docs/capabilities/script.demo.md")));
            SyncState state = SyncState.load(agentRoot);
            assertTrue(state.installed("script.demo").isPresent());
        }
    }

    /** UC-10：manifest digest 变更 → 仅该 id pending，apply 只拉新版本。 */
    @Test
    void uc10DigestChangeMarksUpdatedAndApplyRefetches(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        byte[] v1 = "console.log(\"v1\");\n".getBytes();
        byte[] v2 = "console.log(\"v2\");\n".getBytes();
        String digestV1 = CatalogDigest.sha256Prefix(v1);
        String digestV2 = CatalogDigest.sha256Prefix(v2);

        try (MockWebServer server = new MockWebServer()) {
            server.start();
            String base = server.url("v1/").toString();
            String manifestUrl = server.url("/catalog-index.json").toString();

            String manifestV1 = manifestJson(base, digestV1, "1");
            configureManifestUrl(agentRoot, manifestUrl);

            OkHttpClient http = new OkHttpClient();
            CatalogSyncService service = new CatalogSyncService(agentRoot, http);

            server.enqueue(new MockResponse().setBody(manifestV1));
            server.enqueue(new MockResponse().setBody(new String(v1)));
            service.apply(java.util.List.of("script.demo"));
            assertEquals("console.log(\"v1\");\n", Files.readString(agentRoot.resolve("shared/catalog/scripts/demo.js")));

            String manifestV2 = manifestJson(base, digestV2, "2");
            server.enqueue(new MockResponse().setBody(manifestV2));
            CatalogSyncService.SyncCheckResult check = service.check();
            assertEquals(1, check.pending().size());
            assertEquals("script.demo", check.pending().get(0).item().id());

            server.enqueue(new MockResponse().setBody(manifestV2));
            server.enqueue(new MockResponse().setBody(new String(v2)));
            CatalogSyncService.SyncApplyResult apply = service.apply(java.util.List.of("script.demo"));
            assertEquals(1, apply.appliedIds().size());
            assertEquals("console.log(\"v2\");\n", Files.readString(agentRoot.resolve("shared/catalog/scripts/demo.js")));

            SyncState state = SyncState.load(agentRoot);
            assertEquals(digestV2, state.installed("script.demo").orElseThrow().digest());
        }
    }

    private static String manifestJson(String base, String digest, String version) {
        return """
            {
              "schemaVersion": 1,
              "catalogId": "uc10",
              "baseUrl": "%s",
              "items": [
                {
                  "id": "script.demo",
                  "kind": "script",
                  "version": "%s",
                  "digest": "%s",
                  "path": "scripts/demo.js"
                }
              ]
            }
            """.formatted(base, version, digest);
    }

    private static void configureManifestUrl(Path agentRoot, String manifestUrl) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(PathIo.readString(agentRoot.resolve("agent.manifest.json")));
        ObjectNode catalog = mapper.createObjectNode();
        catalog.put("manifestUrl", manifestUrl);
        root.set("catalog", catalog);
        PathIo.writeString(
            agentRoot.resolve("agent.manifest.json"),
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
        );
    }
}
