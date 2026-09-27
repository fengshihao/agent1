package com.agent1.javaagent.tool.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class ReadAgentDocToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path agentRoot;

    @BeforeEach
    void bootstrap() {
        AgentHomeBootstrap.ensure(agentRoot);
    }

    @Test
    void readsSystemDoc() {
        ReadAgentDocTool tool = new ReadAgentDocTool(agentRoot);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("path", "docs/system/catalog-install.md");
        ToolExecutionResult result = tool.execute("r1", params, new CancellationToken(), u -> {});
        assertTrue(result.getText().contains("PATH: docs/system/catalog-install.md"));
    }

    @Test
    void rejectsSharedPath() {
        ReadAgentDocTool tool = new ReadAgentDocTool(agentRoot);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("path", "shared/catalog/skills/x.md");
        ToolExecutionResult result = tool.execute("r2", params, new CancellationToken(), u -> {});
        assertTrue(result.getText().contains("read_agent_doc only allows"));
    }
}
