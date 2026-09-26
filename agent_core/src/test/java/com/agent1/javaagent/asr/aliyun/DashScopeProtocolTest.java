package com.agent1.javaagent.asr.aliyun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class DashScopeProtocolTest {
    @Test
    void runTaskMessage_containsRequiredFields() throws Exception {
        String msg = DashScopeProtocol.buildRunTaskMessageText("abc123taskid", 16000, "fun-asr-realtime");
        JsonNode root = DashScopeProtocol.parseJson(msg);

        JsonNode header = root.get("header");
        JsonNode payload = root.get("payload");
        JsonNode parameters = payload.get("parameters");

        assertEquals("run-task", header.get("action").asText());
        assertEquals("abc123taskid", header.get("task_id").asText());
        assertEquals("duplex", header.get("streaming").asText());
        assertEquals("audio", payload.get("task_group").asText());
        assertEquals("asr", payload.get("task").asText());
        assertEquals("recognition", payload.get("function").asText());
        assertEquals("fun-asr-realtime", payload.get("model").asText());
        assertEquals("pcm", parameters.get("format").asText());
        assertEquals(16000, parameters.get("sample_rate").asInt());
        assertTrue(payload.get("input").isObject());
    }

    @Test
    void finishTaskMessage_containsRequiredFields() throws Exception {
        String msg = DashScopeProtocol.buildFinishTaskMessageText("xyz789taskid");
        JsonNode root = DashScopeProtocol.parseJson(msg);

        JsonNode header = root.get("header");
        JsonNode payload = root.get("payload");
        assertEquals("finish-task", header.get("action").asText());
        assertEquals("xyz789taskid", header.get("task_id").asText());
        assertEquals("duplex", header.get("streaming").asText());
        assertTrue(payload.get("input").isObject());
    }
}
