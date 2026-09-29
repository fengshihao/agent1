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

/** unpack → 替换 word/document.xml 片段 → validateDocx → pack（docx-raw.js）。 */
public final class DocxRawEditTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WorkspaceSandbox sandbox;
    private final ScriptEngineFactory engineFactory;
    private final long timeoutMs;
    private final Path agentRoot;

    public DocxRawEditTool(
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
        return "docx_raw_edit";
    }

    @Override
    public String description() {
        return "Low-level OOXML edit: unpack docx, replace search in word/document.xml, validate, pack. "
            + "See read_agent_doc docs/system/office-docx.md.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("input_path", MAPPER.createObjectNode().put("type", "string"));
        properties.set("output_path", MAPPER.createObjectNode().put("type", "string"));
        properties.set("work_dir", MAPPER.createObjectNode().put("type", "string"));
        properties.set("search", MAPPER.createObjectNode().put("type", "string"));
        properties.set("replace", MAPPER.createObjectNode().put("type", "string"));
        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode().add("input_path").add("output_path").add("search"));
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
        String input = parameters.path("input_path").asText("").trim();
        String output = parameters.path("output_path").asText("").trim();
        String workDir = parameters.path("work_dir").asText("tmp/docx-ooxml").trim();
        String search = parameters.path("search").asText("");
        String replace = parameters.path("replace").asText("");
        if (input.isEmpty() || output.isEmpty() || search.isEmpty()) {
            return ToolExecutionResult.text("错误：input_path、output_path、search 必填");
        }
        String js =
            """
            import { unpackDocx, packDocx, validateDocx } from './docx-raw.js';
            import fs from 'fs';
            const { dir } = unpackDocx(%s, { workDir: %s });
            const p = dir + '/word/document.xml';
            let xml = fs.readFileSync(p).toString();
            const parts = xml.split(%s);
            if (parts.length < 2) export default { ok: false, reason: 'search not in document.xml' };
            xml = parts.join(%s);
            fs.writeFileSync(p, xml);
            const v1 = validateDocx({ dir });
            if (!v1.ok) export default v1;
            const packed = packDocx(dir, %s);
            export default { ok: true, packed, validate: validateDocx({ path: %s }) };
            """
                .formatted(
                    DocxOfficeJsRunner.jsonString(input),
                    DocxOfficeJsRunner.jsonString(workDir),
                    DocxOfficeJsRunner.jsonString(search),
                    DocxOfficeJsRunner.jsonString(replace),
                    DocxOfficeJsRunner.jsonString(output),
                    DocxOfficeJsRunner.jsonString(output)
                );
        return DocxOfficeJsRunner.run(sandbox, engineFactory, timeoutMs, agentRoot, js, cancellationToken);
    }
}
