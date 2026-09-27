package com.agent1.javaagent.weizhi.desktop.cdp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 启动带 remote-debugging-port 的 Chromium，并解析 Page 级 CDP WebSocket URL。 */
public final class CdpBrowserProcess implements AutoCloseable {

    private final Process process;
    private final int debugPort;
    private CdpJsonRpcClient pageClient;
    private String pageWebSocketUrl;

    private CdpBrowserProcess(Process process, int debugPort) {
        this.process = process;
        this.debugPort = debugPort;
    }

    public static CdpBrowserProcess launch(Path chromiumExecutable) throws Exception {
        int port = findFreePort();
        List<String> cmd = new ArrayList<>();
        cmd.add(chromiumExecutable.toString());
        cmd.add("--headless=new");
        cmd.add("--disable-gpu");
        cmd.add("--no-sandbox");
        cmd.add("--disable-dev-shm-usage");
        cmd.add("--disable-background-networking");
        cmd.add("--user-data-dir=" + Files.createTempDirectory("agent1-chrome-"));
        cmd.add("--allow-file-access-from-files");
        cmd.add("--remote-debugging-address=127.0.0.1");
        cmd.add("--remote-debugging-port=" + port);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = pb.start();
        waitForDebuggerEndpoint(port, 15_000L);
        return new CdpBrowserProcess(process, port);
    }

    public synchronized CdpJsonRpcClient connectFreshPage() throws Exception {
        closePageClient();
        pageWebSocketUrl = createPageTargetWebSocketUrl(debugPort);
        pageClient = new CdpJsonRpcClient(pageWebSocketUrl);
        return pageClient;
    }

    public synchronized CdpJsonRpcClient pageClient() {
        return pageClient;
    }

    private static void waitForDebuggerEndpoint(int port, long timeoutMs) throws Exception {
        HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/json/version"))
            .timeout(Duration.ofMillis(timeoutMs))
            .GET()
            .build();
        long deadline = System.currentTimeMillis() + timeoutMs;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body().contains("webSocketDebuggerUrl")) {
                    return;
                }
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(200L);
        }
        throw new IOException("Chromium 调试端口未就绪 (port=" + port + ")", last);
    }

    private static String createPageTargetWebSocketUrl(int port) throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/json/new?about:blank"))
            .PUT(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(10))
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("json/new 失败: HTTP " + resp.statusCode());
        }
        JsonObject target = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (!target.has("webSocketDebuggerUrl")) {
            throw new IOException("json/new 响应缺少 webSocketDebuggerUrl");
        }
        return target.get("webSocketDebuggerUrl").getAsString();
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private void closePageClient() {
        if (pageClient != null) {
            pageClient.close();
            pageClient = null;
        }
    }

    @Override
    public void close() {
        closePageClient();
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}
