package com.agent1.javaagent.brief;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;

/** 工具结果一行摘要（对齐 Android {@code ChatTranscriptFormatting.formatToolResult} 的简报子集）。 */
public final class BriefToolResultSummary {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PLAIN_MAX = 400;
    private static final int PREVIEW_MAX = 280;

    private BriefToolResultSummary() {
    }

    public static String summarize(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!trimmed.startsWith("{")) {
            return truncatePlain(trimmed);
        }
        try {
            JsonNode json = MAPPER.readTree(trimmed);
            if (!json.path("ok").asBoolean(true)) {
                String error = json.path("error").asText("").trim();
                return truncatePlain(error.isEmpty() ? trimmed : error);
            }
            String outputPath = normalizePath(json.path("outputPath").asText("").trim());
            if (!outputPath.isEmpty()) {
                if (isImagePath(outputPath)) {
                    return joinParts("已生成图片", outputPath, optionalLong(json, "outputBytes", " 字节"));
                }
                return joinParts("已生成文件", outputPath);
            }
            String pathField = normalizePath(json.path("path").asText("").trim());
            if (!pathField.isEmpty()) {
                return "输出: " + pathField;
            }
            String preview = json.path("resultPreview").asText("").trim();
            if (preview.length() > PREVIEW_MAX) {
                return preview.substring(0, PREVIEW_MAX) + "…";
            }
            if (!preview.isEmpty()) {
                return preview;
            }
            return truncatePlain(json.toString());
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return truncatePlain(trimmed);
        }
    }

    private static String optionalLong(JsonNode json, String field, String suffix) {
        if (!json.has(field)) {
            return null;
        }
        long value = json.path(field).asLong(-1L);
        if (value < 0) {
            return null;
        }
        return value + suffix;
    }

    private static String joinParts(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(part);
        }
        return sb.toString();
    }

    private static String normalizePath(String path) {
        if (path.startsWith("./")) {
            return path.substring(2);
        }
        return path;
    }

    private static boolean isImagePath(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot >= path.length() - 1) {
            return false;
        }
        String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("webp") || ext.equals("gif");
    }

    private static String truncatePlain(String text) {
        String oneLine = text.replace('\n', ' ').trim();
        if (oneLine.length() <= PLAIN_MAX) {
            return oneLine;
        }
        return oneLine.substring(0, PLAIN_MAX) + "…";
    }
}
