package com.agent1.javaagent.weizhi.desktop;

import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.weizhi.agent.sandbox.WorkspaceSandbox;
import com.weizhi.agent.web.BridgeCodec;
import com.weizhi.agent.web.WebViewTask;
import com.weizhi.agent.web.WebViewTaskAccess;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** webview_exec 入参准备与回执 JSON（桌面 CDP / Android WebView 共用逻辑）。 */
public final class WebViewExecSupport {

    private static final Gson GSON = new Gson();
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
        .callTimeout(180, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .build();
    private static final int PREVIEW_CHARS = 1024;

    private WebViewExecSupport() {
    }

    public static String execute(
        CdpWebViewRuntime runtime,
        WorkspaceSandbox sandbox,
        String code,
        String wasmUrl,
        String inputPath,
        String outputPath,
        String timeoutMs
    ) {
        String verr = WebViewTask.validate(code, wasmUrl);
        if (verr != null) {
            return errJson(verr);
        }
        long timeout = WebViewTask.DEFAULT_TIMEOUT_MS;
        if (timeoutMs != null && !timeoutMs.trim().isEmpty()) {
            try {
                timeout = Long.parseLong(timeoutMs.trim());
            } catch (NumberFormatException e) {
                return errJson("timeout_ms 必须是毫秒整数(收到: " + timeoutMs + ")。");
            }
        }
        timeout = WebViewTask.clampTimeout(timeout);
        boolean needSandbox = (inputPath != null && !inputPath.trim().isEmpty())
            || (outputPath != null && !outputPath.trim().isEmpty());
        if (needSandbox && sandbox == null) {
            return errJson("宿主未配置 workspace 沙箱,input_path/output_path 不可用。");
        }

        String wasmB64 = null;
        if (wasmUrl != null && !wasmUrl.trim().isEmpty()) {
            byte[] wasm = downloadWasm(wasmUrl.trim());
            if (wasm == null) {
                return errJson("wasm_url 下载失败: " + wasmUrl + "(详见日志);请确认 URL 可达与大小 ≤50MB。");
            }
            wasmB64 = Base64.getEncoder().encodeToString(wasm);
        }

        String inputB64 = null;
        if (inputPath != null && !inputPath.trim().isEmpty()) {
            byte[] bytes = readInput(sandbox, inputPath.trim());
            if (bytes == null) {
                return errJson("input_path 读取失败: " + inputPath + "(详见日志)。");
            }
            inputB64 = Base64.getEncoder().encodeToString(bytes);
        }

        String outputRel = outputPath != null ? outputPath.trim() : "";
        if (!outputRel.isEmpty()) {
            try {
                sandbox.resolveWrite(outputRel);
            } catch (SecurityException e) {
                return errJson("output_path 非法: " + e.getMessage());
            }
        }

        WebViewTask task = new WebViewTask(code, inputB64, wasmB64,
            outputRel.isEmpty() ? null : outputRel, timeout);
        CdpWebViewRuntime.WebViewRuntimeOutcome outcome = runtime.execute(task);
        if (!outcome.ok()) {
            return errJson(outcome.error());
        }
        return renderOk(task, sandbox, outcome);
    }

    private static String renderOk(
        WebViewTask task,
        WorkspaceSandbox sandbox,
        CdpWebViewRuntime.WebViewRuntimeOutcome o
    ) {
        Object result = null;
        boolean unserializable = false;
        try {
            JsonObject payload = JsonParser.parseString(o.payloadJson()).getAsJsonObject();
            if (payload.has("result") && !payload.get("result").isJsonNull()) {
                result = payload.get("result");
            }
            unserializable = payload.has("unserializable") && payload.get("unserializable").getAsBoolean();
        } catch (Exception e) {
            return errJson("结果解析失败: " + e.getMessage());
        }
        String resultText = result == null ? "null"
            : (result instanceof com.google.gson.JsonPrimitive
                && ((com.google.gson.JsonPrimitive) result).isString()
                ? ((com.google.gson.JsonPrimitive) result).getAsString()
                : GSON.toJson(result));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("elapsedMs", o.elapsedMs());
        if (unserializable) {
            m.put("note", "结果不可 JSON 序列化,已降级为字符串形态");
        }
        String outputRel = WebViewTaskAccess.outputRel(task);
        if (outputRel != null) {
            try {
                Path p = sandbox.resolveWrite(outputRel);
                if (p.getParent() != null) {
                    Files.createDirectories(p.getParent());
                }
                byte[] bytes = result instanceof com.google.gson.JsonPrimitive
                    && ((com.google.gson.JsonPrimitive) result).isString()
                    ? resultText.getBytes(StandardCharsets.UTF_8)
                    : GSON.toJson(result).getBytes(StandardCharsets.UTF_8);
                Files.write(p, bytes);
                m.put("outputPath", outputRel);
                m.put("outputBytes", bytes.length);
            } catch (IOException | SecurityException e) {
                return errJson("任务执行成功但结果落盘失败(" + outputRel + "): "
                    + e.getMessage() + "。结果预览: " + preview(resultText));
            }
        } else if (resultText.length() > BridgeCodec.INLINE_LIMIT) {
            m.put("hint", "结果 " + resultText.length() + " 字符未保存:未提供 output_path,"
                + "请带 output_path 重跑获取完整结果。");
        }
        m.put("resultPreview", preview(resultText));
        List<String> console = o.console();
        if (console != null && !console.isEmpty()) {
            m.put("console", console);
        }
        return GSON.toJson(m);
    }

    private static String preview(String s) {
        return s.length() <= PREVIEW_CHARS ? s : s.substring(0, PREVIEW_CHARS)
            + "...(截断,共 " + s.length() + " 字符)";
    }

    public static String errJson(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", msg);
        return GSON.toJson(m);
    }

    private static byte[] readInput(WorkspaceSandbox sandbox, String inputPath) {
        Path p;
        try {
            p = sandbox.resolveRead(inputPath);
        } catch (SecurityException e) {
            return null;
        }
        if (!Files.isRegularFile(p)) {
            return null;
        }
        try {
            long size = Files.size(p);
            if (size > WebViewTask.MAX_INPUT_BYTES) {
                return null;
            }
            return Files.readAllBytes(p);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] downloadWasm(String url) {
        Request req;
        try {
            req = new Request.Builder().url(url).build();
        } catch (IllegalArgumentException e) {
            return null;
        }
        try (Response resp = HTTP.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                return null;
            }
            ResponseBody body = resp.body();
            if (body == null) {
                return null;
            }
            if (body.contentLength() > WebViewTask.MAX_WASM_BYTES) {
                return null;
            }
            try (InputStream in = body.byteStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                long total = 0L;
                int n;
                while ((n = in.read(b)) > 0) {
                    total += n;
                    if (total > WebViewTask.MAX_WASM_BYTES) {
                        return null;
                    }
                    buf.write(b, 0, n);
                }
                return buf.toByteArray();
            }
        } catch (IOException e) {
            return null;
        }
    }
}
