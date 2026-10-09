package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranscriptCodecTest {

    private final TranscriptCodec codec = new TranscriptCodec(new ObjectMapper());

    @Test
    void roundTripPreservesReasoning() {
        AgentMessage original = AgentMessage.assistant(
            "answer",
            "step one\nstep two",
            List.of()
        );
        String line = codec.toLine("run-2", original);
        AgentMessage restored = codec.fromLine(line);
        assertEquals("answer", restored.getContent());
        assertEquals("step one\nstep two", restored.getReasoningContent());
    }

    @Test
    void roundTripPreservesToolCalls() {
        AgentMessage original = AgentMessage.assistant(
            "calling tool",
            List.of(new ToolCall("tc1", "read_file", "{\"path\":\"a.txt\"}"))
        );
        String line = codec.toLine("run-1", original);
        AgentMessage restored = codec.fromLine(line);
        assertEquals(original.getRole(), restored.getRole());
        assertEquals(original.getContent(), restored.getContent());
        assertEquals(1, restored.getToolCalls().size());
        assertEquals("read_file", restored.getToolCalls().get(0).getName());
        assertEquals("{\"path\":\"a.txt\"}", restored.getToolCalls().get(0).getArgumentsJson());
    }

    @Test
    void fromLine_assignsUniqueIdsWhenTranscriptHasBlankOrNullLiteralIds() {
        String line = """
            {"role":"assistant","content":"x","createdAt":1,"runId":"r1","error":false,\
            "toolCalls":[\
            {"id":"","name":"read_file","argumentsJson":"{}"},\
            {"id":"null","name":"write_file","argumentsJson":"{}"}\
            ]}""";
        AgentMessage restored = codec.fromLine(line);
        assertEquals(2, restored.getToolCalls().size());
        String id0 = restored.getToolCalls().get(0).getId();
        String id1 = restored.getToolCalls().get(1).getId();
        assertTrue(id0.startsWith("tool_call_"));
        assertTrue(id1.startsWith("tool_call_"));
        assertNotEquals(id0, id1);
    }
}
