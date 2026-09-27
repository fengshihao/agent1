package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.catalog.CatalogDigest;
import com.agent1.javaagent.catalog.CatalogPlatform;
import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.llm.scripted.ScriptedLlmClient;
import com.agent1.javaagent.llm.scripted.ScriptedResponses;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.run.FileRunStore;
import com.agent1.javaagent.run.RunState;
import com.agent1.javaagent.session.FileSessionStore;
import com.agent1.javaagent.session.ProductivityAgentHost;
import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * UC-07 Scripted：单次 execute_script 内 auto-install + ensureNative（同 Session 刷新 native 目录）。
 */
class ProductivityScriptedNativeCatalogTest {

    private static Path weizhiRepo;

    @BeforeAll
    static void requireWeizhi() {
        weizhiRepo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (weizhiRepo.getNameCount() == 0 || !Files.isDirectory(weizhiRepo)) {
            weizhiRepo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        assumeTrue(WeizhiJniBootstrap.tryLoad(weizhiRepo), "libweizhijni not built");
        assumeTrue(WeizhiEchoMathFixtures.pluginDir().isPresent(), "echo_math plugin not built");
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void uc07ScriptedInstallNativeThenEnsureNative(@TempDir Path agentRoot) throws Exception {
        Path weizhiPlugin = WeizhiEchoMathFixtures.pluginDir().orElseThrow();
        byte[] manifestBytes = Files.readAllBytes(weizhiPlugin.resolve("manifest.json"));
        String soName = WeizhiEchoMathFixtures.soFileName();
        byte[] soBytes = Files.readAllBytes(weizhiPlugin.resolve(soName));
        String digestManifest = CatalogDigest.sha256Prefix(manifestBytes);
        String digestSo = CatalogDigest.sha256Prefix(soBytes);
        String platform = CatalogPlatform.currentLabel();

        AgentHomeBootstrap.ensure(agentRoot);
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            String base = server.url("catalog/").toString();
            String manifestUrl = server.url("/catalog-index.json").toString();
            String manifest = """
                {
                  "schemaVersion": 1,
                  "catalogId": "uc07-scripted",
                  "baseUrl": "%s",
                  "items": [
                    {
                      "id": "native.echo_math.manifest",
                      "kind": "native",
                      "version": "1",
                      "platform": "%s",
                      "digest": "%s",
                      "path": "native/%s/echo_math/manifest.json"
                    },
                    {
                      "id": "native.echo_math.so",
                      "kind": "native",
                      "version": "1",
                      "platform": "%s",
                      "digest": "%s",
                      "path": "native/%s/echo_math/%s"
                    }
                  ]
                }
                """.formatted(
                base,
                platform, digestManifest, platform,
                platform, digestSo, platform, soName
            );

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
            server.enqueue(new MockResponse().setBody(new Buffer().write(manifestBytes)));
            server.enqueue(new MockResponse().setBody(new Buffer().write(soBytes)));

            String scriptJson = "{\"code\":\"" + escapeJson(scriptCode()) + "\"}";

            ScriptedLlmClient llm = ScriptedLlmClient.builder()
                .whenUserMessageContains(
                    "scripted-uc07-native",
                    ScriptedResponses.toolCall("execute_script", scriptJson)
                )
                .whenToolResultSucceeded(ScriptedResponses.text("native 已跑通"))
                .build();

            WeizhiScriptEngineFactory factory = new WeizhiScriptEngineFactory(
                new WeizhiRuntimeOptions().installDesktopCaps(true),
                agentRoot
            );
            AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("mock-key").build();
            try (ProductivityAgentHost host = new ProductivityAgentHost(
                agentRoot,
                config,
                llm,
                factory,
                WeizhiHostSupport.scriptTimeoutMs(),
                ""
            )) {
                host.createSession();
                String runId = host.runUserMessage("任务:scripted-uc07-native 装 echo_math 并相加");
                var messages = host.runtime().getStateSnapshot().getMessages();
                RunState state = new FileRunStore(new FileSessionStore(agentRoot))
                    .read(host.getActiveSessionId(), runId)
                    .getState();
                assertEquals(
                    RunState.SUCCEEDED,
                    state,
                    () -> messages.stream()
                        .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
                        .map(AgentMessage::getContent)
                        .reduce((a, b) -> a + "\n---\n" + b)
                        .orElse("(no tool results)")
                );

                assertTrue(messages.stream()
                    .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
                    .anyMatch(m -> m.getContent().contains("[catalog] auto-installed")));
                assertTrue(messages.stream()
                    .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
                    .anyMatch(m -> m.getContent().contains("42")));
                assertTrue(messages.stream()
                    .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
                    .noneMatch(m -> m.getContent().contains("[coach] catalog.missing_native")));
            }
        }
    }

    private static String scriptCode() {
        return "const p = await host.ensureNative('echo_math'); JSON.stringify(p.add(20, 22));";
    }

    private static String escapeJson(String code) {
        return code.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
