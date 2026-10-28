package com.agent1.javaagent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 把 Skill registry 目录 JSON 渲染进系统提示词的自然语言 Markdown。 */
public final class SkillCatalogDocument {

    private SkillCatalogDocument() {
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
}
