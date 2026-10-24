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

    /** 未指定 {@code timeout_ms} 时宿主默认脚本预算（2 分钟）。 */
    public static final long DEFAULT_TIMEOUT_MS = 120_000L;
    /** {@code timeout_ms} 与 {@code AGENT1_SCRIPT_TIMEOUT_MS} 可请求的上限（10 分钟）。 */
    public static final long MAX_TIMEOUT_MS = 600_000L;
    public static final long MIN_TIMEOUT_MS = 1_000L;

    /**
     * 工具说明（单一事实来源）：运行环境、路径与 import 细则都在这里，
     * 系统提示词只保留「一段脚本做完一件事」的编排原则。
     */
    public static final String DESCRIPTION = """
        在 workspace 跑 QuickJS。不是 Node，不是浏览器，没有 document/window，不能 require。
        code 与 file 二选一。短一次性脚本用 code。超过 20 行或 1000 字符时，运行时会把 code 写入 jobs/ 再执行，并在结果里给出路径；后续用 edit_file 改该文件，再用 file。编排入口用 file（如 jobs/run.js）。
        可以顶层 await。返回值是最后一条表达式，不要写顶层 return。
        文件与路径：读写用全局 fs，或外层 read_file / write_file / edit_file；fs 与 workspace 内 import 可用相对或绝对路径，须在 workspace 根下（引擎归一化）；./ 只表示与当前脚本同目录的 workspace 文件。
        官方能力用 import … from "weizhi:<name>"，按 find_caps 里的示例写；宿主在执行前自动安装缺失的包。宿主系统能力（平台 API）只在脚本里，path 仅相对路径。不要 glob 或 list_dir 去找脚本库。
        MCP：await $mcp.服务器.工具({...})，名字来自 find_caps 的 entry。
        外层工具：await $tools.工具名({...})。已注册时可用 $tools.webview_exec 进入浏览器环境（与外层同名工具相同）。不能再调 run_js。
        批量搜图/下载：同一段 run_js 里 await $tools.images_download({ items:[{url}], dir, name_prefix })，回执是直接对象（如 r.items），不要 JSON.stringify；不要 bash cp 整理。
        可选 timeout_ms（毫秒，1000–600000）：未指定则用宿主默认（约 2 分钟）；长批量可显式加大，上限 10 分钟。
        """.trim();

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
        this.defaultTimeoutMs = defaultTimeoutMs > 0 ? defaultTimeoutMs : DEFAULT_TIMEOUT_MS;
        this.agentRoot = agentRoot == null ? null : agentRoot.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "run_js";
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public long suggestedTimeoutMs(JsonNode parameters, long fallbackMs) {
        return resolveTimeoutMs(parameters, defaultTimeoutMs > 0 ? defaultTimeoutMs : fallbackMs);
    }

    /**
     * 解析本次脚本超时：参数 {@code timeout_ms} 优先，否则宿主 {@code defaultTimeoutMs}，封顶 {@link #MAX_TIMEOUT_MS}。
     */
    static long resolveTimeoutMs(JsonNode parameters, long configuredDefault) {
        long base = configuredDefault > 0 ? configuredDefault : DEFAULT_TIMEOUT_MS;
        if (parameters == null || parameters.isMissingNode() || !parameters.has("timeout_ms")) {
            return Math.min(base, MAX_TIMEOUT_MS);
        }
        JsonNode node = parameters.get("timeout_ms");
        long requested = -1;
        if (node != null && node.canConvertToLong()) {
            requested = node.asLong();
        } else if (node != null && node.isTextual()) {
            try {
                requested = Long.parseLong(node.asText("").trim());
            } catch (NumberFormatException ignored) {
                requested = -1;
            }
        }
        if (requested <= 0) {
            return Math.min(base, MAX_TIMEOUT_MS);
        }
        return Math.min(Math.max(requested, MIN_TIMEOUT_MS), MAX_TIMEOUT_MS);
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
        properties.set(
            "timeout_ms",
            MAPPER.createObjectNode()
                .put("type", "integer")
                .put(
                    "description",
                    "Optional script timeout in ms (1000–600000). Default is host budget (~2 min); raise for batch $tools.images_download."
                )
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
                "错误：run_js 的 file 必须是 workspace 内的 .js/.mjs/.cjs 脚本。"
                    + " 读取 SVG/图片/数据文件请用 read_file；SVG 转 PNG/JPG 请 find_caps「svg png」，"
                    + "再在 .js 里 import { svgToImage } from \"weizhi:svg-raster\"。"
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

        String spilledFile = null;
        if (!hasFile && InlineScriptSpill.exceeds(source)) {
            try {
                spilledFile = InlineScriptSpill.write(sandbox, source);
            } catch (IOException e) {
                return ToolExecutionResult.text("错误：inline 过长，写入 jobs/ 失败: " + e.getMessage());
            }
        }
        final String boundFile = hasFile ? file : spilledFile;

        String prelude = buildArgsPrelude(parameters.get("args"));
        long timeoutMs = resolveTimeoutMs(parameters, defaultTimeoutMs);
        ScriptEvalFrame.SourceKind kind = boundFile == null
            ? ScriptEvalFrame.SourceKind.INLINE
            : ScriptEvalFrame.SourceKind.FILE;

        Path workspaceRoot = sandbox.getRoot();

        ScriptEvalFrame frame;
        String autoInstallPrefix = "";
        for (int attempt = 0; attempt < 2; attempt++) {
            try (ScriptEngine engine = engineFactory.open(workspaceRoot)) {
                frame = new ScriptEvalFrame(
                    kind,
                    boundFile == null ? "" : boundFile,
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
                        timeoutMs,
                        cancellationToken,
                        boundFile
                    );
                    String text = json == null ? "null" : json;
                    if (!autoInstallPrefix.isEmpty()) {
                        text = autoInstallPrefix + text;
                    }
                    return ToolExecutionResult.text(withSpillNotice(text, spilledFile));
                } catch (RuntimeException e) {
                    String failText = ScriptFailureFormatter.formatJson(frame, e);
                    failText = withSpillNotice(failText, spilledFile);
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

    private static String withSpillNotice(String text, String spilledFile) {
        if (spilledFile == null) {
            return text;
        }
        return text + InlineScriptSpill.notice(spilledFile);
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
