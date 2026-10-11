package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.log.query.EventLogReader
import java.nio.file.Path

/** 某一模型在日志里累计的用量。`modelId` 为空表示该条 usage 没能对上 `run_started`。 */
data class ModelUsageSlice(
    val modelId: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val cachedTokens: Long,
)

/** 全部会话累计的模型用量（读 `logs/events.jsonl` 里的 `usage` 行）。 */
data class UsageOverview(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cachedTokens: Long = 0,
    val usageEvents: Int = 0,
    val byModel: List<ModelUsageSlice> = emptyList(),
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens

    val hasUsage: Boolean
        get() = usageEvents > 0

    /** 缓存 token 占输入 token 的百分比；没有输入时为 null。 */
    val cacheHitPercent: Int?
        get() = if (inputTokens > 0) {
            ((cachedTokens * 100.0) / inputTokens).toInt().coerceIn(0, 100)
        } else {
            null
        }
}

object UsageLogSummary {

    fun summarize(eventsFile: Path): UsageOverview {
        val entries = EventLogReader().readAll(eventsFile)
        val modelByRun = HashMap<String, String>()
        for (entry in entries) {
            if (entry.type() != "run_started") continue
            val model = entry.fieldText("model")
            if (model.isNotBlank()) modelByRun[entry.runId()] = model
        }
        var input = 0L
        var output = 0L
        var cached = 0L
        var events = 0
        val slices = LinkedHashMap<String, LongArray>()
        for (entry in entries) {
            if (entry.type() != "usage") continue
            val inTokens = entry.fieldLong("input_tokens")
            val outTokens = entry.fieldLong("output_tokens")
            val cacheTokens = entry.fieldLong("cached_tokens")
            input += inTokens
            output += outTokens
            cached += cacheTokens
            events += 1
            val modelId = modelByRun[entry.runId()].orEmpty()
            val bucket = slices.getOrPut(modelId) { LongArray(3) }
            bucket[0] += inTokens
            bucket[1] += outTokens
            bucket[2] += cacheTokens
        }
        return UsageOverview(
            inputTokens = input,
            outputTokens = output,
            cachedTokens = cached,
            usageEvents = events,
            byModel = slices.map { (modelId, bucket) ->
                ModelUsageSlice(
                    modelId = modelId,
                    inputTokens = bucket[0],
                    outputTokens = bucket[1],
                    cachedTokens = bucket[2],
                )
            },
        )
    }
}
