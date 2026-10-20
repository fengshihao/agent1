package com.agent1.javaagent.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UsageLogSummaryTest {

    @TempDir
    Path temp;

    @Test
    void missingFileIsEmpty() {
        Path missing = temp.resolve("events.jsonl");
        UsageOverview overview = UsageLogSummary.summarize(missing);
        assertFalse(overview.hasUsage());
        assertEquals(0L, overview.totalTokens());
        assertNull(overview.cacheHitPercent());
    }

    @Test
    void sumsUsageLinesAndIgnoresOtherEvents() throws Exception {
        Path file = temp.resolve("events.jsonl");
        Files.writeString(
            file,
            """
            {"type":"usage","input_tokens":1000,"output_tokens":200,"cached_tokens":250}
            {"type":"tool_call","tool_name":"run_js","input_tokens":999}
            {"type":"usage","input_tokens":500,"output_tokens":50}

            """
        );
        UsageOverview overview = UsageLogSummary.summarize(file);
        assertTrue(overview.hasUsage());
        assertEquals(2, overview.usageEvents());
        assertEquals(1500L, overview.inputTokens());
        assertEquals(250L, overview.outputTokens());
        assertEquals(250L, overview.cachedTokens());
        assertEquals(1750L, overview.totalTokens());
        assertEquals(16, overview.cacheHitPercent());
        assertEquals("", overview.byModel().get(0).modelId());
    }

    @Test
    void attributesUsageToRunModel() throws Exception {
        Path file = temp.resolve("events.jsonl");
        Files.writeString(
            file,
            """
            {"type":"run_started","runId":"r1","model":"deepseek-flash"}
            {"type":"usage","runId":"r1","input_tokens":1000000,"output_tokens":1000000,"cached_tokens":0}
            {"type":"run_started","runId":"r2","model":"other-model"}
            {"type":"usage","runId":"r2","input_tokens":2000,"output_tokens":0,"cached_tokens":0}
            """
        );
        UsageOverview overview = UsageLogSummary.summarize(file);
        assertEquals(
            List.of("deepseek-flash", "other-model"),
            overview.byModel().stream().map(ModelUsageSlice::modelId).toList()
        );
    }
}
