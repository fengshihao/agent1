package com.agent1.javaagent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
