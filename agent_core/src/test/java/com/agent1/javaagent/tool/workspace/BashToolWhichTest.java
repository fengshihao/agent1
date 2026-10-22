package com.agent1.javaagent.tool.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.anno.AnnotatedTools;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class BashToolWhichTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void whichNodeDoesNotLeakHostPath(@TempDir Path workspace) throws Exception {
        var tool = AnnotatedTools.from(new BashTool(new WorkspaceSandbox(workspace))).get(0);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("command", "which node");
        String out = tool.execute("b", params, new CancellationToken(), update -> { }).getText();
        assertEquals("(empty output)", out);
    }

    @Test
    void whichLsStillWorks(@TempDir Path workspace) throws Exception {
        var tool = AnnotatedTools.from(new BashTool(new WorkspaceSandbox(workspace))).get(0);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("command", "which ls");
        String out = tool.execute("b", params, new CancellationToken(), update -> { }).getText();
        assertFalse(out.startsWith("Error:"));
        assertFalse(out.equals("(empty output)"));
        assertTrue(out.contains("ls"));
    }
}
