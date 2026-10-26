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
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesDownloadToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private static FilesDownloadTool testTool(Path workspace) {
        return new FilesDownloadTool(
            new WorkspaceSandbox(workspace),
            new OkHttpClient(),
            true,
            false
        );
    }

    @Test
    void rejectsEmptyItems() throws Exception {
        assertNotNull(FilesDownloadTool.validateParameters(MAPPER.readTree("{}")));
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
                MAPPER.readTree("{\"items\":[{\"url\":\"" + url + "\",\"expect\":\"image\"}]}"),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("\"total\":1"), result.getText());
            assertTrue(result.getText().contains("\"succeeded\":1"), result.getText());
            assertTrue(result.getText().contains("imports/01.png"), result.getText());
            assertTrue(result.getText().contains("\"checks\""), result.getText());
            assertTrue(result.getText().contains("\"width\":1"), result.getText());
            assertTrue(Files.isRegularFile(workspace.resolve("imports/01.png")));
        } finally {
            server.shutdown();
        }
    }

    @Test
    void downloadsHtmlShellWithExpect(@TempDir Path workspace) throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        String html = "<!DOCTYPE html><html><body>ok</body></html>";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(html));
        try {
            ToolExecutionResult result = testTool(workspace).execute(
                "call-1",
                MAPPER.readTree(
                    "{\"dir\":\"shells\",\"items\":[{\"url\":\""
                        + server.url("/rapier.html")
                        + "\",\"expect\":\"html\"}]}"
                ),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("\"succeeded\":1"), result.getText());
            assertTrue(result.getText().contains("expect_html"), result.getText());
            assertTrue(result.getText().contains("shells/01.html"), result.getText());
            assertTrue(Files.readString(workspace.resolve("shells/01.html")).contains("<!DOCTYPE html>"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    void htmlExpectRejectsPlainText(@TempDir Path workspace) throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("not html"));
        try {
            ToolExecutionResult result = testTool(workspace).execute(
                "call-1",
                MAPPER.readTree(
                    "{\"items\":[{\"url\":\"" + server.url("/x") + "\",\"expect\":\"html\"}]}"
                ),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("\"succeeded\":0"), result.getText());
            assertTrue(result.getText().contains("不是 HTML"), result.getText());
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
                    "{\"dir\":\"photos\",\"name_prefix\":\"hero\",\"items\":[{\"url\":\""
                        + url
                        + "\",\"expect\":\"image\"}]}"
                ),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("photos/hero-01.png"), result.getText());
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
                        + "\",\"path\":\"assets/icon.svg\",\"expect\":\"image\"}]}"
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
                    "{\"items\":[{\"url\":\""
                        + u1
                        + "\",\"expect\":\"image\"},{\"url\":\""
                        + u2
                        + "\",\"expect\":\"image\"}]}"
                ),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("\"total\":2"), result.getText());
            assertTrue(result.getText().contains("\"succeeded\":2"), result.getText());
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
            assertTrue(result.getText().contains("\"ok\":false"), result.getText());
        } finally {
            server.shutdown();
        }
    }

    @Test
    void buildDefaultPathFormatting() {
        assertEquals("imports/01.png", FilesDownloadTool.buildDefaultPath("imports", "", 1, "png"));
        assertEquals("ultra/hero-02.jpg", FilesDownloadTool.buildDefaultPath("ultra", "hero", 2, "jpg"));
    }

    @Test
    void rejectsBlockedSocialHostWithoutNetwork(@TempDir Path workspace) throws Exception {
        ToolExecutionResult result = testTool(workspace).execute(
            "call-1",
            MAPPER.readTree(
                "{\"items\":[{\"url\":\"https://pbs.twimg.com/media/x?format=webp\",\"path\":\"imports/x.webp\"}]}"
            ),
            new CancellationToken(),
            null
        );
        assertTrue(result.getText().contains("\"succeeded\":0"), result.getText());
        assertTrue(result.getText().contains("Twitter"), result.getText());
    }

    @Test
    void partialBatchJsonKeepsFinishedItems() {
        List<FilesDownloadTool.DownloadItem> items = List.of(
            new FilesDownloadTool.DownloadItem(1, "https://fast.example/a.jpg", "assets/a.jpg", FileDownloadExpect.IMAGE),
            new FilesDownloadTool.DownloadItem(2, "https://slow.example/b.jpg", "", FileDownloadExpect.IMAGE)
        );
        List<FilesDownloadTool.ItemOutcome> finished = List.of(
            FilesDownloadTool.ItemOutcome.success(
                1,
                "https://fast.example/a.jpg",
                "assets/a.jpg",
                1024,
                "jpg",
                "image/jpg",
                10,
                10,
                List.of(new FileDownloadValidation.Check("expect_image", true, "jpg"))
            )
        );
        String json = FilesDownloadTool.buildBatchJson(
            items,
            "assets",
            finished,
            true,
            "batch_deadline"
        ).toString();
        assertTrue(json.contains("\"partial\":true"), json);
        assertTrue(json.contains("\"succeeded\":1"), json);
    }

    @Test
    void rejectsHttpInProductionMode(@TempDir Path workspace) throws Exception {
        FilesDownloadTool prod = new FilesDownloadTool(new WorkspaceSandbox(workspace));
        ToolExecutionResult result = prod.execute(
            "call-1",
            MAPPER.readTree(
                "{\"items\":[{\"url\":\"http://example.com/pic.png\",\"path\":\"imports/pic.png\"}]}"
            ),
            new CancellationToken(),
            null
        );
        assertTrue(result.getText().contains("\"succeeded\":0"), result.getText());
    }
}
