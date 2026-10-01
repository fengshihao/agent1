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
        AgentSkill demo = find(merged, "demo");
        assertTrue(demo.content().contains("from local"));
        assertEquals("local", demo.sourceLabel());
        assertEquals("bundled", find(merged, "skill-creator").sourceLabel());
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
        assertTrue(find(merged, "shared-name").content().contains("from local"));

        Files.delete(localSkill.resolve("SKILL.md"));
        Files.delete(localSkill);
        merged = loader.loadMerged(agentRoot, projectRoot);
        AgentSkill catalog = find(merged, "shared-name");
        assertTrue(catalog.content().contains("from catalog"));
        assertEquals("catalog", catalog.sourceLabel());
    }

    @Test
    void bundledSkillCreatorLoadsWithoutInstall(@TempDir Path agentRoot, @TempDir Path projectRoot) throws Exception {
        AgentSkillLoader.SkillLoadResult bundled = new AgentSkillLoader().loadBundled();
        assertTrue(bundled.warnings().isEmpty());
        AgentSkill creator = find(bundled, "skill-creator");
        assertEquals("bundled", creator.sourceLabel());
        assertTrue(creator.description().contains("Skill"));
        assertTrue(creator.content().contains("promote_request"));
        assertTrue(creator.content().contains("staging/skills"));

        Path local = agentRoot.resolve("shared/local/skills/skill-creator");
        Files.createDirectories(local);
        PathIo.writeString(local.resolve("SKILL.md"), "---\nname: skill-creator\n---\nlocal override\n");
        AgentSkill overridden = find(new AgentSkillLoader().loadMerged(agentRoot, projectRoot), "skill-creator");
        assertEquals("local", overridden.sourceLabel());
        assertTrue(overridden.content().contains("local override"));
    }

    private static AgentSkill find(AgentSkillLoader.SkillLoadResult result, String name) {
        return result.skills().stream()
            .filter(skill -> name.equals(skill.name()))
            .findFirst()
            .orElseThrow();
    }
}
