package com.agent1.javaagent.html.inspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** 桥回传 JSON → {@link InspectedElement}，容错：字段缺失/类型不符取默认值，整体损坏返回 null。 */
public final class HtmlInspectParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HtmlInspectParser() {
    }

    public static InspectedElement parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isObject()) {
                return null;
            }
            return parseObject(root);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static InspectedElement parseObject(JsonNode json) {
        String tag = json.path("tag").asText("").trim().toLowerCase();
        if (tag.isEmpty()) {
            return null;
        }
        return new InspectedElement(
            tag,
            json.path("id").asText("").trim(),
            parseStringList(json.get("classes")),
            json.path("textPreview").asText("").trim(),
            json.path("outerHtml").asText("").trim(),
            parseRenderInfo(json.get("render")),
            parseAncestors(json.get("ancestors"))
        );
    }

    private static InspectRenderInfo parseRenderInfo(JsonNode json) {
        if (json == null || json.isNull() || !json.isObject()) {
            return null;
        }
        return new InspectRenderInfo(
            textOrEmpty(json.get("color")),
            textOrEmpty(json.get("backgroundColor")),
            textOrEmpty(json.get("fontSize")),
            textOrEmpty(json.get("display")),
            positiveIntOrNull(json.get("width")),
            positiveIntOrNull(json.get("height"))
        );
    }

    private static List<InspectAncestor> parseAncestors(JsonNode array) {
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<InspectAncestor> out = new ArrayList<>();
        for (JsonNode node : array) {
            if (node == null || !node.isObject()) {
                continue;
            }
            String tag = node.path("tag").asText("").trim().toLowerCase();
            if (tag.isEmpty()) {
                continue;
            }
            int nth = node.path("nth").asInt(1);
            if (nth < 1) {
                nth = 1;
            }
            out.add(
                new InspectAncestor(
                    tag,
                    node.path("id").asText("").trim(),
                    parseStringList(node.get("classes")),
                    nth
                )
            );
        }
        return out;
    }

    private static List<String> parseStringList(JsonNode array) {
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode node : array) {
            if (node == null || !node.isTextual()) {
                continue;
            }
            String value = node.asText("").trim();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    private static String textOrEmpty(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return "";
        }
        return node.asText("").trim();
    }

    private static Integer positiveIntOrNull(JsonNode node) {
        if (node == null || !node.isInt()) {
            return null;
        }
        int value = node.intValue();
        return value >= 0 ? value : null;
    }
}
