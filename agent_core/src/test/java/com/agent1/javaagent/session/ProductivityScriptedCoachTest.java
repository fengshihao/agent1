package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.llm.scripted.ScriptedLlmClient;
import com.agent1.javaagent.llm.scripted.ScriptedResponses;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.run.FileRunStore;
import com.agent1.javaagent.run.RunState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

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

    private static void prepareCoachManifest(
        Path agentRoot,
        int largeWriteBytes,
        int inlineLongLines,
        int inlineLongBytes
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
        coach.set("triggers", triggers);
        root.set("coach", coach);
        Files.writeString(
            agentRoot.resolve("agent.manifest.json"),
            MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root)
        );
    }
}
