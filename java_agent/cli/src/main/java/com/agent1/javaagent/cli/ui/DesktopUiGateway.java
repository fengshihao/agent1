package com.agent1.javaagent.cli.ui;

import com.agent1.javaagent.brief.BriefChatExport;
import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.log.AgentDataPaths;
import com.agent1.javaagent.log.query.EventLogFilter;
import com.agent1.javaagent.log.query.EventLogQueryService;
import com.agent1.javaagent.log.query.FailedToolCall;
import com.agent1.javaagent.usage.UsageLogSummary;
import com.agent1.javaagent.usage.UsageOverview;
import com.agent1.javaagent.event.AgentEvent;
import com.agent1.javaagent.event.AgentEventListener;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.script.ScriptEngineFactory;
import com.agent1.javaagent.script.ScriptToolBridge;
import com.agent1.javaagent.capability.CapabilitySearchView;
import com.agent1.javaagent.session.FileSessionStore;
import com.agent1.javaagent.mcp.McpServerRecord;
import com.agent1.javaagent.modelcatalog.ModelCatalogService;
import com.agent1.javaagent.modelcatalog.ModelProviderIds;
import com.agent1.javaagent.modelcatalog.RemoteModelOption;
import com.agent1.javaagent.session.ProductivityAgentHost;
import com.agent1.javaagent.session.SessionMeta;
import com.agent1.javaagent.session.SessionSummaryService;
import com.agent1.javaagent.workspace.ArtifactLibrary;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.agent1.javaagent.tool.WorkspaceToolProvider;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 桌面 Web UI 对 Host 的单线程封装（对齐 Android {@code ProductivityAgentGateway}）。
 * {@link #abortActiveRun()} 不走队列，避免被本轮 IO 拖住。
 */
public final class DesktopUiGateway implements Closeable {

    public interface HostFactory {
        ProductivityAgentHost create(AgentRuntimeConfig config);
    }

    private final Path agentRoot;
    private final HostFactory hostFactory;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "desktop-ui-agent");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<AgentRuntimeConfig> config = new AtomicReference<>();
    private final CopyOnWriteArrayList<Consumer<AgentEvent>> eventListeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> activeRunId = new AtomicReference<>();
    private final AtomicReference<String> activeRunSessionId = new AtomicReference<>();

    private volatile ProductivityAgentHost host;
    private final ModelCatalogService modelCatalogService = new ModelCatalogService();

    public DesktopUiGateway(Path agentRoot, HostFactory hostFactory, AgentRuntimeConfig initialConfig) {
        this.agentRoot = agentRoot;
        this.hostFactory = hostFactory;
        this.config.set(initialConfig);
    }

    /** 标准 CLI 装配：Weizhi 可选。 */
    public static DesktopUiGateway createDefault(
        Path agentRoot,
        AgentRuntimeConfig config,
        ScriptEngineFactory scriptEngine,
        long scriptTimeoutMs,
        ScriptToolBridge bridge,
        WorkspaceToolProvider extraTools
    ) {
        return new DesktopUiGateway(
            agentRoot,
            cfg -> new ProductivityAgentHost(
                agentRoot,
                cfg,
                scriptEngine,
                scriptTimeoutMs,
                "",
                bridge,
                extraTools
            ),
            config
        );
    }

    public Path agentRoot() {
        return agentRoot;
    }

    public AgentRuntimeConfig configuration() {
        return config.get();
    }

    public void addEventListener(Consumer<AgentEvent> listener) {
        eventListeners.add(listener);
    }

    public void removeEventListener(Consumer<AgentEvent> listener) {
        eventListeners.remove(listener);
    }

    public String activeRunId() {
        return activeRunId.get();
    }

    public String activeRunSessionId() {
        return activeRunSessionId.get();
    }

    public boolean isRunInProgress() {
        ProductivityAgentHost h = host;
        return h != null && h.isRunInProgress();
    }

    public void abortActiveRun() {
        ProductivityAgentHost h = host;
        if (h != null) {
            h.abortActiveRun();
        }
    }

    public List<SessionMeta> listSessions() {
        return execute(() -> ensureHost().listSessions());
    }

    public String getActiveSessionId() {
        return execute(() -> ensureHost().getActiveSessionId());
    }

    public SessionMeta createSession() {
        return execute(() -> ensureHost().createSession());
    }

    /**
     * 规则化总结当前会话后新建会话；新会话系统提示词附带上一会话摘要（对齐 Web「总结并新开话题」）。
     */
    public SessionRolloverResult rolloverSession(boolean summarize) throws IOException {
        return execute(() -> {
            ProductivityAgentHost agentHost = ensureHost();
            if (agentHost.isRunInProgress()) {
                throw new IllegalStateException("请先停止当前 Run");
            }
            String previousSessionId = agentHost.getActiveSessionId();
            Path summaryPath = null;
            if (summarize && previousSessionId != null && sessionHasConversation(agentHost, previousSessionId)) {
                summaryPath = agentHost.writeSessionSummary();
            }
            SessionMeta created = agentHost.createSession();
            if (summaryPath != null && previousSessionId != null) {
                String markdown = Files.readString(summaryPath, StandardCharsets.UTF_8);
                String supplement = buildCarryoverSupplement(previousSessionId, markdown);
                agentHost.setSessionEnvironmentSupplement(supplement);
                agentHost.switchSession(created.getSessionId());
            }
            return new SessionRolloverResult(
                previousSessionId,
                summaryPath == null ? null : summaryPath.toString(),
                created
            );
        });
    }

    public List<RemoteModelOption> listModels(String providerId, boolean refreshRemote) {
        String provider = providerId == null || providerId.isBlank()
            ? ModelProviderIds.inferProviderId(configuration().getBaseUrl())
            : providerId.trim();
        if (refreshRemote) {
            try {
                return modelCatalogService.fetchPublicModelOptions(provider);
            } catch (IOException ignored) {
                // fall through
            }
        }
        return modelCatalogService.fallbackForProvider(provider);
    }

    public UiConfigStore.UiConfigSnapshot configSnapshot() {
        return UiConfigStore.snapshotFor(agentRoot, configuration());
    }

    public java.util.List<ProductivityAgentHost.RegisteredToolBrief> registeredTools() {
        return execute(() -> ensureHost().registeredTools());
    }

    public Path exportDiagnosticZip() throws IOException {
        return DesktopDiagnosticExport.exportZip(agentRoot, configuration());
    }

    public List<McpServerRecord> listMcpServers() {
        return DesktopMcpService.load(agentRoot);
    }

    public DesktopMcpService.SaveResult saveMcpServers(List<McpServerRecord> servers) {
        return DesktopMcpService.save(agentRoot, servers);
    }

    public String briefTranscript(String sessionId) {
        return execute(() -> {
            ProductivityAgentHost agentHost = ensureHost();
            List<AgentMessage> messages = agentHost.loadTranscript(sessionId);
            String title = agentHost.listSessions().stream()
                .filter(s -> s.getSessionId().equals(sessionId))
                .map(SessionMeta::getTitle)
                .findFirst()
                .orElse("");
            AgentRuntimeConfig cfg = configuration();
            String limits = "contextTurns=" + cfg.getMaxContextTurns()
                + " maxTurnsPerRun=" + cfg.getMaxTurnsPerRun()
                + " maxToolCallsPerRun=" + cfg.getMaxToolCallsPerRun();
            String configError = cfg.getApiKey() == null || cfg.getApiKey().isBlank() ? "api key not configured" : "";
            BriefChatExport.Context context = new BriefChatExport.Context(
                sessionId,
                cfg.getModel(),
                "desktop-web-ui",
                System.getProperty("os.name", ""),
                limits,
                configError
            );
            return BriefChatExport.format(messages, title, context);
        });
    }

    public List<FailedToolCall> listFailedToolCalls(String sessionIdFilter) {
        return execute(() -> {
            Path eventsFile = AgentDataPaths.eventsJsonl(agentRoot);
            if (!Files.isRegularFile(eventsFile)) {
                return List.of();
            }
            EventLogFilter.Builder builder = EventLogFilter.builder();
            if (sessionIdFilter != null && !sessionIdFilter.isBlank()) {
                builder.sessionId(sessionIdFilter.trim());
            }
            return new EventLogQueryService().listFailedToolCalls(eventsFile, builder.build());
        });
    }

    public void switchSession(String sessionId) {
        execute(() -> {
            ensureHost().switchSession(sessionId);
            return null;
        });
    }

    public void deleteSession(String sessionId) {
        execute(() -> {
            ensureHost().deleteSession(sessionId);
            return null;
        });
    }

    public List<AgentMessage> loadTranscript(String sessionId) {
        return execute(() -> ensureHost().loadTranscript(sessionId));
    }

    /** 全 agentRoot 累计用量（events.jsonl）。 */
    public UsageOverview usageOverview() {
        return UsageLogSummary.summarize(AgentDataPaths.eventsJsonl(agentRoot));
    }

    public java.util.List<ArtifactLibrary.ArtifactEntry> listArtifacts() {
        return ArtifactLibrary.listArtifacts(new FileSessionStore(agentRoot).sessionsRoot());
    }

    public java.util.List<DesktopAttachments.ListedFile> listAttachments(String sessionId) {
        return execute(() -> DesktopAttachments.listImports(agentRoot, sessionId));
    }

    public DesktopAttachments.ListedFile importAttachment(String sessionId, String filename, byte[] data) {
        return execute(() -> DesktopAttachments.importBytes(agentRoot, sessionId, filename, data));
    }

    public void deleteAttachment(String sessionId, String workspaceRelativePath) {
        execute(() -> {
            DesktopAttachments.deleteImport(agentRoot, sessionId, workspaceRelativePath);
            return null;
        });
    }

    public DesktopAttachments.ListedFile importArtifactIntoSession(
        String targetSessionId,
        String sourceSessionId,
        String workspaceRelativePath
    ) {
        return execute(() -> {
            Path sessions = new FileSessionStore(agentRoot).sessionsRoot();
            Path src = ArtifactLibrary.resolveWorkspaceFile(sessions, sourceSessionId, workspaceRelativePath);
            if (src == null) {
                throw new IllegalArgumentException("产物不存在");
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(src);
            return DesktopAttachments.importBytes(agentRoot, targetSessionId, src.getFileName().toString(), bytes);
        });
    }

    public java.nio.file.Path resolveWorkspaceFile(String sessionId, String workspaceRelativePath) {
        return execute(() -> {
            Path workspace = new FileSessionStore(agentRoot).workspaceDir(sessionId);
            WorkspaceSandbox sandbox = new WorkspaceSandbox(workspace, agentRoot);
            Path file = sandbox.resolveRead(workspaceRelativePath);
            if (!java.nio.file.Files.isRegularFile(file)) {
                throw new IllegalArgumentException("file not found");
            }
            return file;
        });
    }

    public CapabilitySearchView.Result searchCapabilities(String query, int limit) {
        return execute(() -> ensureHost().searchCapabilities(query, java.util.List.of(), limit));
    }

    /**
     * 在 agent 线程异步启动 Run；事件经 {@link #addEventListener} 广播。
     * 立即返回；调用方勿阻塞等待结束。
     */
    public void submitUserMessage(String sessionId, String text) {
        executor.execute(() -> {
            try {
                ProductivityAgentHost agentHost = ensureHost();
                agentHost.switchSession(sessionId);
                activeRunSessionId.set(sessionId);
                AgentEventListener fanOut = event -> {
                    for (Consumer<AgentEvent> listener : eventListeners) {
                        try {
                            listener.accept(event);
                        } catch (RuntimeException ignored) {
                            // keep broadcasting
                        }
                    }
                };
                AutoCloseable sub = agentHost.runtime().subscribe(fanOut);
                try {
                    String runId = agentHost.runUserMessage(text);
                    activeRunId.set(runId);
                } finally {
                    closeQuietly(sub);
                    activeRunId.set(null);
                    activeRunSessionId.set(null);
                }
            } catch (Exception e) {
                AgentEvent error = new AgentEvent(
                    com.agent1.javaagent.event.AgentEventType.AGENT_ERROR,
                    new com.agent1.javaagent.event.EventPayloads.AgentError(
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()
                    )
                );
                for (Consumer<AgentEvent> listener : eventListeners) {
                    try {
                        listener.accept(error);
                    } catch (RuntimeException ignored) {
                        // keep broadcasting
                    }
                }
                activeRunId.set(null);
                activeRunSessionId.set(null);
            }
        });
    }

    /** 保存 UI 配置并重建 Host（进行中的 Run 会先 abort）。 */
    public AgentRuntimeConfig updateConfig(
        String apiKey,
        String baseUrl,
        String model,
        String providerId,
        int maxContextTurns,
        int maxContextMessages,
        int maxTurnsPerRun,
        int maxToolCallsPerRun,
        String webSearchApiKey,
        String webSearchBaseUrl
    ) throws IOException {
        abortActiveRun();
        UiConfigStore.save(
            agentRoot,
            apiKey,
            baseUrl,
            model,
            providerId,
            maxContextTurns,
            maxContextMessages,
            maxTurnsPerRun,
            maxToolCallsPerRun,
            webSearchApiKey,
            webSearchBaseUrl
        );
        AgentRuntimeConfig next = UiConfigStore.mergeWithEnvironment(agentRoot);
        execute(() -> {
            closeHostLocked();
            config.set(next);
            ensureHost();
            return null;
        });
        return next;
    }

    /** 保存 UI 配置并重建 Host（进行中的 Run 会先 abort）。 */
    public AgentRuntimeConfig updateConfig(String apiKey, String baseUrl, String model) throws IOException {
        UiConfigStore.UiConfigSnapshot snap = UiConfigStore.readSnapshot(agentRoot);
        return updateConfig(
            apiKey,
            baseUrl,
            model,
            snap.providerId(),
            snap.storedMaxContextTurns(),
            snap.storedMaxContextMessages(),
            snap.storedMaxTurnsPerRun(),
            snap.storedMaxToolCallsPerRun(),
            null,
            null
        );
    }

    public record SessionRolloverResult(String previousSessionId, String summaryPath, SessionMeta session) {
    }

    private static boolean sessionHasConversation(ProductivityAgentHost host, String sessionId) {
        for (AgentMessage message : host.loadTranscript(sessionId)) {
            String role = message.getRole();
            if (!AgentMessage.ROLE_USER.equals(role) && !AgentMessage.ROLE_ASSISTANT.equals(role)) {
                continue;
            }
            String content = message.getContent();
            if (content != null && !content.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static String buildCarryoverSupplement(String previousSessionId, String markdown) {
        String clipped = markdown.length() > 3_500 ? markdown.substring(0, 3_500) + "\n…" : markdown;
        return "上一会话（`" + previousSessionId + "`）规则化总结（"
            + SessionSummaryService.SUMMARY_FILE_NAME
            + "），供延续上下文：\n\n"
            + clipped;
    }

    private ProductivityAgentHost ensureHost() {
        ProductivityAgentHost existing = host;
        if (existing != null) {
            return existing;
        }
        ProductivityAgentHost created = hostFactory.create(config.get());
        host = created;
        return created;
    }

    private void closeHostLocked() {
        ProductivityAgentHost h = host;
        host = null;
        if (h != null) {
            try {
                h.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    private <T> T execute(Callable<T> block) {
        try {
            Future<T> future = executor.submit(block);
            return future.get();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause.getMessage(), cause);
        }
    }

    @Override
    public void close() {
        abortActiveRun();
        execute(() -> {
            closeHostLocked();
            return null;
        });
        executor.shutdownNow();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // ignore
        }
    }
}
