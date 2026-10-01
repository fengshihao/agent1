package com.agent1.javaagent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class HttpMcpToolListerTest {

    @Test
    void listsToolsOverHttp() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {"jsonrpc":"2.0","id":1,"result":{"tools":[
                      {"name":"search","description":"find issues"}
                    ]}}
                    """));
            server.start();
            HttpMcpToolLister lister = new HttpMcpToolLister(new OkHttpClient.Builder()
                .callTimeout(5, TimeUnit.SECONDS)
                .build());
            var tools = lister.listTools(new McpServerRecord(
                "demo",
                server.url("/mcp").toString(),
                Map.of("Authorization", "Bearer t"),
                true,
                "",
                0,
                ""
            ));
            assertEquals(1, tools.size());
            assertEquals("search", tools.get(0).name());
            assertEquals("find issues", tools.get(0).description());
            RecordedRequest request = server.takeRequest();
            assertEquals("Bearer t", request.getHeader("Authorization"));
            assertTrue(request.getBody().readUtf8().contains("tools/list"));
        }
    }
}
