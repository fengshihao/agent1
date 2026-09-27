package com.agent1.javaagent.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** D2：脚本失败时返回结构化 JSON 文本（仍走 tool result 字符串）。 */
public final class ScriptFailureFormatter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern ENGINE_LINE = Pattern.compile("(?i)(?:line\\s+|at\\s+)(\\d+)");
    private static final Pattern FILE_LINE = Pattern.compile("([\\w./_-]+):(\\d+)(?::(\\d+))?");

    private ScriptFailureFormatter() {
    }

    public static String formatJson(ScriptEvalFrame frame, RuntimeException error) {
        String raw = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        String message = raw;
        String stack = "";
        int nl = raw.indexOf('\n');
        if (nl >= 0) {
            message = raw.substring(0, nl);
            stack = raw.substring(nl + 1);
        }
        String fullText = raw;
        Integer engineLine = parseEngineLine(fullText);
        Integer userLine = resolveUserLine(frame, fullText, engineLine);

        ObjectNode root = MAPPER.createObjectNode();
        root.put("ok", false);
        root.put("errorType", error.getClass().getSimpleName());
        root.put("message", message);
        if (!stack.isBlank()) {
            root.put("stack", stack.trim());
        }
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
        preludeNode.put("totalSkippedLines", frame.totalPreludeLines());
        root.put("hint", "location.userLine 相对用户脚本；file 模式经 Weizhi eval 文件名绑定。");
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

    private static Integer resolveUserLine(ScriptEvalFrame frame, String fullText, Integer engineLine) {
        if (frame.sourceKind() == ScriptEvalFrame.SourceKind.FILE && frame.filePath() != null && !frame.filePath().isBlank()) {
            Integer fromFile = parseLineForFile(fullText, frame.filePath());
            if (fromFile != null) {
                return fromFile;
            }
            if (engineLine != null) {
                return engineLine;
            }
        }
        int prelude = frame.totalPreludeLines();
        if (engineLine == null) {
            return null;
        }
        return Math.max(1, engineLine - prelude);
    }

    private static Integer parseLineForFile(String text, String filePath) {
        String leaf = filePath;
        int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < filePath.length()) {
            leaf = filePath.substring(slash + 1);
        }
        Matcher matcher = FILE_LINE.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (name.equals(filePath) || name.equals(leaf) || name.endsWith("/" + leaf) || name.endsWith("\\" + leaf)) {
                try {
                    return Integer.parseInt(matcher.group(2));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
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
