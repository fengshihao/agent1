package com.agent1.javaagent.skill;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkillCatalogDocumentTest {

    @Test
    void bundledCatalogRendersTravelAndHtml2pptx() throws Exception {
        String md = SkillCatalogDocument.renderMarkdown(SkillCatalogDocument.loadBundled());
        assertTrue(md.contains("travel-itinerary"), md);
        assertTrue(md.contains("html2pptx"), md);
        assertTrue(md.contains("load_skill"), md);
    }
}
