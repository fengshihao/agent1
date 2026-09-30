package com.agent1.javaagent.tool.office;

import com.agent1.javaagent.catalog.OfficeCatalogScripts;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.ScriptEngine;
import com.agent1.javaagent.script.ScriptEngineFactory;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;

final class DocxOfficeJsRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DocxOfficeJsRunner() {
    }

    static ToolExecutionResult run(
        WorkspaceSandbox sandbox,
        ScriptEngineFactory engineFactory,
        long timeoutMs,
        Path agentRoot,
        String js,
        CancellationToken cancellationToken
    ) {
        if (agentRoot == null || !OfficeCatalogScripts.isOfficeReady(agentRoot)) {
            return ToolExecutionResult.text(
                "错误：docx.js 未就绪。请确保 agentRoot/shared/catalog/scripts 含 docx.js（bootstrap 或 Weizhi assets）"
            );
        }
        try (ScriptEngine engine = engineFactory.open(sandbox.getRoot())) {
            String result = engine.eval(js, timeoutMs, cancellationToken);
            return ToolExecutionResult.text(result);
        } catch (Exception e) {
            return ToolExecutionResult.text("docx 脚本失败: " + e.getMessage());
        }
    }

    static String jsonString(String value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }

    /** JSON object/array literal for embedding in generated JS (not a quoted string). */
    static String jsonLiteral(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "null";
        }
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid JSON for docx script: " + e.getMessage(), e);
        }
    }

    static ToolExecutionResult cancelled(CancellationToken token) {
        if (token != null && token.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }
        return null;
    }
}
