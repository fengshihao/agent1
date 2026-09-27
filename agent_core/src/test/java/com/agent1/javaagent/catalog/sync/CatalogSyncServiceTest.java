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
            String digest = CatalogDigest.sha256Prefix(SCRIPT);
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
                """.formatted(base, digest, digest);
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

            CatalogSyncService.SyncApplyResult apply = service.apply(java.util.List.of("script.demo"));
            assertEquals(1, apply.appliedIds().size());
            assertTrue(apply.errors().isEmpty());

            Path installed = agentRoot.resolve("shared/catalog/scripts/demo.js");
            assertTrue(Files.isRegularFile(installed));
            assertTrue(Files.isRegularFile(agentRoot.resolve("docs/capabilities/script.demo.md")));
            SyncState state = SyncState.load(agentRoot);
            assertTrue(state.installed("script.demo").isPresent());
        }
    }
}
