package com.agent1.javaagent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.capability.CapabilityIndexStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** skills-home 安装（模拟 registry 云装）+ skill 能力卡刷写。 */
public class SkillsHomeInstallTest {

    @TempDir
    Path agentRoot;

    @Test
    void installedSkillWritesSkillsHomeAndCards() throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        installFixtureSkill(agentRoot, "demo-skill", "renderDemo 正文");

        Path skillFile = SkillsHomeInstaller.installedDir(agentRoot, "demo-skill").resolve("SKILL.md");
        assertTrue(Files.isRegularFile(skillFile), "demo-skill SKILL should be in skills-home");

        SkillsCapabilitySync.syncCapabilityCards(agentRoot);
        List<CapabilityIndexStore.CapabilityHit> hits =
            CapabilityIndexStore.search(agentRoot, "demo-skill", List.of(), "any", 8);
        assertTrue(hits.stream().anyMatch(hit -> "skill.demo-skill".equals(hit.id())
            && "skill".equals(hit.kind())));

        AgentSkill demo =
            new AgentSkillLoader().loadMerged(agentRoot, agentRoot).skills().stream()
                .filter(s -> "demo-skill".equals(s.name()))
                .findFirst()
                .orElseThrow();
        assertEquals("skills_home", demo.sourceLabel());
        assertTrue(demo.content().contains("renderDemo"));
    }

    public static void installFixtureSkill(Path agentRoot, String name, String body) throws Exception {
        Path src = Files.createTempDirectory("skill-src-");
        Files.writeString(
            src.resolve("SKILL.md"),
            "---\nname: " + name + "\ndescription: " + name + " 演示\n---\n\n" + body + "\n");
        SkillsHomeInstaller.installFromDirectory(agentRoot, name, "1.0.0", src);
    }
}
