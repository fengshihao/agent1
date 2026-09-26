package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranscriptCodecTest {

    private final TranscriptCodec codec = new TranscriptCodec(new ObjectMapper());

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
}
