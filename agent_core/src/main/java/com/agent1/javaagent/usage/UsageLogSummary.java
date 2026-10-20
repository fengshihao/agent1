package com.agent1.javaagent.usage;

import com.agent1.javaagent.log.query.EventLogEntry;
import com.agent1.javaagent.log.query.EventLogReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从 events.jsonl 聚合 usage（与 Android {@code UsageLogSummary} 对齐）。 */
public final class UsageLogSummary {

    private UsageLogSummary() {
    }

    public static UsageOverview summarize(Path eventsFile) {
        List<EventLogEntry> entries = new EventLogReader().readAll(eventsFile);
        Map<String, String> modelByRun = new HashMap<>();
        for (EventLogEntry entry : entries) {
            if (!"run_started".equals(entry.type())) {
                continue;
            }
            String model = entry.fieldText("model");
            if (!model.isBlank()) {
                modelByRun.put(entry.runId(), model);
            }
        }
        long input = 0;
        long output = 0;
        long cached = 0;
        int events = 0;
        Map<String, long[]> slices = new LinkedHashMap<>();
        for (EventLogEntry entry : entries) {
            if (!"usage".equals(entry.type())) {
                continue;
            }
            long inTokens = entry.fieldLong("input_tokens");
            long outTokens = entry.fieldLong("output_tokens");
            long cacheTokens = entry.fieldLong("cached_tokens");
            input += inTokens;
            output += outTokens;
            cached += cacheTokens;
            events += 1;
            String modelId = modelByRun.getOrDefault(entry.runId(), "");
            long[] bucket = slices.computeIfAbsent(modelId, k -> new long[3]);
            bucket[0] += inTokens;
            bucket[1] += outTokens;
            bucket[2] += cacheTokens;
        }
        List<ModelUsageSlice> byModel = new ArrayList<>();
        for (Map.Entry<String, long[]> e : slices.entrySet()) {
            long[] bucket = e.getValue();
            byModel.add(new ModelUsageSlice(e.getKey(), bucket[0], bucket[1], bucket[2]));
        }
        return new UsageOverview(input, output, cached, events, byModel);
    }
}
