package com.agent1.javaagent.tool.web;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

/**
 * 用 Tavily 搜公开图片（20-A T6）：同一 {@code /search} 传 {@code include_images:true}，
 * 返回图片 URL 与来源页。未配置 Key 时宿主不注册本工具。
 */
public final class ImageSearchTool implements AgentTool {

    public static final String TOOL_NAME = "image_search";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TavilyWebSearchClient client;

    public ImageSearchTool(String apiKey, String baseUrl) {
        this(new TavilyWebSearchClient(apiKey, baseUrl));
    }

    ImageSearchTool(TavilyWebSearchClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return """
            Search public images via Tavily (personal-use decks and documents).
            Returns image URLs plus source pages. Download with image_download before \
            inserting into decks or documents, and cite the source in the output. \
            Do not invent URLs.
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
                .put("description", "Image search query, e.g. \"mountain sunrise photo\".")
        );
        properties.set(
            "count",
            MAPPER.createObjectNode()
                .put("type", "integer")
                .put("description", "1-16, default 8.")
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
        int count = 8;
        if (parameters != null && parameters.has("count") && parameters.get("count").canConvertToInt()) {
            count = parameters.get("count").asInt(8);
        }
        try {
            return ToolExecutionResult.text(client.searchImages(query, count));
        } catch (IOException e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                ? "搜图失败"
                : e.getMessage();
            return ToolExecutionResult.text("错误：" + message);
        }
    }
}
