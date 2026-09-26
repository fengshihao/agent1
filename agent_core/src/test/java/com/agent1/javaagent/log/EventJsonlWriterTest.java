package com.agent1.javaagent.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventJsonlWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temp;

    @Test
    void seqIncrementsAndLinesParse() throws Exception {
        Path logFile = temp.resolve("events.jsonl");
        EventJsonlWriter writer = new EventJsonlWriter(logFile);
        RunLogContext ctx = new RunLogContext("sess-1", "run-1", "", "main");

        writer.write(ctx, "run_started", Map.of("model", "qwen3.5-flash"));
        writer.write(ctx, "model_text_delta", Map.of("delta", "hi"));

        assertEquals(2, ctx.currentSeq());

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(2, lines.size());

        JsonNode first = MAPPER.readTree(lines.get(0));
        assertEquals("sess-1", first.get("sessionId").asText());
        assertEquals("run-1", first.get("runId").asText());
        assertEquals("", first.get("parentRunId").asText());
        assertEquals("main", first.get("agentId").asText());
        assertEquals(1, first.get("seq").asInt());
        assertEquals("run_started", first.get("type").asText());
        assertTrue(first.has("ts"));
        assertEquals("qwen3.5-flash", first.get("model").asText());

        JsonNode second = MAPPER.readTree(lines.get(1));
        assertEquals(2, second.get("seq").asInt());
        assertEquals("model_text_delta", second.get("type").asText());
        assertEquals("hi", second.get("delta").asText());
    }

    @Test
    void truncatesToolPayloads() {
        String longArgs = "x".repeat(300);
        String truncated = (String) EventJsonlWriter.sanitizeField("tool_call", "tool_args", longArgs);
        assertEquals(220 + "...(truncated)".length(), truncated.length());
        assertTrue(truncated.endsWith("...(truncated)"));
    }
}
