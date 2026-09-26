package com.agent1.javaagent.model;

import java.util.Objects;

public final class ToolCall {
    private final String id;
    private final String name;
    private final String argumentsJson;

    public ToolCall(String id, String name, String argumentsJson) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getArgumentsJson() {
        return argumentsJson;
    }
}
