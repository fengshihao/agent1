package com.agent1.javaagent.tool.anno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class AnnotatedToolsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void generatesSchemaAndRejectsWrongType() throws Exception {
        AgentTool tool = AnnotatedTools.from(new EchoTool()).get(0);
        assertEquals("echo", tool.name());
        assertTrue(tool.parametersSchema().get("required").toString().contains("text"));

        ObjectNode bad = MAPPER.createObjectNode();
        bad.put("text", 1);
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> tool.execute("c1", bad, new CancellationToken(), update -> { })
        );
        assertTrue(error.getMessage().contains("应为字符串"));
    }

    @Test
    void timeoutOnlyRaisesAndCancellationIsInjected() throws Exception {
        AgentTool tool = AnnotatedTools.from(new SlowTool()).get(0);
        assertEquals(60_000L, tool.suggestedTimeoutMs(MAPPER.createObjectNode(), 1_000L));
        assertEquals(90_000L, tool.suggestedTimeoutMs(MAPPER.createObjectNode(), 90_000L));

        String present = tool.execute("c2", MAPPER.createObjectNode(), new CancellationToken(), update -> { })
            .getText();
        assertEquals("present", present);

        CancellationToken token = new CancellationToken();
        token.cancel();
        String text = tool.execute("c3", MAPPER.createObjectNode(), token, update -> { }).getText();
        assertEquals("错误：执行已取消", text);
    }

    @Test
    void passesThroughControlFlowResultAndSchemaOverride() throws Exception {
        AgentTool tool = AnnotatedTools.from(new AskShape()).get(0);
        assertEquals("array", tool.parametersSchema().path("properties").path("questions").path("type").asText());
        ToolExecutionResult result = tool.execute(
            "c3",
            MAPPER.createObjectNode().put("title", "hi"),
            new CancellationToken(),
            update -> { }
        );
        assertTrue(result.stopRunWaitingUser());
        assertEquals("paused", result.getText());
    }

    public static final class EchoTool {
        @Tool(name = "echo", description = "echo")
        public String echo(@ToolParam(name = "text", description = "text") String text) {
            return text;
        }
    }

    public static final class SlowTool {
        @Tool(name = "slow", description = "slow", timeoutMs = 60_000L)
        public String slow(CancellationToken token) {
            return token == null ? "missing" : "present";
        }
    }

    public static final class AskShape implements ToolSchemaSource {
        @Tool(name = "ask_shape", description = "ask")
        public ToolExecutionResult ask(@ToolParam(name = "title", required = false) String title) {
            return ToolExecutionResult.waitingForUser("paused", MAPPER.createObjectNode().put("title", title));
        }

        @Override
        public JsonNode parametersSchema(String toolName) {
            ObjectNode schema = MAPPER.createObjectNode();
            schema.put("type", "object");
            ObjectNode questions = MAPPER.createObjectNode();
            questions.put("type", "array");
            questions.set("items", MAPPER.createObjectNode().put("type", "object"));
            ObjectNode properties = MAPPER.createObjectNode();
            properties.set("questions", questions);
            schema.set("properties", properties);
            return schema;
        }
    }
}
