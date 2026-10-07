package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.log.AgentAuditEvents;
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

/**
 * 整表替换当前会话的任务清单，并写入 {@code sessions/<id>/todos.json}。
 * 脚本内通过 {@code $tools.todo_write} 调用；状态没变时不写事件。
 */
public final class TodoWriteTool implements AgentTool {

    public static final String TOOL_NAME = "todo_write";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TodoStore store;
    private final Path agentRoot;
    private final Runnable onChanged;

    public TodoWriteTool(Path sessionDir, Path agentRoot) {
        this(sessionDir, agentRoot, null);
    }

    public TodoWriteTool(Path sessionDir, Path agentRoot, Runnable onChanged) {
        this.store = new TodoStore(sessionDir);
        this.agentRoot = agentRoot;
        this.onChanged = onChanged;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return """
            维护当前会话的短任务清单，方便跨轮记住计划。每次调用用整表替换，不要增量打补丁。
            闲聊、单文件修改、一段脚本就能做完的事不要建清单。
            任务会跨多次往返、或需要让用户看到进度时，先写清单再办事。状态没变不要调用。
            content 用用户的语言写结果，不要写工具名、命令或路径。最多 8 项，同时最多一项 in_progress。
            做完一项就在同一轮把该项标成 completed，并标出下一项。空数组清空清单。
            """.trim();
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("required", MAPPER.createArrayNode().add("todos"));
        ObjectNode item = MAPPER.createObjectNode();
        item.put("type", "object");
        item.set("required", MAPPER.createArrayNode().add("id").add("content").add("status"));
        ObjectNode itemProps = MAPPER.createObjectNode();
        itemProps.set("id", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "Stable id within this list, unique, no spaces."));
        itemProps.set("content", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "User-facing outcome, one line, no tool or path names."));
        ObjectNode status = MAPPER.createObjectNode();
        status.put("type", "string");
        status.put("description", "pending | in_progress | completed. At most one in_progress.");
        status.set("enum", MAPPER.createArrayNode().add("pending").add("in_progress").add("completed"));
        itemProps.set("status", status);
        item.set("properties", itemProps);
        ObjectNode todos = MAPPER.createObjectNode();
        todos.put("type", "array");
        todos.put("maxItems", TodoList.MAX_ITEMS);
        todos.set("items", item);
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("todos", todos);
        schema.set("properties", properties);
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
        JsonNode todosNode = parameters == null ? null : parameters.get("todos");
        TodoList.ParseResult parsed = TodoList.parse(todosNode);
        TodoStore.LoadResult current = store.load();
        if (!current.ok()) {
            return ToolExecutionResult.text(current.error() + "。未改写清单。");
        }
        if (!parsed.ok()) {
            return ToolExecutionResult.text(parsed.error() + currentSuffix(current.list()));
        }
        TodoList next = parsed.list();
        if (next.sameItems(current.list())) {
            return new ToolExecutionResult(
                "清单未变化，无需再写。\n" + bodyOrEmpty(next),
                details(next, true)
            );
        }
        store.save(next);
        AgentAuditEvents.todoUpdated(agentRoot, null, next);
        if (onChanged != null) {
            onChanged.run();
        }
        String head = next.isEmpty() ? "清单已清空。" : "清单已更新。";
        if (next.contentTruncated()) {
            head = head + "过长的条目已截断。";
        }
        String body = next.renderBody();
        String text = body.isEmpty() ? head : head + "\n" + body;
        return new ToolExecutionResult(text, details(next, false));
    }

    private static String currentSuffix(TodoList current) {
        if (current.isEmpty()) {
            return "";
        }
        return "\n当前清单：\n" + current.renderBody();
    }

    private static String bodyOrEmpty(TodoList list) {
        String body = list.renderBody();
        return body.isEmpty() ? "（空）" : body;
    }

    private static ObjectNode details(TodoList list, boolean unchanged) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("kind", "todo_list");
        root.put("unchanged", unchanged);
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
