package com.agent1.android.productivity.logic.business

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageLogSummaryTest {

    @Test
    fun missingFileIsEmpty() {
        val missing = Files.createTempDirectory("usage-missing").resolve("events.jsonl")
        val overview = UsageLogSummary.summarize(missing)
        assertFalse(overview.hasUsage)
        assertEquals(0L, overview.totalTokens)
        assertNull(overview.cacheHitPercent)
    }

    @Test
    fun sumsUsageLinesAndIgnoresOtherEvents() {
        val file = Files.createTempFile("usage", ".jsonl")
        file.toFile().writeText(
            """
            {"type":"usage","input_tokens":1000,"output_tokens":200,"cached_tokens":250}
            {"type":"tool_call","tool_name":"run_js","input_tokens":999}
            {"type":"usage","input_tokens":500,"output_tokens":50}

            """.trimIndent(),
        )
        val overview = UsageLogSummary.summarize(file)
        assertTrue(overview.hasUsage)
        assertEquals(2, overview.usageEvents)
        assertEquals(1500L, overview.inputTokens)
        assertEquals(250L, overview.outputTokens)
        assertEquals(250L, overview.cachedTokens)
        assertEquals(1750L, overview.totalTokens)
        assertEquals(16, overview.cacheHitPercent)
        assertEquals("", overview.byModel.single().modelId)
    }

    @Test
    fun attributesUsageToTheRunModel() {
        val file = Files.createTempFile("usage-model", ".jsonl")
        file.toFile().writeText(
            """
            {"type":"run_started","runId":"r1","model":"deepseek-flash"}
            {"type":"usage","runId":"r1","input_tokens":1000000,"output_tokens":1000000,"cached_tokens":0}
            {"type":"run_started","runId":"r2","model":"other-model"}
            {"type":"usage","runId":"r2","input_tokens":2000,"output_tokens":0,"cached_tokens":0}
            """.trimIndent(),
        )
        val overview = UsageLogSummary.summarize(file)
        assertEquals(listOf("deepseek-flash", "other-model"), overview.byModel.map { it.modelId })
        // 另一段是别的模型，仍按当前的 DeepSeek Flash 一起折成人民币。
        val estimate = UsageCost.estimate(overview, currentModelId = "deepseek-flash")
        assertTrue(estimate.hasCost)
        assertEquals("约 ¥10.00", estimate.headline)
        assertTrue(estimate.caveat.contains("高峰"))
        assertTrue(estimate.caveat.contains("参考"))
        assertFalse(estimate.caveat.contains("7.1"))
    }

    @Test
    fun cacheHitUsesTheCheaperCnyRateOfTheCurrentModel() {
        val usage = UsageOverview(
            inputTokens = 1_000_000,
            outputTokens = 0,
            cachedTokens = 500_000,
            usageEvents = 1,
        )
        val estimate = UsageCost.estimate(usage, currentModelId = "deepseek-flash")
        assertEquals("约 ¥1.02", estimate.headline)
        assertTrue(estimate.lines.any { it.label == "缓存命中" && it.detail.contains("¥0.04/百万") })
    }

    @Test
    fun qwenDomesticPriceStaysInCny() {
        val usage = UsageOverview(
            inputTokens = 1_000_000,
            outputTokens = 0,
            cachedTokens = 0,
            usageEvents = 1,
        )
        val estimate = UsageCost.estimate(usage, currentModelId = "qwen3.7-flash")
        assertEquals("约 ¥0.20", estimate.headline)
        assertFalse(estimate.caveat.contains("7.1"))
    }

    @Test
    fun formatsLargeCountsInMillions() {
        assertEquals("1.24 百万", UsageCost.formatMillions(1_240_000))
        assertEquals("32 万", UsageCost.formatMillions(320_000))
    }
}
