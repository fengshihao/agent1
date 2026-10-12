package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.script.ScriptToolBridge;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeizhiScriptToolInstallerTest {

    private static final String MODULE =
        "import { markdownToDocx } from \"docx.js\";\n"
            + "export default markdownToDocx({ inputPath: 'a.md', outputPath: 'a.docx' });\n";

    @Test
    void moduleSourceStaysASeparateEval() {
        List<WeizhiScriptToolInstaller.EvalStep> steps = WeizhiScriptToolInstaller.evalSteps(
            MODULE,
            null,
            bridge("read_file"),
            null
        );
        assertEquals(2, steps.size());
        assertEquals("<tools-prelude>", steps.get(0).filename());
        assertTrue(steps.get(0).source().startsWith("(function(){"));
        assertEquals("<eval>", steps.get(1).filename());
        assertEquals(MODULE, steps.get(1).source());
        assertFalse(steps.get(1).source().contains("$tools"));
    }

    @Test
    void concatenatedPreludeWouldHideImportFromModuleDetection() {
        String wrapped = WeizhiScriptToolInstaller.wrap(MODULE, bridge("read_file"));
        String[] lines = wrapped.split("\n", -1);
        int preludeLines = WeizhiScriptToolInstaller.TOOLS_PRELUDE_LINE_COUNT;
        assertTrue(preludeLines > 13);
        assertTrue(wrapped.contains("globalThis.$mcp"));
        assertTrue(wrapped.contains("mcp.connect"));
        assertFalse(wrapped.contains("mcp_call_tool"));
        assertEquals("import { markdownToDocx } from \"docx.js\";", lines[preludeLines]);
        assertEquals('{', lines[preludeLines].charAt(7));
    }

    @Test
    void fileEvalKeepsWorkspaceFilename() {
        List<WeizhiScriptToolInstaller.EvalStep> steps = WeizhiScriptToolInstaller.evalSteps(
            "export default 1;\n",
            "globalThis.__agentArgs = [];",
            null,
            "convert.js"
        );
        assertEquals(2, steps.size());
        assertEquals("<agent-args>", steps.get(0).filename());
        assertEquals("convert.js", steps.get(1).filename());
        assertEquals("export default 1;\n", steps.get(1).source());
    }

    @Test
    void preludeEmbedsEnabledMcpServers(@org.junit.jupiter.api.io.TempDir java.nio.file.Path agentRoot)
        throws Exception {
        com.agent1.javaagent.mcp.McpServersFile.save(
            agentRoot,
            List.of(new com.agent1.javaagent.mcp.McpServerRecord(
                "demo",
                "https://example.com/mcp",
                Map.of(),
                true,
                "",
                0,
                ""
            ))
        );
        String prelude = WeizhiScriptToolInstaller.evalSteps(
            "1",
            null,
            bridge("read_file"),
            null,
            agentRoot
        ).get(0).source();
        assertTrue(prelude.contains("https://example.com/mcp"));
        assertTrue(prelude.contains("\"demo\""));
        assertTrue(prelude.contains("callTool(String(tool)"));
        assertTrue(prelude.contains("JSON.parse(out)"));
        assertTrue(prelude.contains("webview_exec"));
        assertTrue(prelude.contains("JSON.parse(t)"));
    }

    @Test
    void webviewExecReceiptIsEmbeddedObject() {
        String receipt = "{\"ok\":true,\"resultPreview\":{\"svg\":1,\"textLen\":4}}";
        assertEquals(receipt, WeizhiScriptToolInstaller.encodeToolResult("webview_exec", "  " + receipt + "  "));
        String envelope = "{\"result\":" + WeizhiScriptToolInstaller.encodeToolResult("webview_exec", receipt) + "}";
        Object result = com.weizhi.platform.MiniJson.parse(envelope);
        assertTrue(result instanceof java.util.Map);
        Object inner = ((java.util.Map<?, ?>) result).get("result");
        assertTrue(inner instanceof java.util.Map);
        assertEquals(Boolean.TRUE, ((java.util.Map<?, ?>) inner).get("ok"));
    }

    @Test
    void otherToolsStayQuotedEvenWhenJson() {
        String body = "{\"ok\":true}";
        String encoded = WeizhiScriptToolInstaller.encodeToolResult("read_file", body);
        assertTrue(encoded.startsWith("\""));
        assertFalse(encoded.equals(body));
    }

    @Test
    void invalidWebviewTextStaysQuoted() {
        String encoded = WeizhiScriptToolInstaller.encodeToolResult("webview_exec", "not-json");
        assertEquals("\"not-json\"", encoded);
    }

    private static ScriptToolBridge bridge(String name) {
        return new ScriptToolBridge() {
            @Override
            public Set<String> exposedNames() {
                return Set.of(name);
            }

            @Override
            public String call(String toolName, Map<String, Object> arguments) {
                return "";
            }
        };
    }
}
