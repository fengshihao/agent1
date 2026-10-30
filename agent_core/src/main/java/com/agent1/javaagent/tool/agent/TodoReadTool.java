package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.todo.TodoList;
import com.agent1.javaagent.todo.TodoStore;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;

/** 读取当前会话任务清单（含已完成项）；清单不进系统提示，按需调用。 */
public final class TodoReadTool implements AgentTool {

    public static final String TOOL_NAME = "todo_read";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TodoStore store;

    public TodoReadTool(Path sessionDir) {
        this.store = new TodoStore(sessionDir);
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "读取当前会话任务清单（含已完成项）。跨轮继续前若需回顾计划，先调用本工具。";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", MAPPER.createObjectNode());
        return schema;
    }

    @Override
    public ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }
        TodoStore.LoadResult loaded = store.load();
        if (!loaded.ok()) {
            return ToolExecutionResult.text(loaded.error());
        }
        TodoList list = loaded.list();
        if (list.isEmpty()) {
            return new ToolExecutionResult("当前无任务清单。", details(list));
        }
        String body = list.renderBody();
        return new ToolExecutionResult("当前任务清单：\n" + body, details(list));
    }

    static ObjectNode details(TodoList list) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("kind", "todo_list");
        ArrayNode todos = root.putArray("todos");
        for (TodoList.Item item : list.items()) {
            ObjectNode node = todos.addObject();
            node.put("id", item.id());
            node.put("content", item.content());
            node.put("status", item.status());
        }
        return root;
    }
}
