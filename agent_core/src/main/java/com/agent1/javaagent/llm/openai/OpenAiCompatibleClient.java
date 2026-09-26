package com.agent1.javaagent.llm.openai;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.llm.LlmCancelledException;
import com.agent1.javaagent.llm.LlmClient;
import com.agent1.javaagent.llm.LlmHttpException;
import com.agent1.javaagent.llm.LlmStreamListener;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.AssistantResponse;
import com.agent1.javaagent.model.ChatRequest;
import com.agent1.javaagent.model.ChatUsage;
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
    static final int DEFAULT_MAX_RETRIES = 2;
    static final long DEFAULT_RETRY_BACKOFF_MS = 500L;

    private final OpenAiCompatibleConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final int maxRetries;
    private final long retryBackoffMs;

    public OpenAiCompatibleClient(OpenAiCompatibleConfig config) {
        this(config, new ObjectMapper());
    }

    public OpenAiCompatibleClient(OpenAiCompatibleConfig config, ObjectMapper mapper) {
        this(config, mapper, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_BACKOFF_MS);
    }

    OpenAiCompatibleClient(
        OpenAiCompatibleConfig config,
        ObjectMapper mapper,
        int maxRetries,
        long retryBackoffMs
    ) {
        this.config = config;
        this.mapper = mapper;
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoffMs = Math.max(0L, retryBackoffMs);
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
        byte[] body = mapper.writeValueAsBytes(buildPayload(request, tools));
        Exception last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (cancellationToken.isCancelled()) {
                throw new LlmCancelledException();
            }
            try {
                return streamOnce(body, streamListener, cancellationToken);
            } catch (LlmCancelledException cancelled) {
                throw cancelled;
            } catch (Exception e) {
                last = e;
                if (cancellationToken.isCancelled()) {
                    throw new LlmCancelledException();
                }
                if (attempt == maxRetries || !shouldRetry(e)) {
                    throw e;
                }
                sleepBackoff(attempt);
            }
        }
        throw last == null ? new IllegalStateException("SSE failed: unknown") : last;
    }

    ObjectNode buildPayload(ChatRequest request, List<AgentTool> tools) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", request.getModel());
        payload.put("stream", true);
        ObjectNode streamOptions = mapper.createObjectNode();
        streamOptions.put("include_usage", true);
        payload.set("stream_options", streamOptions);
        if (config.getTemperature() != null) {
            payload.put("temperature", config.getTemperature());
        }
        payload.set("messages", toOpenAiMessages(request.getMessages()));
        if (!tools.isEmpty()) {
            payload.set("tools", toOpenAiTools(tools));
        }
        if (shouldSendThinkingOptions()) {
            ObjectNode thinking = mapper.createObjectNode();
            thinking.put("type", "enabled");
            if (isCodingPlanEndpoint()) {
                thinking.put("clear_thinking", false);
            }
            payload.set("thinking", thinking);
        }
        return payload;
    }

    private boolean shouldSendThinkingOptions() {
        String base = config.getBaseUrl().toLowerCase();
        return base.contains("bigmodel.cn") || base.contains("z.ai");
    }

    private boolean isCodingPlanEndpoint() {
        return config.getBaseUrl().contains("/api/coding/");
    }

    private AssistantResponse streamOnce(
        byte[] body,
        LlmStreamListener streamListener,
        CancellationToken cancellationToken
    ) throws Exception {
        Request httpRequest = new Request.Builder()
            .url(config.getBaseUrl().replaceAll("/+$", "") + "/chat/completions")
            .addHeader("Authorization", "Bearer " + config.getApiKey())
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(body, JSON))
            .build();

        StreamAccumulator acc = new StreamAccumulator();
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
                        parseDelta(root, acc, streamListener);
                    } catch (Exception e) {
                        errors.add(e);
                        done.countDown();
                        eventSource.cancel();
                    }
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                    try {
                        if (!cancellationToken.isCancelled()) {
                            errors.add(toFailure(t, response));
                        }
                    } catch (Throwable handlerFailure) {
                        errors.add(
                            new IllegalStateException(
                                "SSE onFailure handler error (original failure may be lost)",
                                handlerFailure
                            )
                        );
                    } finally {
                        done.countDown();
                    }
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

        if (cancellationToken.isCancelled()) {
            throw new LlmCancelledException();
        }
        if (!errors.isEmpty()) {
            throw errors.get(0);
        }
        return acc.toResponse();
    }

    private Exception toFailure(Throwable t, okhttp3.Response response) {
        if (t == null && response == null) {
            return new IllegalStateException("SSE failed: unknown (no Throwable, no Response)");
        }
        String body = "";
        if (response != null && response.body() != null) {
            try {
                body = response.body().string();
            } catch (IOException ignored) {
                // Ignore secondary parse failure.
            }
        }
        String reason = describeFailure(t, response);
        String message = "SSE failed: " + reason + " " + body;
        if (response != null) {
            if (t != null) {
                return new LlmHttpException(response.code(), message, t);
            }
            return new LlmHttpException(response.code(), message);
        }
        if (t != null) {
            return new IllegalStateException(message, t);
        }
        return new IllegalStateException(message);
    }

    static boolean shouldRetry(Exception e) {
        if (e instanceof LlmCancelledException) {
            return false;
        }
        if (e instanceof LlmHttpException http) {
            int code = http.getStatusCode();
            return code == 429 || code >= 500;
        }
        String message = e.getMessage();
        if (message != null && DashScopeSseError.looksRetryable(message)) {
            return true;
        }
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("timeout") || lower.contains("timed out") || lower.contains("connection reset");
    }

    private void sleepBackoff(int attempt) {
        long delay = retryBackoffMs * (attempt + 1);
        if (delay <= 0L) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
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
                if (!message.getReasoningContent().isBlank()) {
                    node.put("reasoning_content", message.getReasoningContent());
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
                        functionNode.set("function", functionNode);
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
        StreamAccumulator acc,
        LlmStreamListener streamListener
    ) {
        String gatewayError = DashScopeSseError.messageOf(root);
        if (gatewayError != null) {
            throw new IllegalStateException(gatewayError);
        }
        acc.mergeUsage(root.get("usage"));

        JsonNode choices = root.path("choices");
        if (!choices.isArray()) {
            return;
        }
        for (JsonNode choice : choices) {
            JsonNode finish = choice.get("finish_reason");
            if (finish != null && finish.isTextual() && !finish.asText("").isBlank()) {
                acc.finishReason = finish.asText();
            }
            JsonNode delta = choice.path("delta");
            JsonNode content = delta.get("content");
            if (content != null && content.isTextual()) {
                String value = content.asText("");
                acc.text.append(value);
                streamListener.onTextDelta(value);
            }

            JsonNode reasoning = delta.get("reasoning_content");
            if (reasoning == null) {
                reasoning = delta.get("reasoning");
            }
            if (reasoning != null && reasoning.isTextual()) {
                String value = reasoning.asText("");
                if (!value.isEmpty()) {
                    acc.reasoning.append(value);
                    streamListener.onReasoningDelta(value);
                }
            }

            JsonNode toolCalls = delta.get("tool_calls");
            if (toolCalls != null && toolCalls.isArray()) {
                for (JsonNode callDelta : toolCalls) {
                    int index = callDelta.path("index").asInt(0);
                    PartialToolCall partial = acc.toolCallByIndex.computeIfAbsent(index, key -> new PartialToolCall());
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

    /**
     * OkHttp SSE may pass a null {@code Throwable} to {@code EventSourceListener#onFailure}; never call
     * {@code t.getMessage()} unless {@code t} is non-null.
     */
    private static String describeFailure(Throwable t, okhttp3.Response response) {
        String fromThrowable = safeThrowableSummary(t);
        if (fromThrowable != null) {
            return fromThrowable;
        }
        if (response != null) {
            return "HTTP " + response.code() + " " + response.message();
        }
        return "unknown (no Throwable, no Response)";
    }

    /** Returns null if {@code t} is null; never dereferences a null throwable. */
    private static String safeThrowableSummary(Throwable t) {
        if (t == null) {
            return null;
        }
        try {
            String m = String.valueOf(t.getMessage());
            if (m != null && !m.isBlank()) {
                return m;
            }
            return t.getClass().getSimpleName();
        } catch (Throwable ignored) {
            return t.getClass().getSimpleName();
        }
    }

    private static final class StreamAccumulator {
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final Map<Integer, PartialToolCall> toolCallByIndex = new HashMap<>();
        private String finishReason;
        private ChatUsage usage;

        private void mergeUsage(JsonNode usageNode) {
            if (usageNode == null || usageNode.isMissingNode() || usageNode.isNull()) {
                return;
            }
            long input = firstLong(usageNode, "prompt_tokens", "input_tokens");
            long output = firstLong(usageNode, "completion_tokens", "output_tokens");
            Long cached = null;
            JsonNode details = usageNode.get("prompt_tokens_details");
            if (details != null && details.has("cached_tokens")) {
                cached = details.path("cached_tokens").asLong(0L);
            } else if (usageNode.has("cached_tokens")) {
                cached = usageNode.path("cached_tokens").asLong(0L);
            }
            usage = new ChatUsage(input, output, cached);
        }

        private static long firstLong(JsonNode node, String primary, String fallback) {
            if (node.has(primary)) {
                return node.path(primary).asLong(0L);
            }
            return node.path(fallback).asLong(0L);
        }

        private AssistantResponse toResponse() {
            List<ToolCall> toolCalls = toolCallByIndex.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> entry.getValue().toToolCall())
                .collect(Collectors.toList());
            return new AssistantResponse(text.toString(), reasoning.toString(), toolCalls, finishReason, usage);
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
