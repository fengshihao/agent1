package com.agent1.javaagent.tool.office;

import com.agent1.javaagent.catalog.OfficeCatalogScripts;
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

/** Markdown → docx via Weizhi {@code docx.js}（见 weizhi docs/AGENT1_DOCX_INTEGRATION.md）。 */
public final class DocxMarkdownToWordTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkspaceSandbox sandbox;
    private final ScriptEngineFactory engineFactory;
    private final long timeoutMs;
    private final Path agentRoot;

    public DocxMarkdownToWordTool(
        WorkspaceSandbox sandbox,
        ScriptEngineFactory engineFactory,
        long timeoutMs,
        Path agentRoot
    ) {
        this.sandbox = sandbox;
        this.engineFactory = engineFactory;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 600_000L;
        this.agentRoot = agentRoot == null ? null : agentRoot.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "docx_markdown_to_word";
    }

    @Override
    public String description() {
        return "Convert workspace Markdown file to .docx using Weizhi docx.js (markdownToDocx). "
            + "Requires catalog scripts docx.js installed under agentRoot.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set(
            "input_path",
            MAPPER.createObjectNode().put("type", "string").put("description", "Workspace-relative .md path")
        );
        properties.set(
            "output_path",
            MAPPER.createObjectNode().put("type", "string").put("description", "Workspace-relative .docx path")
        );
        properties.set(
            "title",
            MAPPER.createObjectNode().put("type", "string").put("description", "Optional document title")
        );
        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode().add("input_path").add("output_path"));
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
        String inputPath = parameters.path("input_path").asText("").trim();
        String outputPath = parameters.path("output_path").asText("").trim();
        if (inputPath.isEmpty() || outputPath.isEmpty()) {
            return ToolExecutionResult.text("错误：需要 input_path 与 output_path");
        }
        String title = parameters.path("title").asText("").trim();
        String titleJson = title.isEmpty() ? "" : ", title: " + jsonString(title);
        String js =
            """
            import { markdownToDocx } from './docx.js';
            export default markdownToDocx({
              inputPath: %s,
              outputPath: %s%s
            });
            """.formatted(jsonString(inputPath), jsonString(outputPath), titleJson);

        return DocxOfficeJsRunner.run(sandbox, engineFactory, timeoutMs, agentRoot, js, cancellationToken);
    }

    private static String jsonString(String value) {
        return DocxOfficeJsRunner.jsonString(value);
    }
}
