package com.agent1.javaagent.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ScriptFailureFormatterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void mapsEngineLineToUserLine() throws Exception {
        ScriptEvalFrame frame = new ScriptEvalFrame(
            ScriptEvalFrame.SourceKind.INLINE,
            "",
            1,
            0,
            10
        );
        String json = ScriptFailureFormatter.formatJson(
            frame,
            new RuntimeException("SyntaxError: unexpected token at line 8")
        );
        JsonNode node = MAPPER.readTree(json);
        assertEquals(false, node.path("ok").asBoolean());
        assertEquals(7, node.path("location").path("userLine").asInt());
        assertEquals(8, node.path("location").path("engineLine").asInt());
        assertEquals("inline", node.path("source").path("kind").asText());
    }

    @Test
    void detectsFailureJson() {
        assertTrue(ScriptFailureFormatter.looksLikeFailureJson("{\"ok\":false,\"message\":\"x\"}"));
    }
}
