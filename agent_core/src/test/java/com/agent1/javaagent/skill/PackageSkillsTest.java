package com.agent1.javaagent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.util.PathIo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 遗留：仍支持包 manifest 带 {@code skill} 字段时的解析（新官方 skill 走 skills-home）。 */
class PackageSkillsTest {

    @TempDir
    Path temp;

    @Test
    void loadsOnlyCurrentVersionPackage() throws Exception {
        Path agentRoot = newAgentRoot();
        installFakePackage(agentRoot, "0.1.0", false);
        installFakePackage(agentRoot, "0.2.0", true);
        writeIndex(agentRoot, "html2pptx", "0.2.0");

        List<AgentSkill> skills = PackageSkills.loadInstalled(agentRoot);
        assertEquals(1, skills.size());
        AgentSkill skill = skills.get(0);
        assertEquals("html2pptx", skill.name());
        assertEquals("package", skill.sourceLabel());
        assertTrue(skill.description().contains("自由版式"));
        assertTrue(skill.content().contains("renderHtmlDeck"));
        assertTrue(skill.frontmatter().get("tags").contains("pptx"));
    }

    @Test
    void skillNotDeclaredInManifestIsIgnored() throws Exception {
        Path agentRoot = newAgentRoot();
        installFakePackage(agentRoot, "0.2.0", true);
        Path manifest = agentRoot.resolve("weizhi-home/packages/html2pptx@0.2.0/manifest.json");
        PathIo.writeString(manifest, "{\"name\":\"html2pptx\",\"version\":\"0.2.0\",\"entry\":\"html2pptx.js\"}");
        writeIndex(agentRoot, "html2pptx", "0.2.0");

        assertTrue(PackageSkills.loadInstalled(agentRoot).isEmpty());
    }

    @Test
    void missingIndexYieldsNoSkills() throws Exception {
        Path agentRoot = newAgentRoot();
        installFakePackage(agentRoot, "0.2.0", true);
        assertTrue(PackageSkills.loadInstalled(agentRoot).isEmpty());
    }

    @Test
    void html2pptxIsNoLongerBundledSkill() {
        AgentSkillLoader.SkillLoadResult bundled = new AgentSkillLoader().loadBundled();
        assertTrue(bundled.warnings().isEmpty());
        assertTrue(bundled.skills().stream().noneMatch(skill -> "html2pptx".equals(skill.name())));
    }

    private Path newAgentRoot() throws Exception {
        Path agentRoot = Files.createTempDirectory(temp, "agent");
        AgentHomeBootstrap.ensure(agentRoot);
        return agentRoot;
    }

    private void installFakePackage(Path agentRoot, String version, boolean withSkill) throws Exception {
        Path pkgDir = agentRoot.resolve("weizhi-home/packages/html2pptx@" + version);
        Files.createDirectories(pkgDir);
        PathIo.writeString(
            pkgDir.resolve("manifest.json"),
            "{\"name\":\"html2pptx\",\"version\":\"" + version
                + "\",\"entry\":\"html2pptx.js\",\"skill\":\"SKILL.md\"}"
        );
        PathIo.writeString(pkgDir.resolve("html2pptx.js"), "export const renderHtmlDeck = () => {};");
        if (withSkill) {
            PathIo.writeString(
                pkgDir.resolve("SKILL.md"),
                "---\n"
                    + "name: html2pptx\n"
                    + "description: 用 HTML 自由版式生成 PPT，设计感 deck。\n"
                    + "tags: pptx,ppt,html,版式\n"
                    + "---\n"
                    + "# 用法\n\n"
                    + "import { renderHtmlDeck } from \"weizhi:html2pptx\";\n"
            );
        }
    }

    private void writeIndex(Path agentRoot, String name, String version) throws Exception {
        PathIo.writeString(
            agentRoot.resolve("weizhi-home/packages-index.json"),
            "{\"" + name + "\":\"" + version + "\"}"
        );
    }
}
