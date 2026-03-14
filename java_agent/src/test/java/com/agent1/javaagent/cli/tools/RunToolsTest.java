package com.agent1.javaagent.cli.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class RunToolsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void runBashTool_shouldExecuteCommand() {
        RunBashTool tool = new RunBashTool();
        ObjectNode params = MAPPER.createObjectNode().put("command", "echo hello");
        String out = tool.execute("1", params, new CancellationToken(), update -> { }).getText();
        assertTrue(out.contains("hello"));
    }

    @Test
    void runPythonTool_shouldExecuteInlineScript() {
        RunPythonTool tool = new RunPythonTool();
        ObjectNode params = MAPPER.createObjectNode().put("script", "print('ok')");
        String out = tool.execute("1", params, new CancellationToken(), update -> { }).getText();
        assertTrue(out.contains("ok"));
    }
}
