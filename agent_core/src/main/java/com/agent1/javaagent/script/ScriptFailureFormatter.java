package com.agent1.javaagent.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** D2：脚本失败时返回结构化 JSON 文本（仍走 tool result 字符串）。 */
public final class ScriptFailureFormatter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern ENGINE_LINE = Pattern.compile("(?i)(?:line\\s+|at\\s+)(\\d+)");

    private ScriptFailureFormatter() {
    }

    public static String formatJson(ScriptEvalFrame frame, RuntimeException error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        Integer engineLine = parseEngineLine(message);
        int prelude = frame.totalPreludeLines();
        Integer userLine = engineLine == null ? null : Math.max(1, engineLine - prelude);

        ObjectNode root = MAPPER.createObjectNode();
        root.put("ok", false);
        root.put("errorType", error.getClass().getSimpleName());
        root.put("message", message);
        ObjectNode location = root.putObject("location");
        if (userLine != null) {
            location.put("userLine", userLine);
        }
        if (engineLine != null) {
            location.put("engineLine", engineLine);
        }
        ObjectNode source = root.putObject("source");
        source.put("kind", frame.sourceKind() == ScriptEvalFrame.SourceKind.FILE ? "file" : "inline");
        if (frame.sourceKind() == ScriptEvalFrame.SourceKind.FILE) {
            source.put("path", frame.filePath());
        }
        ObjectNode preludeNode = root.putObject("prelude");
        preludeNode.put("agentArgsLines", frame.agentArgsLines());
        preludeNode.put("weizhiToolsLines", frame.weizhiToolsLines());
        preludeNode.put("totalSkippedLines", prelude);
        root.put("hint", "location.userLine 相对用户脚本；完整 D4 需 Weizhi 内建行号 remap。");
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"ok\":false,\"message\":" + quote(message) + "}";
        }
    }

    public static boolean looksLikeFailureJson(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.startsWith("{") && trimmed.contains("\"ok\":false");
    }

    private static Integer parseEngineLine(String message) {
        Matcher matcher = ENGINE_LINE.matcher(message);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
