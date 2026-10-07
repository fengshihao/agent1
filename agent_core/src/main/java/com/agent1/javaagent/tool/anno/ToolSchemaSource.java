package com.agent1.javaagent.tool.anno;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 复杂参数（嵌套对象、带 items 的数组）用手写 schema，不走反射推导。
 * 返回 null 时退回自动生成的 schema。
 */
public interface ToolSchemaSource {

    JsonNode parametersSchema(String toolName);
}
