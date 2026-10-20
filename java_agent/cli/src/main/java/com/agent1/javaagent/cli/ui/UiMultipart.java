package com.agent1.javaagent.cli.ui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 极简 multipart/form-data 解析（单文件场景）。 */
final class UiMultipart {

    record Part(String name, String filename, byte[] body) {
    }

    private static final Pattern BOUNDARY = Pattern.compile("boundary=([^;\\s]+)");

    private UiMultipart() {
    }

    static List<Part> parse(byte[] raw, String contentType) {
        if (contentType == null || !contentType.toLowerCase().startsWith("multipart/form-data")) {
            return List.of();
        }
        Matcher m = BOUNDARY.matcher(contentType);
        if (!m.find()) {
            return List.of();
        }
        String boundary = m.group(1);
        if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
            boundary = boundary.substring(1, boundary.length() - 1);
        }
        byte[] delim = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
        List<Part> parts = new ArrayList<>();
        int i = indexOf(raw, delim, 0);
        while (i >= 0) {
            int start = i + delim.length;
            if (start + 2 <= raw.length && raw[start] == '-' && raw[start + 1] == '-') {
                break;
            }
            if (start < raw.length && raw[start] == '\r') {
                start += 2;
            } else if (start < raw.length && raw[start] == '\n') {
                start += 1;
            }
            int next = indexOf(raw, delim, start);
            if (next < 0) {
                next = raw.length;
            }
            byte[] section = slice(raw, start, next);
            Part part = parseSection(section);
            if (part != null) {
                parts.add(part);
            }
            i = next;
        }
        return parts;
    }

    private static Part parseSection(byte[] section) {
        int headerEnd = indexOf(section, "\r\n\r\n".getBytes(StandardCharsets.UTF_8), 0);
        if (headerEnd < 0) {
            return null;
        }
        String headers = new String(slice(section, 0, headerEnd), StandardCharsets.UTF_8);
        byte[] body = slice(section, headerEnd + 4, section.length);
        if (body.length >= 2 && body[body.length - 2] == '\r' && body[body.length - 1] == '\n') {
            body = slice(body, 0, body.length - 2);
        }
        String name = headerField(headers, "name");
        String filename = headerField(headers, "filename");
        return new Part(name, filename, body);
    }

    private static String headerField(String headers, String field) {
        for (String line : headers.split("\r\n")) {
            if (!line.toLowerCase().startsWith("content-disposition:")) {
                continue;
            }
            String[] tokens = line.split(";");
            for (String token : tokens) {
                String t = token.trim();
                if (t.startsWith(field + "=")) {
                    String value = t.substring(field.length() + 1).trim();
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
        }
        return "";
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static byte[] slice(byte[] src, int from, int to) {
        int len = Math.max(0, to - from);
        byte[] out = new byte[len];
        System.arraycopy(src, from, out, 0, len);
        return out;
    }
}
