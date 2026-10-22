package com.agent1.javaagent.tool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImagesDownloadToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private static ImagesDownloadTool testTool(Path workspace) {
        return new ImagesDownloadTool(
            new WorkspaceSandbox(workspace),
            new OkHttpClient(),
            true,
            false
        );
    }

    @Test
    void rejectsEmptyItems() throws Exception {
        assertNotNull(ImagesDownloadTool.validateParameters(MAPPER.readTree("{}")));
        ToolExecutionResult result = testTool(Files.createTempDirectory("ws")).execute(
            "c",
            MAPPER.createObjectNode(),
            new CancellationToken(),
            null
        );
        assertTrue(result.getText().contains("items"), result.getText());
    }

    @Test
    void defaultNumberedPathsUnderImports(@TempDir Path workspace) throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(200).setBody(new Buffer().write(PNG)));
        try {
            String url = server.url("/a.png").toString();
            ToolExecutionResult result = testTool(workspace).execute(
                "call-1",
                MAPPER.readTree("{\"items\":[{\"url\":\"" + url + "\"}]}"),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("\"total\":1"), result.getText());
            assertTrue(result.getText().contains("\"succeeded\":1"), result.getText());
            assertTrue(result.getText().contains("imports/01.png"), result.getText());
            assertTrue(result.getText().contains("\"size\""), result.getText());
            assertFalse(result.getText().contains("\"bytes\""), result.getText());
            assertTrue(Files.isRegularFile(workspace.resolve("imports/01.png")));
        } finally {
            server.shutdown();
        }
    }

    @Test
    void namePrefixAndDir(@TempDir Path workspace) throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(200).setBody(new Buffer().write(PNG)));
        try {
            String url = server.url("/x").toString();
            ToolExecutionResult result = testTool(workspace).execute(
                "call-1",
                MAPPER.readTree(
                    "{\"dir\":\"photos\",\"name_prefix\":\"hero\",\"items\":[{\"url\":\"" + url + "\"}]}"
                ),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("photos/hero-01.png"), result.getText());
            assertTrue(Files.isRegularFile(workspace.resolve("photos/hero-01.png")));
        } finally {
            server.shutdown();
        }
    }

    @Test
    void explicitItemPath(@TempDir Path workspace) throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(200)
            .setBody("<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"));
        try {
            ToolExecutionResult result = testTool(workspace).execute(
                "call-1",
                MAPPER.readTree(
                    "{\"items\":[{\"url\":\""
                        + server.url("/icon")
                        + "\",\"path\":\"assets/icon.svg\"}]}"
                ),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("\"type\":\"svg\""), result.getText());
            assertTrue(Files.isRegularFile(workspace.resolve("assets/icon.svg")));
        } finally {
            server.shutdown();
        }
    }

    @Test
    void parallelBatchWithPerItemOk(@TempDir Path workspace) throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(200).setBody(new Buffer().write(PNG)));
        server.enqueue(new MockResponse().setResponseCode(200).setBody(new Buffer().write(PNG)));
        try {
            String u1 = server.url("/one").toString();
            String u2 = server.url("/two").toString();
            ToolExecutionResult result = testTool(workspace).execute(
                "call-1",
                MAPPER.readTree(
                    "{\"items\":[{\"url\":\"" + u1 + "\"},{\"url\":\"" + u2 + "\"}]}"
                ),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("\"total\":2"), result.getText());
            assertTrue(result.getText().contains("\"succeeded\":2"), result.getText());
            assertTrue(result.getText().contains("\"ok\":true"), result.getText());
        } finally {
            server.shutdown();
        }
    }

    @Test
    void failedItemHasZeroSizeAndError(@TempDir Path workspace) throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(404));
        try {
            ToolExecutionResult result = testTool(workspace).execute(
                "call-1",
                MAPPER.readTree("{\"items\":[{\"url\":\"" + server.url("/missing") + "\"}]}"),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("\"succeeded\":0"), result.getText());
            assertTrue(result.getText().contains("\"size\":\"0 B\""), result.getText());
            assertTrue(result.getText().contains("\"ok\":false"), result.getText());
            assertTrue(result.getText().contains("error"), result.getText());
        } finally {
            server.shutdown();
        }
    }

    @Test
    void buildDefaultPathFormatting() {
        assertEquals("imports/01.png", ImagesDownloadTool.buildDefaultPath("imports", "", 1, "png"));
        assertEquals("ultra/hero-02.jpg", ImagesDownloadTool.buildDefaultPath("ultra", "hero", 2, "jpg"));
    }

    @Test
    void rejectsHttpInProductionMode(@TempDir Path workspace) throws Exception {
        ImagesDownloadTool prod = new ImagesDownloadTool(new WorkspaceSandbox(workspace));
        ToolExecutionResult result = prod.execute(
            "call-1",
            MAPPER.readTree(
                "{\"items\":[{\"url\":\"http://example.com/pic.png\",\"path\":\"imports/pic.png\"}]}"
            ),
            new CancellationToken(),
            null
        );
        assertTrue(result.getText().contains("\"succeeded\":0"), result.getText());
        assertTrue(result.getText().contains("https") || result.getText().contains("error"), result.getText());
    }
}
