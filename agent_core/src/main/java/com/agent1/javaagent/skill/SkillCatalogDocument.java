package com.agent1.javaagent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 解析 {@code skills-catalog/stable.json} 并渲染进系统提示词的自然语言目录。 */
public final class SkillCatalogDocument {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BUNDLED_CATALOG = "/agent-home/skills-catalog/stable.json";

    private SkillCatalogDocument() {
    }

    public static JsonNode loadBundled() throws IOException {
        try (InputStream in = SkillCatalogDocument.class.getResourceAsStream(BUNDLED_CATALOG)) {
            if (in == null) {
                throw new IOException("missing bundled skills catalog");
            }
            return MAPPER.readTree(in);
        }
    }

    public static String renderMarkdown(JsonNode catalog) {
        if (catalog == null || catalog.isMissingNode()) {
            return "";
        }
        JsonNode categories = catalog.path("categories");
        if (!categories.isArray() || categories.isEmpty()) {
            return "";
        }
        List<JsonNode> sorted = new ArrayList<>();
        categories.forEach(sorted::add);
        sorted.sort(Comparator.comparing(node -> node.path("id").asText("")));
        StringBuilder sb = new StringBuilder();
        sb.append("## 官方 Skill 目录（先 load_skill，无匹配再 find_caps）\n");
        for (JsonNode category : sorted) {
            String id = category.path("id").asText("").trim();
            if (id.isEmpty()) {
                continue;
            }
            sb.append("\n### ").append(id).append("\n");
            JsonNode skills = category.path("skills");
            if (!skills.isArray()) {
                continue;
            }
            List<JsonNode> skillRows = new ArrayList<>();
            skills.forEach(skillRows::add);
            skillRows.sort(Comparator.comparing(node -> node.path("name").asText("")));
            for (JsonNode skill : skillRows) {
                String name = skill.path("name").asText("").trim();
                String line = skill.path("line").asText("").trim();
                if (name.isEmpty()) {
                    continue;
                }
                sb.append("- **").append(name).append("**");
                if (!line.isEmpty()) {
                    sb.append("：").append(line);
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    public static String bundledMarkdown() {
        try {
            return renderMarkdown(loadBundled());
        } catch (IOException e) {
            return "";
        }
    }
}
