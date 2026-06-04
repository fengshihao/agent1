package com.agent1.javaagent.session;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.core.AgentRuntime;
import com.agent1.javaagent.core.AgentStateSnapshot;
import com.agent1.javaagent.llm.LlmClient;
import com.agent1.javaagent.log.AgentEventJsonlBridge;
import com.agent1.javaagent.log.AgentDataPaths;
import com.agent1.javaagent.log.RunLogContext;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.prompt.ProductivitySystemPromptBuilder;
import com.agent1.javaagent.run.FileRunStore;
import com.agent1.javaagent.run.RunRecord;
import com.agent1.javaagent.run.RunState;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ChatHistoryTool;
import com.agent1.javaagent.tool.workspace.EditFileTool;
import com.agent1.javaagent.tool.workspace.ListDirTool;
import com.agent1.javaagent.tool.workspace.ReadFileTool;
import com.agent1.javaagent.tool.workspace.WriteFileTool;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import java.io.Closeable;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.agent1.javaagent.llm.openai.OpenAiCompatibleClient;

/**
 * 生产力助手宿主：多会话、Run 落盘、工作区工具与 transcript 同步（01 + 02 MVP）。
 */
public final class ProductivityAgentHost implements Closeable {

    private static final String PAUSED_MARKER = "对话回合超过上限";

    private final Path agentRoot;
    private final FileSessionStore sessionStore;
    private final FileRunStore runStore;
    private final AgentRuntime runtime;
    private String activeSessionId;

    public ProductivityAgentHost(Path agentRoot, AgentRuntimeConfig config, LlmClient llmClient) {
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
        this.sessionStore = new FileSessionStore(this.agentRoot);
        this.runStore = new FileRunStore(sessionStore);
        this.runtime = new AgentRuntime(
            config.toAgentOptionsBuilder("").tools(List.of()).build(),
            llmClient
        );
    }

    /** CLI 用：按配置构造 OpenAI 兼容客户端。 */
    public ProductivityAgentHost(Path agentRoot, AgentRuntimeConfig config) {
        this(
            agentRoot,
            config,
            new OpenAiCompatibleClient(config.toOpenAiCompatibleConfig(Duration.ofSeconds(120), 0.2))
        );
    }

    public Path agentRoot() {
        return agentRoot;
    }

    public SessionMeta createSession() {
        SessionMeta meta = sessionStore.createSession();
        switchSession(meta.getSessionId());
        return meta;
    }

    public List<SessionMeta> listSessions() {
        return sessionStore.listSessions();
    }

    public void switchSession(String sessionId) {
        sessionStore.getSession(sessionId);
        this.activeSessionId = sessionId;
        refreshRuntimeForActiveSession();
    }

    public void deleteSession(String sessionId) {
        sessionStore.deleteSession(sessionId);
        if (sessionId.equals(activeSessionId)) {
            activeSessionId = null;
            runtime.replaceMessages(List.of());
            runtime.setTools(List.of());
            runtime.setSystemPrompt("");
        }
    }

    public String getActiveSessionId() {
        return activeSessionId;
    }

    public AgentRuntime runtime() {
        return runtime;
    }

    /**
     * 处理一条用户消息：新建 Run、写事件、同步 transcript、更新 Run 终态。
     */
    public String runUserMessage(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("message text required");
        }
        String sessionId = requireActiveSession();
        assertNoRunningMainRun(sessionId);

        String runId = newRunId();
        String now = Instant.now().toString();
        RunRecord running = new RunRecord(runId, sessionId, RunState.RUNNING, now, now, null);
        runStore.write(running);

        RunLogContext logContext = new RunLogContext(sessionId, runId, "", "main");
        AgentEventJsonlBridge bridge = new AgentEventJsonlBridge(logContext, AgentDataPaths.eventsJsonl(agentRoot));
        AutoCloseable subscription = runtime.subscribe(bridge);

        int messageCountBefore = runtime.getStateSnapshot().getMessages().size();
        RunState terminal = RunState.SUCCEEDED;
        String lastError = null;

        try {
            runtime.prompt(text.trim()).join();
            runtime.waitForIdle();
            AgentStateSnapshot snapshot = runtime.getStateSnapshot();
            String err = snapshot.getError();
            if (err != null && !err.isBlank()) {
                lastError = err;
                terminal = err.contains(PAUSED_MARKER) ? RunState.PAUSED : RunState.FAILED;
                if (terminal == RunState.PAUSED) {
                    bridge.writeRunPaused(err);
                }
            }
            persistNewMessages(sessionId, runId, messageCountBefore, snapshot.getMessages());
        } catch (Exception e) {
            terminal = RunState.FAILED;
            lastError = e.getMessage() == null ? e.toString() : e.getMessage();
            bridge.writeRunCancelled(lastError);
            throw e;
        } finally {
            closeQuietly(subscription);
            runStore.write(running.withTerminal(terminal, Instant.now().toString(), lastError));
        }
        return runId;
    }

    private void persistNewMessages(
        String sessionId,
        String runId,
        int messageCountBefore,
        List<AgentMessage> messages
    ) {
        for (int i = messageCountBefore; i < messages.size(); i++) {
            sessionStore.appendMessage(sessionId, runId, messages.get(i));
        }
    }

    private void assertNoRunningMainRun(String sessionId) {
        Path runsDir = sessionStore.sessionDir(sessionId).resolve("runs");
        if (!java.nio.file.Files.isDirectory(runsDir)) {
            return;
        }
        try (var stream = java.nio.file.Files.list(runsDir)) {
            for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                String runId = name.substring(0, name.length() - ".json".length());
                runStore.readOptional(sessionId, runId).ifPresent(record -> {
                    if (record.getState() == RunState.RUNNING) {
                        throw new IllegalStateException(
                            "session already has a running run: " + record.getRunId()
                        );
                    }
                });
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("scan runs failed: " + runsDir, e);
        }
    }

    private void refreshRuntimeForActiveSession() {
        String sessionId = requireActiveSession();
        List<AgentMessage> transcript = sessionStore.loadTranscript(sessionId);
        runtime.replaceMessages(transcript);
        Path workspace = sessionStore.workspaceDir(sessionId);
        runtime.setSystemPrompt(new ProductivitySystemPromptBuilder().buildMainPrompt(workspace, false));
        runtime.setTools(buildTools(sessionId, workspace));
    }

    private List<AgentTool> buildTools(String sessionId, Path workspace) {
        WorkspaceSandbox sandbox = new WorkspaceSandbox(workspace);
        List<AgentTool> tools = new ArrayList<>();
        tools.add(new ReadFileTool(sandbox));
        tools.add(new WriteFileTool(sandbox));
        tools.add(new EditFileTool(sandbox));
        tools.add(new ListDirTool(sandbox));
        tools.add(new ChatHistoryTool(() -> sessionStore.loadTranscript(sessionId)));
        return tools;
    }

    private String requireActiveSession() {
        if (activeSessionId == null || activeSessionId.isBlank()) {
            throw new IllegalStateException("no active session; create or switch session first");
        }
        return activeSessionId;
    }

    private static String newRunId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() {
        runtime.close();
    }
}
