package com.agent1.javaagent.tool.script;

import com.agent1.javaagent.catalog.AgentCatalogPaths;
import com.agent1.javaagent.coach.CatalogMissingNativeHints;
import com.agent1.javaagent.catalog.sync.CatalogSyncService;
import com.agent1.javaagent.log.AgentAuditEvents;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.ScriptEngine;
import com.agent1.javaagent.script.ScriptEngineFactory;
import com.agent1.javaagent.script.ScriptEvalFrame;
import com.agent1.javaagent.script.ScriptFailureFormatter;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.agent1.javaagent.util.PathIo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExecuteScriptTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkspaceSandbox sandbox;
    private final ScriptEngineFactory engineFactory;
    private final long defaultTimeoutMs;
    private final Path agentRoot;

    public ExecuteScriptTool(
        WorkspaceSandbox sandbox,
        ScriptEngineFactory engineFactory,
        long defaultTimeoutMs
    ) {
        this(sandbox, engineFactory, defaultTimeoutMs, null);
    }

    public ExecuteScriptTool(
        WorkspaceSandbox sandbox,
        ScriptEngineFactory engineFactory,
        long defaultTimeoutMs,
        Path agentRoot
    ) {
        this.sandbox = sandbox;
        this.engineFactory = engineFactory;
        this.defaultTimeoutMs = defaultTimeoutMs > 0 ? defaultTimeoutMs : 600_000L;
        this.agentRoot = agentRoot == null ? null : agentRoot.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "execute_script";
    }

    @Override
    public String description() {
        return "Run JavaScript in the session workspace sandbox (Weizhi). Provide code or a workspace-relative file path.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set(
            "code",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Inline JavaScript source. Mutually exclusive with file.")
        );
        properties.set(
            "file",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Workspace-relative .js file to run. Mutually exclusive with code.")
        );
        properties.set(
            "args",
            MAPPER.createObjectNode()
                .put("type", "array")
                .put("description", "Optional JSON array passed to the script as globalThis.__agentArgs.")
        );
        schema.set("properties", properties);
        return schema;
    }

    @Override
    public ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) {
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }

        String code = parameters.path("code").asText("").trim();
        String file = parameters.path("file").asText("").trim();
        boolean hasCode = !code.isEmpty();
        boolean hasFile = !file.isEmpty();
        if (hasCode && hasFile) {
            return ToolExecutionResult.text("错误：code 与 file 只能二选一");
        }
        if (!hasCode && !hasFile) {
            return ToolExecutionResult.text("错误：必须提供 code 或 file");
        }

        if (hasFile && !looksLikeJavaScriptFile(file)) {
            return ToolExecutionResult.text(
                "错误：execute_script 的 file 必须是 workspace 内的 .js/.mjs/.cjs 脚本。"
                    + " 读取 SVG/图片/数据文件请用 read_file；需要 DOM/canvas/SVG 栅格化请用 webview_exec（input_path 传入文件）。"
            );
        }

        final String source;
        if (hasFile) {
            final Path resolved;
            try {
                resolved = sandbox.resolveWrite(file);
            } catch (SecurityException e) {
                return ToolExecutionResult.text("错误：路径超出工作区范围: " + file);
            }
            try {
                source = PathIo.readString(resolved);
            } catch (IOException e) {
                return ToolExecutionResult.text("错误：无法读取脚本文件 " + file + ": " + e.getMessage());
            }
        } else {
            source = code;
        }

        String prelude = buildArgsPrelude(parameters.get("args"));
        ScriptEvalFrame.SourceKind kind = hasFile ? ScriptEvalFrame.SourceKind.FILE : ScriptEvalFrame.SourceKind.INLINE;

        Path workspaceRoot = sandbox.getRoot();

        ScriptEvalFrame frame;
        String autoInstallPrefix = "";
        for (int attempt = 0; attempt < 2; attempt++) {
            try (ScriptEngine engine = engineFactory.open(workspaceRoot)) {
                frame = new ScriptEvalFrame(
                    kind,
                    hasFile ? file : "",
                    ScriptEvalFrame.countPreludeLines(prelude),
                    engine.agentHostPreludeLines(),
                    ScriptEvalFrame.countLines(source)
                );
                AtomicBoolean cancelSent = new AtomicBoolean(false);
                Thread watcher = startCancelWatcher(engine, cancellationToken, cancelSent);
                try {
                    String json = engine.evalForAgent(
                        source,
                        prelude,
                        defaultTimeoutMs,
                        cancellationToken,
                        hasFile ? file : null
                    );
                    String text = json == null ? "null" : json;
                    if (!autoInstallPrefix.isEmpty()) {
                        text = autoInstallPrefix + text;
                    }
                    return ToolExecutionResult.text(text);
                } catch (RuntimeException e) {
                    String failText = ScriptFailureFormatter.formatJson(frame, e);
                    if (attempt == 0 && tryAutoInstallNative(source, failText)) {
                        autoInstallPrefix = "[catalog] auto-installed native plugin for ensureNative\n\n";
                        continue;
                    }
                    return ToolExecutionResult.text(failText);
                } finally {
                    cancelSent.set(true);
                    watcher.interrupt();
                    try {
                        watcher.join(2_000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        return ToolExecutionResult.text("错误：脚本执行失败（auto-install 重试后仍失败）");
    }

    private boolean tryAutoInstallNative(String source, String failText) {
        if (agentRoot == null || !CatalogMissingNativeHints.looksLikeMissingNative(failText)) {
            return false;
        }
        String plugin = CatalogMissingNativeHints.resolvePluginName(source, failText);
        if (plugin.isBlank()) {
            return false;
        }
        try {
            CatalogSyncService service = new CatalogSyncService(agentRoot);
            CatalogSyncService.SyncApplyResult apply = service.applyNativePlugin(plugin);
            Path manifest = AgentCatalogPaths.nativePluginsDir(agentRoot)
                .resolve(plugin.trim())
                .resolve("manifest.json");
            if (!Files.isRegularFile(manifest)) {
                return false;
            }
            AgentAuditEvents.catalogNativeAutoInstalled(
                agentRoot,
                null,
                plugin,
                apply.appliedIds() == null ? List.of() : apply.appliedIds()
            );
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean looksLikeJavaScriptFile(String file) {
        String normalized = file.trim().replace('\\', '/').toLowerCase();
        return normalized.endsWith(".js")
            || normalized.endsWith(".mjs")
            || normalized.endsWith(".cjs");
    }

    private static String buildArgsPrelude(JsonNode argsNode) {
        if (argsNode == null || argsNode.isNull() || !argsNode.isArray()) {
            return "";
        }
        try {
            return "globalThis.__agentArgs = " + MAPPER.writeValueAsString(argsNode) + ";";
        } catch (IOException e) {
            return "";
        }
    }

    private static Thread startCancelWatcher(
        ScriptEngine engine,
        CancellationToken token,
        AtomicBoolean done
    ) {
        Thread t = new Thread(() -> {
            while (!done.get() && !Thread.currentThread().isInterrupted()) {
                if (token.isCancelled()) {
                    engine.cancel();
                    return;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "execute-script-cancel");
        t.setDaemon(true);
        t.start();
        return t;
    }
}
