package com.agent1.javaagent.tool.web;

import java.util.Locale;

/** {@link FilesDownloadTool} 对落盘内容的期望类型。 */
enum FileDownloadExpect {
    AUTO,
    IMAGE,
    HTML,
    JSON,
    TEXT,
    ANY;

    static FileDownloadExpect parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        String n = raw.trim().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "image", "img", "picture" -> IMAGE;
            case "html", "htm" -> HTML;
            case "json" -> JSON;
            case "text", "txt", "plain" -> TEXT;
            case "any", "binary", "bin" -> ANY;
            case "auto" -> AUTO;
            default -> throw new IllegalArgumentException("不支持的 expect: " + raw);
        };
    }

    String defaultExtension() {
        return switch (this) {
            case HTML -> "html";
            case JSON -> "json";
            case TEXT -> "txt";
            case IMAGE -> "bin";
            case ANY, AUTO -> "bin";
        };
    }
}
