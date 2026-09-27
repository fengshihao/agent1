package com.agent1.javaagent.tool.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.core.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogSyncToolsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void syncStatusReportsMissingManifest(@TempDir Path agentRoot) {
        AgentHomeBootstrap.ensure(agentRoot);
        var tool = new CatalogSyncStatusTool(agentRoot);
        String text = tool.execute("s1", MAPPER.createObjectNode(), new CancellationToken(), u -> {}).getText();
        assertTrue(text.contains("未配置") || text.contains("manifest"));
    }

    @Test
    void catalogInstallWithoutConfig(@TempDir Path agentRoot) {
        AgentHomeBootstrap.ensure(agentRoot);
        var tool = new CatalogInstallTool(agentRoot);
        String text = tool.execute(
            "c1",
            MAPPER.createObjectNode().putArray("ids").add("x"),
            new CancellationToken(),
            u -> {}
        ).getText();
        assertTrue(text.contains("未配置") || text.contains("manifest"));
    }
}
