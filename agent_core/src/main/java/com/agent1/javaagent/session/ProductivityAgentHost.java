package com.agent1.javaagent.session;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.core.AgentRuntime;
import com.agent1.javaagent.core.AgentStateSnapshot;
import com.agent1.javaagent.core.RunOutcome;
import com.agent1.javaagent.event.AgentEvent;
import com.agent1.javaagent.event.AgentEventType;
import com.agent1.javaagent.event.EventPayloads;
import com.agent1.javaagent.llm.LlmClient;
import com.agent1.javaagent.log.AgentEventJsonlBridge;
import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.log.AgentDataPaths;
import com.agent1.javaagent.log.EventJsonlRetention;
import com.agent1.javaagent.log.EventJsonlWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agent1.javaagent.mcp.McpCapabilitySync;
import com.agent1.javaagent.mcp.McpServerRecord;
import com.agent1.javaagent.mcp.McpServersFile;
import com.agent1.javaagent.log.RunLogContext;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.prompt.DesktopEnvironmentProvider;
import com.agent1.javaagent.prompt.HostEnvironmentProvider;
import com.agent1.javaagent.prompt.ProductivitySystemPromptBuilder;
import com.agent1.javaagent.skill.SkillCatalogSync;
import com.agent1.javaagent.skill.SkillRepository;
import com.agent1.javaagent.tool.anno.AnnotatedTools;
import com.agent1.javaagent.tool.skill.LoadSkillByNameTool;
import com.agent1.javaagent.core.UserMessageTimestampAppender;
import com.agent1.javaagent.run.FileRunStore;
import com.agent1.javaagent.run.RunRecord;
import com.agent1.javaagent.run.RunState;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ChatHistoryTool;
import com.agent1.javaagent.tool.workspace.EditFileTool;
import com.agent1.javaagent.tool.workspace.ListDirTool;
import com.agent1.javaagent.tool.web.FilesDownloadTool;
import com.agent1.javaagent.tool.web.ImageSearchTool;
import com.agent1.javaagent.tool.web.ReadUrlTool;
import com.agent1.javaagent.tool.workspace.ReadFileTool;
import com.agent1.javaagent.script.AgentToolsScriptBridge;
import com.agent1.javaagent.script.MutableScriptToolBridge;
import com.agent1.javaagent.script.ScriptEngineFactory;
import com.agent1.javaagent.script.ScriptToolBridge;
import com.agent1.javaagent.tool.WorkspaceToolProvider;
import com.agent1.javaagent.tool.script.ExecuteScriptTool;
import com.agent1.javaagent.workspace.WorkspaceTmpCleaner;
import com.agent1.javaagent.coach.AgentCoachConfig;
import com.agent1.javaagent.coach.ProductivityCoach;
import com.agent1.javaagent.tool.agent.AskUserTool;
import com.agent1.javaagent.tool.agent.TodoReadTool;
import com.agent1.javaagent.tool.agent.TodoWriteTool;
import com.agent1.javaagent.tool.agent.CapabilitySearchTool;
import com.agent1.javaagent.tool.agent.ListSessionsTool;
import com.agent1.javaagent.capability.CapabilitySearchView;
import com.fasterxml.jackson.databind.JsonNode;
import com.agent1.javaagent.tool.agent.PromoteRequestTool;
import com.agent1.javaagent.tool.web.WebSearchTool;
import com.agent1.javaagent.tool.anno.AnnotatedTools;
import com.agent1.javaagent.tool.media.ImageResizer;
import com.agent1.javaagent.tool.media.MediaInspector;
import com.agent1.javaagent.tool.media.MediaResizeTool;
import com.agent1.javaagent.tool.media.MediaTools;
import com.agent1.javaagent.tool.media.UnavailableImageResizer;
import com.agent1.javaagent.tool.media.UnavailableMediaInspector;
import com.agent1.javaagent.tool.workspace.WriteFileTool;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.agent1.javaagent.llm.openai.OpenAiCompatibleClient;

/**
 * 生产力助手宿主：多会话、Run 落盘、工作区工具与 transcript 同步（01 + 02 MVP）。
 */
public final class ProductivityAgentHost implements Closeable {

    private static final Logger LOG = Logger.getLogger(ProductivityAgentHost.class.getName());

    /**
     * 单次模型 SSE 连接读超时。思考/工具阶段可能超过 2 分钟无 token，120s 会误断（events 里 {@code SSE failed: timeout}）。
     */
    static final Duration LLM_SSE_TIMEOUT = Duration.ofMinutes(5);

    /** tmp 清扫后台单线程（22 号规划：run 收尾异步清，不拖慢回话收口）。 */
    private static final java.util.concurrent.ExecutorService TMP_CLEANER =
        java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "workspace-tmp-cleaner");
            thread.setDaemon(true);
            return thread;
        });

    private final Path agentRoot;
    private final Path projectRoot;
    private final FileSessionStore sessionStore;
    private final FileRunStore runStore;
    private final AgentRuntime runtime;
    private final ScriptEngineFactory scriptEngineFactory;
    private final long executeScriptTimeoutMs;
    private final String scriptPromptAppend;
    private final ScriptToolBridge scriptToolBridge;
    private final WorkspaceToolProvider extraTools;
    /** find_caps 检索时按 Host 平台过滤（android / desktop），不由模型传参。 */
    private final String capabilitySearchPlatform;
    private final AgentRuntimeConfig runtimeConfig;
    private volatile ImageResizer imageResizer = UnavailableImageResizer.INSTANCE;
    private volatile MediaInspector mediaInspector = UnavailableMediaInspector.INSTANCE;
    private volatile String sessionEnvironmentSupplement = "";
    private volatile HostEnvironmentProvider environmentProvider = DesktopEnvironmentProvider.INSTANCE;
    private volatile List<SkillRepository> skillRepositories = List.of();
    private ProductivityCoach productivityCoach;
    private String activeSessionId;

    public ProductivityAgentHost(Path agentRoot, AgentRuntimeConfig config, LlmClient llmClient) {
        this(agentRoot, config, llmClient, null, 0L, "");
    }

    public ProductivityAgentHost(
        Path agentRoot,
        AgentRuntimeConfig config,
        LlmClient llmClient,
        ScriptEngineFactory scriptEngineFactory,
        long executeScriptTimeoutMs,
        String scriptPromptAppend
    ) {
        this(
            agentRoot,
            config,
            llmClient,
            scriptEngineFactory,
            executeScriptTimeoutMs,
            scriptPromptAppend,
            null,
            null,
            null
        );
    }

    public ProductivityAgentHost(
        Path agentRoot,
        AgentRuntimeConfig config,
        LlmClient llmClient,
        ScriptEngineFactory scriptEngineFactory,
        long executeScriptTimeoutMs,
        String scriptPromptAppend,
        ScriptToolBridge scriptToolBridge,
        WorkspaceToolProvider extraTools
    ) {
        this(
            agentRoot,
            config,
            llmClient,
            scriptEngineFactory,
            executeScriptTimeoutMs,
            scriptPromptAppend,
            scriptToolBridge,
            extraTools,
            null
        );
    }

    public ProductivityAgentHost(
        Path agentRoot,
        AgentRuntimeConfig config,
        LlmClient llmClient,
        ScriptEngineFactory scriptEngineFactory,
        long executeScriptTimeoutMs,
        String scriptPromptAppend,
        ScriptToolBridge scriptToolBridge,
        WorkspaceToolProvider extraTools,
        String capabilitySearchPlatform
    ) {
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
        this.projectRoot = AgentDataPaths.projectRoot(this.agentRoot);
        AgentHomeBootstrap.ensure(this.agentRoot);
        SkillCatalogSync.refreshAsync(this.agentRoot);
        EventJsonlRetention.pruneOnStartup(AgentDataPaths.eventsJsonl(this.agentRoot), new ObjectMapper());
        this.sessionStore = new FileSessionStore(this.agentRoot);
        this.runStore = new FileRunStore(sessionStore);
        this.scriptEngineFactory = scriptEngineFactory;
        this.executeScriptTimeoutMs = executeScriptTimeoutMs;
        this.scriptPromptAppend = scriptPromptAppend == null ? "" : scriptPromptAppend.trim();
        this.scriptToolBridge = scriptToolBridge;
        this.extraTools = extraTools;
        this.capabilitySearchPlatform = normalizeCapabilitySearchPlatform(capabilitySearchPlatform);
        this.runtimeConfig = config;
        this.runtime = new AgentRuntime(
            config.toAgentOptionsBuilder("")
                .transformContext(new UserMessageTimestampAppender())
                .tools(List.of())
                .build(),
            llmClient
        );
    }

    /** CLI 用：按配置构造 OpenAI 兼容客户端。 */
    public ProductivityAgentHost(Path agentRoot, AgentRuntimeConfig config) {
        this(
            agentRoot,
            config,
            new OpenAiCompatibleClient(config.toOpenAiCompatibleConfig(LLM_SSE_TIMEOUT, 0.2))
        );
    }

    /** CLI 用：可选 Weizhi 脚本引擎。 */
    public ProductivityAgentHost(
        Path agentRoot,
        AgentRuntimeConfig config,
        ScriptEngineFactory scriptEngineFactory,
        long executeScriptTimeoutMs,
        String scriptPromptAppend
    ) {
        this(
            agentRoot,
            config,
            new OpenAiCompatibleClient(config.toOpenAiCompatibleConfig(LLM_SSE_TIMEOUT, 0.2)),
            scriptEngineFactory,
            executeScriptTimeoutMs,
            scriptPromptAppend
        );
    }

    /** CLI / Android：Weizhi 工具环与脚本 {@code $tools} 桥。 */
    public ProductivityAgentHost(
        Path agentRoot,
        AgentRuntimeConfig config,
        ScriptEngineFactory scriptEngineFactory,
        long executeScriptTimeoutMs,
        String scriptPromptAppend,
        ScriptToolBridge scriptToolBridge,
        WorkspaceToolProvider extraTools
    ) {
        this(
            agentRoot,
            config,
            scriptEngineFactory,
            executeScriptTimeoutMs,
            scriptPromptAppend,
            scriptToolBridge,
            extraTools,
            null
        );
    }

    /** CLI / Android：同上，并指定 find_caps 的平台过滤（如 {@code android}）。 */
    public ProductivityAgentHost(
        Path agentRoot,
        AgentRuntimeConfig config,
        ScriptEngineFactory scriptEngineFactory,
        long executeScriptTimeoutMs,
        String scriptPromptAppend,
        ScriptToolBridge scriptToolBridge,
        WorkspaceToolProvider extraTools,
        String capabilitySearchPlatform
    ) {
        this(
            agentRoot,
            config,
            new OpenAiCompatibleClient(config.toOpenAiCompatibleConfig(LLM_SSE_TIMEOUT, 0.2)),
            scriptEngineFactory,
            executeScriptTimeoutMs,
            scriptPromptAppend,
            scriptToolBridge,
            extraTools,
            capabilitySearchPlatform
        );
    }

    /** Android 传入 {@code Bitmap} 实现。未设置时 {@code media_resize} 返回未实现。 */
    public void setImageResizer(ImageResizer imageResizer) {
        this.imageResizer = imageResizer == null ? UnavailableImageResizer.INSTANCE : imageResizer;
    }

    /** Android 传入系统媒体实现。未设置时探测和抽帧返回未实现。 */
    public void setMediaInspector(MediaInspector mediaInspector) {
        this.mediaInspector = mediaInspector == null ? UnavailableMediaInspector.INSTANCE : mediaInspector;
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

    /**
     * 加载会话 transcript 并切换为活动会话（与 Android Gateway {@code loadTranscript} 语义一致）。
     * 返回 runtime 当前消息快照（含系统提示词刷新后的上下文）。
     */
    public List<AgentMessage> loadTranscript(String sessionId) {
        switchSession(sessionId);
        return List.copyOf(runtime.getStateSnapshot().getMessages());
    }

    /** 宿主注入的环境摘要（如 Android 会话可访问文件列表），在 {@link #refreshRuntimeForActiveSession} 时写入系统提示词。 */
    public void setSessionEnvironmentSupplement(String supplement) {
        this.sessionEnvironmentSupplement = supplement == null ? "" : supplement.trim();
    }

    /**
     * 系统提示词「当前环境状态」段的信息来源（平台 / JS 运行环境 / 目录结构及只读/可写标注）。
     * CLI 用桌面默认实现；Android 注入自己的实现。传 null 恢复默认。
     */
    public void setEnvironmentProvider(HostEnvironmentProvider provider) {
        this.environmentProvider = provider == null
            ? DesktopEnvironmentProvider.INSTANCE
            : provider;
    }

    /**
     * 宿主特有的 skill 源（如 Android assets），插入 {@code load_skill} 统一优先级表
     * 的 workspace 与 skills-home 之间。传 null 清空。
     */
    public void setSkillRepositories(List<SkillRepository> repositories) {
        this.skillRepositories = repositories == null ? List.of() : List.copyOf(repositories);
    }

    /** 用户通过 UI 添加附件等：写入 transcript 并刷新 runtime，不触发 LLM Run。 */
    public void appendUserMessageToTranscript(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("message text required");
        }
        String sessionId = requireActiveSession();
        sessionStore.appendMessage(sessionId, "ui", AgentMessage.user(text));
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

    /** 7.5：规则化写入 {@code sessions/<id>/session.summary.md}。 */
    public Path writeSessionSummary() throws IOException {
        RunLogContext ctx = new RunLogContext(
            "cli",
            "summarize-" + Instant.now().toEpochMilli(),
            "",
            "cli"
        );
        return new SessionSummaryService(agentRoot).writeSummary(requireActiveSession(), ctx);
    }

    public AgentRuntime runtime() {
        return runtime;
    }

    /**
     * 与模型调用 {@code find_caps} 相同的 query / kinds / limit 和正文。
     * 进行中的 Run 不刷新 MCP 索引，避免和当轮写入抢同一份库。
     */
    public CapabilitySearchView.Result searchCapabilities(String query, List<String> kinds, int limit) {
        if (!runtime.isRunning()) {
            refreshMcpIndex();
        }
        return CapabilitySearchView.search(agentRoot, capabilitySearchPlatform, projectRoot, query, kinds, limit);
    }

    /** 当前会话已经装进 runtime 的系统提示词；未切换会话时为空。 */
    public String activeSystemPrompt() {
        return runtime.getStateSnapshot().getSystemPrompt();
    }

    /** 当前会话已注册工具的名称、说明和参数 schema，与发给模型的工具列表一致。 */
    public List<RegisteredToolBrief> registeredTools() {
        List<RegisteredToolBrief> out = new ArrayList<>();
        for (AgentTool tool : runtime.getTools()) {
            JsonNode schema = tool.parametersSchema();
            String schemaText = schema == null || schema.isNull() ? "" : schema.toPrettyString();
            String description = tool.description() == null ? "" : tool.description();
            out.add(new RegisteredToolBrief(tool.name(), description, schemaText));
        }
        return List.copyOf(out);
    }

    public record RegisteredToolBrief(String name, String description, String parametersSchema) {
    }

    public boolean isRunInProgress() {
        return runtime.isRunning();
    }

    /** 停止当前 Run（工具返回后生效；进行中的模型请求会被取消）。 */
    public void abortActiveRun() {
        runtime.abort();
        runtime.waitForIdle();
    }

    /**
     * 处理一条用户消息：新建 Run、写事件、同步 transcript、更新 Run 终态。
     */
    public String runUserMessage(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("message text required");
        }
        String sessionId = requireActiveSession();
        applySystemPrompt(sessionId);
        recoverInterruptedRuns(sessionId);
        stripTrailingRunFailures(sessionId);
        assertNoRunningMainRun(sessionId);
        if (productivityCoach != null) {
            productivityCoach.resetRun();
        }

        String runId = newRunId();
        String now = Instant.now().toString();
        RunRecord running = new RunRecord(runId, sessionId, RunState.RUNNING, now, now, null);
        runStore.write(running);

        RunLogContext logContext = new RunLogContext(sessionId, runId, "", "main");
        runtime.setRunAuditBinding(agentRoot, logContext);
        AgentEventJsonlBridge bridge = new AgentEventJsonlBridge(logContext, AgentDataPaths.eventsJsonl(agentRoot));
        bridge.setDeferRunTerminal(true);
        writeWorkspaceSnapshot(logContext, sessionId, "start");
        AutoCloseable bridgeSubscription = runtime.subscribe(bridge); // NOPMD CloseResource — finally 中 closeQuietly

        int[] messageCountBefore = {runtime.getStateSnapshot().getMessages().size()};
        int[] completedTurns = {0};
        RunRecord[] runningRef = {running};

        AutoCloseable checkpointSubscription = runtime.subscribe(event -> onRunCheckpoint( // NOPMD CloseResource — finally 中 closeQuietly
            event,
            sessionId,
            runId,
            messageCountBefore,
            completedTurns,
            runningRef
        ));

        RunState terminal = RunState.SUCCEEDED;
        String lastError = null;
        RuntimeException toThrow = null;

        try {
            runtime.prompt(text.trim()).join();
            runtime.waitForIdle();
            AgentStateSnapshot snapshot = runtime.getStateSnapshot();
            lastError = snapshot.getError();
            terminal = resolveTerminal(lastError);
        } catch (Exception e) {
            toThrow = e instanceof RuntimeException re ? re : new RuntimeException(e);
            AgentStateSnapshot snapshot = runtime.getStateSnapshot();
            lastError = snapshot.getError();
            if (lastError == null || lastError.isBlank()) {
                lastError = toThrow.getMessage();
            }
            terminal = resolveTerminal(lastError);
            if (terminal == RunState.SUCCEEDED) {
                terminal = RunState.FAILED;
            }
        } finally {
            AgentStateSnapshot snapshot = runtime.getStateSnapshot();
            List<AgentMessage> beforeRepair = snapshot.getMessages();
            List<AgentMessage> afterRun = TranscriptPairingPersistence.repairInMemory(beforeRepair);
            runtime.replaceMessages(afterRun);
            if (TranscriptPairingPersistence.persistIfRepaired(sessionStore, sessionId, beforeRepair, afterRun)) {
                messageCountBefore[0] = afterRun.size();
            } else {
                persistNewMessages(sessionId, runId, messageCountBefore[0], afterRun);
            }
            bridge.writeRunTerminal(terminal, lastError, snapshot.getMessages().size());
            runStore.write(
                runningRef[0].withTerminal(terminal, Instant.now().toString(), lastError)
            );
            writeWorkspaceSnapshot(logContext, sessionId, "end");
            scheduleTmpClean(logContext, sessionId);
            closeQuietly(checkpointSubscription);
            closeQuietly(bridgeSubscription);
            runtime.clearRunAuditBinding();
            applySystemPrompt(sessionId);
        }
        if (toThrow != null) {
            throw toThrow;
        }
        return runId;
    }

    private void onRunCheckpoint(
        AgentEvent event,
        String sessionId,
        String runId,
        int[] messageCountBefore,
        int[] completedTurns,
        RunRecord[] runningRef
    ) {
        if (event.getType() != AgentEventType.TURN_END) {
            return;
        }
        if (!(event.getPayload() instanceof EventPayloads.TurnEnd)) {
            return;
        }
        completedTurns[0] += 1;
        AgentStateSnapshot snapshot = runtime.getStateSnapshot();
        persistNewMessages(sessionId, runId, messageCountBefore[0], snapshot.getMessages());
        messageCountBefore[0] = snapshot.getMessages().size();
        String now = Instant.now().toString();
        runningRef[0] = runningRef[0].withCheckpoint(
            completedTurns[0],
            snapshot.getMessages().size(),
            now
        );
        runStore.write(runningRef[0]);
    }

    /**
     * workspace 快照台账事件（22 号规划 T3）：run 起止各一次，聚合文件数/总字节/tmp 字节，
     * 供产物库「本会话占用 X MB、可清理 Y MB」类展示；IO 失败静默（不打断 run）。
     */
    private void writeWorkspaceSnapshot(RunLogContext logContext, String sessionId, String phase) {
        try {
            WorkspaceTmpCleaner.SnapshotStats stats =
                WorkspaceTmpCleaner.snapshot(sessionStore.workspaceDir(sessionId));
            new EventJsonlWriter(AgentDataPaths.eventsJsonl(agentRoot)).write(
                logContext,
                "workspace_snapshot",
                WorkspaceTmpCleaner.snapshotEventFields(phase, stats)
            );
        } catch (RuntimeException e) {
            LOG.warning("workspace snapshot failed: " + e.getMessage());
        }
    }

    /**
     * run 收尾后台清扫 tmp（22 号规划 T1/T2）：代管前缀 + 年龄窗口 + 配额 LRU，
     * 删了东西才写 {@code tmp_cleaned} audit 事件；清扫不阻塞回话收口。
     */
    private void scheduleTmpClean(RunLogContext logContext, String sessionId) {
        Path workspace = sessionStore.workspaceDir(sessionId);
        Path root = agentRoot;
        TMP_CLEANER.execute(() -> {
            try {
                WorkspaceTmpCleaner.CleanResult result = WorkspaceTmpCleaner.clean(
                    workspace, WorkspaceTmpCleaner.DEFAULT_MIN_AGE, WorkspaceTmpCleaner.DEFAULT_QUOTA_BYTES);
                if (result.removedCount > 0) {
                    new EventJsonlWriter(AgentDataPaths.eventsJsonl(root)).write(
                        logContext,
                        "tmp_cleaned",
                        java.util.Map.of(
                            "removedCount", result.removedCount,
                            "removedBytes", result.removedBytes,
                            "quotaTrimmed", result.quotaTrimmed
                        )
                    );
                }
            } catch (RuntimeException e) {
                LOG.warning("workspace tmp clean failed: " + e.getMessage());
            }
        });
    }

    private static RunState resolveTerminal(String error) {
        if (error == null || error.isBlank()) {
            return RunState.SUCCEEDED;
        }
        if (RunOutcome.isCancelled(error)) {
            return RunState.CANCELLED;
        }
        if (RunOutcome.isPaused(error) || RunOutcome.isToolBudget(error)) {
            return RunState.PAUSED;
        }
        if (RunOutcome.isWaitingUser(error)) {
            return RunState.WAITING_USER;
        }
        return RunState.FAILED;
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

    /**
     * 兜底自愈（24 号真机问题）：run 因网络 / SSE / API 等原因失败时，AgentRuntime 会把
     * 「错误: …」失败记录落进 transcript。若用户点「继续」重试又失败，记录会一条条堆积，
     * 上下文被本地错误污染。新 run 启动前，把尾部连续的失败记录、及其紧邻的重试指令
     * 「继续」一并清除并写回 transcript——发给模型的是干净上下文，重试等于从头再来一次。
     */
    private void stripTrailingRunFailures(String sessionId) {
        List<AgentMessage> current = runtime.getStateSnapshot().getMessages();
        int cut = current.size();
        boolean droppedFailure = false;
        while (cut > 0) {
            AgentMessage tail = current.get(cut - 1);
            if (RunOutcome.isRunFailureRecord(tail)) {
                cut -= 1;
                droppedFailure = true;
                continue;
            }
            if (droppedFailure
                && AgentMessage.ROLE_USER.equals(tail.getRole())
                && RunOutcome.CONTINUE_PROMPT.equals(tail.getContent().trim())) {
                cut -= 1;
                continue;
            }
            break;
        }
        if (!droppedFailure) {
            return;
        }
        List<AgentMessage> cleaned = new ArrayList<>(current.subList(0, cut));
        runtime.replaceMessages(cleaned);
        TranscriptPairingPersistence.persistIfRepaired(sessionStore, sessionId, current, cleaned);
        LOG.info("stripped " + (current.size() - cut) + " trailing run-failure record(s) before new run");
    }

    /**
     * 进程被系统杀掉时，进行中的 Run 会永远停在 {@code running}，下一条消息会被拒绝。
     * 当前进程并没有在跑这条 Run 时，把它收成失败，让用户可以继续发消息。
     */
    private void recoverInterruptedRuns(String sessionId) {
        if (runtime.isRunning()) {
            return;
        }
        Path runsDir = sessionStore.sessionDir(sessionId).resolve("runs");
        if (!java.nio.file.Files.isDirectory(runsDir)) {
            return;
        }
        String now = Instant.now().toString();
        try (var stream = java.nio.file.Files.list(runsDir)) {
            for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                String runId = name.substring(0, name.length() - ".json".length());
                runStore.readOptional(sessionId, runId).ifPresent(record -> {
                    if (record.getState() == RunState.RUNNING) {
                        runStore.write(record.withTerminal(
                            RunState.FAILED,
                            now,
                            "上次运行因进程中断未结束"
                        ));
                    }
                });
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("scan runs failed: " + runsDir, e);
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
        List<AgentMessage> transcript = TranscriptPairingPersistence.loadRepaired(sessionStore, sessionId);
        runtime.replaceMessages(transcript);
        Path workspace = sessionStore.workspaceDir(sessionId);
        applySystemPrompt(sessionId);
        runtime.setTools(buildTools(sessionId, workspace));
        runtime.setWorkspaceSandbox(new WorkspaceSandbox(workspace, agentRoot));
        AgentCoachConfig coachConfig = AgentCoachConfig.load(agentRoot);
        productivityCoach = coachConfig.enabled() ? coachConfig.toCoach() : null;
        runtime.setProductivityCoach(productivityCoach);
        refreshMcpIndex();
    }

    private Set<String> enabledMcpServerNames() {
        Set<String> names = new LinkedHashSet<>();
        for (McpServerRecord server : McpServersFile.load(agentRoot)) {
            if (server.enabled() && server.name() != null && !server.name().isBlank()) {
                names.add(server.name());
            }
        }
        return Set.copyOf(names);
    }

    /** 配置了 MCP 时，把工具名写入 find_caps。缓存有效则不访问网络。 */
    private void refreshMcpIndex() {
        try {
            McpCapabilitySync.ensureIndexed(agentRoot);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "mcp capability index skipped", e);
        }
    }

    private List<AgentTool> buildTools(String sessionId, Path workspace) {
        WorkspaceSandbox sandbox = new WorkspaceSandbox(workspace, agentRoot);
        List<AgentTool> tools = new ArrayList<>();
        tools.add(new ReadFileTool(sandbox));
        tools.add(new ReadUrlTool());
        tools.add(new WriteFileTool(sandbox));
        tools.addAll(AnnotatedTools.from(new MediaResizeTool(sandbox, imageResizer)));
        tools.addAll(AnnotatedTools.from(new MediaTools(sandbox, mediaInspector)));
        tools.add(new EditFileTool(sandbox));
        tools.add(new ListDirTool(sandbox));
        tools.add(new PromoteRequestTool(agentRoot, workspace));
        tools.add(new ListSessionsTool(sessionStore, this::getActiveSessionId));
        tools.add(new ChatHistoryTool(() -> sessionStore.loadTranscript(sessionId)));
        tools.add(new AskUserTool());
        tools.add(new TodoWriteTool(sessionStore.sessionDir(sessionId), agentRoot));
        tools.add(new TodoReadTool(sessionStore.sessionDir(sessionId)));
        tools.add(new CapabilitySearchTool(
            agentRoot,
            capabilitySearchPlatform,
            projectRoot,
            false,
            runtimeConfig.isWebSearchConfigured()
        ));
        LoadSkillByNameTool loadSkill = new LoadSkillByNameTool(
            agentRoot,
            projectRoot,
            workspace,
            skillRepositories
        );
        loadSkill.setOptionalCapabilities(
            runtimeConfig.isWebSearchConfigured(),
            enabledMcpServerNames()
        );
        tools.addAll(AnnotatedTools.from(loadSkill));
        if (runtimeConfig.isWebSearchConfigured()) {
            tools.add(new WebSearchTool(
                runtimeConfig.getWebSearchApiKey(),
                runtimeConfig.getWebSearchBaseUrl()
            ));
            tools.add(new ImageSearchTool(
                runtimeConfig.getWebSearchApiKey(),
                runtimeConfig.getWebSearchBaseUrl()
            ));
        }
        tools.add(new FilesDownloadTool(sandbox));
        if (extraTools != null) {
            List<AgentTool> extra = extraTools.toolsFor(sandbox);
            if (extra != null && !extra.isEmpty()) {
                Set<String> names = new HashSet<>();
                for (AgentTool t : tools) {
                    names.add(t.name());
                }
                for (AgentTool t : extra) {
                    if (!names.add(t.name())) {
                        continue;
                    }
                    tools.add(t);
                }
            }
        }
        if (scriptEngineFactory != null) {
            tools.add(new ExecuteScriptTool(sandbox, scriptEngineFactory, executeScriptTimeoutMs, agentRoot));
        }
        if (scriptToolBridge instanceof MutableScriptToolBridge mutable) {
            mutable.set(new AgentToolsScriptBridge(tools));
        }
        return ProductivityLlmToolSurface.forLlm(tools);
    }

    private void applySystemPrompt(String sessionId) {
        Path workspace = sessionStore.workspaceDir(sessionId);
        boolean scriptTool = scriptEngineFactory != null;
        String prompt = new ProductivitySystemPromptBuilder()
            .hostAppend(scriptPromptAppend)
            .skillCatalogSection(SkillCatalogSync.promptSection(agentRoot))
            .webSearch(runtimeConfig.isWebSearchConfigured())
            .runLimits(runtimeConfig.getMaxTurnsPerRun(), runtimeConfig.getMaxToolCallsPerRun())
            .buildMainPrompt(
                workspace,
                agentRoot,
                scriptTool,
                scriptToolBridge != null,
                sessionEnvironmentSupplement,
                environmentProvider
            );
        runtime.setSystemPrompt(prompt);
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

    private static String normalizeCapabilitySearchPlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return "desktop";
        }
        return platform.trim().toLowerCase(java.util.Locale.ROOT);
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
