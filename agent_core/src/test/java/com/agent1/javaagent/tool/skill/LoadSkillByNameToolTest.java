package com.agent1.javaagent.tool.skill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.skill.SkillsBundledInstall;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoadSkillByNameToolTest {

    @TempDir
    Path agentRoot;

    @Test
    void loadsTravelItineraryAfterBundledInstall() throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        SkillsBundledInstall.syncFromClasspath(agentRoot);
        LoadSkillByNameTool tool = new LoadSkillByNameTool(agentRoot, agentRoot);
        String text = tool.load("travel-itinerary", null);
        assertTrue(text.contains("[skill] travel-itinerary loaded"), text);
        assertTrue(text.contains("skills_home"), text);
        assertTrue(text.contains("load_skill"), text);
    }

    @Test
    void defaultPathLoadsSkillBodyWithoutFrontmatter() throws Exception {
        writeLocalSkill(
            "demo",
            """
            ---
            name: demo
            description: 本地演示 skill
            ---

            # 演示剧本

            按本步骤做。
            """
        );
        LoadSkillByNameTool tool = new LoadSkillByNameTool(agentRoot, null);
        String text = tool.load("demo", null);
        assertTrue(text.contains("[skill] demo loaded (source: local)"), text);
        assertTrue(text.contains("演示剧本"), text);
        // frontmatter 是元数据，不进正文
        assertFalse(text.contains("description: 本地演示 skill"), text);
    }

    @Test
    void pathReadsResourceFromSameSource() throws Exception {
        Path dir = agentRoot.resolve("shared/local/skills/demo");
        writeLocalSkill("demo", "正文");
        Files.createDirectories(dir.resolve("references"));
        Files.writeString(dir.resolve("references/a.md"), "附录内容");

        LoadSkillByNameTool tool = new LoadSkillByNameTool(agentRoot, null);
        String text = tool.load("demo", "references/a.md");
        assertTrue(text.contains("[skill] demo references/a.md (source: local)"), text);
        assertTrue(text.contains("附录内容"), text);
    }

    @Test
    void localOverridesInstalledSkill() throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        SkillsBundledInstall.syncFromClasspath(agentRoot);
        writeLocalSkill("travel-itinerary", "本地改写版路书");

        LoadSkillByNameTool tool = new LoadSkillByNameTool(agentRoot, null);
        String text = tool.load("travel-itinerary", null);
        assertTrue(text.contains("(source: local)"), text);
        assertTrue(text.contains("本地改写版路书"), text);
    }

    @Test
    void missingSkillReportsError() {
        // 指向不可达的本地 registry，避免单测访问网络
        System.setProperty("agent1.skill.registry.url", "http://127.0.0.1:1/registry.json");
        try {
            LoadSkillByNameTool tool = new LoadSkillByNameTool(agentRoot, null);
            String text = tool.load("no-such-skill-xyz", null);
            assertTrue(text.startsWith("错误："), text);
        } finally {
            System.clearProperty("agent1.skill.registry.url");
        }
    }

    @Test
    void rejectsBlankNameAndUnsafePath() throws Exception {
        writeLocalSkill("demo", "正文");
        LoadSkillByNameTool tool = new LoadSkillByNameTool(agentRoot, null);
        assertTrue(tool.load("  ", null).startsWith("错误"));
        assertTrue(tool.load("demo", "../outside.md").startsWith("错误"));
        assertTrue(tool.load("demo", "/etc/passwd").startsWith("错误"));
    }

    private void writeLocalSkill(String name, String body) throws Exception {
        Path dir = agentRoot.resolve("shared/local/skills").resolve(name);
        Files.createDirectories(dir);
        String content = body.startsWith("---")
            ? body
            : "---\nname: " + name + "\ndescription: " + name + " 本地\n---\n\n" + body;
        Files.writeString(dir.resolve("SKILL.md"), content);
    }
}
