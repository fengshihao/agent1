package com.agent1.javaagent.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.AgentState;
import com.agent1.javaagent.event.AgentEvent;
import com.agent1.javaagent.event.AgentEventType;
import com.agent1.javaagent.event.EventPayloads;
import com.agent1.javaagent.model.AgentMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentEventJsonlBridgeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temp;

    @Test
    void agentLifecycleWritesRunStartedAndCompleted() throws Exception {
        Path logFile = temp.resolve("events.jsonl");
        RunLogContext ctx = new RunLogContext("s1", "r1", "", "main");
        AgentEventJsonlBridge bridge = new AgentEventJsonlBridge(ctx, logFile);

        var snap = new AgentState("sys", "qwen3.5-flash", List.of(), List.of()).snapshot();
        bridge.onEvent(new AgentEvent(AgentEventType.AGENT_START, snap));
        bridge.onEvent(new AgentEvent(AgentEventType.AGENT_END, new EventPayloads.AgentEnd(List.of())));

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(2, lines.size());
        JsonNode start = MAPPER.readTree(lines.get(0));
        JsonNode end = MAPPER.readTree(lines.get(1));
        assertEquals("run_started", start.get("type").asText());
        assertEquals(1, start.get("seq").asInt());
        assertEquals("run_completed", end.get("type").asText());
        assertEquals(2, end.get("seq").asInt());
        assertEquals("ok", end.get("status").asText());
    }
}
