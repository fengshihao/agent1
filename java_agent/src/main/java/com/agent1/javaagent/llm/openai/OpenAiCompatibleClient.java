package com.agent1.javaagent.llm.openai;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.llm.LlmClient;
import com.agent1.javaagent.llm.LlmStreamListener;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.AssistantResponse;
import com.agent1.javaagent.model.ChatRequest;
import com.agent1.javaagent.model.ToolCall;
import com.agent1.javaagent.tool.AgentTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

public final class OpenAiCompatibleClient implements LlmClient {
    private static final MediaType JSON = MediaType.parse("application/json");

    private final OpenAiCompatibleConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    public OpenAiCompatibleClient(OpenAiCompatibleConfig config) {
        this(config, new ObjectMapper());
    }

    public OpenAiCompatibleClient(OpenAiCompatibleConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(config.getTimeout())
            .readTimeout(config.getTimeout())
            .writeTimeout(config.getTimeout())
            .build();
    }

    @Override
    public AssistantResponse streamChat(
        ChatRequest request,
        List<AgentTool> tools,
        LlmStreamListener streamListener,
        CancellationToken cancellationToken
    ) throws Exception {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", request.getModel());
        payload.put("stream", true);
        if (config.getTemperature() != null) {
            payload.put("temperature", config.getTemperature());
        }
        payload.set("messages", toOpenAiMessages(request.getMessages()));
        if (!tools.isEmpty()) {
            payload.set("tools", toOpenAiTools(tools));
        }

        Request httpRequest = new Request.Builder()
            .url(config.getBaseUrl().replaceAll("/+$", "") + "/chat/completions")
            .addHeader("Authorization", "Bearer " + config.getApiKey())
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(mapper.writeValueAsBytes(payload), JSON))
            .build();

        StringBuilder textBuilder = new StringBuilder();
        Map<Integer, PartialToolCall> toolCallByIndex = new HashMap<>();
        CountDownLatch done = new CountDownLatch(1);
        List<Exception> errors = new ArrayList<>();

        EventSource eventSource = EventSources.createFactory(httpClient).newEventSource(
            httpRequest,
            new EventSourceListener() {
                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    if (cancellationToken.isCancelled()) {
                        eventSource.cancel();
                        return;
                    }
                    if ("[DONE]".equals(data)) {
                        done.countDown();
                        eventSource.cancel();
                        return;
                    }
                    try {
                        JsonNode root = mapper.readTree(data);
                        parseDelta(root, textBuilder, toolCallByIndex, streamListener);
                    } catch (Exception e) {
                        errors.add(e);
                        done.countDown();
                        eventSource.cancel();
                    }
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                    if (!cancellationToken.isCancelled()) {
                        String body = "";
                        if (response != null && response.body() != null) {
                            try {
                                body = response.body().string();
                            } catch (IOException ignored) {
                                // Ignore secondary parse failure.
                            }
                        }
                        errors.add(new IllegalStateException("SSE failed: " + t.getMessage() + " " + body, t));
                    }
                    done.countDown();
                }

                @Override
                public void onClosed(EventSource eventSource) {
                    done.countDown();
                }
            }
        );

        while (!done.await(100, TimeUnit.MILLISECONDS)) {
            if (cancellationToken.isCancelled()) {
                eventSource.cancel();
                done.countDown();
                break;
            }
        }

        if (!errors.isEmpty()) {
            throw errors.get(0);
        }

        List<ToolCall> toolCalls = toolCallByIndex.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
            .map(entry -> entry.getValue().toToolCall())
            .collect(Collectors.toList());
        return new AssistantResponse(textBuilder.toString(), toolCalls);
    }

    private ArrayNode toOpenAiMessages(List<AgentMessage> messages) {
        ArrayNode result = mapper.createArrayNode();
        for (AgentMessage message : messages) {
            ObjectNode node = mapper.createObjectNode();
            if (
                AgentMessage.ROLE_SYSTEM.equals(message.getRole())
                    || AgentMessage.ROLE_USER.equals(message.getRole())
                    || AgentMessage.ROLE_ASSISTANT.equals(message.getRole())
            ) {
                node.put("role", message.getRole());
                if (!message.getContent().isBlank()) {
                    node.put("content", message.getContent());
                } else {
                    node.putNull("content");
                }
                if (!message.getToolCalls().isEmpty()) {
                    ArrayNode toolCalls = mapper.createArrayNode();
                    for (ToolCall call : message.getToolCalls()) {
                        ObjectNode callNode = mapper.createObjectNode();
                        callNode.put("id", call.getId());
                        callNode.put("type", "function");
                        ObjectNode functionNode = mapper.createObjectNode();
                        functionNode.put("name", call.getName());
                        functionNode.put("arguments", call.getArgumentsJson());
                        callNode.set("function", functionNode);
                        toolCalls.add(callNode);
                    }
                    node.set("tool_calls", toolCalls);
                }
            } else if (AgentMessage.ROLE_TOOL_RESULT.equals(message.getRole())) {
                node.put("role", "tool");
                node.put("tool_call_id", message.getToolCallId());
                node.put("content", message.getContent());
            } else {
                node.put("role", message.getRole());
                node.put("content", message.getContent());
            }
            result.add(node);
        }
        return result;
    }

    private ArrayNode toOpenAiTools(List<AgentTool> tools) {
        ArrayNode toolsNode = mapper.createArrayNode();
        for (AgentTool tool : tools) {
            ObjectNode toolNode = mapper.createObjectNode();
            toolNode.put("type", "function");
            ObjectNode function = mapper.createObjectNode();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.set("parameters", tool.parametersSchema() == null ? mapper.createObjectNode() : tool.parametersSchema());
            toolNode.set("function", function);
            toolsNode.add(toolNode);
        }
        return toolsNode;
    }

    private void parseDelta(
        JsonNode root,
        StringBuilder textBuilder,
        Map<Integer, PartialToolCall> toolCallByIndex,
        LlmStreamListener streamListener
    ) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray()) {
            return;
        }
        for (JsonNode choice : choices) {
            JsonNode delta = choice.path("delta");
            JsonNode content = delta.get("content");
            if (content != null && content.isTextual()) {
                String value = content.asText("");
                textBuilder.append(value);
                streamListener.onTextDelta(value);
            }

            JsonNode toolCalls = delta.get("tool_calls");
            if (toolCalls != null && toolCalls.isArray()) {
                for (JsonNode callDelta : toolCalls) {
                    int index = callDelta.path("index").asInt(0);
                    PartialToolCall partial = toolCallByIndex.computeIfAbsent(index, key -> new PartialToolCall());
                    if (callDelta.has("id")) {
                        String id = callDelta.get("id").asText();
                        if (id != null && !id.isBlank()) {
                            partial.id = id;
                        }
                    }
                    JsonNode function = callDelta.get("function");
                    if (function != null) {
                        if (function.has("name")) {
                            String name = function.get("name").asText();
                            if (name != null && !name.isBlank()) {
                                partial.name = name;
                            }
                        }
                        if (function.has("arguments")) {
                            partial.arguments.append(function.get("arguments").asText(""));
                        }
                    }
                    streamListener.onToolCallDelta(partial.toToolCall());
                }
            }
        }
    }

    private static final class PartialToolCall {
        private String id = "tool_call_" + System.nanoTime();
        private String name = "";
        private final StringBuilder arguments = new StringBuilder();

        private ToolCall toToolCall() {
            return new ToolCall(id, name, arguments.toString());
        }
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        if (httpClient.cache() != null) {
            try {
                httpClient.cache().close();
            } catch (IOException ignored) {
                // ignore cache close failure
            }
        }
    }
}
