package com.agent1.javaagent.tool.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AgentEvolutionStubToolsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void promoteRequestStub() {
        var tool = new PromoteRequestTool();
        assertTrue(tool.execute("p1", MAPPER.createObjectNode(), new CancellationToken(), u -> {})
            .getText()
            .contains("未实现"));
    }

    @Test
    void catalogInstallStub() {
        var tool = new CatalogInstallTool();
        assertTrue(tool.execute("c1", MAPPER.createObjectNode().putArray("ids").add("x"), new CancellationToken(), u -> {})
            .getText()
            .contains("未实现"));
    }
}
