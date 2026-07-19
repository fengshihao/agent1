package com.agent1.javaagent.llm.openai;

import com.fasterxml.jackson.databind.JsonNode;

/** HTTP 200 SSE 里的 DashScope / OpenAI 网关错误（避免当成空回复）。 */
final class DashScopeSseError {

    private DashScopeSseError() {
    }

    static String messageOf(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }
        JsonNode error = root.get("error");
        if (error != null && error.isObject()) {
            String code = text(error, "code");
            String message = text(error, "message");
            if (code != null || message != null) {
                return format(code, message);
            }
        }
        String code = firstNonBlank(text(root, "error_code"), text(root, "code"));
        String message = text(root, "message");
        if (code != null && looksLikeGatewayCode(code)) {
            return format(code, message);
        }
        return null;
    }

    static boolean looksRetryable(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("throttling")
            || lower.contains("ratequota")
            || lower.contains("429");
    }

    private static boolean looksLikeGatewayCode(String code) {
        return code.startsWith("Invalid")
            || code.startsWith("Throttling")
            || code.startsWith("AccessDenied")
            || code.startsWith("BadRequest")
            || code.startsWith("DataInspection")
            || code.startsWith("Model.")
            || "Arrearage".equals(code);
    }

    private static String format(String code, String message) {
        StringBuilder sb = new StringBuilder("DashScope error");
        if (code != null && !code.isBlank()) {
            sb.append(" [").append(code).append("]");
        }
        if (message != null && !message.isBlank()) {
            sb.append(": ").append(message);
        }
        return sb.toString();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
