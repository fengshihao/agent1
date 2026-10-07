package com.agent1.javaagent.tool.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.FakeScriptEngineFactory;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecuteScriptToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temp;

    private Path workspaceA;
    private Path workspaceB;
    private FakeScriptEngineFactory factory;
    private ExecuteScriptTool tool;

    @BeforeEach
    void setUp() throws Exception {
        workspaceA = temp.resolve("session-a");
        workspaceB = temp.resolve("session-b");
        Files.createDirectories(workspaceA);
        Files.createDirectories(workspaceB);
        factory = new FakeScriptEngineFactory("\"ok\"");
        tool = new ExecuteScriptTool(new WorkspaceSandbox(workspaceA), factory, 60_000);
    }

    @Test
    void nameIsExecuteScript() {
        assertEquals("run_js", tool.name());
    }

    @Test
    void descriptionPrefersFileForLongOrIterativeScripts() {
        String description = tool.description();
        assertTrue(description.contains("20 行或 1000 字符"));
        assertTrue(description.contains("edit_file"));
        assertTrue(description.contains("$tools.webview_exec"));
        assertFalse(description.contains("稳定行号"));
    }

    @Test
    void rejectsBothCodeAndFile() throws Exception {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("code", "1+1");
        params.put("file", "x.js");

        ToolExecutionResult result = tool.execute("c1", params, new CancellationToken(), u -> {});

        assertTrue(result.getText().contains("code") && result.getText().contains("file"));
    }

    @Test
    void rejectsNeitherCodeNorFile() throws Exception {
        ToolExecutionResult result = tool.execute("c1", MAPPER.createObjectNode(), new CancellationToken(), u -> {});

        assertTrue(result.getText().contains("code") || result.getText().contains("file"));
    }

    @Test
    void inlineCodeUsesSessionWorkspace() throws Exception {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("code", "1+2");

        ToolExecutionResult result = tool.execute("c1", params, new CancellationToken(), u -> {});

        assertEquals("\"ok\"", result.getText());
        assertEquals(workspaceA.toAbsolutePath().normalize(), factory.lastEvalWorkspace().toAbsolutePath().normalize());
    }

    @Test
    void readsScriptFromWorkspaceFile() throws Exception {
        Files.writeString(workspaceA.resolve("run.js"), "3+4", StandardCharsets.UTF_8);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("file", "run.js");

        ToolExecutionResult result = tool.execute("c1", params, new CancellationToken(), u -> {});

        assertEquals("\"ok\"", result.getText());
    }

    @Test
    void fileOutsideWorkspaceIsRejected() throws Exception {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("file", "../escape.js");

        ToolExecutionResult result = tool.execute("c1", params, new CancellationToken(), u -> {});

        assertTrue(result.getText().contains("工作区") || result.getText().contains("路径"));
    }

    @Test
    void engineErrorMessageIsReturnedVerbatim() {
        com.agent1.javaagent.script.ScriptEngineFactory failing = workspace -> new com.agent1.javaagent.script.ScriptEngine() {
            @Override
            public String eval(String jsSource, long timeoutMs, CancellationToken token) {
                throw new RuntimeException("timeout: script exceeded wall clock");
            }

            @Override
            public void close() {
            }
        };
        ExecuteScriptTool failingTool = new ExecuteScriptTool(new WorkspaceSandbox(workspaceA), failing, 60_000);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("code", "while(true){}");

        ToolExecutionResult result = failingTool.execute("c1", params, new CancellationToken(), u -> {});

        assertTrue(result.getText().contains("\"ok\":false"));
        assertTrue(result.getText().contains("timeout"));
    }

    @Test
    void fakeEngineDoesNotSeeOtherSessionWorkspace() throws Exception {
        ExecuteScriptTool toolB = new ExecuteScriptTool(new WorkspaceSandbox(workspaceB), factory, 60_000);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("code", "1");

        tool.execute("c1", params, new CancellationToken(), u -> {});
        toolB.execute("c2", params, new CancellationToken(), u -> {});

        assertEquals(2, factory.openedWorkspaces().size());
        assertFalse(factory.openedWorkspaces().get(0).equals(factory.openedWorkspaces().get(1)));
    }

    @Test
    void passesArgsAsPrelude() throws Exception {
        RecordingFactory recording = new RecordingFactory();
        ExecuteScriptTool withArgs = new ExecuteScriptTool(new WorkspaceSandbox(workspaceA), recording, 60_000);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("code", "globalThis.__agentArgs");
        ArrayNode args = params.putArray("args");
        args.add("a");
        args.add(1);

        withArgs.execute("c1", params, new CancellationToken(), u -> {});

        assertTrue(recording.lastUserSource.contains("globalThis.__agentArgs"));
        assertTrue(recording.lastAgentPrelude.contains("[\"a\",1]") || recording.lastAgentPrelude.contains("[\"a\", 1]"));
    }

    private static final class RecordingFactory implements com.agent1.javaagent.script.ScriptEngineFactory {
        String lastUserSource = "";
        String lastAgentPrelude = "";

        @Override
        public com.agent1.javaagent.script.ScriptEngine open(Path workspace) {
            return new com.agent1.javaagent.script.ScriptEngine() {
                @Override
                public String eval(String jsSource, long timeoutMs, CancellationToken token) {
                    lastUserSource = jsSource;
                    return "[]";
                }

                @Override
                public String evalForAgent(
                    String userSource,
                    String agentArgsPrelude,
                    long timeoutMs,
                    CancellationToken cancellationToken,
                    String workspaceRelativeFile
                ) {
                    lastUserSource = userSource;
                    lastAgentPrelude = agentArgsPrelude == null ? "" : agentArgsPrelude;
                    return "[]";
                }

                @Override
                public void close() {
                }
            };
        }
    }

    @Test
    void longInlineSpillsToJobsAndStillRuns() throws Exception {
        String code = "a".repeat(InlineScriptSpill.MAX_CHARS + 1);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("code", code);

        ToolExecutionResult result = tool.execute("c1", params, new CancellationToken(), u -> {});

        assertTrue(result.getText().startsWith("\"ok\""));
        assertTrue(result.getText().contains(InlineScriptSpill.MARKER + "jobs/inline-"));
        assertTrue(result.getText().contains("edit_file"));
        Path spilled = Files.list(workspaceA.resolve("jobs")).findFirst().orElseThrow();
        assertEquals(code, Files.readString(spilled));
    }

    @Test
    void twentyLinesAndOneThousandCharsStayInline() throws Exception {
        String twentyLines = ("x\n").repeat(19) + "x";
        assertEquals(20, twentyLines.split("\n", -1).length);
        ObjectNode byLines = MAPPER.createObjectNode();
        byLines.put("code", twentyLines);
        ToolExecutionResult linesResult = tool.execute("c1", byLines, new CancellationToken(), u -> {});
        assertEquals("\"ok\"", linesResult.getText());
        assertFalse(Files.exists(workspaceA.resolve("jobs")));

        ObjectNode byChars = MAPPER.createObjectNode();
        byChars.put("code", "b".repeat(InlineScriptSpill.MAX_CHARS));
        ToolExecutionResult charsResult = tool.execute("c2", byChars, new CancellationToken(), u -> {});
        assertEquals("\"ok\"", charsResult.getText());
        assertFalse(Files.exists(workspaceA.resolve("jobs")));
    }

    @Test
    void rejectsNonJavaScriptFileExtension() throws Exception {
        Files.writeString(workspaceA.resolve("dog.svg"), "<svg/>", StandardCharsets.UTF_8);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("file", "dog.svg");

        ToolExecutionResult result = tool.execute("c1", params, new CancellationToken(), u -> {});

        assertTrue(result.getText().contains("svgToImage"));
        assertFalse(factory.lastEvalWorkspace() != null);
    }
}
