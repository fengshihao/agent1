package com.agent1.javaagent.log.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.log.EventJsonlWriter;
import com.agent1.javaagent.log.RunLogContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventLogQueryServiceTest {

    @TempDir
    Path temp;

    private Path logFile;
    private EventLogQueryService query;

    @BeforeEach
    void setUp() {
        logFile = temp.resolve("events.jsonl");
        query = new EventLogQueryService();
    }

    @Test
    void listsFailedToolCallsWithRunAndToolName() throws Exception {
        writeFailedToolRun("sess-a", "run-fail");

        List<FailedToolCall> failed = query.listFailedToolCalls(logFile, EventLogFilter.builder().build());

        assertEquals(1, failed.size());
        assertEquals("run-fail", failed.get(0).runId());
        assertEquals("sess-a", failed.get(0).sessionId());
        assertEquals("write_file", failed.get(0).toolName());
        assertTrue(failed.get(0).errorMessage().contains("denied"));
    }

    @Test
    void filtersFailedBySessionAndRun() throws Exception {
        writeFailedToolRun("sess-a", "run-fail");
        writeFailedToolRun("sess-b", "run-other");

        List<FailedToolCall> filtered = query.listFailedToolCalls(
            logFile,
            EventLogFilter.builder().sessionId("sess-a").runId("run-fail").build()
        );

        assertEquals(1, filtered.size());
        assertEquals("run-fail", filtered.get(0).runId());
    }

    @Test
    void summarizesChildRunsUnderParentWithTokens() throws Exception {
        EventJsonlWriter writer = new EventJsonlWriter(logFile);
        RunLogContext parent = new RunLogContext("sess1", "runP", "", "main");
        writer.write(parent, "run_started", Map.of());
        writer.write(parent, "run_completed", Map.of("status", "ok"));

        RunLogContext child = new RunLogContext("sess1", "runC", "runP", "explore");
        writer.write(child, "run_started", Map.of());
        writer.write(child, "usage", Map.of("input_tokens", 10L, "output_tokens", 5L));
        writer.write(child, "run_completed", Map.of("status", "ok"));

        List<RunAggregate> children = query.listChildRunAggregates(logFile, "runP");

        assertEquals(1, children.size());
        RunAggregate agg = children.get(0);
        assertEquals("runC", agg.runId());
        assertEquals("runP", agg.parentRunId());
        assertEquals(10L, agg.inputTokens());
        assertEquals(5L, agg.outputTokens());
        assertEquals("run_completed", agg.terminalType());
    }

    @Test
    void queryDoesNotModifyLogFile() throws Exception {
        writeFailedToolRun("sess-a", "run-fail");
        FileTime before = Files.getLastModifiedTime(logFile);
        long sizeBefore = Files.size(logFile);

        query.findEvents(logFile, EventLogFilter.builder().sessionId("sess-a").build());
        query.listFailedToolCalls(logFile, EventLogFilter.builder().build());
        query.listChildRunAggregates(logFile, "runP");

        assertEquals(before, Files.getLastModifiedTime(logFile));
        assertEquals(sizeBefore, Files.size(logFile));
        assertTrue(Files.isReadable(logFile));
    }

    @Test
    void summarizeRunIncludesDurationAndFailedToolCount() throws Exception {
        writeFailedToolRun("sess-a", "run-fail");

        List<RunAggregate> runs = query.summarizeRuns(
            logFile,
            EventLogFilter.builder().runId("run-fail").build()
        );

        assertEquals(1, runs.size());
        RunAggregate agg = runs.get(0);
        assertEquals(1, agg.failedToolCount());
        assertEquals("run_failed", agg.terminalType());
        assertTrue(agg.durationMs() >= 0);
    }

    private void writeFailedToolRun(String sessionId, String runId) {
        EventJsonlWriter writer = new EventJsonlWriter(logFile);
        RunLogContext ctx = new RunLogContext(sessionId, runId, "", "main");
        writer.write(ctx, "run_started", Map.of("model", "qwen"));
        writer.write(
            ctx,
            "tool_call",
            Map.of("tool_name", "write_file", "tool_call_id", "tc1", "tool_args", "{}")
        );
        writer.write(
            ctx,
            "tool_result",
            Map.of(
                "tool_call_id",
                "tc1",
                "result",
                "err",
                "is_error",
                true,
                "error_message",
                "denied",
                "duration_ms",
                12L
            )
        );
        writer.write(ctx, "run_failed", Map.of("error", "tool failed"));
    }
}
