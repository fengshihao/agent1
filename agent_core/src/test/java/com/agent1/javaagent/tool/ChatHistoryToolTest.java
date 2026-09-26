package com.agent1.javaagent.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatHistoryToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void matchesToolNameCaseInsensitively() throws Exception {
        List<AgentMessage> transcript = List.of(
            AgentMessage.assistant("", List.of(new ToolCall("c1", "read_file", "{}")))
        );
        ChatHistoryTool tool = new ChatHistoryTool(() -> transcript);

        ObjectNode params = MAPPER.createObjectNode();
        params.put("query", "READ_FILE");

        ToolExecutionResult result = tool.execute("id", params, new CancellationToken(), u -> {});

        assertTrue(result.getText().contains("role=assistant"));
        assertTrue(result.getText().contains("read_file"));
    }

    @Test
    void snippetIsAtMost300Chars() {
        String longContent = "x".repeat(400);
        String described = ChatHistoryTool.describe(AgentMessage.user(longContent));
        String snippet = ChatHistoryTool.snippetAroundQuery(described, "xxx", ChatHistoryTool.SNIPPET_MAX_CHARS);
        assertTrue(snippet.length() <= ChatHistoryTool.SNIPPET_MAX_CHARS + 2);
    }

    @Test
    void respectsLimit() throws Exception {
        List<AgentMessage> transcript = List.of(
            AgentMessage.user("alpha one"),
            AgentMessage.user("alpha two"),
            AgentMessage.user("alpha three")
        );
        ChatHistoryTool tool = new ChatHistoryTool(() -> transcript);

        ObjectNode params = MAPPER.createObjectNode();
        params.put("query", "alpha");
        params.put("limit", 2);

        ToolExecutionResult result = tool.execute("id", params, new CancellationToken(), u -> {});

        assertTrue(result.getText().contains("#0"));
        assertTrue(result.getText().contains("#1"));
        assertFalse(result.getText().contains("#2"));
    }
}
