package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.skill.SkillsHomeInstaller;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.agent.CapabilitySearchTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** fixture 安装后 skill 在 skills-home，不在 weizhi package 目录（Skill 与 Package 分离）。 */
class WeizhiFixtureSkillHomeTest {

    @Test
    void syncInstallsSkillsHomeNotPackageSkill(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);

        WeizhiTestPackages.syncFromFixture(agentRoot);
        WeizhiTestSkills.syncFromFixture(agentRoot);

        Path packageSkill = WeizhiBundledPackages.weizhiHome(agentRoot)
            .resolve("packages/html2pptx@" + WeizhiTestPackages.fixtureVersion("html2pptx"))
            .resolve("SKILL.md");
        assertFalse(Files.exists(packageSkill), "package should not carry SKILL.md");

        Path skillFile = SkillsHomeInstaller.installedDir(agentRoot, "html2pptx").resolve("SKILL.md");
        assertTrue(Files.isRegularFile(skillFile), "html2pptx skill in skills-home");

        List<CapabilityIndexStore.CapabilityHit> hits =
            CapabilityIndexStore.search(agentRoot, "html2pptx", List.of(), "any", 8);
        assertTrue(hits.stream().anyMatch(hit -> "skill.html2pptx".equals(hit.id())
            && "skill".equals(hit.kind())
            && "skill:html2pptx".equals(hit.entry())));

        Path travelSkill = SkillsHomeInstaller.installedDir(agentRoot, "travel-itinerary").resolve("SKILL.md");
        assertTrue(Files.isRegularFile(travelSkill), "travel-itinerary in skills-home");

        List<CapabilityIndexStore.CapabilityHit> travelHits =
            CapabilityIndexStore.search(agentRoot, "旅游 行程攻略 路书", List.of(), "any", 10);
        assertTrue(travelHits.stream().anyMatch(hit -> "skill.travel-itinerary".equals(hit.id())
            && "skill".equals(hit.kind())));

        CapabilitySearchTool tool = new CapabilitySearchTool(agentRoot, "android");
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("query", "旅游 行程攻略 路书 自驾");
        ToolExecutionResult loaded = tool.execute("t-travel", params, new CancellationToken(), u -> {
        });
        String text = loaded.getText();
        assertFalse(text.contains("[skill] travel-itinerary"), text);
        assertFalse(text.contains("queryDrivingLegs"), text);
        assertFalse(text.contains("jobs/roadbook.js"), text);
    }
}
