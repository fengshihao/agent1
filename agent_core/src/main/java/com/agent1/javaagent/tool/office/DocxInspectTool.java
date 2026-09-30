package com.agent1.javaagent.tool.office;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.ScriptEngineFactory;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;

/** 观察 docx 块结构 / textView（API 见 docs/system/office-docx.md）。 */
public final class DocxInspectTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WorkspaceSandbox sandbox;
    private final ScriptEngineFactory engineFactory;
    private final long timeoutMs;
    private final Path agentRoot;

    public DocxInspectTool(
        WorkspaceSandbox sandbox,
        ScriptEngineFactory engineFactory,
        long timeoutMs,
        Path agentRoot
    ) {
        this.sandbox = sandbox;
        this.engineFactory = engineFactory;
        this.timeoutMs = timeoutMs;
        this.agentRoot = agentRoot;
    }

    @Override
    public String name() {
        return "docx_inspect";
    }

    @Override
    public String description() {
        return "Inspect a workspace .docx: textView or listBlocks via docx.js readDocx. See read_file docs/system/office-docx.md.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("docx_path", MAPPER.createObjectNode().put("type", "string"));
        ObjectNode mode = MAPPER.createObjectNode();
        mode.put("type", "string");
        mode.set("enum", MAPPER.createArrayNode().add("text_view").add("headings").add("plain_text"));
        properties.set("mode", mode);
        properties.set(
            "include_style",
            MAPPER.createObjectNode().put("type", "boolean").put("description", "For text_view only")
        );
        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode().add("docx_path"));
        return schema;
    }

    @Override
    public ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) {
        ToolExecutionResult cancelled = DocxOfficeJsRunner.cancelled(cancellationToken);
        if (cancelled != null) {
            return cancelled;
        }
        String path = parameters.path("docx_path").asText("").trim();
        if (path.isEmpty()) {
            return ToolExecutionResult.text("错误：docx_path 必填");
        }
        String mode = parameters.path("mode").asText("text_view").trim();
        boolean includeStyle = parameters.path("include_style").asBoolean(false);
        String body = switch (mode) {
            case "headings" -> "export default { ok: true, headings: doc.headings() };";
            case "plain_text" -> "export default { ok: true, text: doc.plainText() };";
            default -> includeStyle
                ? "export default { ok: true, view: doc.textView({ includeStyle: true }) };"
                : "export default { ok: true, view: doc.textView() };";
        };
        String js =
            """
            import { readDocx } from './docx.js';
            const doc = readDocx(%s);
            %s
            """.formatted(DocxOfficeJsRunner.jsonString(path), body);
        return DocxOfficeJsRunner.run(sandbox, engineFactory, timeoutMs, agentRoot, js, cancellationToken);
    }
}
