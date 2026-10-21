package com.agent1.javaagent.brief;

import com.agent1.javaagent.model.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** {@code ask_user} 简报解析（对齐 Android {@code AskUserFormatting} 子集）。 */
public final class BriefAskUserSupport {

    public static final String TOOL_NAME = "ask_user";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BriefAskUserSupport() {
    }

    public record Question(
        String id,
        String prompt,
        String type,
        List<String> options,
        Integer ratingMin,
        Integer ratingMax
    ) {
    }

    public record Request(String title, List<Question> questions) {
    }

    public static Request parseRequestFromToolCall(ToolCall call) {
        if (call == null || !TOOL_NAME.equals(call.getName())) {
            return null;
        }
        return parseRequest(call.getArgumentsJson());
    }

    public static Request parseRequest(String argumentsJson) {
        try {
            JsonNode root = MAPPER.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            JsonNode questionsNode = root.path("questions");
            if (!questionsNode.isArray() || questionsNode.isEmpty()) {
                return null;
            }
            List<Question> questions = new ArrayList<>();
            for (JsonNode q : questionsNode) {
                String id = q.path("id").asText("").trim();
                String prompt = q.path("prompt").asText("").trim();
                if (id.isEmpty() || prompt.isEmpty()) {
                    continue;
                }
                String type = q.path("type").asText("text").trim();
                if (type.isEmpty()) {
                    type = "text";
                }
                List<String> options = new ArrayList<>();
                JsonNode optionsNode = q.path("options");
                if (optionsNode.isArray()) {
                    for (JsonNode opt : optionsNode) {
                        String s = opt.asText("").trim();
                        if (!s.isEmpty()) {
                            options.add(s);
                        }
                    }
                }
                Integer ratingMin = null;
                Integer ratingMax = null;
                if (q.has("min")) {
                    ratingMin = q.path("min").asInt();
                } else if ("rating".equals(type)) {
                    ratingMin = q.path("min").asInt(1);
                }
                if (q.has("max")) {
                    ratingMax = q.path("max").asInt();
                } else if ("rating".equals(type)) {
                    ratingMax = q.path("max").asInt(5);
                }
                questions.add(new Question(id, prompt, type, options, ratingMin, ratingMax));
            }
            if (questions.isEmpty()) {
                return null;
            }
            String title = root.path("title").asText("").trim();
            return new Request(title.isEmpty() ? null : title, questions);
        } catch (Exception e) {
            return null;
        }
    }

    public static String formatForBrief(Request request, String toolResultSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("**ask_user（等待用户回复）**").append('\n');
        if (request.title != null) {
            sb.append("- 标题：").append(request.title).append('\n');
        }
        sb.append("- 问题数：").append(request.questions.size()).append('\n');
        for (int i = 0; i < request.questions.size(); i++) {
            Question q = request.questions.get(i);
            sb.append("  ").append(i + 1).append(". [").append(q.id).append("] ").append(q.prompt);
            if (!"text".equals(q.type)) {
                sb.append(" (").append(q.type).append(')');
            }
            sb.append('\n');
            if ("rating".equals(q.type) && q.ratingMin != null && q.ratingMax != null) {
                sb.append("     分值：").append(q.ratingMin).append('–').append(q.ratingMax).append('\n');
            } else if (!q.options.isEmpty()) {
                sb.append("     选项：").append(String.join(", ", q.options)).append('\n');
            }
        }
        if (toolResultSummary != null && !toolResultSummary.trim().isEmpty()) {
            sb.append("- 工具摘要：").append(toolResultSummary.trim()).append('\n');
        }
        return sb.toString().trim();
    }
}
