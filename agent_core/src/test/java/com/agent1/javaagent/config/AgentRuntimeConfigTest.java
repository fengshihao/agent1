package com.agent1.javaagent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class AgentRuntimeConfigTest {

    @Test
    void builder_usesDefaultsWhenFieldsBlank() {
        AgentRuntimeConfig c = AgentRuntimeConfig.builder().build();
        assertEquals(AgentRuntimeDefaults.DEFAULT_MODEL, c.getModel());
        assertEquals(AgentRuntimeDefaults.DEFAULT_BASE_URL, c.getBaseUrl());
        assertEquals(AgentRuntimeDefaults.DEFAULT_MAX_CONTEXT_TURNS, c.getMaxContextTurns());
        assertEquals(AgentRuntimeDefaults.DEFAULT_MAX_TURNS_PER_RUN, c.getMaxTurnsPerRun());
        assertFalse(c.isModelConfigured());
        assertEquals(
            "未配置 API Key（请设置 QWEN_API_KEY、DASHSCOPE_API_KEY 或 OPENAI_API_KEY）",
            c.configurationError()
        );
    }

    @Test
    void propertiesOverrideModel() {
        Properties p = new Properties();
        p.setProperty("model", "custom-model");
        p.setProperty("apiKey", "sk-test");
        AgentRuntimeConfig c = AgentRuntimeConfig.fromProperties(p);
        assertEquals("custom-model", c.getModel());
        assertTrue(c.isModelConfigured());
        assertNull(c.configurationError());
    }
}
