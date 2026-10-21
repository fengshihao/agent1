package com.agent1.javaagent.cli.ui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** workspace 文件与预览 URL 编码（与 Web UI {@code api.ts} 一致）。 */
public final class WorkspaceFileUrls {

    private WorkspaceFileUrls() {
    }

    public static String filesBase(String sessionId, String parentRelativePath) {
        String encodedSession = encode(sessionId);
        String path = encodePathSegments(parentRelativePath);
        if (path.isEmpty()) {
            return "/api/sessions/" + encodedSession + "/files/";
        }
        return "/api/sessions/" + encodedSession + "/files/" + path + "/";
    }

    public static String previewUrl(String sessionId, String workspaceRelativePath) {
        return "/api/sessions/" + encode(sessionId) + "/preview/" + encodePathSegments(workspaceRelativePath);
    }

    public static String workspaceFileUrl(String sessionId, String workspaceRelativePath) {
        return "/api/sessions/" + encode(sessionId) + "/files/" + encodePathSegments(workspaceRelativePath);
    }

    public static String encodePathSegments(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        String normalized = relativePath.replace('\\', '/');
        String[] parts = normalized.split("/");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(encode(part));
        }
        return out.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
