package com.agent1.javaagent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolCallPairingRepairTest {

    @Test
    void insertsMissingResultBeforeTheNextAssistantMessage() {
        List<AgentMessage> repaired = ToolCallPairingRepair.ensureResults(List.of(
            AgentMessage.assistant("", List.of(new ToolCall("c1", "edit_file", "{}"))),
            AgentMessage.assistant("错误: 工具调用次数超过上限（48）", List.of()),
            AgentMessage.user("继续")
        ));
        assertEquals(AgentMessage.ROLE_TOOL_RESULT, repaired.get(1).getRole());
        assertEquals("c1", repaired.get(1).getToolCallId());
        assertTrue(repaired.get(1).isError());
        assertEquals("错误: 工具调用次数超过上限（48）", repaired.get(2).getContent());
    }

    @Test
    void reordersToolResultsToMatchToolCallSequence() {
        List<AgentMessage> repaired = ToolCallPairingRepair.ensureResults(List.of(
            AgentMessage.assistant("", List.of(
                new ToolCall("c1", "a", "{}"),
                new ToolCall("c2", "b", "{}")
            )),
            AgentMessage.toolResult("c2", "second", false),
            AgentMessage.toolResult("c1", "first", false)
        ));
        assertEquals("c1", repaired.get(1).getToolCallId());
        assertEquals("first", repaired.get(1).getContent());
        assertEquals("c2", repaired.get(2).getToolCallId());
        assertEquals("second", repaired.get(2).getContent());
    }

    @Test
    void leavesAlreadyPairedCallsUntouched() {
        AgentMessage result = AgentMessage.toolResult("c1", "ok", false);
        List<AgentMessage> input = List.of(
            AgentMessage.assistant("", List.of(new ToolCall("c1", "edit_file", "{}"))),
            result
        );
        List<AgentMessage> repaired = ToolCallPairingRepair.ensureResults(input);
        assertEquals(2, repaired.size());
        assertEquals("ok", repaired.get(1).getContent());
    }
}
