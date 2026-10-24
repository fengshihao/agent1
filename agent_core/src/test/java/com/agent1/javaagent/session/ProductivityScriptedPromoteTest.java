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
                ScriptedResponses.text("staging 就绪")
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

            // promote_request 已移入仅 $tools 桥（ProductivityLlmToolSurface，LLM 面不可直调；
            // 面隐藏由 ProductivityAgentHostTest 覆盖）——staging 晋升本体直调工具验收。
            Path workspace = new FileSessionStore(agentRoot).workspaceDir(host.getActiveSessionId());
            var tool = new com.agent1.javaagent.tool.agent.PromoteRequestTool(agentRoot, workspace);
            var result = tool.execute(
                "call-promote-1",
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"note\":\"scripted-uc08\"}"),
                new com.agent1.javaagent.core.CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("promotion_completed"), result.getText());

            Path localSkill = agentRoot.resolve("shared/local/skills/demo-skill/SKILL.md");
            assertTrue(Files.isRegularFile(localSkill));
            assertTrue(Files.readString(localSkill).contains("Promoted"));

            String events = Files.readString(AgentDataPaths.eventsJsonl(agentRoot));
            assertTrue(events.contains("\"type\":\"promotion_completed\""));
            assertTrue(events.contains("scripted-uc08"));
        }
    }
}
