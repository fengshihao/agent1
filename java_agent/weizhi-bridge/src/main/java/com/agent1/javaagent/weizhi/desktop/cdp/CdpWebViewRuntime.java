package com.agent1.javaagent.weizhi.desktop.cdp;

import com.agent1.javaagent.weizhi.desktop.DesktopBootstrapAssets;
import com.agent1.javaagent.weizhi.desktop.DesktopChromiumLocator;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.weizhi.agent.web.BridgeCodec;
import com.weizhi.agent.web.WebViewQueue;
import com.weizhi.agent.web.WebViewTask;
import com.weizhi.agent.web.WebViewTaskAccess;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 桌面 CDP 版 webview_exec 运行时（语义对齐 Android {@code WebViewRuntime}）。 */
public final class CdpWebViewRuntime {

    private static final long BOOT_TIMEOUT_MS = 15_000L;
    private static final int MAX_CONSOLE_LINES = 200;
    private static final int MAX_CONSOLE_LINE_CHARS = 1024;

    private static volatile CdpWebViewRuntime instance;

    private final Path chromiumPath;
    private final DesktopBootstrapAssets bootstrap;
    private final Gson gson = new Gson();
    private final WebViewQueue queue = new WebViewQueue();

    private CdpBrowserProcess browser;
    private CdpJsonRpcClient client;
    private boolean bootstrapReady;
    private volatile String activeTaskId;

    private CdpWebViewRuntime(Path chromiumPath, DesktopBootstrapAssets bootstrap) {
        this.chromiumPath = chromiumPath;
        this.bootstrap = bootstrap;
    }

    public static CdpWebViewRuntime getInstance(Path weizhiRepo) {
        Path chrome = DesktopChromiumLocator.resolveExecutable()
            .orElseThrow(() -> new IllegalStateException("未找到 Chromium，请安装或设置 AGENT1_CHROMIUM_PATH"));
        CdpWebViewRuntime r = instance;
        if (r == null) {
            synchronized (CdpWebViewRuntime.class) {
                r = instance;
                if (r == null) {
                    r = new CdpWebViewRuntime(chrome, new DesktopBootstrapAssets(weizhiRepo));
                    instance = r;
                }
            }
        }
        return r;
    }

    public static boolean isAvailable() {
        return DesktopChromiumLocator.enabledByEnvironment()
            && DesktopChromiumLocator.resolveExecutable().isPresent();
    }

    public WebViewRuntimeOutcome execute(WebViewTask task) {
        if (!queue.tryAcquire()) {
            return WebViewRuntimeOutcome.error(
                "webview_exec 队列已满(" + WebViewQueue.QUEUE_LIMIT + " 个任务在途),请稍后重试。",
                Collections.emptyList(),
                0L
            );
        }
        long start = System.currentTimeMillis();
        String taskId = "wv-cdp-" + System.nanoTime();
        TaskEntry entry = new TaskEntry();
        activeTaskId = taskId;
        try {
            synchronized (this) {
                ensureClient();
                if (!ensureBootstrap(entry)) {
                    return WebViewRuntimeOutcome.error(entry.failReason, entry.console(), elapsed(start));
                }
                wireEvents(entry, taskId);
                String js = BridgeCodec.buildInvokeJs(BridgeCodec.encodeTask(gson, taskId, task));
                JsonObject evalParams = new JsonObject();
                evalParams.addProperty("expression", js);
                evalParams.addProperty("awaitPromise", true);
                evalParams.addProperty("returnByValue", false);
                client.call("Runtime.evaluate", evalParams, 5_000L);
                long timeoutMs = WebViewTaskAccess.timeoutMs(task);
                long deadline = System.currentTimeMillis() + timeoutMs;
                while (!entry.latch.await(500L, TimeUnit.MILLISECONDS)) {
                    pollBindingQueue(entry, taskId);
                    if (System.currentTimeMillis() >= deadline) {
                        resetPage();
                        return WebViewRuntimeOutcome.error(
                            "任务超时(" + timeoutMs + "ms,页面已重置)。若 code 有写副作用,其结果不可信。",
                            entry.console(),
                            elapsed(start)
                        );
                    }
                }
                if (entry.failed()) {
                    return WebViewRuntimeOutcome.error(entry.failReason, entry.console(), elapsed(start));
                }
                String payload = entry.payloadJson;
                if (entry.chunked) {
                    try {
                        payload = new String(entry.assembler.assemble(), StandardCharsets.UTF_8);
                    } catch (Exception ex) {
                        return WebViewRuntimeOutcome.error(
                            "大结果分块重组失败: " + ex.getMessage(),
                            entry.console(),
                            elapsed(start)
                        );
                    }
                }
                return WebViewRuntimeOutcome.ok(payload, entry.console(), elapsed(start));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return WebViewRuntimeOutcome.error("执行等待被中断", entry.console(), elapsed(start));
        } catch (Exception e) {
            return WebViewRuntimeOutcome.error(
                e.getMessage() == null ? e.toString() : e.getMessage(),
                entry.console(),
                elapsed(start)
            );
        } finally {
            activeTaskId = null;
            queue.release();
        }
    }

    private void ensureBrowser() throws Exception {
        if (browser == null) {
            browser = CdpBrowserProcess.launch(chromiumPath);
            client = browser.connectFreshPage();
            bootstrapReady = false;
            return;
        }
        if (client == null) {
            client = browser.connectFreshPage();
            bootstrapReady = false;
        }
    }

    private void ensureClient() throws Exception {
        ensureBrowser();
    }

    private boolean ensureBootstrap(TaskEntry entry) throws Exception {
        if (bootstrapReady) {
            return true;
        }
        client.call("Runtime.enable", null, BOOT_TIMEOUT_MS);
        client.call("Page.enable", null, BOOT_TIMEOUT_MS);
        client.call("Log.enable", null, BOOT_TIMEOUT_MS);
        JsonObject binding = new JsonObject();
        binding.addProperty("name", "agentNativeBridge");
        client.call("Runtime.addBinding", binding, BOOT_TIMEOUT_MS);

        CountDownLatch loaded = new CountDownLatch(1);
        client.setEventHandler(event -> {
            String method = event.get("method").getAsString();
            if ("Page.loadEventFired".equals(method)) {
                loaded.countDown();
            }
        });

        String html = bootstrap.loadHtmlDocument();
        Path htmlFile = Files.createTempFile("agent1-webview-bootstrap-", ".html");
        Files.writeString(htmlFile, html, StandardCharsets.UTF_8);
        JsonObject nav = new JsonObject();
        nav.addProperty("url", htmlFile.toUri().toString());
        client.call("Page.navigate", nav, BOOT_TIMEOUT_MS);
        if (!loaded.await(BOOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            entry.failNative("CDP 引导页加载超时");
            return false;
        }
        if (!waitForAgentBridge(entry)) {
            return false;
        }
        bootstrapReady = true;
        return true;
    }

    private void wireEvents(TaskEntry entry, String taskId) {
        client.setEventHandler(event -> {
            if (!event.has("method")) {
                return;
            }
            String method = event.get("method").getAsString();
            JsonObject params = event.getAsJsonObject("params");
            if ("Runtime.bindingCalled".equals(method)) {
                handleBinding(entry, taskId, params);
            } else if ("Runtime.consoleAPICalled".equals(method)) {
                appendConsole(entry, params);
            }
        });
    }

    private boolean waitForAgentBridge(TaskEntry entry) throws Exception {
        JsonObject probe = new JsonObject();
        probe.addProperty("expression", "typeof window.__agentRun === 'function'");
        probe.addProperty("returnByValue", true);
        long deadline = System.currentTimeMillis() + BOOT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            JsonObject result = client.call("Runtime.evaluate", probe, 3_000L);
            if (result.has("result")) {
                JsonObject value = result.getAsJsonObject("result");
                if (value.has("value") && value.get("value").getAsBoolean()) {
                    return true;
                }
            }
            Thread.sleep(100L);
        }
        entry.failNative("CDP 引导页未注入 bridge.js（缺少 __agentRun）");
        return false;
    }

    private void pollBindingQueue(TaskEntry entry, String expectedTaskId) {
        try {
            JsonObject evalParams = new JsonObject();
            evalParams.addProperty(
                "expression",
                "(function(){try{return JSON.stringify(window.__agentBindingQueue||[]);}"
                    + "catch(e){return '[]';}})()"
            );
            evalParams.addProperty("returnByValue", true);
            JsonObject result = client.call("Runtime.evaluate", evalParams, 3_000L);
            if (!result.has("result")) {
                return;
            }
            JsonObject value = result.getAsJsonObject("result");
            if (!value.has("value") || value.get("value").isJsonNull()) {
                return;
            }
            JsonArray queue = JsonParser.parseString(value.get("value").getAsString()).getAsJsonArray();
            if (queue.isEmpty()) {
                return;
            }
            JsonObject clear = new JsonObject();
            clear.addProperty("expression", "window.__agentBindingQueue = []");
            client.call("Runtime.evaluate", clear, 2_000L);
            for (int i = 0; i < queue.size(); i++) {
                JsonObject synthetic = new JsonObject();
                synthetic.addProperty("payload", queue.get(i).getAsString());
                handleBinding(entry, expectedTaskId, synthetic);
            }
        } catch (Exception ignored) {
            // 轮询失败时仍依赖 Runtime.bindingCalled
        }
    }

    private void handleBinding(TaskEntry entry, String expectedTaskId, JsonObject params) {
        if (params == null || !params.has("payload")) {
            return;
        }
        try {
            JsonObject msg = JsonParser.parseString(params.get("payload").getAsString()).getAsJsonObject();
            String taskId = msg.get("taskId").getAsString();
            if (!expectedTaskId.equals(taskId)) {
                return;
            }
            String kind = msg.get("k").getAsString();
            if ("c".equals(kind)) {
                entry.assembler.feed(
                    msg.get("seq").getAsInt(),
                    msg.get("total").getAsInt(),
                    msg.get("b64").getAsString()
                );
                return;
            }
            boolean isError = msg.has("isError") && msg.get("isError").getAsBoolean();
            String payload = msg.get("payload").getAsString();
            if (isError) {
                entry.error(extractError(payload));
                return;
            }
            JsonObject o = JsonParser.parseString(payload).getAsJsonObject();
            if (o.has("chunked")) {
                entry.chunked = true;
                entry.finishOk(null);
            } else {
                entry.finishOk(payload);
            }
        } catch (Exception ex) {
            entry.error("结果解析失败: " + ex.getMessage());
        }
    }

    private void appendConsole(TaskEntry entry, JsonObject params) {
        if (activeTaskId == null || params == null || !params.has("args")) {
            return;
        }
        String level = params.has("type") ? params.get("type").getAsString() : "log";
        String prefix = "error".equals(level) ? "[error] " : "warning".equals(level) ? "[warn] " : "";
        StringBuilder text = new StringBuilder();
        params.getAsJsonArray("args").forEach(arg -> {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            if (arg.isJsonPrimitive()) {
                text.append(arg.getAsString());
            } else {
                text.append(arg.toString());
            }
        });
        String line = text.toString();
        if (line.length() > MAX_CONSOLE_LINE_CHARS) {
            line = line.substring(0, MAX_CONSOLE_LINE_CHARS) + "...(截断)";
        }
        entry.appendConsole(prefix + line);
    }

    private static String extractError(String payload) {
        try {
            JsonObject o = JsonParser.parseString(payload).getAsJsonObject();
            return o.has("result") && !o.get("result").isJsonNull()
                ? o.get("result").getAsString() : payload;
        } catch (Exception ex) {
            return payload;
        }
    }

    private void resetPage() {
        try {
            if (browser != null) {
                client = browser.connectFreshPage();
            }
        } catch (Exception ignored) {
            if (browser != null) {
                browser.close();
                browser = null;
            }
            client = null;
        }
        bootstrapReady = false;
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    public record WebViewRuntimeOutcome(
        boolean ok,
        String payloadJson,
        String error,
        List<String> console,
        long elapsedMs
    ) {
        static WebViewRuntimeOutcome ok(String payloadJson, List<String> console, long elapsedMs) {
            return new WebViewRuntimeOutcome(true, payloadJson, null, console, elapsedMs);
        }

        static WebViewRuntimeOutcome error(String error, List<String> console, long elapsedMs) {
            return new WebViewRuntimeOutcome(false, null, error, console, elapsedMs);
        }
    }

    private static final class TaskEntry {
        final CountDownLatch latch = new CountDownLatch(1);
        final BridgeCodec.ChunkAssembler assembler = new BridgeCodec.ChunkAssembler();
        private final List<String> console = Collections.synchronizedList(new ArrayList<>());
        private final AtomicBoolean failed = new AtomicBoolean();
        volatile String failReason;
        volatile String payloadJson;
        volatile boolean chunked;

        boolean failed() {
            return failed.get();
        }

        void finishOk(String json) {
            payloadJson = json;
            latch.countDown();
        }

        void error(String msg) {
            if (failed.compareAndSet(false, true)) {
                failReason = msg;
                latch.countDown();
            }
        }

        void failNative(String msg) {
            error("native: " + msg);
        }

        void appendConsole(String line) {
            if (console.size() < MAX_CONSOLE_LINES) {
                console.add(line);
            }
        }

        List<String> console() {
            synchronized (console) {
                return new ArrayList<>(console);
            }
        }
    }
}
