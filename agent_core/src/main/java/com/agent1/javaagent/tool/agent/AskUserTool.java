package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Set;

/**
 * 向用户发起结构化澄清问题并暂停 Run（宿主终态 {@code waiting_user}）。
 * 模型不得编造用户答案；用户下一条消息将开启新的 Run。
 */
public final class AskUserTool implements AgentTool {

    public static final String TOOL_NAME = "ask_user";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return """
            Ask the user structured clarifying questions and pause the run until they reply.
            Use when required information is missing (e.g. travel origin, dates, budget).
            Do not call other tools in the same turn after ask_user; do not invent answers.
            """.trim();
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set(
            "required",
            MAPPER.createArrayNode().add("questions")
        );
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set(
            "title",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Short heading shown with the form (optional).")
        );
        ObjectNode questionItem = MAPPER.createObjectNode();
        questionItem.put("type", "object");
        questionItem.set(
            "required",
            MAPPER.createArrayNode().add("id").add("prompt")
        );
        ObjectNode qProps = MAPPER.createObjectNode();
        qProps.set("id", MAPPER.createObjectNode().put("type", "string"));
        qProps.set("prompt", MAPPER.createObjectNode().put("type", "string"));
        qProps.set(
            "type",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "text | single_choice | multi_choice")
        );
        qProps.set(
            "options",
            MAPPER.createObjectNode()
                .put("type", "array")
                .set("items", MAPPER.createObjectNode().put("type", "string"))
        );
        qProps.set("required", MAPPER.createObjectNode().put("type", "boolean"));
        qProps.set("default", MAPPER.createObjectNode().put("type", "string"));
        questionItem.set("properties", qProps);
        properties.set(
            "questions",
            MAPPER.createObjectNode()
                .put("type", "array")
                .put("minItems", 1)
                .set("items", questionItem)
        );
        properties.set(
            "allow_freeform_reply",
            MAPPER.createObjectNode()
                .put("type", "boolean")
                .put("description", "If true, user may reply in free text instead of per-field answers.")
        );
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
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }
        JsonNode questionsNode = parameters == null ? null : parameters.get("questions");
        if (questionsNode == null || !questionsNode.isArray() || questionsNode.isEmpty()) {
            return ToolExecutionResult.text("错误：questions 必须为非空数组");
        }

        Set<String> seenIds = new HashSet<>();
        ArrayNode normalizedQuestions = MAPPER.createArrayNode();
        for (JsonNode q : questionsNode) {
            String id = q.path("id").asText("").trim();
            String prompt = q.path("prompt").asText("").trim();
            if (id.isEmpty()) {
                return ToolExecutionResult.text("错误：每个 question 需要非空 id");
            }
            if (prompt.isEmpty()) {
                return ToolExecutionResult.text("错误：question id=" + id + " 需要非空 prompt");
            }
            if (!seenIds.add(id)) {
                return ToolExecutionResult.text("错误：question id 重复: " + id);
            }
            String qType = q.path("type").asText("text").trim();
            if (qType.isEmpty()) {
                qType = "text";
            }
            if (!qType.equals("text") && !qType.equals("single_choice") && !qType.equals("multi_choice")) {
                return ToolExecutionResult.text("错误：question id=" + id + " 的 type 无效: " + qType);
            }
            JsonNode options = q.get("options");
            if (("single_choice".equals(qType) || "multi_choice".equals(qType))
                && (options == null || !options.isArray() || options.isEmpty())) {
                return ToolExecutionResult.text("错误：question id=" + id + " 的选择题需要非空 options");
            }
            ObjectNode copy = MAPPER.createObjectNode();
            copy.put("id", id);
            copy.put("prompt", prompt);
            copy.put("type", qType);
            if (options != null && options.isArray()) {
                copy.set("options", options);
            }
            if (q.has("required")) {
                copy.put("required", q.path("required").asBoolean(false));
            }
            if (q.has("default")) {
                copy.put("default", q.path("default").asText(""));
            }
            normalizedQuestions.add(copy);
        }

        String title = parameters.path("title").asText("").trim();
        boolean allowFreeform = parameters.path("allow_freeform_reply").asBoolean(true);

        ObjectNode request = MAPPER.createObjectNode();
        request.put("kind", "ask_user_request");
        if (!title.isEmpty()) {
            request.put("title", title);
        }
        request.put("allow_freeform_reply", allowFreeform);
        request.set("questions", normalizedQuestions);

        int count = normalizedQuestions.size();
        String summary = "已向用户提出 " + count + " 个问题，Run 已暂停等待回复。"
            + "请勿继续调用工具或编造用户答案；用户回复后将开启新的 Run。";
        return ToolExecutionResult.waitingForUser(summary, request);
    }
}
