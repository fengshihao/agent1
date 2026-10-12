package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void fromLine_preservesBlankOrNullLiteralIdsAsIs() {
        String line = """
            {"role":"assistant","content":"x","createdAt":1,"runId":"r1","error":false,\
            "toolCalls":[\
            {"id":"","name":"read_file","argumentsJson":"{}"},\
            {"id":"null","name":"write_file","argumentsJson":"{}"}\
            ]}""";
        AgentMessage restored = codec.fromLine(line);
        assertEquals(2, restored.getToolCalls().size());
        // decode 不改写 id：原样保留，避免与 toolResult 侧错配
        assertEquals("", restored.getToolCalls().get(0).getId());
        assertEquals("null", restored.getToolCalls().get(1).getId());
    }

    @Test
    void fromLine_keepsAssistantToolCallIdPairedWithToolResultId() {
        String assistantLine = """
            {"role":"assistant","content":"x","createdAt":1,"runId":"r1","error":false,\
            "toolCalls":[{"id":"","name":"edit_file","argumentsJson":"{}"}]}""";
        String toolResultLine = """
            {"role":"toolResult","content":"ok","createdAt":2,"runId":"r1","error":false,"toolCallId":""}""";

        AgentMessage assistant = codec.fromLine(assistantLine);
        AgentMessage toolResult = codec.fromLine(toolResultLine);

        // 两侧空 id 必须配对一致，不得在 decode 层各自改写成不同值
        String callId = assistant.getToolCalls().get(0).getId();
        assertEquals("", callId);
        assertEquals(callId, toolResult.getToolCallId());
    }

    @Test
    void fromLine_keepsJsonNullIdPairedWithMissingToolCallId() {
        String assistantLine = """
            {"role":"assistant","content":"x","createdAt":1,"runId":"r1","error":false,\
            "toolCalls":[{"id":null,"name":"edit_file","argumentsJson":"{}"}]}""";
        String toolResultLine = """
            {"role":"toolResult","content":"ok","createdAt":2,"runId":"r1","error":false}""";

        AgentMessage assistant = codec.fromLine(assistantLine);
        AgentMessage toolResult = codec.fromLine(toolResultLine);

        // assistant 侧 JSON null 与 toolResult 侧缺失字段对称归一为 ""，两侧仍配对一致
        assertEquals("", assistant.getToolCalls().get(0).getId());
        assertEquals("", toolResult.getToolCallId());
    }

    @Test
    void fromLine_doesNotFabricateToolCallIdForNonToolRoles() {
        String line = """
            {"role":"user","content":"hi","createdAt":1,"runId":"r1","error":false}""";
        AgentMessage restored = codec.fromLine(line);
        // 非 toolResult 消息保持 null，round-trip 不得引入 toolCallId 字段
        assertNull(restored.getToolCallId());
    }
}
