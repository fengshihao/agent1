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

class ReadFileToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temp;

    private ReadFileTool tool;

    @BeforeEach
    void setUp() throws Exception {
        Path root = temp.resolve("workspace");
        Files.createDirectories(root);
        Files.writeString(root.resolve("hello.txt"), "line1\nline2\n", StandardCharsets.UTF_8);
        tool = new ReadFileTool(new WorkspaceSandbox(root));
    }

    @Test
    void readsLinesWithRelativePath() throws Exception {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("path", "hello.txt");
        params.put("limit", 10);

        ToolExecutionResult result = tool.execute(
            "call_1",
            params,
            new CancellationToken(),
            update -> {}
        );

        assertTrue(result.getText().contains("PATH: hello.txt"));
        assertTrue(result.getText().contains("1|line1"));
        assertTrue(result.getText().contains("2|line2"));
    }
}
