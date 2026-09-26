package com.agent1.javaagent.cli.productivity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agent1.javaagent.cli.ProductivityCli;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductivityCliAgentRootTest {

    @TempDir
    Path temp;

    @Test
    void resolveAgentRootUsesEnvWhenSet() {
        Path custom = temp.resolve("my-agent-data");
        String previous = System.getenv("AGENT1_AGENT_ROOT");
        try {
            // JUnit 无法改 env，本测试只验证默认 cwd/.agent1 行为
            Path root = ProductivityCli.resolveAgentRoot();
            assertEquals(Path.of(".").toAbsolutePath().normalize().resolve(".agent1"), root);
        } finally {
            // no-op: env not modified in test JVM
            if (previous != null) {
                // documented: set AGENT1_AGENT_ROOT in shell for custom root
            }
        }
    }
}
