package com.agent1.javaagent.tool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

/** image_search（20-A T6）：Tavily include_images 请求与图片/来源页输出。 */
class ImageSearchToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void searchesImagesThroughTavily() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("""
                {"images":[
                  {"url":"https://img.example.com/sunrise.jpg","description":"mountain sunrise"},
                  "https://img.example.com/valley.png"
                ],
                "results":[
                  {"url":"https://photos.example.com/gallery/sunrise","title":"Sunrise gallery"},
                  {"url":"https://other.example.com/page","title":"Other"}
                ]}
                """));
        try {
            ImageSearchTool tool = new ImageSearchTool("tvly-test", server.url("/").toString());
            ToolExecutionResult result = tool.execute(
                "call-1",
                MAPPER.readTree("{\"query\":\"山峰 日出\",\"count\":6}"),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("image_search: 山峰 日出"), result.getText());
            assertTrue(result.getText().contains("https://img.example.com/sunrise.jpg"), result.getText());
            assertTrue(result.getText().contains("mountain sunrise"), result.getText());
            assertTrue(result.getText().contains("https://img.example.com/valley.png"), result.getText());
            assertTrue(result.getText().contains("来源页"), result.getText());
            assertTrue(result.getText().contains("https://photos.example.com/gallery/sunrise"), result.getText());
            assertTrue(result.getText().contains("files_download"), result.getText());

            RecordedRequest recorded = server.takeRequest();
            assertEquals("/search", recorded.getPath());
            assertEquals("Bearer tvly-test", recorded.getHeader("Authorization"));
            JsonNode body = MAPPER.readTree(new String(recorded.getBody().readByteArray(), StandardCharsets.UTF_8));
            assertEquals("山峰 日出", body.path("query").asText());
            assertTrue(body.path("include_images").asBoolean(), "应传 include_images:true");
            assertEquals(6, body.path("max_results").asInt());
        } finally {
            server.shutdown();
        }
    }

    @Test
    void reportsHttpError() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"detail\":\"bad key\"}"));
        try {
            ImageSearchTool tool = new ImageSearchTool("tvly-bad", server.url("/").toString());
            ToolExecutionResult result = tool.execute(
                "call-1",
                MAPPER.readTree("{\"query\":\"cats\"}"),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("错误"), result.getText());
            assertTrue(result.getText().contains("401"), result.getText());
        } finally {
            server.shutdown();
        }
    }

    @Test
    void rejectsEmptyQueryWithoutNetwork() throws Exception {
        ImageSearchTool tool = new ImageSearchTool("tvly-test", "https://search.invalid");
        ToolExecutionResult result = tool.execute(
            "call-1",
            MAPPER.readTree("{\"query\":\"  \"}"),
            new CancellationToken(),
            null
        );
        assertTrue(result.getText().contains("query"), result.getText());
    }
}
