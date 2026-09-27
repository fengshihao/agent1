package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.log.AgentDataPaths;
import com.agent1.javaagent.llm.scripted.ScriptedLlmClient;
import com.agent1.javaagent.llm.scripted.ScriptedResponses;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.run.FileRunStore;
import com.agent1.javaagent.run.RunState;
import com.agent1.javaagent.script.FakeScriptEngineFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * UC-02 / UC-12 等高成本 LLM 场景：用 {@link ScriptedLlmClient} 驱动真实工具链 + Coach（Mock 优先）。
 */
class ProductivityScriptedCoachTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void uc12OutsideWriteAppendsPathOutsideCoach(@TempDir Path agentRoot) throws Exception {
        prepareCoachManifest(agentRoot, 65_536, 80, 8_192);

        ScriptedLlmClient llm = ScriptedLlmClient.builder()
            .whenUserMessageContains(
                "scripted-coach-outside",
                ScriptedResponses.toolCall(
                    "write_file",
                    "{\"path\":\"../shared/catalog/foo.txt\",\"content\":\"test\"}"
                ),
                ScriptedResponses.text("已看到工具回执")
            )
            .build();

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("mock-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(agentRoot, config, llm)) {
            host.createSession();
            String runId = host.runUserMessage("任务:scripted-coach-outside 越权写入");
            assertEquals(RunState.SUCCEEDED, new FileRunStore(new FileSessionStore(agentRoot))
                .read(host.getActiveSessionId(), runId).getState());

            assertTrue(host.runtime().getStateSnapshot().getMessages().stream()
                .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
                .anyMatch(m -> m.getContent().contains("[coach] path.outside_attempt")));
        }
    }

    @Test
    void uc02LargeWriteAppendsLargeWriteCoachWhenThresholdLow(@TempDir Path agentRoot) throws Exception {
        prepareCoachManifest(agentRoot, 5, 80, 8_192);

        ScriptedLlmClient llm = ScriptedLlmClient.builder()
            .whenUserMessageContains(
                "scripted-coach-large",
                ScriptedResponses.toolCall(
                    "write_file",
                    "{\"path\":\"big.txt\",\"content\":\"12345\"}"
                ),
                ScriptedResponses.text("大写入 coach 已验")
            )
            .build();

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("mock-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(agentRoot, config, llm)) {
            host.createSession();
            host.runUserMessage("任务:scripted-coach-large 大文件写入");

            assertTrue(host.runtime().getStateSnapshot().getMessages().stream()
                .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
                .anyMatch(m -> m.getContent().contains("[coach] file.large_write")));
        }
    }

    @Test
    void uc05ScriptFailRepeatCoachAfterTwoInlineFailures(@TempDir Path agentRoot) throws Exception {
        prepareCoachManifest(agentRoot, 65_536, 80, 8_192, 2);

        ScriptedLlmClient llm = ScriptedLlmClient.builder()
            .whenUserMessageContains(
                "scripted-fail-repeat-uc05",
                ScriptedResponses.toolCall("execute_script", "{\"code\":\"bad();\"}")
            )
            .whenToolResultFailed(
                ScriptedResponses.toolCall("execute_script", "{\"code\":\"bad();\"}")
            )
            .whenToolResultFailed(ScriptedResponses.text("已记录 fail_repeat"))
            .build();

        FakeScriptEngineFactory scripts = new FakeScriptEngineFactory(new RuntimeException("SyntaxError: at line 1"));
        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("mock-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(
            agentRoot,
            config,
            llm,
            scripts,
            5_000,
            ""
        )) {
            host.createSession();
            host.runUserMessage("任务:scripted-fail-repeat-uc05 重复脚本失败");

            assertTrue(host.runtime().getStateSnapshot().getMessages().stream()
                .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
                .anyMatch(m -> m.getContent().contains("[coach] script.fail_repeat")));
        }
    }

    @Test
    void ucCatalogPendingCoachAfterSyncStatus(@TempDir Path agentRoot) throws Exception {
        prepareCoachManifest(agentRoot, 65_536, 80, 8_192, 3);
        AgentHomeBootstrap.ensure(agentRoot);

        try (MockWebServer server = new MockWebServer()) {
            server.start();
            String base = server.url("v1/").toString();
            String manifestUrl = server.url("/catalog-index.json").toString();
            String manifest = """
                {"schemaVersion":1,"catalogId":"c","baseUrl":"%s","items":[
                {"id":"script.x","kind":"script","version":"1","digest":"sha256:ab","path":"scripts/x.js"}]}
                """.formatted(base);
            ObjectNode root = (ObjectNode) MAPPER.readTree(
                Files.readString(agentRoot.resolve("agent.manifest.json"))
            );
            ObjectNode catalog = MAPPER.createObjectNode();
            catalog.put("manifestUrl", manifestUrl);
            root.set("catalog", catalog);
            Files.writeString(
                agentRoot.resolve("agent.manifest.json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root)
            );
            server.enqueue(new MockResponse().setBody(manifest));

            ScriptedLlmClient llm = ScriptedLlmClient.builder()
                .whenUserMessageContains(
                    "scripted-catalog-pending",
                    ScriptedResponses.toolCall("catalog_sync_status", "{}")
                )
                .whenToolResultContains("pending:", ScriptedResponses.text("看到 pending"))
                .build();

            AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("mock-key").build();
            try (ProductivityAgentHost host = new ProductivityAgentHost(agentRoot, config, llm)) {
                host.createSession();
                host.runUserMessage("任务:scripted-catalog-pending sync check");

                assertTrue(host.runtime().getStateSnapshot().getMessages().stream()
                    .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
                    .anyMatch(m -> m.getContent().contains("[coach] catalog.pending")));

                String events = Files.readString(AgentDataPaths.eventsJsonl(agentRoot));
                assertTrue(events.contains("\"type\":\"coach_fired\""));
                assertTrue(events.contains("catalog.pending"));
            }
        }
    }

    private static void prepareCoachManifest(
        Path agentRoot,
        int largeWriteBytes,
        int inlineLongLines,
        int inlineLongBytes
    ) throws Exception {
        prepareCoachManifest(agentRoot, largeWriteBytes, inlineLongLines, inlineLongBytes, 3);
    }

    private static void prepareCoachManifest(
        Path agentRoot,
        int largeWriteBytes,
        int inlineLongLines,
        int inlineLongBytes,
        int scriptFailRepeat
    ) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        ObjectNode root = (ObjectNode) MAPPER.readTree(
            Files.readString(agentRoot.resolve("agent.manifest.json"))
        );
        ObjectNode coach = MAPPER.createObjectNode();
        coach.put("enabled", true);
        ObjectNode triggers = MAPPER.createObjectNode();
        triggers.put("fileLargeWriteBytes", largeWriteBytes);
        triggers.put("scriptInlineLongLines", inlineLongLines);
        triggers.put("scriptInlineLongBytes", inlineLongBytes);
        triggers.put("scriptFailRepeat", scriptFailRepeat);
        coach.set("triggers", triggers);
        root.set("coach", coach);
        Files.writeString(
            agentRoot.resolve("agent.manifest.json"),
            MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root)
        );
    }
}
