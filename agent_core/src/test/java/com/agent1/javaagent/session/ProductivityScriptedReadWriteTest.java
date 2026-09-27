package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.llm.scripted.ScriptedLlmClient;
import com.agent1.javaagent.llm.scripted.ScriptedResponses;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.run.FileRunStore;
import com.agent1.javaagent.run.RunState;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Mock LLM 驱动生产力 Host 完整工具循环（不访问真实模型）。 */
class ProductivityScriptedReadWriteTest {

    @Test
    void mockLlmWriteThenReadFile(@TempDir Path agentRoot) throws Exception {
        ScriptedLlmClient llm = ScriptedLlmClient.builder()
            .whenUserMessageContains(
                "scripted-read-write",
                ScriptedResponses.toolCall(
                    "write_file",
                    "{\"path\":\"note.txt\",\"content\":\"mock-llm-hello\"}"
                ),
                ScriptedResponses.toolCall("read_file", "{\"path\":\"note.txt\"}"),
                ScriptedResponses.text("已读写完成")
            )
            .build();

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("mock-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(agentRoot, config, llm)) {
            host.createSession();
            String runId = host.runUserMessage("任务:scripted-read-write 测试读写");
            assertEquals(RunState.SUCCEEDED, new FileRunStore(new FileSessionStore(agentRoot))
                .read(host.getActiveSessionId(), runId).getState());

            Path workspace = new FileSessionStore(agentRoot).workspaceDir(host.getActiveSessionId());
            assertTrue(Files.readString(workspace.resolve("note.txt")).contains("mock-llm-hello"));

            AgentMessage last = host.runtime().getStateSnapshot().getMessages().stream()
                .filter(m -> AgentMessage.ROLE_ASSISTANT.equals(m.getRole()))
                .reduce((a, b) -> b)
                .orElseThrow();
            assertEquals("已读写完成", last.getContent());
        }
    }
}
