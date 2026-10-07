package com.agent1.javaagent.tool.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.log.AgentDataPaths;
import com.agent1.javaagent.log.RunAuditScope;
import com.agent1.javaagent.log.RunLogContext;
import com.agent1.javaagent.script.AgentToolsScriptBridge;
import com.agent1.javaagent.todo.TodoList;
import com.agent1.javaagent.todo.TodoStore;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TodoWriteToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temp;

    @Test
    void replacesListPersistsAndEmitsEvent() throws Exception {
        Path session = sessionDir();
        AtomicInteger refreshed = new AtomicInteger();
        TodoWriteTool tool = new TodoWriteTool(session, temp, refreshed::incrementAndGet);
        RunAuditScope.bind(temp, new RunLogContext("s1", "r1", "", "main"));
        try {
            ToolExecutionResult result = tool.execute(
                "tc1",
                params(item("1", "写出大纲", "in_progress"), item("2", "核对格式", "pending")),
                new CancellationToken(),
                update -> { }
            );
            assertTrue(result.getText().contains("清单已更新"));
            assertTrue(result.getText().contains("写出大纲"));
            assertEquals("todo_list", result.getDetails().path("kind").asText());
            assertFalse(result.getDetails().path("unchanged").asBoolean());
            assertEquals(1, refreshed.get());

            TodoStore.LoadResult loaded = new TodoStore(session).load();
            assertTrue(loaded.ok());
            assertEquals(2, loaded.list().items().size());
            assertTrue(loaded.list().promptSection().contains("当前任务清单"));

            String events = PathIo.readString(AgentDataPaths.eventsJsonl(temp), StandardCharsets.UTF_8);
            assertTrue(events.contains("\"type\":\"todo_updated\""));
            assertTrue(events.contains("\"open_count\":2"));
            assertTrue(events.contains("\"sessionId\":\"s1\""));
        } finally {
            RunAuditScope.clear();
        }
    }

    @Test
    void unchangedSkipsEventAndRefresh() throws Exception {
        Path session = sessionDir();
        AtomicInteger refreshed = new AtomicInteger();
        TodoWriteTool tool = new TodoWriteTool(session, temp, refreshed::incrementAndGet);
        ObjectNode params = params(item("1", "写出大纲", "completed"));
        tool.execute("tc1", params, new CancellationToken(), update -> { });
        ToolExecutionResult again = tool.execute("tc2", params, new CancellationToken(), update -> { });
        assertTrue(again.getText().contains("清单未变化"));
        assertTrue(again.getDetails().path("unchanged").asBoolean());
        assertEquals(1, refreshed.get());
        String events = PathIo.readString(AgentDataPaths.eventsJsonl(temp), StandardCharsets.UTF_8);
        assertEquals(1, count(events, "todo_updated"));
        assertEquals("", new TodoStore(session).load().list().promptSection());
    }

    @Test
    void twoInProgressRejectedWithoutWrite() throws Exception {
        Path session = sessionDir();
        TodoWriteTool tool = new TodoWriteTool(session, temp);
        ToolExecutionResult result = tool.execute(
            "tc1",
            params(item("1", "甲", "in_progress"), item("2", "乙", "in_progress")),
            new CancellationToken(),
            update -> { }
        );
        assertTrue(result.getText().contains("同时只能有一项"));
        assertFalse(Files.exists(session.resolve(TodoStore.FILE_NAME)));
    }

    @Test
    void emptyListClearsFile() throws Exception {
        Path session = sessionDir();
        TodoWriteTool tool = new TodoWriteTool(session, temp);
        tool.execute("tc1", params(item("1", "写出大纲", "in_progress")), new CancellationToken(), update -> { });
        ToolExecutionResult cleared = tool.execute("tc2", params(), new CancellationToken(), update -> { });
        assertTrue(cleared.getText().contains("清单已清空"));
        assertFalse(Files.exists(session.resolve(TodoStore.FILE_NAME)));
    }

    @Test
    void truncatesLongContent() throws Exception {
        Path session = sessionDir();
        TodoWriteTool tool = new TodoWriteTool(session, temp);
        String longText = "甲".repeat(TodoList.MAX_CONTENT_CHARS + 5);
        ToolExecutionResult result = tool.execute(
            "tc1",
            params(item("1", longText, "pending")),
            new CancellationToken(),
            update -> { }
        );
        assertTrue(result.getText().contains("截断"));
        String saved = new TodoStore(session).load().list().items().get(0).content();
        assertEquals(TodoList.MAX_CONTENT_CHARS, saved.length());
    }

    @Test
    void corruptFileIsNotOverwritten() throws Exception {
        Path session = sessionDir();
        Files.writeString(session.resolve(TodoStore.FILE_NAME), "{");
        TodoWriteTool tool = new TodoWriteTool(session, temp);
        ToolExecutionResult result = tool.execute(
            "tc1",
            params(item("1", "写出大纲", "pending")),
            new CancellationToken(),
            update -> { }
        );
        assertTrue(result.getText().contains("无法读取"));
        assertEquals("{", Files.readString(session.resolve(TodoStore.FILE_NAME)));
    }

    @Test
    void exposedToScriptBridge() throws Exception {
        Path session = sessionDir();
        AgentToolsScriptBridge bridge = new AgentToolsScriptBridge(List.of(new TodoWriteTool(session, temp)));
        assertTrue(bridge.exposedNames().contains(TodoWriteTool.TOOL_NAME));
        String text = bridge.call(TodoWriteTool.TOOL_NAME, java.util.Map.of(
            "todos", List.of(java.util.Map.of("id", "1", "content", "写出大纲", "status", "pending"))
        ));
        assertTrue(text.contains("清单已更新"));
        assertEquals(1, new TodoStore(session).load().list().items().size());
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

    private static int count(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int i = text.indexOf(needle, from);
            if (i < 0) {
                return count;
            }
            count++;
            from = i + needle.length();
        }
    }
}
