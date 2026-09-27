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

/** 7.1 / UC：catalog skill 经 skill 工具可读（Scripted）。 */
class ProductivityScriptedCatalogSkillTest {

    @Test
    void readCatalogSkillViaSkillTool(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path skillDir = agentRoot.resolve("shared/catalog/skills/catalog-demo");
        Files.createDirectories(skillDir);
        PathIo.writeString(skillDir.resolve("SKILL.md"), "---\nname: catalog-demo\n---\n# Catalog skill body\n");

        ScriptedLlmClient llm = ScriptedLlmClient.builder()
            .whenUserMessageContains(
                "scripted-catalog-skill-71",
                ScriptedResponses.toolCall("skill", "{\"action\":\"read\",\"skill_name\":\"catalog-demo\"}")
            )
            .whenToolResultContains("Catalog skill body", ScriptedResponses.text("已读 catalog skill"))
            .build();

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("mock-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(agentRoot, config, llm)) {
            host.createSession();
            String runId = host.runUserMessage("任务:scripted-catalog-skill-71 读 catalog skill");
            assertEquals(
                RunState.SUCCEEDED,
                new FileRunStore(new FileSessionStore(agentRoot)).read(host.getActiveSessionId(), runId).getState()
            );

            assertTrue(host.runtime().getStateSnapshot().getMessages().stream()
                .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
                .anyMatch(m -> m.getContent().contains("Catalog skill body")
                    && m.getContent().contains("source: catalog")));
        }
    }
}
