package com.agent1.javaagent.skill;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class SkillCatalogDocumentTest {

    @Test
    void fixtureCatalogRendersTravelAndHtml2pptx() throws Exception {
        try (InputStream in = SkillCatalogDocumentTest.class.getResourceAsStream("/skill-catalog/stable.json")) {
            assertTrue(in != null, "test fixture skill-catalog/stable.json");
            String md = SkillCatalogDocument.renderMarkdown(new ObjectMapper().readTree(in));
            assertTrue(md.contains("travel-itinerary"), md);
            assertTrue(md.contains("html2pptx"), md);
            assertTrue(md.contains("video-edit"), md);
            assertTrue(md.contains("docx"), md);
            assertTrue(md.contains("xlsx"), md);
            assertTrue(md.contains("Excel"), md);
            assertTrue(md.contains("load_skill"), md);
            assertTrue(md.contains("曲线路线图"), md);
        }
    }
}
