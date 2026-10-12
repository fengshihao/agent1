package com.agent1.javaagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public final class ToolExecutionResult {
    private final String text;
    private final JsonNode details;
    private final boolean stopRunWaitingUser;
    private final boolean error;

    public ToolExecutionResult(String text, JsonNode details) {
        this(text, details, false);
    }

    public ToolExecutionResult(String text, JsonNode details, boolean stopRunWaitingUser) {
        this(text, details, stopRunWaitingUser, false);
    }

    public ToolExecutionResult(String text, JsonNode details, boolean stopRunWaitingUser, boolean error) {
        this.text = text == null ? "" : text;
        this.details = details;
        this.stopRunWaitingUser = stopRunWaitingUser;
        this.error = error;
    }

    public static ToolExecutionResult text(String text) {
        return new ToolExecutionResult(text, null);
    }

    /** 工具自己判定失败。上层看这个标记，不从文案里猜。 */
    public static ToolExecutionResult error(String text) {
        return new ToolExecutionResult(text, null, false, true);
    }

    /** 工具已向用户发起结构化提问；Run 应在本次工具后结束并进入 {@code waiting_user}。 */
    public static ToolExecutionResult waitingForUser(String text, JsonNode details) {
        return new ToolExecutionResult(text, details, true);
    }

    public String getText() {
        return text;
    }

    public JsonNode getDetails() {
        return details;
    }

    public boolean stopRunWaitingUser() {
        return stopRunWaitingUser;
    }

    public boolean isError() {
        return error;
    }
}
