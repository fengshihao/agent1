package com.agent1.javaagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public final class ToolExecutionResult {
    private final String text;
    private final JsonNode details;

    public ToolExecutionResult(String text, JsonNode details) {
        this.text = text == null ? "" : text;
        this.details = details;
    }

    public static ToolExecutionResult text(String text) {
        return new ToolExecutionResult(text, null);
    }

    public String getText() {
        return text;
    }

    public JsonNode getDetails() {
        return details;
    }
}
