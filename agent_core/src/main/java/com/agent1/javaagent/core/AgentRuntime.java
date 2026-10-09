package com.agent1.javaagent.core;

import com.agent1.javaagent.config.AgentRuntimeDefaults;
import com.agent1.javaagent.event.AgentEvent;
import com.agent1.javaagent.event.AgentEventListener;
import com.agent1.javaagent.event.AgentEventType;
import com.agent1.javaagent.event.EventPayloads;
import com.agent1.javaagent.llm.LlmCancelledException;
import com.agent1.javaagent.llm.LlmClient;
import com.agent1.javaagent.llm.LlmStreamListener;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.AssistantResponse;
import com.agent1.javaagent.model.ChatRequest;
import com.agent1.javaagent.model.ChatUsage;
import com.agent1.javaagent.model.ToolCall;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolArgumentValidator;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolExecutionUpdate;
import com.agent1.javaagent.coach.ProductivityCoach;
import com.agent1.javaagent.log.AgentAuditEvents;
import com.agent1.javaagent.log.RunAuditScope;
import com.agent1.javaagent.log.RunLogContext;
import com.agent1.javaagent.script.ScriptToolRunContext;
import com.agent1.javaagent.tool.workspace.HtmlHarnessSmoke;
import com.agent1.javaagent.workspace.ToolResultSpill;
import java.nio.file.Path;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
    private final ExecutorService toolExecutor = new ToolExecutorService();
    private final Duration defaultToolTimeout;
    private final int maxContextTurns;
    private final int maxContextMessages;
    private final int maxTurnsPerRun;
    private final int maxToolCallsPerRun;

    private CompletableFuture<Void> runningTask;
    private CancellationToken cancellationToken;
    private volatile WorkspaceSandbox workspaceSandbox;
    private volatile ProductivityCoach productivityCoach;
    private volatile Path runAuditAgentRoot;
    private volatile RunLogContext runAuditLogContext;
    private volatile boolean stopRunWaitingUser;

    /** 本轮 run 改动过、待收尾冒烟的 html 路径；run 异常中止时保留到下一个 run 再检。 */
    private final Set<String> pendingHtmlSmokePaths = new LinkedHashSet<>();
    /** 收尾冒烟失败后的修复反馈轮数（每 run 重置），防止「改坏→修复」无限循环。 */
    private int htmlSmokeFixRounds;
    private static final int MAX_HTML_SMOKE_FIX_ROUNDS = 2;

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
        this.maxContextTurns = options.getMaxContextTurns();
        this.maxContextMessages = options.getMaxContextMessages();
        this.maxTurnsPerRun = options.getMaxTurnsPerRun();
        this.maxToolCallsPerRun = options.getMaxToolCallsPerRun();
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

    public List<AgentTool> getTools() {
        return state.getTools();
    }

    public void replaceMessages(List<AgentMessage> messages) {
        state.replaceMessages(messages);
    }

    /** 生产力 Coach（方案 A）；{@code null} 表示不追加。 */
    public void setProductivityCoach(ProductivityCoach coach) {
        this.productivityCoach = coach;
    }

    /** 生产力工作区：大工具结果 spill 与路径校验。 */
    public void setWorkspaceSandbox(WorkspaceSandbox sandbox) {
        this.workspaceSandbox = sandbox;
    }

    /** 绑定当前 Run 的 session/runId，供工具与 Coach 写 P.4 审计事件。 */
    public void setRunAuditBinding(Path agentRoot, RunLogContext logContext) {
        this.runAuditAgentRoot = agentRoot == null ? null : agentRoot.toAbsolutePath().normalize();
        this.runAuditLogContext = logContext;
    }

    public void clearRunAuditBinding() {
        this.runAuditAgentRoot = null;
        this.runAuditLogContext = null;
    }

    public synchronized boolean isRunning() {
        return runningTask != null && !runningTask.isDone();
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
        state.setError(null);
        emit(AgentEventType.AGENT_START, state.snapshot());
        int turnIndex = 0;
        int toolCallCount = 0;
        htmlSmokeFixRounds = 0;
        stopRunWaitingUser = false;

        try {
            while (!token.isCancelled() && turnIndex < maxTurnsPerRun) {
                emit(AgentEventType.TURN_START, new EventPayloads.TurnStart(turnIndex));

                AssistantResponse assistantResponse = runSingleTurn(token);
                emitUsage(assistantResponse);
                AgentMessage assistantMessage = AgentMessage.assistant(
                    assistantResponse.getContent(),
                    assistantResponse.getReasoningContent(),
                    assistantResponse.getToolCalls()
                );
                state.appendMessage(assistantMessage);
                emit(AgentEventType.MESSAGE_END, new EventPayloads.MessageEvent(assistantMessage));

                List<AgentMessage> toolResults = new ArrayList<>();
                if (assistantResponse.isTruncatedToolCall()) {
                    for (ToolCall toolCall : assistantResponse.getToolCalls()) {
                        toolResults.add(recordTruncatedToolCall(toolCall));
                    }
                } else if (!assistantResponse.getToolCalls().isEmpty()) {
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
                        if (stopRunWaitingUser) {
                            break;
                        }
                    }
                }

                emit(AgentEventType.TURN_END, new EventPayloads.TurnEnd(assistantMessage, toolResults));
                if (stopRunWaitingUser) {
                    state.setError(RunOutcome.waitingUserMessage());
                    break;
                }
                if (assistantResponse.getToolCalls().isEmpty()) {
                    // 交付点：对本轮改动过的 html 统一冒烟；未过则注入修复反馈让 AI 修完再交付
                    AgentMessage smokeFeedback = runHtmlSmokeAtDelivery(token);
                    if (smokeFeedback != null) {
                        state.appendMessage(smokeFeedback);
                        emit(AgentEventType.MESSAGE_END, new EventPayloads.MessageEvent(smokeFeedback));
                        turnIndex += 1;
                        continue;
                    }
                    break;
                }
                turnIndex += 1;
            }

            if (token.isCancelled()) {
                state.setError(RunOutcome.CANCELLED_MESSAGE);
                return;
            }

            if (turnIndex >= maxTurnsPerRun) {
                appendProgressSummaryTurn(token);
                state.setError(RunOutcome.pausedMessage(maxTurnsPerRun));
                return;
            }
        } catch (LlmCancelledException cancelled) {
            state.setError(RunOutcome.CANCELLED_MESSAGE);
            return;
        } catch (Throwable e) {
            // 捕获 Throwable（含 NoSuchMethodError 等 Error）：run 中途崩溃时不能让会话进入
            // 「tool_call 无回执」的不可恢复状态，否则之后每轮请求都会被服务端拒绝。
            if (token.isCancelled()) {
                state.setError(RunOutcome.CANCELLED_MESSAGE);
                return;
            }
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            state.setError(message);
            appendInterruptedToolResults();
            AgentMessage errorMessage = AgentMessage.assistant("错误: " + message, List.of());
            state.appendMessage(errorMessage);
            emit(AgentEventType.MESSAGE_END, new EventPayloads.MessageEvent(errorMessage));
            emit(AgentEventType.AGENT_ERROR, new EventPayloads.AgentError(message));
        } finally {
            state.setStreaming(false);
            state.setStreamMessage(null);
            state.clearPendingToolCalls();
            emit(AgentEventType.AGENT_END, new EventPayloads.AgentEnd(state.getMessages()));
        }
    }

    private void appendProgressSummaryTurn(CancellationToken token) throws Exception {
        state.appendMessage(AgentMessage.user(RunOutcome.progressSummaryUserPrompt()));
        AssistantResponse summary = runSingleTurn(token, List.of());
        emitUsage(summary);
        AgentMessage assistantMessage = AgentMessage.assistant(
            summary.getContent(),
            summary.getReasoningContent(),
            summary.getToolCalls()
        );
        state.appendMessage(assistantMessage);
        emit(AgentEventType.MESSAGE_END, new EventPayloads.MessageEvent(assistantMessage));
    }

    /**
     * 交付点收尾冒烟：对本轮改动过的 html 各跑一次 {@link HtmlHarnessSmoke#smokeFile}。
     * 有失败且修复反馈未超限时，返回 user 消息让 AI 修复后再交付；
     * 全过 / 超限 / 取消时返回 {@code null}，结果以注记形式进入 transcript。
     */
    private AgentMessage runHtmlSmokeAtDelivery(CancellationToken token) {
        if (pendingHtmlSmokePaths.isEmpty()) {
            return null;
        }
        StringBuilder failures = new StringBuilder();
        StringBuilder notes = new StringBuilder();
        Iterator<String> pending = pendingHtmlSmokePaths.iterator();
        while (pending.hasNext()) {
            if (token != null && token.isCancelled()) {
                break; // 剩余未检路径保留到下一个 run
            }
            String path = pending.next();
            pending.remove();
            HtmlHarnessSmoke.Outcome outcome;
            try {
                outcome = HtmlHarnessSmoke.smokeFile(
                    state.getTool("webview_exec"),
                    workspaceSandbox,
                    path,
                    token
                );
            } catch (Throwable smokeFailure) {
                // 兜底捕获 Throwable（Android 低 API 会抛 Error）：收尾冒烟不能把 run 打崩
                String message = smokeFailure.getMessage() == null ? "html_smoke 异常" : smokeFailure.getMessage();
                failures.append("[html_smoke] ").append(path).append(" 失败。宿主检查异常（").append(message).append("）\n");
                continue;
            }
            if (outcome == null) {
                continue; // 已不是 harness 页，安静跳过
            }
            if (outcome.failed()) {
                failures.append(outcome.text()).append('\n');
            } else {
                notes.append(outcome.text()).append('\n');
            }
        }
        if (failures.length() == 0 && notes.length() == 0) {
            return null;
        }
        if (failures.length() == 0) {
            appendSmokeNote(notes.toString().trim());
            return null;
        }
        if (htmlSmokeFixRounds >= MAX_HTML_SMOKE_FIX_ROUNDS) {
            // 修复轮超限：不再让 AI 重试，但失败详情必须留在 transcript 里供下一轮与人工审查
            appendSmokeNote(failures.append(notes).toString().trim());
            return null;
        }
        htmlSmokeFixRounds += 1;
        return AgentMessage.user(
            "交付前 html_smoke 检查未通过，请修复以下页面后重新交付（必须实际修改文件，不要只口头解释）：\n"
                + failures.toString().trim()
        );
    }

    /** 收尾冒烟注记：进入 transcript 供下一轮上下文与人工审查，不触发额外模型调用。 */
    private void appendSmokeNote(String note) {
        AgentMessage smokeNote = AgentMessage.user(note);
        state.appendMessage(smokeNote);
        emit(AgentEventType.MESSAGE_END, new EventPayloads.MessageEvent(smokeNote));
    }

    private AssistantResponse runSingleTurn(CancellationToken token) throws Exception {
        return runSingleTurn(token, null);
    }

    private AssistantResponse runSingleTurn(CancellationToken token, List<AgentTool> toolsOverride) throws Exception {
        AgentMessage streamMessage = AgentMessage.assistant("", List.of());
        state.setStreaming(true);
        state.setStreamMessage(streamMessage);
        emit(AgentEventType.MESSAGE_START, new EventPayloads.MessageEvent(streamMessage));

        List<AgentTool> toolsForTurn = toolsOverride != null ? toolsOverride : state.getTools();
        ChatRequest request = new ChatRequest(state.getModel(), buildContextMessages());
        AssistantResponse response = llmClient.streamChat(
            request,
            toolsForTurn,
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

                @Override
                public void onReasoningDelta(String delta) {
                    AgentMessage current = state.getStreamMessage();
                    if (current == null) {
                        return;
                    }
                    AgentMessage updated =
                        current.withReasoningContent(current.getReasoningContent() + delta);
                    state.setStreamMessage(updated);
                    emit(
                        AgentEventType.REASONING_UPDATE,
                        new EventPayloads.ReasoningUpdate(delta, updated)
                    );
                }

                @Override
                public void onRetryAttempt() {
                    state.setStreamMessage(AgentMessage.assistant("", List.of()));
                    emit(AgentEventType.MESSAGE_RESET, new EventPayloads.MessageReset());
                }
            },
            token
        );

        state.setStreaming(false);
        state.setStreamMessage(null);
        return response;
    }

    private void emitUsage(AssistantResponse response) {
        ChatUsage usage = response.getUsage();
        if (usage == null) {
            return;
        }
        emit(
            AgentEventType.USAGE,
            new EventPayloads.Usage(usage.getInputTokens(), usage.getOutputTokens(), usage.getCachedTokens())
        );
    }

    private AgentMessage recordTruncatedToolCall(ToolCall toolCall) {
        String errorMessage =
            "模型输出被 max_tokens 截断，工具参数不完整，本次调用未执行。请拆成更小的写入或减少体积后重试。";
        AgentMessage toolResult = AgentMessage.toolResult(toolCall.getId(), errorMessage, true);
        state.appendMessage(toolResult);
        emit(AgentEventType.TOOL_EXECUTION_START, new EventPayloads.ToolExecutionStart(toolCall));
        emit(
            AgentEventType.TOOL_EXECUTION_END,
            new EventPayloads.ToolExecutionEnd(
                toolCall.getId(),
                ToolExecutionResult.text(errorMessage),
                true,
                errorMessage
            )
        );
        return toolResult;
    }

    /** run 中途崩溃（含 Error）时为无回执的 tool_call 补合成 toolResult，保证 transcript 配对完整、会话可继续。 */
    private void appendInterruptedToolResults() {
        for (String pendingId : state.getPendingToolCalls()) {
            String note = "宿主执行中断，本次调用无结果，请重发";
            state.appendMessage(AgentMessage.toolResult(pendingId, note, true));
            emit(
                AgentEventType.TOOL_EXECUTION_END,
                new EventPayloads.ToolExecutionEnd(pendingId, ToolExecutionResult.text(note), true, note)
            );
        }
    }

    private AgentMessage executeToolCall(ToolCall toolCall, CancellationToken token) {
        state.addPendingToolCall(toolCall.getId());
        emit(AgentEventType.TOOL_EXECUTION_START, new EventPayloads.ToolExecutionStart(toolCall));

        AgentTool tool = state.getTool(toolCall.getName());
        ToolExecutionResult result;
        boolean isError = false;
        String errorMessage = null;
        JsonNode parameters = null;

        try {
            if (tool == null) {
                throw new IllegalStateException("Tool not found: " + toolCall.getName());
            }

            parameters = mapper.readTree(toolCall.getArgumentsJson());
            java.util.Optional<String> validationError =
                ToolArgumentValidator.validateRequired(tool.parametersSchema(), parameters);
            if (validationError.isPresent()) {
                isError = true;
                errorMessage = validationError.get();
                result = ToolExecutionResult.text(errorMessage);
            } else {
                final JsonNode paramsForExecute = parameters;
                long timeoutMs = estimateToolTimeoutMs(tool, parameters);
                CompletableFuture<ToolExecutionResult> toolTask = CompletableFuture.supplyAsync(
                () -> {
                    RunAuditScope.bind(runAuditAgentRoot, runAuditLogContext);
                    ScriptToolRunContext.bind(token);
                    try {
                        return tool.execute(
                            toolCall.getId(),
                            paramsForExecute,
                            token,
                            update -> emit(
                                AgentEventType.TOOL_EXECUTION_UPDATE,
                                new EventPayloads.ToolExecutionUpdatePayload(toolCall.getId(), sanitizeUpdate(update))
                            )
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        RunAuditScope.clear();
                        ScriptToolRunContext.clear();
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
            }
        } catch (Exception e) {
            isError = true;
            errorMessage = e.getMessage() == null ? "Tool execution failed" : e.getMessage();
            result = ToolExecutionResult.text(errorMessage);
        }

        if (!isError && workspaceSandbox != null) {
            result = ToolResultSpill.maybeSpill(workspaceSandbox, toolCall.getName(), result);
        }

        if (!isError && isHtmlDelivery(toolCall.getName()) && parameters != null) {
            // 每笔只收集 html 路径；浏览器冒烟移到 run 收尾统一执行
            //（旧实现每笔 edit 内嵌 ~10s 冒烟，真机日志显示编辑反馈循环被拖慢到 ~20s/笔）。
            try {
                String htmlPath = HtmlHarnessSmoke.htmlPathOf(parameters);
                if (htmlPath != null) {
                    pendingHtmlSmokePaths.add(htmlPath);
                }
            } catch (Throwable collectFailure) { // NOPMD EmptyCatchBlock — 有意吞掉：路径收集失败只损失收尾冒烟覆盖面，不能打崩工具回执
                // 兜底捕获 Throwable（Android 低 API 上会抛 NoSuchMethodError 等 Error（非 Exception））。
            }
        }

        ProductivityCoach coach = productivityCoach;
        ToolExecutionResult toolResult = result;
        if (coach != null && toolResult != null) {
            toolResult = coach.maybeAugment(toolCall.getName(), parameters, toolResult, isError);
            if (toolResult != null && runAuditAgentRoot != null && toolResult.getText() != null) {
                String hookId = AgentAuditEvents.parseCoachHookId(toolResult.getText());
                if (!hookId.isBlank()) {
                    AgentAuditEvents.coachFired(
                        runAuditAgentRoot,
                        runAuditLogContext,
                        hookId,
                        toolCall.getName(),
                        toolCall.getId(),
                        AgentAuditEvents.parseCoachAdvice(toolResult.getText())
                    );
                }
            }
        }
        if (toolResult == null) {
            toolResult = ToolExecutionResult.text(errorMessage == null ? "" : errorMessage);
        }

        if (!isError && toolResult.stopRunWaitingUser()) {
            stopRunWaitingUser = true;
        }

        AgentMessage toolResultMessage = AgentMessage.toolResult(toolCall.getId(), toolResult.getText(), isError);
        state.appendMessage(toolResultMessage);
        // pending 只在本次调用拿到回执后才清除：中途抛 Error 穿透时保留 pending，
        // 让 run 层兜底补合成 toolResult，保证 transcript 配对完整。
        state.removePendingToolCall(toolCall.getId());
        emit(
            AgentEventType.TOOL_EXECUTION_END,
            new EventPayloads.ToolExecutionEnd(toolCall.getId(), toolResult, isError, errorMessage)
        );
        return toolResultMessage;
    }

    private static boolean isHtmlDelivery(String toolName) {
        return "write_file".equals(toolName) || "edit_file".equals(toolName);
    }

    private ToolExecutionUpdate sanitizeUpdate(ToolExecutionUpdate update) {
        if (update == null) {
            return new ToolExecutionUpdate("", null);
        }
        return update;
    }

    private long estimateToolTimeoutMs(AgentTool tool, JsonNode parameters) {
        long fallback = Math.max(defaultToolTimeout.toMillis(), 1_000L);
        if (tool == null) {
            return fallback;
        }
        return Math.max(tool.suggestedTimeoutMs(parameters, fallback), 1_000L);
    }

    private List<AgentMessage> buildContextMessages() {
        String systemPrompt = state.getSystemPrompt();
        List<AgentMessage> transformed = transformContext.transform(state.getMessages());
        List<AgentMessage> forModel = ContextTurnLimiter.limitByUserTurns(transformed, maxContextTurns);
        forModel = ToolResultTruncator.truncateOldTurns(
            forModel,
            AgentRuntimeDefaults.DEFAULT_TOOL_RESULT_TRUNCATE_CHARS
        );
        forModel = MessageHistoryLimiter.limitTail(forModel, maxContextMessages);
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
