package com.agent1.javaagent.cli.productivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.cli.ProductivityCli;
import com.agent1.javaagent.log.AgentDataPaths;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductivityCliAgentRootTest {

    @Test
    void resolveAgentRootMatchesAgentDataPaths() {
        Path cli = ProductivityCli.resolveAgentRoot();
        Path paths = AgentDataPaths.agentRoot();
        assertEquals(paths, cli);
        assertTrue(cli.isAbsolute());
        Path project = ProductivityCli.resolveProjectRoot(cli);
        assertTrue(project.isAbsolute());
    }
}
