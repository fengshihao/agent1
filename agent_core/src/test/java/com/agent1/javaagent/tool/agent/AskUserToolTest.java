package com.agent1.javaagent.tool.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class AskUserToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AskUserTool tool = new AskUserTool();

    @Test
    void validQuestionsReturnsWaitingForUserWithDetails() throws Exception {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("title", "广西行程");
        params.put("allow_freeform_reply", true);
        ArrayNode questions = MAPPER.createArrayNode();
        ObjectNode q1 = MAPPER.createObjectNode();
        q1.put("id", "origin");
        q1.put("prompt", "从哪个城市出发？");
        q1.put("type", "text");
        q1.put("required", true);
        questions.add(q1);
        params.set("questions", questions);

        ToolExecutionResult result = tool.execute("tc1", params, new CancellationToken(), u -> {
        });

        assertTrue(result.stopRunWaitingUser());
        assertTrue(result.getText().contains("已向用户提出"));
        assertEquals("ask_user_request", result.getDetails().path("kind").asText());
        assertEquals(1, result.getDetails().path("questions").size());
    }

    @Test
    void choiceQuestionRequiresOptions() {
        ObjectNode params = MAPPER.createObjectNode();
        ArrayNode questions = MAPPER.createArrayNode();
        ObjectNode q1 = MAPPER.createObjectNode();
        q1.put("id", "style");
        q1.put("prompt", "偏好？");
        q1.put("type", "single_choice");
        questions.add(q1);
        params.set("questions", questions);

        ToolExecutionResult result = tool.execute("tc1", params, new CancellationToken(), u -> {
        });

        assertFalse(result.stopRunWaitingUser());
        assertTrue(result.getText().contains("options"));
    }
}
