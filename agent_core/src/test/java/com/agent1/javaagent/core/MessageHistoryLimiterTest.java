package com.agent1.javaagent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageHistoryLimiterTest {

    @Test
    void limitTail_zeroOrNegative_returnsFullCopy() {
        List<AgentMessage> m = List.of(AgentMessage.user("a"), AgentMessage.user("b"));
        assertEquals(2, MessageHistoryLimiter.limitTail(m, 0).size());
        assertEquals(2, MessageHistoryLimiter.limitTail(m, -1).size());
    }

    @Test
    void limitTail_keepsLastN() {
        List<AgentMessage> m = List.of(
            AgentMessage.user("1"),
            AgentMessage.user("2"),
            AgentMessage.user("3")
        );
        List<AgentMessage> t = MessageHistoryLimiter.limitTail(m, 2);
        assertEquals(2, t.size());
        assertEquals("2", t.get(0).getContent());
        assertEquals("3", t.get(1).getContent());
    }

    @Test
    void limitTail_startingAtToolResult_includesPrecedingAssistant() {
        ToolCall tc = new ToolCall("c1", "run_bash", "{}");
        List<AgentMessage> m = List.of(
            AgentMessage.user("old"),
            AgentMessage.assistant("", List.of(tc)),
            AgentMessage.toolResult("c1", "stdout", false),
            AgentMessage.user("new")
        );
        List<AgentMessage> t = MessageHistoryLimiter.limitTail(m, 2);
        assertEquals(3, t.size());
        assertEquals(AgentMessage.ROLE_ASSISTANT, t.get(0).getRole());
        assertEquals(AgentMessage.ROLE_TOOL_RESULT, t.get(1).getRole());
        assertEquals(AgentMessage.ROLE_USER, t.get(2).getRole());
    }
}
