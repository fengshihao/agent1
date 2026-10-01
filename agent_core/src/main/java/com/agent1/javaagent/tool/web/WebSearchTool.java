package com.agent1.javaagent.tool.web;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

/** 用 Tavily 检索公开网页。未配置 Key 时宿主不注册本工具。 */
public final class WebSearchTool implements AgentTool {

    public static final String TOOL_NAME = "web_search";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TavilyWebSearchClient client;

    public WebSearchTool(String apiKey, String baseUrl) {
        this(new TavilyWebSearchClient(apiKey, baseUrl));
    }

    WebSearchTool(TavilyWebSearchClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return """
            Search the public web via Tavily.
            Use for current facts, news, and sources that are not in the workspace.
            Do not invent results. Cite title and url from the tool output.
            """.trim();
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("required", MAPPER.createArrayNode().add("query"));
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set(
            "query",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Search query.")
        );
        properties.set(
            "max_results",
            MAPPER.createObjectNode()
                .put("type", "integer")
                .put("description", "1-8, default 5. Basic search (1 Tavily credit).")
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
        String query = parameters == null ? "" : parameters.path("query").asText("").trim();
        if (query.isEmpty()) {
            return ToolExecutionResult.text("错误：query 不能为空");
        }
        int maxResults = 5;
        if (parameters != null && parameters.has("max_results") && parameters.get("max_results").canConvertToInt()) {
            maxResults = parameters.get("max_results").asInt(5);
        }
        try {
            return ToolExecutionResult.text(client.search(query, maxResults));
        } catch (IOException e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                ? "搜索失败"
                : e.getMessage();
            return ToolExecutionResult.text("错误：" + message);
        }
    }
}
