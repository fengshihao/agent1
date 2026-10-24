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

class SkillsHomeInstallTest {

    @TempDir
    Path agentRoot;

    @Test
    void bundledInstallWritesSkillsHomeAndCards() throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        SkillsBundledInstall.syncFromClasspath(agentRoot);

        Path skillFile = SkillsHomeInstaller.installedDir(agentRoot, "html2pptx").resolve("SKILL.md");
        assertTrue(Files.isRegularFile(skillFile), "html2pptx SKILL should be in skills-home");

        List<CapabilityIndexStore.CapabilityHit> hits =
            CapabilityIndexStore.search(agentRoot, "html2pptx", List.of(), "any", 8);
        assertTrue(hits.stream().anyMatch(hit -> "skill.html2pptx".equals(hit.id())
            && "skill".equals(hit.kind())));

        AgentSkill pptx =
            new AgentSkillLoader().loadMerged(agentRoot, agentRoot).skills().stream()
                .filter(s -> "pptx".equals(s.name()))
                .findFirst()
                .orElseThrow();
        assertEquals("skills_home", pptx.sourceLabel());
        assertTrue(pptx.content().contains("renderPptx"));
    }
}
