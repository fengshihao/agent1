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
        "import { markdownToDocx } from './docx.js';\n"
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
        assertTrue(wrapped.contains("mcp_call_tool"));
        assertTrue(wrapped.contains("mcp__"));
        assertEquals("import { markdownToDocx } from './docx.js';", lines[preludeLines]);
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
