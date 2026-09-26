package com.agent1.javaagent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class EnvAgentRuntimeConfigLoaderTest {

    @Test
    void propertiesOverrideEnvDefaults() {
        Properties p = new Properties();
        p.setProperty("maxContextTurns", "3");
        p.setProperty("maxTurnsPerRun", "8");
        AgentRuntimeConfig c = AgentRuntimeConfig.fromProperties(p);
        assertEquals(3, c.getMaxContextTurns());
        assertEquals(8, c.getMaxTurnsPerRun());
        assertEquals(AgentRuntimeDefaults.DEFAULT_MODEL, c.getModel());
    }

    @Test
    void propertiesSetQwenModelForTesting() {
        Properties p = new Properties();
        p.setProperty("apiKey", "test-key");
        p.setProperty("model", "qwen3.7-flash");
        AgentRuntimeConfig c = AgentRuntimeConfig.fromProperties(p);
        assertEquals("qwen3.7-flash", c.getModel());
        assertEquals("test-key", c.getApiKey());
        assertNull(c.configurationError());
    }
}
