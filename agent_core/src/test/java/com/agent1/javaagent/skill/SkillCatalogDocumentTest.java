package com.agent1.javaagent.skill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SkillCatalogDocumentTest {

    @Test
    void bundledCatalogRendersTravelAndHtml2pptx() throws Exception {
        String md = SkillCatalogDocument.renderMarkdown(SkillCatalogDocument.loadBundled());
        assertTrue(md.contains("travel-itinerary"), md);
        assertTrue(md.contains("html2pptx"), md);
        assertTrue(md.contains("video-edit"), md);
        assertTrue(md.contains("docx"), md);
        assertTrue(md.contains("xlsx"), md);
        assertTrue(md.contains("Excel"), md);
        assertTrue(md.contains("load_skill"), md);
        assertTrue(md.contains("曲线路线图"), md);
    }

    @Test
    void travelSkillKeepsThePageBriefAndDropsSchematicPipeline() throws Exception {
        String skill;
        try (var in = SkillCatalogDocumentTest.class.getResourceAsStream(
            "/agent-home/skills-bundled/travel-itinerary/SKILL.md")) {
            skill = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(skill.contains("头图是照片"), skill);
        assertTrue(skill.contains("amapuri://route/plan/"), skill);
        assertTrue(skill.contains("baidumap://map/direction"), skill);
        assertTrue(skill.contains("曲线"), skill);
        assertTrue(skill.contains("清单"), skill);
        assertTrue(skill.contains("references/roadbook-sample.html"), skill);
        assertTrue(skill.contains("不要读本 skill 的 `scripts/`"), skill);
        assertFalse(skill.contains("配色偏旅行杂志"), skill);
        assertFalse(skill.contains("renderSchematicMap"), skill);
    }
}
