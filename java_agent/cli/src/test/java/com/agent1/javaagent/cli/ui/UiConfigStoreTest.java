package com.agent1.javaagent.cli.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiConfigStoreTest {

    @TempDir
    Path temp;

    @Test
    void saveAndMergeOverridesModel() throws Exception {
        UiConfigStore.save(temp, "sk-from-ui", "https://example.com/v1", "ui-model");
        assertTrue(Files.isRegularFile(UiConfigStore.configPath(temp)));

        AgentRuntimeConfig merged = UiConfigStore.mergeWithEnvironment(temp);
        assertEquals("sk-from-ui", merged.getApiKey());
        assertEquals("https://example.com/v1", merged.getBaseUrl());
        assertEquals("ui-model", merged.getModel());
    }

    @Test
    void saveWithoutKeyKeepsExisting() throws Exception {
        UiConfigStore.save(temp, "keep-me", null, "m1");
        UiConfigStore.save(temp, null, "https://x/v1", "m2");
        AgentRuntimeConfig merged = UiConfigStore.mergeWithEnvironment(temp);
        assertEquals("keep-me", merged.getApiKey());
        assertEquals("https://x/v1", merged.getBaseUrl());
        assertEquals("m2", merged.getModel());
    }

    @Test
    void limitsInFileOverrideEnvironment() throws Exception {
        UiConfigStore.save(temp, "k", "https://example.com/v1", "m", "dashscope", 12, 0, 30, 40);
        AgentRuntimeConfig merged = UiConfigStore.mergeWithEnvironment(temp);
        assertEquals(12, merged.getMaxContextTurns());
        assertEquals(30, merged.getMaxTurnsPerRun());
        assertEquals(40, merged.getMaxToolCallsPerRun());
    }

    @Test
    void webSearchOnlySaveKeepsModel() throws Exception {
        UiConfigStore.save(temp, "keep-me", "https://example.com/v1", "ui-model", "dashscope", 12, 0, 30, 40);
        UiConfigStore.save(temp, null, null, null, null, 0, 0, 0, 0, "tvly-from-ui", "https://api.tavily.com");
        AgentRuntimeConfig merged = UiConfigStore.mergeWithEnvironment(temp);
        assertEquals("keep-me", merged.getApiKey());
        assertEquals("ui-model", merged.getModel());
        assertEquals(12, merged.getMaxContextTurns());
        assertEquals("tvly-from-ui", merged.getWebSearchApiKey());
        assertEquals("https://api.tavily.com", merged.getWebSearchBaseUrl());
    }
}
