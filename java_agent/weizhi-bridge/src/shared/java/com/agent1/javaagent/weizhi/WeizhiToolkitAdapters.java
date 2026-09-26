package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.DelegatingAgentTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weizhi.agent.tool.AgentToolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 把 Weizhi {@link AgentToolkit#exportSchemas()} 收成 Agent1 工具。 */
public final class WeizhiToolkitAdapters {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private WeizhiToolkitAdapters() {
    }

    public static List<AgentTool> toAgentTools(AgentToolkit toolkit) {
        if (toolkit == null) {
            throw new IllegalArgumentException("toolkit required");
        }
        List<AgentTool> tools = new ArrayList<>();
        for (Map<String, Object> schema : toolkit.exportSchemas()) {
            Object rawName = schema.get("name");
            if (rawName == null || String.valueOf(rawName).isBlank()) {
                continue;
            }
            String name = String.valueOf(rawName);
            Object rawDescription = schema.get("description");
            String description = rawDescription == null ? "" : String.valueOf(rawDescription);
            JsonNode parameters = MAPPER.valueToTree(schema.get("input_schema"));
            tools.add(new DelegatingAgentTool(name, description, parameters, (params, token) -> call(toolkit, name, params, token)));
        }
        return List.copyOf(tools);
    }

    private static String call(
        AgentToolkit toolkit,
        String name,
        JsonNode params,
        CancellationToken token
    ) {
        if (token != null && token.isCancelled()) {
            return "错误：执行已取消";
        }
        Map<String, Object> args = params == null || params.isNull() || !params.isObject()
            ? Map.of()
            : MAPPER.convertValue(params, MAP_TYPE);
        String result = toolkit.call(name, args);
        return result == null ? "" : result;
    }
}
