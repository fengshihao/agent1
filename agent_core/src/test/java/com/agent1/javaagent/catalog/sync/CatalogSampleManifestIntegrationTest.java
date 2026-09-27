package com.agent1.javaagent.catalog.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 仓库 catalog-sample 静态资源（script + js_lib）端到端 apply。 */
class CatalogSampleManifestIntegrationTest {

    @Test
    void applySampleManifestTwoKinds(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path manifestPath = Path.of(
            getClass().getResource("/catalog-sample/catalog-index.json").toURI()
        );
        Path sampleRoot = manifestPath.getParent();

        byte[] script = Files.readAllBytes(sampleRoot.resolve("scripts/sample-hello.js"));
        byte[] jsLib = Files.readAllBytes(sampleRoot.resolve("libs/js/sample-inc.js"));

        try (MockWebServer server = new MockWebServer()) {
            server.start();
            String base = server.url("catalog-sample/").toString();
            String manifestUrl = server.url("/catalog-index.json").toString();
            String manifest = Files.readString(sampleRoot.resolve("catalog-index.json"))
                .replace("https://example.invalid/agent1/catalog-sample/", base);

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = (ObjectNode) mapper.readTree(PathIo.readString(agentRoot.resolve("agent.manifest.json")));
            ObjectNode catalog = mapper.createObjectNode();
            catalog.put("manifestUrl", manifestUrl);
            root.set("catalog", catalog);
            PathIo.writeString(
                agentRoot.resolve("agent.manifest.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
            );

            OkHttpClient http = new OkHttpClient();
            CatalogSyncService service = new CatalogSyncService(agentRoot, http);

            server.enqueue(new MockResponse().setBody(manifest));
            assertEquals(2, service.check().pending().size());

            server.enqueue(new MockResponse().setBody(manifest));
            server.enqueue(new MockResponse().setBody(new String(script, StandardCharsets.UTF_8)));
            server.enqueue(new MockResponse().setBody(new String(jsLib, StandardCharsets.UTF_8)));

            var apply = service.apply(java.util.List.of("script.sample-hello", "lib.sample-inc"));
            assertEquals(2, apply.appliedIds().size());
            assertTrue(Files.isRegularFile(agentRoot.resolve("shared/catalog/scripts/sample-hello.js")));
            assertTrue(Files.isRegularFile(agentRoot.resolve("shared/catalog/libs/js/sample-inc.js")));
        }
    }
}
