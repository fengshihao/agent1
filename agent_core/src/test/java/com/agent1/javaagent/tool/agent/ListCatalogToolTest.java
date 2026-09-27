package com.agent1.javaagent.tool.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;

class ListCatalogToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path agentRoot;

    @BeforeEach
    void bootstrap() throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Files.writeString(agentRoot.resolve("shared/catalog/scripts/demo.js"), "// demo");
    }

    @Test
    void summarizesCounts() {
        ListCatalogTool tool = new ListCatalogTool(agentRoot);
        ToolExecutionResult result = tool.execute("l1", MAPPER.createObjectNode(), new CancellationToken(), u -> {});
        assertTrue(result.getText().contains("scripts: 1 files"));
        assertTrue(result.getText().contains("CATALOG_ROOT"));
    }
}
