package com.agent1.javaagent.tool;

import com.agent1.javaagent.core.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;

/** 把外部工具环（例如 Weizhi {@code AgentToolkit}）适配成 {@link AgentTool}。 */
public final class DelegatingAgentTool implements AgentTool {

    @FunctionalInterface
    public interface ToolBody {
        String execute(JsonNode parameters, CancellationToken cancellationToken) throws Exception;
    }

    private final String name;
    private final String description;
    private final JsonNode parametersSchema;
    private final ToolBody body;

    public DelegatingAgentTool(String name, String description, JsonNode parametersSchema, ToolBody body) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name required");
        }
        if (body == null) {
            throw new IllegalArgumentException("tool body required");
        }
        this.name = name;
        this.description = description == null ? "" : description;
        this.parametersSchema = parametersSchema;
        this.body = body;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public JsonNode parametersSchema() {
        return parametersSchema;
    }

    @Override
    public ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }
        try {
            String text = body.execute(parameters, cancellationToken);
            return ToolExecutionResult.text(text == null ? "" : text);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return ToolExecutionResult.text("错误：" + message);
        }
    }
}
