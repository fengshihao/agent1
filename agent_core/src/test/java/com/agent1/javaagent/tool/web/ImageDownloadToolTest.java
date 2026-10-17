package com.agent1.javaagent.tool.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

/**
 * image_download（20-A T6）：https 白名单 + 内网拒绝 + 大小上限 + 魔数校验 +
 * workspace 落盘。allowLoopback/免 https 只给本地 MockWebServer 用。
 */
class ImageDownloadToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private static ImageDownloadTool testTool(Path workspace) {
        return new ImageDownloadTool(
            new WorkspaceSandbox(workspace),
            new OkHttpClient(),
            true,
            false
        );
    }

    @Test
    void downloadsPngWithMagicSniffedExtension(@TempDir Path workspace) throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        // URL 后缀是 .jpg，但内容是 PNG —— 扩展名必须按魔数定
        server.enqueue(new MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "image/jpeg")
            .setBody(new Buffer().write(PNG)));
        try {
            ToolExecutionResult result = testTool(workspace).execute(
                "call-1",
                MAPPER.readTree("{\"url\":\"" + server.url("/photos/sunrise.jpg") + "\"}"),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("\"type\":\"png\""), result.getText());
            assertTrue(result.getText().contains("imports/sunrise.png"), result.getText());
            assertTrue(result.getText().contains("\"source\""), "产物标注来源 url: " + result.getText());
            assertTrue(Files.isRegularFile(workspace.resolve("imports/sunrise.png")));
        } finally {
            server.shutdown();
        }
    }

    @Test
    void downloadsSvgText(@TempDir Path workspace) throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(200)
            .setBody("<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"));
        try {
            ToolExecutionResult result = testTool(workspace).execute(
                "call-1",
                MAPPER.readTree("{\"url\":\"" + server.url("/icon.svg") + "\",\"path\":\"assets/icon.svg\"}"),
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
    void rejectsNonImageBytes(@TempDir Path workspace) throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("this is not an image at all"));
        try {
            ToolExecutionResult result = testTool(workspace).execute(
                "call-1",
                MAPPER.readTree("{\"url\":\"" + server.url("/fake.png") + "\"}"),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("魔数") || result.getText().contains("图片"), result.getText());
            assertFalse(Files.exists(workspace.resolve("imports")));
        } finally {
            server.shutdown();
        }
    }

    @Test
    void rejectsOversizedBody(@TempDir Path workspace) throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        Buffer big = new Buffer();
        byte[] chunk = new byte[64 * 1024];
        for (int i = 0; i < 40; i++) {
            big.write(chunk);
        }
        // 2.5MB 响应，max_mb=1
        server.enqueue(new MockResponse().setResponseCode(200).setBody(big));
        try {
            ToolExecutionResult result = testTool(workspace).execute(
                "call-1",
                MAPPER.readTree("{\"url\":\"" + server.url("/huge.png") + "\",\"max_mb\":1}"),
                new CancellationToken(),
                null
            );
            assertTrue(result.getText().contains("大小上限"), result.getText());
        } finally {
            server.shutdown();
        }
    }

    @Test
    void rejectsHttpAndLoopbackInProductionMode(@TempDir Path workspace) throws Exception {
        // 生产构造（allowLoopback=false, requireHttps=true）：http 与 localhost 一律拒绝，不发起请求
        ImageDownloadTool prod = new ImageDownloadTool(new WorkspaceSandbox(workspace));
        ToolExecutionResult http = prod.execute(
            "call-1",
            MAPPER.readTree("{\"url\":\"http://example.com/pic.png\"}"),
            new CancellationToken(),
            null
        );
        assertTrue(http.getText().contains("https"), http.getText());
        ToolExecutionResult loopback = prod.execute(
            "call-1",
            MAPPER.readTree("{\"url\":\"https://localhost/pic.png\"}"),
            new CancellationToken(),
            null
        );
        assertTrue(loopback.getText().contains("内网") || loopback.getText().contains("本机"), loopback.getText());
    }
}
