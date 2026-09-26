package com.agent1.javaagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;

/** 最小 JSON Schema 校验：必填字段与非空字符串。 */
public final class ToolArgumentValidator {

    private ToolArgumentValidator() {
    }

    /**
     * @return 校验失败时的模型可读错误；成功则 empty
     */
    public static java.util.Optional<String> validateRequired(JsonNode schema, JsonNode parameters) {
        if (schema == null || parameters == null) {
            return java.util.Optional.empty();
        }
        JsonNode required = schema.get("required");
        if (required == null || !required.isArray()) {
            return java.util.Optional.empty();
        }
        Iterator<JsonNode> it = required.elements();
        while (it.hasNext()) {
            String field = it.next().asText("");
            if (field.isBlank()) {
                continue;
            }
            if (!parameters.has(field) || parameters.get(field).isNull()) {
                return java.util.Optional.of("错误：缺少必填参数 " + field);
            }
            JsonNode value = parameters.get(field);
            if (value.isTextual() && value.asText("").isBlank()) {
                return java.util.Optional.of("错误：参数 " + field + " 不能为空");
            }
        }
        return java.util.Optional.empty();
    }
}
