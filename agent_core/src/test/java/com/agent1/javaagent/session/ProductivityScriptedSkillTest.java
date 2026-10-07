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
import com.agent1.javaagent.util.PathIo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** UC-09：promote 后 find_caps 直接带上 local skill 正文。 */
class ProductivityScriptedSkillTest {

    @Test
    void uc09ReadLocalSkillAfterSeed(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path skillDir = agentRoot.resolve("shared/local/skills/uc09-skill");
        Files.createDirectories(skillDir);
        PathIo.writeString(skillDir.resolve("SKILL.md"), "---\nname: uc09-skill\n---\n# UC09 body\n");

        ScriptedLlmClient llm = ScriptedLlmClient.builder()
            .whenUserMessageContains(
                "scripted-skill-uc09",
                ScriptedResponses.toolCall("find_caps", "{\"query\":\"uc09-skill\"}")
            )
            .whenToolResultContains("UC09 body", ScriptedResponses.text("skill 已读"))
            .build();

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("mock-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(agentRoot, config, llm)) {
            host.createSession();
            String runId = host.runUserMessage("任务:scripted-skill-uc09 读 local skill");
            assertEquals(
                RunState.SUCCEEDED,
                new FileRunStore(new FileSessionStore(agentRoot)).read(host.getActiveSessionId(), runId).getState()
            );

            assertTrue(host.runtime().getStateSnapshot().getMessages().stream()
                .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
                .anyMatch(m -> m.getContent().contains("UC09 body") && m.getContent().contains("source: local")));
        }
    }
}
