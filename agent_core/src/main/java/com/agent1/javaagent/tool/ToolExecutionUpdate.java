package com.agent1.javaagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public final class ToolExecutionUpdate {
    private final String text;
    private final JsonNode details;

    public ToolExecutionUpdate(String text, JsonNode details) {
        this.text = text == null ? "" : text;
        this.details = details;
    }

    public String getText() {
        return text;
    }

    public JsonNode getDetails() {
        return details;
    }
}
