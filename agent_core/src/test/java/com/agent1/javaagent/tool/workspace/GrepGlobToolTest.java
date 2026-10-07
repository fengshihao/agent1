package com.agent1.javaagent.tool.workspace;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.anno.AnnotatedTools;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GrepGlobToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void grepAndGlobFindWorkspaceFile(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("note.txt"), "hello-agent1\n");
        WorkspaceSandbox sandbox = new WorkspaceSandbox(workspace);
        AgentTool grep = AnnotatedTools.from(new GrepTool(sandbox)).get(0);
        AgentTool glob = AnnotatedTools.from(new GlobTool(sandbox)).get(0);

        ObjectNode grepParams = MAPPER.createObjectNode();
        grepParams.put("pattern", "hello-agent1");
        String grepText = grep.execute("g", grepParams, new CancellationToken(), update -> { }).getText();
        assertTrue(grepText.contains("note.txt"));

        ObjectNode globParams = MAPPER.createObjectNode();
        globParams.put("pattern", "*.txt");
        String globText = glob.execute("l", globParams, new CancellationToken(), update -> { }).getText();
        assertTrue(globText.contains("note.txt"));
    }
}
