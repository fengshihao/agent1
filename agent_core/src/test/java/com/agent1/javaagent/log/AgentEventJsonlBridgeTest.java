package com.agent1.javaagent.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.AgentState;
import com.agent1.javaagent.event.AgentEvent;
import com.agent1.javaagent.event.AgentEventType;
import com.agent1.javaagent.event.EventPayloads;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import com.agent1.javaagent.run.RunState;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.agent.AskUserTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    @Test
    void askUserWritesUserInputRequestedAndRunWaitingUser() throws Exception {
        Path logFile = temp.resolve("events.jsonl");
        RunLogContext ctx = new RunLogContext("s1", "r1", "", "main");
        AgentEventJsonlBridge bridge = new AgentEventJsonlBridge(ctx, logFile);
        bridge.setDeferRunTerminal(true);

        ObjectNode request = MAPPER.createObjectNode();
        request.put("kind", "ask_user_request");
        ArrayNode questions = MAPPER.createArrayNode();
        ObjectNode q = MAPPER.createObjectNode();
        q.put("id", "origin");
        q.put("prompt", "出发地");
        questions.add(q);
        request.set("questions", questions);

        ToolCall call = new ToolCall("tc-ask", AskUserTool.TOOL_NAME, "{}");
        bridge.onEvent(new AgentEvent(AgentEventType.TOOL_EXECUTION_START, new EventPayloads.ToolExecutionStart(call)));
        bridge.onEvent(new AgentEvent(
            AgentEventType.TOOL_EXECUTION_END,
            new EventPayloads.ToolExecutionEnd(
                "tc-ask",
                ToolExecutionResult.waitingForUser("paused", request),
                false,
                null
            )
        ));
        bridge.writeRunTerminal(RunState.WAITING_USER, "等待用户输入", 3);

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(4, lines.size());
        JsonNode requested = MAPPER.readTree(lines.get(2));
        JsonNode terminal = MAPPER.readTree(lines.get(3));
        assertEquals("user_input_requested", requested.get("type").asText());
        assertEquals("origin", requested.get("request").get("questions").get(0).get("id").asText());
        assertEquals("run_waiting_user", terminal.get("type").asText());
        assertEquals("waiting_user", terminal.get("status").asText());
    }

    @Test
    void usageEventWritesUsageLine() throws Exception {
        Path logFile = temp.resolve("events.jsonl");
        RunLogContext ctx = new RunLogContext("s1", "r1", "", "main");
        AgentEventJsonlBridge bridge = new AgentEventJsonlBridge(ctx, logFile);

        var snap = new AgentState("sys", "qwen3.7-flash", List.of(), List.of()).snapshot();
        bridge.onEvent(new AgentEvent(AgentEventType.AGENT_START, snap));
        bridge.onEvent(new AgentEvent(AgentEventType.USAGE, new EventPayloads.Usage(11, 3, 2L)));

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(2, lines.size());
        JsonNode usage = MAPPER.readTree(lines.get(1));
        assertEquals("usage", usage.get("type").asText());
        assertEquals(11, usage.get("input_tokens").asInt());
        assertEquals(3, usage.get("output_tokens").asInt());
        assertEquals(2, usage.get("cached_tokens").asInt());
    }
}
