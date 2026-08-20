package com.agent1.javaagent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolResultTruncatorTest {

    @Test
    void truncateOldTurns_replacesOldToolResultOver280() {
        String oldResult = "x".repeat(281);
        List<AgentMessage> messages = List.of(
            AgentMessage.user("first"),
            AgentMessage.assistant("", List.of(new ToolCall("c1", "read_file", "{}"))),
            AgentMessage.toolResult("c1", oldResult, false),
            AgentMessage.user("second"),
            AgentMessage.assistant("latest", List.of())
        );

        List<AgentMessage> trimmed = ToolResultTruncator.truncateOldTurns(messages, 280);
        assertEquals(5, trimmed.size());
        assertTrue(trimmed.get(2).getContent().contains("已省略"));
        assertTrue(trimmed.get(2).getContent().contains("281"));
        assertEquals("c1", trimmed.get(2).getToolCallId());
        assertEquals("latest", trimmed.get(4).getContent());
    }

    @Test
    void truncateOldTurns_keepsLatestTurnFullText() {
        String latest = "y".repeat(400);
        List<AgentMessage> messages = List.of(
            AgentMessage.user("first"),
            AgentMessage.assistant("a", List.of()),
            AgentMessage.user("second"),
            AgentMessage.assistant("", List.of(new ToolCall("c2", "read_file", "{}"))),
            AgentMessage.toolResult("c2", latest, false)
        );

        List<AgentMessage> trimmed = ToolResultTruncator.truncateOldTurns(messages, 280);
        assertEquals(latest, trimmed.get(4).getContent());
    }

    @Test
    void truncateOldTurns_zeroMeansNoOp() {
        List<AgentMessage> messages = List.of(AgentMessage.toolResult("c", "z".repeat(300), false));
        assertEquals(1, ToolResultTruncator.truncateOldTurns(messages, 0).size());
        assertEquals(300, ToolResultTruncator.truncateOldTurns(messages, 0).get(0).getContent().length());
    }
}
