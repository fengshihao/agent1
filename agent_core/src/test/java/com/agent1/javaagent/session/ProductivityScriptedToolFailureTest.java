package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.llm.scripted.ScriptedLlmClient;
import com.agent1.javaagent.llm.scripted.ScriptedResponses;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.run.FileRunStore;
import com.agent1.javaagent.run.RunState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

/** Mock LLM 在 tool 回执失败时走分支，而非盲目回复成功。 */
class ProductivityScriptedToolFailureTest {

    @Test
    void mockLlmAcknowledgesReadFileFailure(@TempDir Path agentRoot) throws Exception {
        ScriptedLlmClient llm = ScriptedLlmClient.builder()
            .whenUserMessageContains(
                "scripted-tool-fail",
                ScriptedResponses.toolCall("read_file", "{\"path\":\"missing-file.txt\"}")
            )
            .whenToolResultFailed(ScriptedResponses.text("工具失败，未读到文件"))
            .whenToolResultSucceeded(ScriptedResponses.text("不应在失败场景出现"))
            .build();

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("mock-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(agentRoot, config, llm)) {
            host.createSession();
            String runId = host.runUserMessage("任务:scripted-tool-fail 读不存在的文件");
            assertEquals(RunState.SUCCEEDED, new FileRunStore(new FileSessionStore(agentRoot))
                .read(host.getActiveSessionId(), runId).getState());

            AgentMessage lastAssistant = host.runtime().getStateSnapshot().getMessages().stream()
                .filter(m -> AgentMessage.ROLE_ASSISTANT.equals(m.getRole()))
                .reduce((a, b) -> b)
                .orElseThrow();
            assertEquals("工具失败，未读到文件", lastAssistant.getContent());
            assertTrue(host.runtime().getStateSnapshot().getMessages().stream()
                .anyMatch(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole())
                    && (m.isError() || m.getContent().contains("错误"))));
        }
    }
}
