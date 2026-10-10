package com.agent1.javaagent.weizhi.desktop;

import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
import com.google.gson.Gson;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.weizhi.agent.web.BridgeCodec;
import com.weizhi.agent.web.WebViewTask;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** webview_exec 入参准备与回执 JSON。落盘语义对齐 Android {@code WebViewExecTool}（weizhi#18）。 */
public final class WebViewExecSupport {

    /** 非图片结果超过此字节数自动落盘（与 bridge 内联上限一致）。 */
    static final int AUTO_SPILL_BYTES = BridgeCodec.INLINE_LIMIT;
    /** 落盘后的回执预览：不含结果正文。 */
    static final String SPILL_PREVIEW = "完整结果已写入 outputPath";

    private static final Gson GSON = new Gson();
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
        .callTimeout(180, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .build();
    private static final AtomicInteger SPILL_SEQ = new AtomicInteger();

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
        String outputRel = outputPath != null ? outputPath.trim() : "";
        boolean needSandbox = (inputPath != null && !inputPath.trim().isEmpty()) || !outputRel.isEmpty();
        if (needSandbox && sandbox == null) {
            return errJson("宿主未配置 workspace 沙箱,input_path/output_path 不可用。");
        }
        if (!outputRel.isEmpty()) {
            try {
                sandbox.resolveWrite(outputRel);
            } catch (SecurityException e) {
                return errJson("output_path 非法: " + e.getMessage());
            }
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

        WebViewTask task = new WebViewTask(code, inputB64, wasmB64, timeout);
        CdpWebViewRuntime.WebViewRuntimeOutcome outcome = runtime.execute(task);
        if (!outcome.ok()) {
            return errJson(outcome.error());
        }
        return renderOk(sandbox, outcome, outputRel.isEmpty() ? null : outputRel);
    }

    static String renderOk(
        WorkspaceSandbox sandbox,
        CdpWebViewRuntime.WebViewRuntimeOutcome o,
        String outputRel
    ) {
        WebViewSpillResult parsed = WebViewSpillResult.parse(o.payloadJson());
        if (parsed.parseError != null) {
            return errJson("结果解析失败: " + parsed.parseError);
        }
        if (parsed.spillUtf8 == null) {
            return errJson("没有可落盘的返回值:脚本返回了 null 或 undefined。不会写入文件。");
        }
        boolean explicit = outputRel != null && !outputRel.isEmpty();
        boolean image = "string".equals(parsed.resultType) && WebViewSpillResult.isImageBase64(parsed.text);
        boolean spill = explicit || image || parsed.spillUtf8.length > AUTO_SPILL_BYTES;
        if (spill && sandbox == null) {
            return errJson("结果需要写入工作区文件,但宿主未配置 workspace 沙箱。");
        }

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("ok", true);
        receipt.put("elapsedMs", o.elapsedMs());
        if (parsed.unserializable) {
            receipt.put("note", "结果不可 JSON 序列化,已降级为字符串形态");
        }
        if (spill) {
            try {
                String rel = explicit ? outputRel : allocateSpillRel(sandbox);
                Path path = sandbox.resolveWrite(rel);
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.write(path, parsed.spillUtf8);
                receipt.put("outputPath", rel);
                receipt.put("outputBytes", parsed.spillUtf8.length);
            } catch (IOException | SecurityException e) {
                return errJson("结果写入失败: " + e.getMessage());
            }
        }
        receipt.put("resultType", parsed.resultType);
        receipt.put(
            "resultPreview",
            spill ? SPILL_PREVIEW : BridgeCodec.embedPreview(parsed.resultType, parsed.text)
        );
        List<String> console = o.console();
        if (console != null && !console.isEmpty()) {
            receipt.put("console", console);
        }
        return GSON.toJson(receipt);
    }

    private static String allocateSpillRel(WorkspaceSandbox sandbox) throws IOException {
        for (int n = 0; n < 8; n++) {
            String rel = "tmp/webview_exec/wv-" + System.currentTimeMillis()
                + "-" + SPILL_SEQ.incrementAndGet() + ".b64";
            if (!Files.exists(sandbox.resolveWrite(rel))) {
                return rel;
            }
        }
        throw new IOException("无法分配 tmp/webview_exec/*.b64");
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
