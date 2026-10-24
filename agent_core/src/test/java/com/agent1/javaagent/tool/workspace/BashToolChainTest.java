package com.agent1.javaagent.tool.workspace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.anno.AnnotatedTools;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BashToolChainTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path workspace;

    @Test
    void semicolonRunsTwoCommands() throws Exception {
        Files.writeString(workspace.resolve("a.txt"), "hello");
        String out = run("echo one; cat a.txt");
        assertTrue(out.contains("one"));
        assertTrue(out.contains("hello"));
        assertTrue(out.contains("---"));
    }

    @Test
    void andStopsOnFailure() throws Exception {
        String out = run("cat missing.txt && echo ok");
        assertFalse(out.contains("ok"));
    }

    @Test
    void pipeToWc() throws Exception {
        Files.writeString(workspace.resolve("lines.txt"), "a\nb\nc\n");
        String out = run("cat lines.txt | wc -l");
        assertTrue(out.trim().startsWith("3") || out.contains("3"));
    }

    @Test
    void redirectStdout() throws Exception {
        String out = run("echo saved > out/red.txt");
        assertTrue(Files.isRegularFile(workspace.resolve("out/red.txt")));
        assertTrue(Files.readString(workspace.resolve("out/red.txt")).contains("saved"));
        assertTrue(out.contains("已成功执行") || out.isBlank() || out.contains("saved"));
    }

    private String run(String command) throws Exception {
        AgentTool tool = AnnotatedTools.from(new BashTool(new WorkspaceSandbox(workspace))).get(0);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("command", command);
        return tool.execute("id", params, new CancellationToken(), ignored -> {}).getText();
    }
}
