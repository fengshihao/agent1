package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.DelegatingAgentTool;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weizhi.agent.tool.AgentToolkit;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 把 Weizhi {@link AgentToolkit#exportSchemas()} 收成 Agent1 工具。 */
public final class WeizhiToolkitAdapters {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> PATH_FIELDS = Set.of(
        "path", "file", "dest", "sourceDir", "input_path", "output_path"
    );
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private WeizhiToolkitAdapters() {
    }

    public static List<AgentTool> toAgentTools(AgentToolkit toolkit) {
        return toAgentTools(toolkit, null);
    }

    public static List<AgentTool> toAgentTools(AgentToolkit toolkit, WorkspaceSandbox sandbox) {
        return toAgentTools(toolkit, sandbox, false);
    }

    public static List<AgentTool> toAgentTools(
        AgentToolkit toolkit,
        WorkspaceSandbox sandbox,
        boolean androidHost
    ) {
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
            if ("webview_exec".equals(name)) {
                description = WebViewExecHost.augmentDescription(description, androidHost);
                parameters = WebViewExecHost.augmentParameters(parameters);
            }
            JsonNode schemaParameters = parameters;
            tools.add(new DelegatingAgentTool(
                name,
                description,
                schemaParameters,
                (params, token) -> invoke(toolkit, name, params, token, sandbox)
            ));
        }
        return List.copyOf(tools);
    }

    private static String invoke(
        AgentToolkit toolkit,
        String name,
        JsonNode params,
        CancellationToken token,
        WorkspaceSandbox sandbox
    ) {
        JsonNode argsNode = rewriteLogicalPaths(name, params, sandbox);
        if ("webview_exec".equals(name)) {
            argsNode = WebViewExecHost.rewriteArgs(argsNode);
        }
        String raw = call(toolkit, name, argsNode, token);
        if ("webview_exec".equals(name)) {
            return WebViewExecHost.materialize(sandbox, raw);
        }
        return raw;
    }

    static JsonNode rewriteLogicalPaths(String name, JsonNode params, WorkspaceSandbox sandbox) {
        if (sandbox == null || params == null || !params.isObject()) {
            return params;
        }
        ObjectNode copy = null;
        var fields = params.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!field.getValue().isTextual()) {
                continue;
            }
            String key = field.getKey();
            String raw = field.getValue().asText();
            String logical = null;
            if ("bash".equals(name) && "command".equals(key)) {
                logical = rewriteCommandPaths(raw, sandbox);
            } else if (PATH_FIELDS.contains(key)) {
                logical = tryLogical(sandbox, raw);
            }
            if (logical != null && !logical.equals(raw)) {
                if (copy == null) {
                    copy = params.deepCopy();
                }
                copy.put(key, logical);
            }
        }
        return copy == null ? params : copy;
    }

    private static String tryLogical(WorkspaceSandbox sandbox, String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        try {
            return sandbox.logicalPath(raw);
        } catch (SecurityException e) {
            return raw;
        }
    }

    static String rewriteCommandPaths(String command, WorkspaceSandbox sandbox) {
        if (command == null || command.isEmpty()) {
            return command;
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < command.length()) {
            char c = command.charAt(i);
            if (c == '"' || c == '\'') {
                int end = command.indexOf(c, i + 1);
                if (end < 0) {
                    out.append(command.substring(i));
                    break;
                }
                String inner = command.substring(i + 1, end);
                out.append(c).append(rewriteToken(inner, sandbox)).append(c);
                i = end + 1;
                continue;
            }
            if (Character.isWhitespace(c)) {
                out.append(c);
                i++;
                continue;
            }
            int j = i;
            while (j < command.length() && !Character.isWhitespace(command.charAt(j))) {
                j++;
            }
            out.append(rewriteToken(command.substring(i, j), sandbox));
            i = j;
        }
        return out.toString();
    }

    private static String rewriteToken(String token, WorkspaceSandbox sandbox) {
        if (token.isEmpty() || token.startsWith("-")) {
            return token;
        }
        try {
            if (!Path.of(token).isAbsolute()) {
                return token;
            }
        } catch (RuntimeException e) {
            return token;
        }
        return tryLogical(sandbox, token);
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
