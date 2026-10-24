package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.agent.CapabilitySearchTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 内置包安装链路：SKILL.md 随包落地，find_caps 同时命中 weizhi_package 卡与随包 skill 卡。 */
class WeizhiBundledPackagesSkillTest {

    @TempDir
    Path temp;

    @Test
    void syncInstallsPackageSkillAndWritesCard(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);

        WeizhiBundledPackages.syncFromClasspath(agentRoot);

        Path skillFile = WeizhiBundledPackages.weizhiHome(agentRoot)
            .resolve("packages/html2pptx@" + WeizhiBundledPackages.bundledVersion("html2pptx"))
            .resolve("SKILL.md");
        assertTrue(Files.isRegularFile(skillFile), "installed package should carry SKILL.md");

        List<CapabilityIndexStore.CapabilityHit> hits =
            CapabilityIndexStore.search(agentRoot, "html2pptx", List.of(), "any", 8);
        assertTrue(hits.stream().anyMatch(hit -> "skill.html2pptx".equals(hit.id())
            && "skill".equals(hit.kind())
            && "skill:html2pptx".equals(hit.entry())));
        assertTrue(hits.stream().anyMatch(hit -> "html2pptx.render".equals(hit.id())
            && "weizhi_package".equals(hit.kind())));

        Path travelSkill = WeizhiBundledPackages.weizhiHome(agentRoot)
            .resolve("packages/travel-itinerary@" + WeizhiBundledPackages.bundledVersion("travel-itinerary"))
            .resolve("SKILL.md");
        assertTrue(Files.isRegularFile(travelSkill), "travel-itinerary should carry SKILL.md");

        List<CapabilityIndexStore.CapabilityHit> travelHits =
            CapabilityIndexStore.search(agentRoot, "旅游 行程攻略 路书", List.of(), "any", 10);
        assertTrue(travelHits.stream().anyMatch(hit -> "skill.travel-itinerary".equals(hit.id())
            && "skill".equals(hit.kind())));
        assertTrue(travelHits.stream().anyMatch(hit -> "travel-itinerary.roadbook".equals(hit.id())
            && "weizhi_package".equals(hit.kind())));

        CapabilitySearchTool tool = new CapabilitySearchTool(agentRoot, "android");
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("query", "旅游 行程攻略 路书 自驾");
        ToolExecutionResult loaded = tool.execute("t-travel", params, new CancellationToken(), u -> {
        });
        String text = loaded.getText();
        assertTrue(text.contains("[skill] travel-itinerary loaded"), text);
        assertTrue(text.contains("source: package"), text);
        assertTrue(text.contains("queryDrivingLegs"), text);
        assertTrue(text.contains("不含机票火车酒店"), text);
    }
}
