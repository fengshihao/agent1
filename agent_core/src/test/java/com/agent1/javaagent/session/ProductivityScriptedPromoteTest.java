package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.llm.scripted.ScriptedLlmClient;
import com.agent1.javaagent.llm.scripted.ScriptedResponses;
import com.agent1.javaagent.log.AgentDataPaths;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.run.FileRunStore;
import com.agent1.javaagent.run.RunState;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** UC-08：staging → promote_request → shared/local（Scripted，无真实 LLM）。 */
class ProductivityScriptedPromoteTest {

    @Test
    void uc08PromoteStagingSkillToLocal(@TempDir Path agentRoot) throws Exception {
        String skillBody = "---\\nname: demo-skill\\n---\\n# Promoted\\n";
        ScriptedLlmClient llm = ScriptedLlmClient.builder()
            .whenUserMessageContains(
                "scripted-promote-uc08",
                ScriptedResponses.toolCall(
                    "write_file",
                    "{\"path\":\"staging/skills/demo-skill/SKILL.md\",\"content\":\"" + skillBody + "\"}"
                ),
                ScriptedResponses.toolCall("promote_request", "{\"note\":\"scripted-uc08\"}"),
                ScriptedResponses.text("晋升完成")
            )
            .build();

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("mock-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(agentRoot, config, llm)) {
            host.createSession();
            String runId = host.runUserMessage("任务:scripted-promote-uc08 沉淀 skill");
            assertEquals(
                RunState.SUCCEEDED,
                new FileRunStore(new FileSessionStore(agentRoot)).read(host.getActiveSessionId(), runId).getState()
            );

            Path localSkill = agentRoot.resolve("shared/local/skills/demo-skill/SKILL.md");
            assertTrue(Files.isRegularFile(localSkill));
            assertTrue(Files.readString(localSkill).contains("Promoted"));

            String events = Files.readString(AgentDataPaths.eventsJsonl(agentRoot));
            assertTrue(events.contains("promotion_completed"));

            assertTrue(host.runtime().getStateSnapshot().getMessages().stream()
                .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
                .anyMatch(m -> m.getContent().contains("promotion_completed")));
        }
    }
}
