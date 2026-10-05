package com.agent1.javaagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** 把 MCP inputSchema 收成短参数说明，避免整段 JSON 进上下文。 */
public final class McpSchemaBrief {

    private static final int MAX_CHARS = 700;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpSchemaBrief() {
    }

    public static String format(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank() || "{}".equals(schemaJson)) {
            return "";
        }
        JsonNode schema;
        try {
            schema = MAPPER.readTree(schemaJson);
        } catch (Exception e) {
            return "";
        }
        if (schema == null || !schema.isObject()) {
            return "";
        }
        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject() || properties.isEmpty()) {
            return "";
        }
        Set<String> required = new HashSet<>();
        JsonNode requiredNode = schema.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
            for (JsonNode name : requiredNode) {
                if (name.isTextual()) {
                    required.add(name.asText());
                }
            }
        }
        StringBuilder out = new StringBuilder();
        Iterator<String> names = properties.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode prop = properties.get(name);
            String type = prop.path("type").asText("any");
            String description = prop.path("description").asText("").replace('\n', ' ').trim();
            if (description.length() > 80) {
                description = description.substring(0, 80);
            }
            out.append("    ").append(name).append(' ').append(type);
            if (required.contains(name)) {
                out.append(" 必填");
            }
            if (!description.isEmpty()) {
                out.append(" ").append(description);
            }
            out.append('\n');
            if (out.length() >= MAX_CHARS) {
                out.append("    …\n");
                break;
            }
        }
        return out.toString().trim();
    }
}
