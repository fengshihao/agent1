package com.agent1.javaagent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.util.PathIo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentSkillLoaderTest {

    @Test
    void mergedLocalOverridesProject(@TempDir Path agentRoot, @TempDir Path projectRoot) throws Exception {
        Path projectSkill = projectRoot.resolve(".claude/skills/demo");
        Files.createDirectories(projectSkill);
        PathIo.writeString(projectSkill.resolve("SKILL.md"), "---\nname: demo\n---\nfrom project\n");

        Path localSkill = agentRoot.resolve("shared/local/skills/demo");
        Files.createDirectories(localSkill);
        PathIo.writeString(localSkill.resolve("SKILL.md"), "---\nname: demo\n---\nfrom local\n");

        AgentSkillLoader loader = new AgentSkillLoader();
        AgentSkillLoader.SkillLoadResult merged = loader.loadMerged(agentRoot, projectRoot);
        assertEquals(1, merged.skills().size());
        assertTrue(merged.skills().get(0).content().contains("from local"));
        assertEquals("local", merged.skills().get(0).sourceLabel());
    }

    @Test
    void mergedCatalogBetweenProjectAndLocal(@TempDir Path agentRoot, @TempDir Path projectRoot) throws Exception {
        Path projectSkill = projectRoot.resolve(".claude/skills/shared-name");
        Files.createDirectories(projectSkill);
        PathIo.writeString(projectSkill.resolve("SKILL.md"), "---\nname: shared-name\n---\nfrom project\n");

        Path catalogSkill = agentRoot.resolve("shared/catalog/skills/shared-name");
        Files.createDirectories(catalogSkill);
        PathIo.writeString(catalogSkill.resolve("SKILL.md"), "---\nname: shared-name\n---\nfrom catalog\n");

        Path localSkill = agentRoot.resolve("shared/local/skills/shared-name");
        Files.createDirectories(localSkill);
        PathIo.writeString(localSkill.resolve("SKILL.md"), "---\nname: shared-name\n---\nfrom local\n");

        AgentSkillLoader loader = new AgentSkillLoader();
        AgentSkillLoader.SkillLoadResult merged = loader.loadMerged(agentRoot, projectRoot);
        assertEquals(1, merged.skills().size());
        assertTrue(merged.skills().get(0).content().contains("from local"));

        Files.delete(localSkill.resolve("SKILL.md"));
        Files.delete(localSkill);
        merged = loader.loadMerged(agentRoot, projectRoot);
        assertTrue(merged.skills().get(0).content().contains("from catalog"));
        assertEquals("catalog", merged.skills().get(0).sourceLabel());
    }
}
