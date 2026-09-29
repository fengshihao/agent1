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

/** readDocx → grep → replaceInBlock/replaceAll → save。 */
public final class DocxReadGrepEditTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WorkspaceSandbox sandbox;
    private final ScriptEngineFactory engineFactory;
    private final long timeoutMs;
    private final Path agentRoot;

    public DocxReadGrepEditTool(
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
        return "docx_read_grep_edit";
    }

    @Override
    public String description() {
        return "Grep/replace in workspace docx then save. Params: docx_path, pattern, output_path; "
            + "replace_with or block_index+replace_in_block. API: read_agent_doc docs/system/office-docx.md.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("docx_path", MAPPER.createObjectNode().put("type", "string"));
        properties.set("pattern", MAPPER.createObjectNode().put("type", "string"));
        properties.set("output_path", MAPPER.createObjectNode().put("type", "string"));
        properties.set("replace_with", MAPPER.createObjectNode().put("type", "string"));
        properties.set(
            "block_index",
            MAPPER.createObjectNode().put("type", "integer").put("description", "Use with replace_with for replaceInBlock")
        );
        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode().add("docx_path").add("pattern").add("output_path"));
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
        String docx = parameters.path("docx_path").asText("").trim();
        String pattern = parameters.path("pattern").asText("").trim();
        String out = parameters.path("output_path").asText("").trim();
        String replace = parameters.path("replace_with").asText("").trim();
        if (docx.isEmpty() || pattern.isEmpty() || out.isEmpty()) {
            return ToolExecutionResult.text("错误：docx_path、pattern、output_path 必填");
        }
        String editJs;
        if (!replace.isEmpty()) {
            editJs =
                """
                const { matches } = doc.grep(%s);
                if (matches.length === 0) {
                  doc.replaceAll(%s, %s);
                } else {
                  doc.replaceInBlock(matches[0].blockIndex, %s, %s);
                }
                export default doc.save(%s);
                """
                    .formatted(
                        DocxOfficeJsRunner.jsonString(pattern),
                        DocxOfficeJsRunner.jsonString(pattern),
                        DocxOfficeJsRunner.jsonString(replace),
                        DocxOfficeJsRunner.jsonString(pattern),
                        DocxOfficeJsRunner.jsonString(replace),
                        DocxOfficeJsRunner.jsonString(out)
                    );
        } else {
            editJs =
                """
                const { matches } = doc.grep(%s);
                export default { ok: true, matches };
                """
                    .formatted(DocxOfficeJsRunner.jsonString(pattern));
        }
        String js =
            """
            import { readDocx } from './docx.js';
            const doc = readDocx(%s);
            %s
            """.formatted(DocxOfficeJsRunner.jsonString(docx), editJs);
        return DocxOfficeJsRunner.run(sandbox, engineFactory, timeoutMs, agentRoot, js, cancellationToken);
    }
}
