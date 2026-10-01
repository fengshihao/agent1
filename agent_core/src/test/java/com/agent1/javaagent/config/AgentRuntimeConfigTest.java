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
            "未配置 API Key（请在 App「模型配置」中填写，或编译时设置 QWEN_API_KEY / DASHSCOPE_API_KEY）",
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
        assertFalse(c.isWebSearchConfigured());
    }

    @Test
    void propertiesConfigureTavilyWebSearch() {
        Properties p = new Properties();
        p.setProperty("apiKey", "sk-test");
        p.setProperty("webSearchApiKey", "tvly-test");
        p.setProperty("webSearchBaseUrl", "https://search.example");
        AgentRuntimeConfig c = AgentRuntimeConfig.fromProperties(p);
        assertTrue(c.isWebSearchConfigured());
        assertEquals("tvly-test", c.getWebSearchApiKey());
        assertEquals("https://search.example", c.getWebSearchBaseUrl());
        assertTrue(com.agent1.javaagent.modelcatalog.RuntimeConfigSummary.from(c).isWebSearchConfigured());
    }
}
