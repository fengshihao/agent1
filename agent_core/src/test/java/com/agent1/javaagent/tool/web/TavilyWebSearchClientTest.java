package com.agent1.javaagent.tool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class TavilyWebSearchClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void searchPostsBearerTokenAndFormatsHits() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {"answer":"A short answer","results":[{"title":"Example","url":"https://example.com/a","content":"snippet"}]}
                    """));
            server.start();
            String base = server.url("/").toString().replaceAll("/+$", "");
            WebSearchTool tool = new WebSearchTool("tvly-test", base);
            ObjectNode params = MAPPER.createObjectNode();
            params.put("query", "latest news");
            params.put("max_results", 3);
            String text = tool.execute("s1", params, new CancellationToken(), update -> {
            }).getText();

            RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
            assertEquals("/search", recorded.getPath());
            assertEquals("Bearer tvly-test", recorded.getHeader("Authorization"));
            String body = recorded.getBody().readUtf8();
            assertTrue(body.contains("\"query\":\"latest news\""));
            assertTrue(body.contains("\"search_depth\":\"basic\""));
            assertFalse(body.contains("tvly-test"));
            assertTrue(text.contains("A short answer"));
            assertTrue(text.contains("Example"));
            assertTrue(text.contains("https://example.com/a"));
            assertFalse(text.contains("tvly-test"));
        }
    }

    @Test
    void httpErrorBecomesToolText() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"unauthorized\"}"));
            server.start();
            String base = server.url("/").toString().replaceAll("/+$", "");
            WebSearchTool tool = new WebSearchTool("tvly-bad", base);
            ObjectNode params = MAPPER.createObjectNode().put("query", "hello");
            String text = tool.execute("s2", params, new CancellationToken(), update -> {
            }).getText();
            assertTrue(text.startsWith("错误：Tavily HTTP 401"));
            assertFalse(text.contains("tvly-bad"));
        }
    }

    @Test
    void blankQueryDoesNotCallNetwork() {
        WebSearchTool tool = new WebSearchTool("", "https://api.tavily.com");
        String text = tool.execute(
            "s3",
            MAPPER.createObjectNode(),
            new CancellationToken(),
            update -> {
            }
        ).getText();
        assertEquals("错误：query 不能为空", text);
    }
}
