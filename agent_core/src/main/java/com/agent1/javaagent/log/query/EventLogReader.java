package com.agent1.javaagent.log.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 顺序读取 {@code events.jsonl}，不修改文件。 */
public final class EventLogReader {

    private final ObjectMapper mapper;

    public EventLogReader() {
        this(new ObjectMapper());
    }

    public EventLogReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<EventLogEntry> readAll(Path eventsFile) {
        if (eventsFile == null || !Files.isRegularFile(eventsFile)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(eventsFile, StandardCharsets.UTF_8);
            List<EventLogEntry> entries = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) {
                    continue;
                }
                JsonNode root = mapper.readTree(line);
                entries.add(new EventLogEntry(i + 1, root));
            }
            return entries;
        } catch (IOException e) {
            throw new IllegalStateException("read events failed: " + eventsFile, e);
        }
    }
}
