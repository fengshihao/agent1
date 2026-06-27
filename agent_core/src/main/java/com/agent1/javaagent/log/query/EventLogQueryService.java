package com.agent1.javaagent.log.query;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 05-日志查询：只读分析 {@code events.jsonl}。 */
public final class EventLogQueryService {

    private static final Set<String> TERMINAL_TYPES = Set.of(
        "run_completed",
        "run_failed",
        "run_cancelled",
        "run_paused"
    );

    private final EventLogReader reader;

    public EventLogQueryService() {
        this(new EventLogReader());
    }

    public EventLogQueryService(EventLogReader reader) {
        this.reader = reader;
    }

    public List<EventLogEntry> findEvents(Path eventsFile, EventLogFilter filter) {
        EventLogFilter effective = filter == null ? EventLogFilter.builder().build() : filter;
        List<EventLogEntry> all = reader.readAll(eventsFile);
        List<EventLogEntry> out = new ArrayList<>();
        for (EventLogEntry entry : all) {
            if (matchesGeneral(entry, effective)) {
                out.add(entry);
            }
        }
        return out;
    }

    public List<FailedToolCall> listFailedToolCalls(Path eventsFile, EventLogFilter filter) {
        EventLogFilter effective = filter == null ? EventLogFilter.builder().build() : filter;
        List<EventLogEntry> all = reader.readAll(eventsFile);
        Map<String, String> toolNamesByCallId = new HashMap<>();
        List<FailedToolCall> failed = new ArrayList<>();

        for (EventLogEntry entry : all) {
            if (!matchesGeneral(entry, effective)) {
                continue;
            }
            if ("tool_call".equals(entry.type())) {
                toolNamesByCallId.put(
                    entry.fieldText("tool_call_id"),
                    entry.fieldText("tool_name")
                );
            }
        }

        for (EventLogEntry entry : all) {
            if (!matchesGeneral(entry, effective)) {
                continue;
            }
            if (!"tool_result".equals(entry.type()) || !entry.fieldBool("is_error")) {
                continue;
            }
            String toolCallId = entry.fieldText("tool_call_id");
            String toolName = toolNamesByCallId.getOrDefault(toolCallId, "");
            if (effective.toolName().isPresent() && !effective.toolName().get().equals(toolName)) {
                continue;
            }
            failed.add(
                new FailedToolCall(
                    entry.sessionId(),
                    entry.runId(),
                    toolCallId,
                    toolName,
                    entry.fieldText("error_message")
                )
            );
        }
        return failed;
    }

    public List<RunAggregate> summarizeRuns(Path eventsFile, EventLogFilter filter) {
        return aggregateByRun(findEvents(eventsFile, filter));
    }

    public List<RunAggregate> listChildRunAggregates(Path eventsFile, String parentRunId) {
        if (parentRunId == null || parentRunId.isBlank()) {
            return List.of();
        }
        List<EventLogEntry> all = reader.readAll(eventsFile);
        Map<String, List<EventLogEntry>> byRun = new LinkedHashMap<>();
        for (EventLogEntry entry : all) {
            if (!parentRunId.equals(entry.parentRunId())) {
                continue;
            }
            byRun.computeIfAbsent(entry.runId(), ignored -> new ArrayList<>()).add(entry);
        }
        List<RunAggregate> aggregates = new ArrayList<>();
        for (List<EventLogEntry> runEvents : byRun.values()) {
            aggregates.add(buildAggregate(runEvents));
        }
        return aggregates;
    }

    private static List<RunAggregate> aggregateByRun(List<EventLogEntry> entries) {
        Map<String, List<EventLogEntry>> byRun = new LinkedHashMap<>();
        for (EventLogEntry entry : entries) {
            byRun.computeIfAbsent(entry.runId(), ignored -> new ArrayList<>()).add(entry);
        }
        List<RunAggregate> out = new ArrayList<>();
        for (List<EventLogEntry> runEvents : byRun.values()) {
            out.add(buildAggregate(runEvents));
        }
        return out;
    }

    private static RunAggregate buildAggregate(List<EventLogEntry> runEvents) {
        if (runEvents.isEmpty()) {
            throw new IllegalArgumentException("runEvents empty");
        }
        EventLogEntry first = runEvents.get(0);
        int failedTools = 0;
        long inputTokens = 0;
        long outputTokens = 0;
        String terminalType = "";
        Instant start = null;
        Instant end = null;

        for (EventLogEntry entry : runEvents) {
            if ("tool_result".equals(entry.type()) && entry.fieldBool("is_error")) {
                failedTools += 1;
            }
            if ("usage".equals(entry.type())) {
                inputTokens += entry.fieldLong("input_tokens");
                outputTokens += entry.fieldLong("output_tokens");
            }
            if (TERMINAL_TYPES.contains(entry.type())) {
                terminalType = entry.type();
            }
            Instant ts = parseTs(entry.ts());
            if (ts != null) {
                if (start == null || ts.isBefore(start)) {
                    start = ts;
                }
                if (end == null || ts.isAfter(end)) {
                    end = ts;
                }
            }
        }

        long durationMs = 0;
        if (start != null && end != null && !end.isBefore(start)) {
            durationMs = end.toEpochMilli() - start.toEpochMilli();
        }

        return new RunAggregate(
            first.sessionId(),
            first.runId(),
            first.parentRunId(),
            first.agentId(),
            runEvents.size(),
            failedTools,
            inputTokens,
            outputTokens,
            durationMs,
            terminalType
        );
    }

    private static boolean matchesGeneral(EventLogEntry entry, EventLogFilter filter) {
        if (filter.sessionId().isPresent() && !filter.sessionId().get().equals(entry.sessionId())) {
            return false;
        }
        if (filter.runId().isPresent() && !filter.runId().get().equals(entry.runId())) {
            return false;
        }
        if (filter.parentRunId().isPresent() && !filter.parentRunId().get().equals(entry.parentRunId())) {
            return false;
        }
        if (filter.failedOnly()) {
            return "tool_result".equals(entry.type()) && entry.fieldBool("is_error");
        }
        if (filter.toolName().isPresent()) {
            return "tool_call".equals(entry.type())
                && filter.toolName().get().equals(entry.fieldText("tool_name"));
        }
        return true;
    }

    private static Instant parseTs(String ts) {
        if (ts == null || ts.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(ts);
        } catch (Exception ignored) {
            return null;
        }
    }
}
