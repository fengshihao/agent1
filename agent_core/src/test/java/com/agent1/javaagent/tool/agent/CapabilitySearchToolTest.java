package com.agent1.javaagent.tool.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CapabilitySearchToolTest {

    @TempDir
    Path temp;

    @Test
    void searchReturnsDocxHint() throws Exception {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilitySearchTool tool = new CapabilitySearchTool(agentRoot);
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("query", "markdown word docx");

        ToolExecutionResult result = tool.execute("t1", params, new CancellationToken(), u -> {
        });

        assertTrue(result.getText().contains("docx"));
        assertTrue(result.getDetails() != null && result.getDetails().has("hits"));
    }
}
