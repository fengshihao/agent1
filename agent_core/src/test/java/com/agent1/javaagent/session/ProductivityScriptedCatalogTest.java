package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.catalog.CatalogDigest;
import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.llm.scripted.ScriptedLlmClient;
import com.agent1.javaagent.llm.scripted.ScriptedResponses;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.log.AgentDataPaths;
import com.agent1.javaagent.run.FileRunStore;
import com.agent1.javaagent.run.RunState;
import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** UC-06 / UC-10：catalog_sync_status + catalog_install（Mock HTTP + Scripted）。 */
class ProductivityScriptedCatalogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final byte[] SCRIPT = "console.log(\"catalog-demo\");\n".getBytes();

    @Test
    void uc06InstallPendingCatalogItem(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            String base = server.url("v1/").toString();
            String manifestUrl = server.url("/catalog-index.json").toString();
            String digest = CatalogDigest.sha256Prefix(SCRIPT);
            String manifest = """
                {
                  "schemaVersion": 1,
                  "catalogId": "scripted",
                  "baseUrl": "%s",
                  "items": [
                    {
                      "id": "script.demo",
                      "kind": "script",
                      "version": "1",
                      "digest": "%s",
                      "path": "scripts/demo.js"
                    }
                  ]
                }
                """.formatted(base, digest);

            ObjectNode root = (ObjectNode) MAPPER.readTree(PathIo.readString(agentRoot.resolve("agent.manifest.json")));
            ObjectNode catalog = MAPPER.createObjectNode();
            catalog.put("manifestUrl", manifestUrl);
            root.set("catalog", catalog);
            PathIo.writeString(
                agentRoot.resolve("agent.manifest.json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root)
            );

            server.enqueue(new MockResponse().setBody(manifest));
            server.enqueue(new MockResponse().setBody(manifest));
            server.enqueue(new MockResponse().setBody(new String(SCRIPT)));

            ScriptedLlmClient llm = ScriptedLlmClient.builder()
                .whenUserMessageContains(
                    "scripted-catalog-uc06",
                    ScriptedResponses.toolCall("catalog_sync_status", "{}")
                )
                .whenToolResultContains(
                    "pending:",
                    ScriptedResponses.toolCall(
                        "catalog_install",
                        "{\"ids\":[\"script.demo\"]}"
                    )
                )
                .whenToolResultContains("ok script.demo", ScriptedResponses.text("catalog 已装"))
                .build();

            AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("mock-key").build();
            try (ProductivityAgentHost host = new ProductivityAgentHost(agentRoot, config, llm)) {
                host.createSession();
                String runId = host.runUserMessage("任务:scripted-catalog-uc06 安装 catalog 脚本");
                assertEquals(
                    RunState.SUCCEEDED,
                    new FileRunStore(new FileSessionStore(agentRoot)).read(host.getActiveSessionId(), runId).getState()
                );

                assertTrue(Files.isRegularFile(agentRoot.resolve("shared/catalog/scripts/demo.js")));
                assertTrue(host.runtime().getStateSnapshot().getMessages().stream()
                    .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
                    .anyMatch(m -> m.getContent().contains("ok script.demo")));

                String events = Files.readString(AgentDataPaths.eventsJsonl(agentRoot));
                assertTrue(events.contains("\"type\":\"catalog_sync_checked\""));
                assertTrue(events.contains("\"type\":\"catalog_sync_completed\""));
            }
        }
    }
}
