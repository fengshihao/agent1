package com.agent1.javaagent.promote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.log.AgentDataPaths;
import com.agent1.javaagent.util.PathIo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromotionServiceTest {

    @Test
    void promotesSkillAndScript(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path skillDir = workspace.resolve("staging/skills/demo-skill");
        Files.createDirectories(skillDir);
        PathIo.writeString(skillDir.resolve("SKILL.md"), "---\nname: demo\n---\n# Demo\n");
        Path scripts = workspace.resolve("staging/scripts");
        Files.createDirectories(scripts);
        PathIo.writeString(scripts.resolve("helper.js"), "export function hi() { return 1; }\n");

        PromotionService.PromotionResult result = new PromotionService(agentRoot, workspace).promote("test");
        assertTrue(result.ok());
        assertEquals(2, result.promoted().size());
        assertTrue(Files.isRegularFile(agentRoot.resolve("shared/local/skills/demo-skill/SKILL.md")));
        assertTrue(Files.isRegularFile(agentRoot.resolve("shared/local/scripts/helper.js")));
        assertTrue(Files.isRegularFile(agentRoot.resolve("shared/catalog/scripts/helper.js")));
        assertTrue(Files.isRegularFile(agentRoot.resolve("docs/capabilities/local.skill.demo-skill.md")));
        assertTrue(Files.isRegularFile(AgentDataPaths.eventsJsonl(agentRoot)));
    }

    @Test
    void rejectsSecretPattern(@TempDir Path workspace) throws Exception {
        Path scripts = workspace.resolve("staging/scripts");
        Files.createDirectories(scripts);
        PathIo.writeString(scripts.resolve("bad.js"), "const api_key = 'sk-abcdefghijklmnopqrstuvwxyz';\n");

        Path agentRoot = Files.createTempDirectory("ar").toRealPath();
        AgentHomeBootstrap.ensure(agentRoot);
        PromotionService.PromotionResult result = new PromotionService(agentRoot, workspace).promote("");
        assertTrue(!result.ok());
        assertTrue(result.rejections().stream().anyMatch(r -> r.contains("bad.js")));
    }
}
