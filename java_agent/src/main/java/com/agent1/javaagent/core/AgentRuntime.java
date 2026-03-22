package com.agent1.javaagent.core;

import com.agent1.javaagent.event.AgentEvent;
import com.agent1.javaagent.event.AgentEventListener;
import com.agent1.javaagent.event.AgentEventType;
import com.agent1.javaagent.event.EventPayloads;
import com.agent1.javaagent.llm.LlmClient;
import com.agent1.javaagent.llm.LlmStreamListener;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.AssistantResponse;
import com.agent1.javaagent.model.ChatRequest;
import com.agent1.javaagent.model.ToolCall;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.memory.MemorySqliteCatalog;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolExecutionUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Closeable;
import java.time.Duration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

public final class AgentRuntime implements Closeable {
    private final AgentState state;
    private final LlmClient llmClient;
    private final ContextTransformer transformContext;
    private final ObjectMapper mapper;
    private final List<AgentEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Subject<AgentEvent> eventSubject = PublishSubject.<AgentEvent>create().toSerialized();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService toolExecutor = Executors.newCachedThreadPool();
    private final Duration defaultToolTimeout;
    private final int maxContextMessages;
    private final int maxTurnsPerRun;
    private final int maxToolCallsPerRun;
    private final Optional<Path> memoryDatabasePath;
    private final AtomicBoolean attachMemoryCatalogNextLlmCall = new AtomicBoolean(false);

    private CompletableFuture<Void> runningTask;
    private CancellationToken cancellationToken;

    public AgentRuntime(AgentOptions options, LlmClient llmClient) {
        this(options, llmClient, new ObjectMapper());
    }

    public AgentRuntime(AgentOptions options, LlmClient llmClient, ObjectMapper mapper) {
        this.state = new AgentState(
            options.getSystemPrompt(),
            options.getModel(),
            options.getTools(),
            options.getMessages()
        );
        this.llmClient = llmClient;
        this.transformContext = options.getTransformContext();
        this.mapper = mapper;
        this.defaultToolTimeout = options.getDefaultToolTimeout();
        this.maxContextMessages = options.getMaxContextMessages();
        this.maxTurnsPerRun = options.getMaxTurnsPerRun();
        this.maxToolCallsPerRun = options.getMaxToolCallsPerRun();
        this.memoryDatabasePath = options.getMemoryDatabasePath();
    }

    public AgentStateSnapshot getStateSnapshot() {
        return state.snapshot();
    }

    public void setSystemPrompt(String systemPrompt) {
        state.setSystemPrompt(systemPrompt);
    }

    public void setModel(String model) {
        state.setModel(model);
    }

    public void setTools(List<AgentTool> tools) {
        state.setTools(tools);
    }

    public void replaceMessages(List<AgentMessage> messages) {
        state.replaceMessages(messages);
    }

    public void appendMessage(AgentMessage message) {
        state.appendMessage(message);
    }

    public AutoCloseable subscribe(AgentEventListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public Observable<AgentEvent> observeEvents() {
        return eventSubject.hide();
    }

    public synchronized CompletableFuture<Void> prompt(String content) {
        return prompt(AgentMessage.user(content));
    }

    public synchronized CompletableFuture<Void> prompt(AgentMessage message) {
        if (!AgentMessage.ROLE_USER.equals(message.getRole())) {
            throw new IllegalArgumentException("prompt(message) only accepts role=user");
        }
        state.appendMessage(message);
        if (memoryDatabasePath.isPresent()) {
            attachMemoryCatalogNextLlmCall.set(true);
        }
        return startRunLocked();
    }

    public synchronized CompletableFuture<Void> continueRun() {
        List<AgentMessage> messages = state.getMessages();
        if (messages.isEmpty()) {
            throw new IllegalStateException("Cannot continue without messages");
        }
        String role = messages.get(messages.size() - 1).getRole();
        if (!AgentMessage.ROLE_USER.equals(role) && !AgentMessage.ROLE_TOOL_RESULT.equals(role)) {
            throw new IllegalStateException("Last message must be user or toolResult for continueRun()");
        }
        return startRunLocked();
    }

    public synchronized void abort() {
        if (cancellationToken != null) {
            cancellationToken.cancel();
        }
    }

    public void waitForIdle() {
        CompletableFuture<Void> task;
        synchronized (this) {
            task = runningTask;
        }
        if (task != null) {
            task.join();
        }
    }

    private synchronized CompletableFuture<Void> startRunLocked() {
        if (runningTask != null && !runningTask.isDone()) {
            throw new IllegalStateException("Agent is already running");
        }
        cancellationToken = new CancellationToken();
        runningTask = CompletableFuture.runAsync(() -> runAgentLoop(cancellationToken), executor);
        return runningTask;
    }

    private void runAgentLoop(CancellationToken token) {
        emit(AgentEventType.AGENT_START, state.snapshot());
        int turnIndex = 0;
        int toolCallCount = 0;

        try {
            while (!token.isCancelled() && turnIndex < maxTurnsPerRun) {
                emit(AgentEventType.TURN_START, new EventPayloads.TurnStart(turnIndex));

                AssistantResponse assistantResponse = runSingleTurn(token);
                AgentMessage assistantMessage = AgentMessage.assistant(
                    assistantResponse.getContent(),
                    assistantResponse.getToolCalls()
                );
                state.appendMessage(assistantMessage);
                emit(AgentEventType.MESSAGE_END, new EventPayloads.MessageEvent(assistantMessage));

                List<AgentMessage> toolResults = new ArrayList<>();
                if (!assistantResponse.getToolCalls().isEmpty()) {
                    for (ToolCall toolCall : assistantResponse.getToolCalls()) {
                        if (token.isCancelled()) {
                            break;
                        }
                        if (toolCallCount >= maxToolCallsPerRun) {
                            throw new IllegalStateException(
                                "工具调用次数超过上限（" + maxToolCallsPerRun + "），已停止本轮以避免循环重试"
                            );
                        }
                        toolResults.add(executeToolCall(toolCall, token));
                        toolCallCount += 1;
                    }
                }

                emit(AgentEventType.TURN_END, new EventPayloads.TurnEnd(assistantMessage, toolResults));
                if (assistantResponse.getToolCalls().isEmpty()) {
                    break;
                }
                turnIndex += 1;
            }
            if (turnIndex >= maxTurnsPerRun) {
                throw new IllegalStateException(
                    "对话回合超过上限（" + maxTurnsPerRun + "），已停止本轮以避免循环重试"
                );
            }
        } catch (Exception e) {
            state.setError(e.getMessage());
            emit(AgentEventType.AGENT_ERROR, new EventPayloads.AgentError(e.getMessage()));
        } finally {
            state.setStreaming(false);
            state.setStreamMessage(null);
            state.clearPendingToolCalls();
            emit(AgentEventType.AGENT_END, new EventPayloads.AgentEnd(state.getMessages()));
        }
    }

    private AssistantResponse runSingleTurn(CancellationToken token) throws Exception {
        AgentMessage streamMessage = AgentMessage.assistant("", List.of());
        state.setStreaming(true);
        state.setStreamMessage(streamMessage);
        emit(AgentEventType.MESSAGE_START, new EventPayloads.MessageEvent(streamMessage));

        ChatRequest request = new ChatRequest(state.getModel(), buildContextMessages());
        AssistantResponse response = llmClient.streamChat(
            request,
            state.getTools(),
            new LlmStreamListener() {
                @Override
                public void onTextDelta(String delta) {
                    AgentMessage current = state.getStreamMessage();
                    if (current == null) {
                        return;
                    }
                    AgentMessage updated = current.withContent(current.getContent() + delta);
                    state.setStreamMessage(updated);
                    emit(
                        AgentEventType.MESSAGE_UPDATE,
                        new EventPayloads.MessageUpdate(delta, updated)
                    );
                }
            },
            token
        );

        state.setStreaming(false);
        state.setStreamMessage(null);
        return response;
    }

    private AgentMessage executeToolCall(ToolCall toolCall, CancellationToken token) {
        state.addPendingToolCall(toolCall.getId());
        emit(AgentEventType.TOOL_EXECUTION_START, new EventPayloads.ToolExecutionStart(toolCall));

        AgentTool tool = state.getTool(toolCall.getName());
        ToolExecutionResult result;
        boolean isError = false;
        String errorMessage = null;

        try {
            if (tool == null) {
                throw new IllegalStateException("Tool not found: " + toolCall.getName());
            }

            JsonNode parameters = mapper.readTree(toolCall.getArgumentsJson());
            long timeoutMs = estimateToolTimeoutMs(toolCall.getName(), parameters);
            CompletableFuture<ToolExecutionResult> toolTask = CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return tool.execute(
                            toolCall.getId(),
                            parameters,
                            token,
                            update -> emit(
                                AgentEventType.TOOL_EXECUTION_UPDATE,
                                new EventPayloads.ToolExecutionUpdatePayload(toolCall.getId(), sanitizeUpdate(update))
                            )
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                toolExecutor
            );

            try {
                result = toolTask.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                toolTask.cancel(true);
                isError = true;
                errorMessage = "工具执行超时（" + timeoutMs + "ms）: " + toolCall.getName();
                result = ToolExecutionResult.text(errorMessage + "。已中断本次调用，请调整参数后重试。");
            } catch (ExecutionException exec) {
                Throwable cause = exec.getCause() == null ? exec : exec.getCause();
                throw new RuntimeException(cause);
            }
        } catch (Exception e) {
            isError = true;
            errorMessage = e.getMessage() == null ? "Tool execution failed" : e.getMessage();
            result = ToolExecutionResult.text(errorMessage);
        } finally {
            state.removePendingToolCall(toolCall.getId());
        }

        AgentMessage toolResultMessage = AgentMessage.toolResult(toolCall.getId(), result.getText(), isError);
        state.appendMessage(toolResultMessage);
        emit(
            AgentEventType.TOOL_EXECUTION_END,
            new EventPayloads.ToolExecutionEnd(toolCall.getId(), result, isError, errorMessage)
        );
        return toolResultMessage;
    }

    private ToolExecutionUpdate sanitizeUpdate(ToolExecutionUpdate update) {
        if (update == null) {
            return new ToolExecutionUpdate("", null);
        }
        return update;
    }

    private long estimateToolTimeoutMs(String toolName, JsonNode parameters) {
        long fallback = Math.max(defaultToolTimeout.toMillis(), 1_000L);
        if (toolName == null) {
            return fallback;
        }
        return switch (toolName) {
            case "read" -> Math.min(fallback, 10_000L);
            case "run_bash" -> Math.max(fallback, 60_000L);
            case "run_python" -> Math.max(fallback, 45_000L);
            case "skill" -> estimateSkillToolTimeoutMs(fallback, parameters);
            default -> fallback;
        };
    }

    private long estimateSkillToolTimeoutMs(long fallback, JsonNode parameters) {
        String action = parameters == null ? "" : parameters.path("action").asText("");
        if ("search".equalsIgnoreCase(action)) {
            return Math.max(fallback, 20_000L);
        }
        if ("install".equalsIgnoreCase(action)) {
            return Math.max(fallback, 90_000L);
        }
        if ("uninstall".equalsIgnoreCase(action)) {
            return Math.max(fallback, 15_000L);
        }
        return Math.max(fallback, 15_000L);
    }

    private List<AgentMessage> buildContextMessages() {
        String systemPrompt = state.getSystemPrompt();
        if (attachMemoryCatalogNextLlmCall.getAndSet(false) && memoryDatabasePath.isPresent()) {
            systemPrompt = systemPrompt + "\n\n" + MemorySqliteCatalog.buildSection(memoryDatabasePath.get());
        }
        List<AgentMessage> transformed = transformContext.transform(state.getMessages());
        List<AgentMessage> forModel = MessageHistoryLimiter.limitTail(transformed, maxContextMessages);
        if (systemPrompt.isBlank()) {
            return forModel;
        }
        List<AgentMessage> withSystem = new ArrayList<>();
        withSystem.add(AgentMessage.system(systemPrompt));
        withSystem.addAll(forModel);
        return withSystem;
    }

    private void emit(AgentEventType type, Object payload) {
        AgentEvent event = new AgentEvent(type, payload);
        if (!eventSubject.hasComplete() && !eventSubject.hasThrowable()) {
            eventSubject.onNext(event);
        }
        for (AgentEventListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    @Override
    public synchronized void close() {
        abort();
        if (runningTask != null) {
            runningTask.join();
        }
        executor.shutdown();
        toolExecutor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            toolExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (!eventSubject.hasComplete() && !eventSubject.hasThrowable()) {
            eventSubject.onComplete();
        }
        try {
            llmClient.close();
        } catch (Exception ignored) {
            // best effort resource cleanup
        }
    }
}
