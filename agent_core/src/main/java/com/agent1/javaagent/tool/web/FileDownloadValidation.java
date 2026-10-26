package com.agent1.javaagent.tool.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FileDownloadValidation {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FileDownloadValidation() {
    }

    record Result(boolean ok, String detected, String fileExtension, String error, List<Check> checks) {
    }

    record Check(String id, boolean ok, String detail) {
    }

    static Result validate(byte[] bytes, FileDownloadExpect expect) {
        List<Check> checks = new ArrayList<>();
        checks.add(new Check("non_empty", bytes.length > 0, bytes.length > 0 ? "size=" + bytes.length : "empty body"));
        if (bytes.length == 0) {
            return new Result(false, "empty", "bin", "下载内容为空", checks);
        }

        String imageType = FilesDownloadTool.sniffImageType(bytes);
        boolean html = looksLikeHtml(bytes);
        boolean json = looksLikeJson(bytes);
        boolean text = looksLikeText(bytes);

        FileDownloadExpect resolved = expect == FileDownloadExpect.AUTO ? autoExpect(imageType, html, json, text) : expect;
        String detected = detectLabel(imageType, html, json, text, resolved);
        String ext = extensionFor(imageType, resolved);

        switch (resolved) {
            case IMAGE -> {
                boolean ok = imageType != null;
                checks.add(new Check("expect_image", ok, ok ? imageType : "not an image"));
                if (!ok) {
                    return new Result(false, detected, ext, "不是支持的图片（png/jpg/gif/webp/svg）", checks);
                }
                return new Result(true, detected, imageType, null, checks);
            }
            case HTML -> {
                checks.add(new Check("expect_html", html, html ? "html marker" : "missing <!DOCTYPE or <html"));
                if (!html) {
                    return new Result(false, detected, ext, "不是 HTML 薄壳或页面", checks);
                }
                return new Result(true, "html", "html", null, checks);
            }
            case JSON -> {
                checks.add(new Check("expect_json", json, json ? "parsed" : "invalid json"));
                if (!json) {
                    return new Result(false, detected, ext, "不是合法 JSON", checks);
                }
                return new Result(true, "json", "json", null, checks);
            }
            case TEXT -> {
                checks.add(new Check("expect_text", text, text ? "utf-8 text" : "not plain text"));
                if (!text) {
                    return new Result(false, detected, ext, "不是可读文本", checks);
                }
                return new Result(true, "text", "txt", null, checks);
            }
            case ANY -> {
                checks.add(new Check("expect_any", true, "accepted"));
                return new Result(true, detected, ext, null, checks);
            }
            default -> {
                checks.add(new Check("expect_auto", true, detected));
                return new Result(true, detected, ext, null, checks);
            }
        }
    }

    private static FileDownloadExpect autoExpect(String imageType, boolean html, boolean json, boolean text) {
        if (imageType != null) {
            return FileDownloadExpect.IMAGE;
        }
        if (html) {
            return FileDownloadExpect.HTML;
        }
        if (json) {
            return FileDownloadExpect.JSON;
        }
        if (text) {
            return FileDownloadExpect.TEXT;
        }
        return FileDownloadExpect.ANY;
    }

    private static String detectLabel(String imageType, boolean html, boolean json, boolean text, FileDownloadExpect resolved) {
        if (imageType != null) {
            return "image/" + imageType;
        }
        if (html) {
            return "html";
        }
        if (json) {
            return "json";
        }
        if (text) {
            return "text";
        }
        return resolved == FileDownloadExpect.ANY ? "binary" : "unknown";
    }

    private static String extensionFor(String imageType, FileDownloadExpect resolved) {
        if (imageType != null) {
            return imageType;
        }
        return resolved.defaultExtension();
    }

    static boolean looksLikeHtml(byte[] bytes) {
        String head = new String(bytes, 0, Math.min(bytes.length, 512), StandardCharsets.UTF_8).trim();
        if (head.startsWith("\uFEFF")) {
            head = head.substring(1).trim();
        }
        String lower = head.toLowerCase(Locale.ROOT);
        return lower.startsWith("<!doctype html") || lower.startsWith("<html");
    }

    static boolean looksLikeJson(byte[] bytes) {
        String trimmed = new String(bytes, StandardCharsets.UTF_8).trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        char first = trimmed.charAt(0);
        if (first != '{' && first != '[') {
            return false;
        }
        try {
            MAPPER.readTree(trimmed);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean looksLikeText(byte[] bytes) {
        if (bytes.length > 512 * 1024) {
            return false;
        }
        String s = new String(bytes, StandardCharsets.UTF_8);
        int control = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                continue;
            }
            if (Character.isISOControl(c)) {
                control++;
            }
        }
        return control < Math.max(4, s.length() / 200);
    }
}
