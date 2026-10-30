package com.agent1.javaagent.tool.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.todo.TodoStore;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TodoReadToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temp;

    @Test
    void readsPersistedListIncludingCompleted() throws Exception {
        Path session = sessionDir();
        TodoWriteTool write = new TodoWriteTool(session, temp);
        write.execute(
            "w1",
            params(item("1", "写出大纲", "completed"), item("2", "核对格式", "pending")),
            new CancellationToken(),
            update -> { }
        );
        TodoReadTool read = new TodoReadTool(session);
        ToolExecutionResult result = read.execute("r1", MAPPER.createObjectNode(), new CancellationToken(), u -> { });
        assertTrue(result.getText().contains("写出大纲"));
        assertTrue(result.getText().contains("核对格式"));
        assertEquals("todo_list", result.getDetails().path("kind").asText());
        assertEquals(2, result.getDetails().path("todos").size());
    }

    @Test
    void emptyListMessage() throws Exception {
        Path session = sessionDir();
        TodoReadTool read = new TodoReadTool(session);
        ToolExecutionResult result = read.execute("r1", MAPPER.createObjectNode(), new CancellationToken(), u -> { });
        assertTrue(result.getText().contains("无任务清单"));
        assertEquals(0, result.getDetails().path("todos").size());
    }

    private Path sessionDir() throws Exception {
        Path session = temp.resolve("sessions").resolve("s1");
        Files.createDirectories(session);
        return session;
    }

    private static ObjectNode params(ObjectNode... items) {
        ObjectNode params = MAPPER.createObjectNode();
        ArrayNode todos = params.putArray("todos");
        for (ObjectNode item : items) {
            todos.add(item);
        }
        return params;
    }

    private static ObjectNode item(String id, String content, String status) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", id);
        node.put("content", content);
        node.put("status", status);
        return node;
    }
}
