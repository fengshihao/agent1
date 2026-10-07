package com.agent1.javaagent.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventJsonlRetentionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temp;

    @Test
    void pruneDropsLinesOlderThanRetention() throws Exception {
        Path logFile = temp.resolve("events.jsonl");
        String oldTs = Instant.now().minus(Duration.ofDays(3)).toString();
        String newTs = Instant.now().minus(Duration.ofHours(1)).toString();
        Files.writeString(
            logFile,
            line(oldTs, "old") + "\n" + line(newTs, "new") + "\n",
            StandardCharsets.UTF_8
        );

        int removed = EventJsonlRetention.prune(logFile, MAPPER, Duration.ofDays(2));

        assertEquals(1, removed);
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"new\""));
    }

    @Test
    void pruneKeepsLineWithoutTs() throws Exception {
        Path logFile = temp.resolve("events.jsonl");
        Files.writeString(logFile, "{\"type\":\"legacy\"}\n", StandardCharsets.UTF_8);

        int removed = EventJsonlRetention.prune(logFile, MAPPER, Duration.ofDays(2));

        assertEquals(0, removed);
        assertEquals(1, Files.readAllLines(logFile).size());
    }

    private static String line(String ts, String type) {
        return "{\"ts\":\"" + ts + "\",\"type\":\"" + type + "\"}";
    }
}
