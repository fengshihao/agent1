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
        assertEquals("execute_script", tool.name());
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

        assertTrue(recording.lastSource.contains("__agentArgs"));
        assertTrue(recording.lastSource.contains("[\"a\",1]") || recording.lastSource.contains("[\"a\", 1]"));
    }

    private static final class RecordingFactory implements com.agent1.javaagent.script.ScriptEngineFactory {
        String lastSource = "";

        @Override
        public com.agent1.javaagent.script.ScriptEngine open(Path workspace) {
            return new com.agent1.javaagent.script.ScriptEngine() {
                @Override
                public String eval(String jsSource, long timeoutMs, CancellationToken token) {
                    lastSource = jsSource;
                    return "[]";
                }

                @Override
                public void close() {
                }
            };
        }
    }
}
