package com.agent1.javaagent.tool.workspace;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class WorkspaceFileToolsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temp;

    private Path root;
    private WorkspaceSandbox sandbox;

    @BeforeEach
    void setUp() throws Exception {
        root = temp.resolve("workspace");
        Files.createDirectories(root);
        sandbox = new WorkspaceSandbox(root);
    }

    @Test
    void writeAndListAndEdit() throws Exception {
        WriteFileTool write = new WriteFileTool(sandbox);
        ObjectNode w = MAPPER.createObjectNode();
        w.put("path", "notes.txt");
        w.put("content", "alpha beta");
        ToolExecutionResult wResult = write.execute("w1", w, new CancellationToken(), u -> {});
        assertTrue(wResult.getText().contains("notes.txt"));

        ListDirTool list = new ListDirTool(sandbox);
        ObjectNode l = MAPPER.createObjectNode();
        l.put("path", ".");
        ToolExecutionResult lResult = list.execute("l1", l, new CancellationToken(), u -> {});
        assertTrue(lResult.getText().contains("notes.txt"));

        EditFileTool edit = new EditFileTool(sandbox);
        ObjectNode e = MAPPER.createObjectNode();
        e.put("path", "notes.txt");
        e.put("old_string", "beta");
        e.put("new_string", "gamma");
        edit.execute("e1", e, new CancellationToken(), u -> {});

        String onDisk = Files.readString(root.resolve("notes.txt"), StandardCharsets.UTF_8);
        assertTrue(onDisk.contains("alpha gamma"));
    }

    @Test
    void writeRejectsEscape() throws Exception {
        WriteFileTool write = new WriteFileTool(sandbox);
        ObjectNode w = MAPPER.createObjectNode();
        w.put("path", "../outside.txt");
        w.put("content", "x");
        ToolExecutionResult result = write.execute("w2", w, new CancellationToken(), u -> {});
        assertTrue(result.getText().contains("错误") || result.getText().toLowerCase().contains("error"));
    }
}
