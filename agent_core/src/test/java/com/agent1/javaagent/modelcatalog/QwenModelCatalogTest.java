package com.agent1.javaagent.modelcatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import org.junit.jupiter.api.Test;

class QwenModelCatalogTest {

    @Test
    void qwen37FlashMatchesOfficialContextLimits() {
        QwenModelInfo flash = QwenModelCatalog.findByModelId("qwen3.7-flash").orElseThrow();
        assertEquals(1_000_000L, flash.getContextWindowTokens());
        assertEquals(991_808L, flash.getMaxInputTokens());
        assertEquals(131_072L, flash.getMaxOutputTokens());
        assertEquals(262_144L, flash.getMaxThinkingChainTokens());
        assertTrue(flash.isFunctionCalling());
    }

    @Test
    void snapshotModelIdResolvesToSameCatalogEntry() {
        assertTrue(QwenModelCatalog.findByModelId("qwen3.7-flash-2026-07-15").isPresent());
    }

    @Test
    void runtimeSummaryLinksCurrentModelToCatalog() {
        AgentRuntimeConfig config = AgentRuntimeConfig.builder()
            .apiKey("sk-test")
            .model("qwen3.7-flash")
            .build();
        RuntimeConfigSummary summary = RuntimeConfigSummary.from(config);
        assertEquals("qwen3.7-flash", summary.getModelId());
        assertTrue(summary.getCatalogMatch().isPresent());
        assertEquals("Qwen3.7 Flash", summary.getCatalogMatch().get().getDisplayName());
    }
}
