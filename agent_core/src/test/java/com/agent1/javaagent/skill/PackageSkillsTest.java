package com.agent1.javaagent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.agent.CapabilitySearchTool;
import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 随微智包分发的 skill：安装→kind=skill 卡→find_caps 双卡命中→删包重装。 */
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
    void syncCardsMakesFindCapsHitPackageAndSkillCards() throws Exception {
        Path agentRoot = newAgentRoot();
        installFakePackage(agentRoot, "0.2.0", true);
        writeIndex(agentRoot, "html2pptx", "0.2.0");
        PackageSkills.syncCapabilityCards(agentRoot);

        List<CapabilityIndexStore.CapabilityHit> hits =
            CapabilityIndexStore.search(agentRoot, "html2pptx", List.of(), "any", 8);
        assertTrue(hits.stream().anyMatch(hit -> "skill.html2pptx".equals(hit.id())
            && "skill".equals(hit.kind())
            && "skill:html2pptx".equals(hit.entry())));
        assertTrue(hits.stream().anyMatch(hit -> "html2pptx.render".equals(hit.id())
            && "weizhi_package".equals(hit.kind())));

        CapabilitySearchTool tool = new CapabilitySearchTool(agentRoot);
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("query", "html2pptx 自由版式");
        ToolExecutionResult result = tool.execute("t-pkg-skill", params, new CancellationToken(), u -> {
        });
        String text = result.getText();
        assertTrue(text.contains("[skill] html2pptx loaded"), text);
        assertTrue(text.contains("source: package"), text);
        assertTrue(text.contains("import { renderHtmlDeck } from \"weizhi:html2pptx\""), text);
    }

    @Test
    void deleteAndReinstallRestoresSkillCard() throws Exception {
        Path agentRoot = newAgentRoot();
        installFakePackage(agentRoot, "0.2.0", true);
        writeIndex(agentRoot, "html2pptx", "0.2.0");

        PackageSkills.syncCapabilityCards(agentRoot);
        assertTrue(skillCardPresent(agentRoot));

        deleteRecursive(agentRoot.resolve("weizhi-home/packages/html2pptx@0.2.0"));
        PackageSkills.syncCapabilityCards(agentRoot);
        assertFalse(skillCardPresent(agentRoot));

        installFakePackage(agentRoot, "0.2.0", true);
        PackageSkills.syncCapabilityCards(agentRoot);
        assertTrue(skillCardPresent(agentRoot));
    }

    @Test
    void localSkillOverridesPackageSkillInMergedLoad() throws Exception {
        Path agentRoot = newAgentRoot();
        installFakePackage(agentRoot, "0.2.0", true);
        writeIndex(agentRoot, "html2pptx", "0.2.0");

        AgentSkillLoader.SkillLoadResult merged =
            new AgentSkillLoader().loadMerged(agentRoot, agentRoot);
        AgentSkill fromPackage = merged.skills().stream()
            .filter(skill -> "html2pptx".equals(skill.name()))
            .findFirst()
            .orElseThrow();
        assertEquals("package", fromPackage.sourceLabel());

        Path local = agentRoot.resolve("shared/local/skills/html2pptx");
        Files.createDirectories(local);
        PathIo.writeString(local.resolve("SKILL.md"), "---\nname: html2pptx\n---\nlocal override\n");
        AgentSkill overridden =
            new AgentSkillLoader().loadMerged(agentRoot, agentRoot).skills().stream()
                .filter(skill -> "html2pptx".equals(skill.name()))
                .findFirst()
                .orElseThrow();
        assertEquals("local", overridden.sourceLabel());
        assertTrue(overridden.content().contains("local override"));
    }

    @Test
    void html2pptxIsNoLongerBundledSkill() {
        AgentSkillLoader.SkillLoadResult bundled = new AgentSkillLoader().loadBundled();
        assertTrue(bundled.warnings().isEmpty());
        assertTrue(bundled.skills().stream().noneMatch(skill -> "html2pptx".equals(skill.name())));
    }

    private boolean skillCardPresent(Path agentRoot) {
        return CapabilityIndexStore.search(agentRoot, "html2pptx", List.of("skill"), "any", 8)
            .stream()
            .anyMatch(hit -> "skill.html2pptx".equals(hit.id()));
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

    private static void deleteRecursive(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }
}
