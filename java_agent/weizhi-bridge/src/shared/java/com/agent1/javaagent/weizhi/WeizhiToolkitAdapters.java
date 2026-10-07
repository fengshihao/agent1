package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Set;

/** 把模型传入的工作区绝对路径收成逻辑路径。 */
public final class WeizhiToolkitAdapters {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> PATH_FIELDS = Set.of(
        "path", "file", "dest", "sourceDir", "input_path", "output_path"
    );

    private WeizhiToolkitAdapters() {
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
}
