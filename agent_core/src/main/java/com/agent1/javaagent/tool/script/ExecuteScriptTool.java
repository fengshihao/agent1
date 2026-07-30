package com.agent1.javaagent.tool.script;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.ScriptEngine;
import com.agent1.javaagent.script.ScriptEngineFactory;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExecuteScriptTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkspaceSandbox sandbox;
    private final ScriptEngineFactory engineFactory;
    private final long defaultTimeoutMs;

    public ExecuteScriptTool(
        WorkspaceSandbox sandbox,
        ScriptEngineFactory engineFactory,
        long defaultTimeoutMs
    ) {
        this.sandbox = sandbox;
        this.engineFactory = engineFactory;
        this.defaultTimeoutMs = defaultTimeoutMs > 0 ? defaultTimeoutMs : 600_000L;
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

        final String source;
        if (hasFile) {
            final Path resolved;
            try {
                resolved = sandbox.resolve(file);
            } catch (SecurityException e) {
                return ToolExecutionResult.text("错误：路径超出工作区范围: " + file);
            }
            try {
                source = Files.readString(resolved, StandardCharsets.UTF_8);
            } catch (IOException e) {
                return ToolExecutionResult.text("错误：无法读取脚本文件 " + file + ": " + e.getMessage());
            }
        } else {
            source = code;
        }

        String prelude = buildArgsPrelude(parameters.get("args"));
        String js = prelude.isEmpty() ? source : prelude + "\n" + source;

        Path workspaceRoot = sandbox.getRoot();
        try (ScriptEngine engine = engineFactory.open(workspaceRoot)) {
            AtomicBoolean cancelSent = new AtomicBoolean(false);
            Thread watcher = startCancelWatcher(engine, cancellationToken, cancelSent);
            try {
                String json = engine.eval(js, defaultTimeoutMs, cancellationToken);
                return ToolExecutionResult.text(json == null ? "null" : json);
            } catch (RuntimeException e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                return ToolExecutionResult.text(msg);
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
