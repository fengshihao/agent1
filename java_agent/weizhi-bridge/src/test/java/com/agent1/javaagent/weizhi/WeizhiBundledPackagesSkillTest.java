package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.capability.CapabilityIndexStore;
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
            .resolve("packages/html2pptx@" + WeizhiBundledPackages.HTML2PPTX_VERSION)
            .resolve("SKILL.md");
        assertTrue(Files.isRegularFile(skillFile), "installed package should carry SKILL.md");

        List<CapabilityIndexStore.CapabilityHit> hits =
            CapabilityIndexStore.search(agentRoot, "html2pptx", List.of(), "any", 8);
        assertTrue(hits.stream().anyMatch(hit -> "skill.html2pptx".equals(hit.id())
            && "skill".equals(hit.kind())
            && "skill:html2pptx".equals(hit.entry())));
        assertTrue(hits.stream().anyMatch(hit -> "html2pptx.render".equals(hit.id())
            && "weizhi_package".equals(hit.kind())));
    }
}
