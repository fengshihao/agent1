package com.agent1.javaagent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agent1.javaagent.model.AgentMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextTurnLimiterTest {

    @Test
    void limitByUserTurns_keepsLastNUserMessages() {
        List<AgentMessage> messages = List.of(
            AgentMessage.user("u1"),
            AgentMessage.assistant("a1", List.of()),
            AgentMessage.user("u2"),
            AgentMessage.assistant("a2", List.of()),
            AgentMessage.user("u3"),
            AgentMessage.assistant("a3", List.of())
        );
        List<AgentMessage> trimmed = ContextTurnLimiter.limitByUserTurns(messages, 2);
        assertEquals(4, trimmed.size());
        assertEquals("u2", trimmed.get(0).getContent());
        assertEquals("a3", trimmed.get(3).getContent());
    }

    @Test
    void limitByUserTurns_zeroMeansNoLimit() {
        List<AgentMessage> messages = List.of(AgentMessage.user("only"));
        assertEquals(1, ContextTurnLimiter.limitByUserTurns(messages, 0).size());
    }
}
