package com.agent1.javaagent.examples;

import com.agent1.javaagent.core.AgentOptions;
import com.agent1.javaagent.core.AgentRuntime;
import com.agent1.javaagent.event.AgentEventType;
import com.agent1.javaagent.llm.openai.OpenAiCompatibleClient;
import com.agent1.javaagent.llm.openai.OpenAiCompatibleConfig;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;

public final class PcCliExample {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PcCliExample() {
    }

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("DASHSCOPE_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请设置 OPENAI_API_KEY 或 DASHSCOPE_API_KEY");
        }

        String baseUrl = System.getenv("OPENAI_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = System.getenv("DASHSCOPE_BASE_URL");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com/v1";
        }

        String model = System.getenv("OPENAI_MODEL");
        if (model == null || model.isBlank()) {
            model = "gpt-4o-mini";
        }

        AgentTool echoTool = new AgentTool() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public String description() {
                return "Echo back input text";
            }

            @Override
            public JsonNode parametersSchema() {
                var schema = MAPPER.createObjectNode();
                schema.put("type", "object");
                var properties = MAPPER.createObjectNode();
                properties.set("text", MAPPER.createObjectNode().put("type", "string"));
                schema.set("properties", properties);
                schema.set("required", MAPPER.createArrayNode().add("text"));
                return schema;
            }

            @Override
            public ToolExecutionResult execute(
                String toolCallId,
                JsonNode parameters,
                com.agent1.javaagent.core.CancellationToken cancellationToken,
                com.agent1.javaagent.tool.ToolUpdateListener onUpdate
            ) {
                return ToolExecutionResult.text("echo:" + parameters.path("text").asText(""));
            }
        };

        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
            new OpenAiCompatibleConfig(apiKey, baseUrl, Duration.ofSeconds(60), 0.2)
        );

        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder(model)
                .systemPrompt("You are a concise coding assistant.")
                .tools(List.of(echoTool))
                .build(),
            client
        );

        runtime.subscribe(event -> {
            if (event.getType() == AgentEventType.MESSAGE_UPDATE) {
                Object payload = event.getPayload();
                String delta = ((com.agent1.javaagent.event.EventPayloads.MessageUpdate) payload).getDelta();
                System.out.print(delta);
            }
            if (event.getType() == AgentEventType.TOOL_EXECUTION_END) {
                System.out.println("\n[tool done]");
            }
        });

        runtime.prompt("请先调用 echo 工具，参数 text=hello，然后总结一句话。").join();
        runtime.close();
        System.out.println();
    }
}
