package com.agent1.javaagent.tool.workspace;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.util.PathIo;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * HTML 交付冒烟（18 号规划 Phase C 优化：检测时机从「每笔写入」改为「run 收尾统一」）。
 *
 * 每笔 write/edit 只收集路径（无浏览器调用，~0ms）；
 * {@code AgentRuntime} 在 run 交付前对本轮改动过的 html 各跑一次
 * {@link #smokeFile}（宿主用已注册的 {@code webview_exec} 在 iframe 里打开并回报指标）。
 * 这样编辑反馈循环回到 ~100ms 级，白屏拦截仍保留在交付边界。
 */
public final class HtmlHarnessSmoke {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 在引导页里开 iframe 写 srcdoc。不能 document.write，否则会拆掉 WebView 与宿主的桥。
     * iframe 的 load 到了就读 DOM。普通页面立刻返回；Mermaid、图表一类最多再等 4 秒，图一出现就停。
     */
    static final String PROBE_JS = """
        return (async () => {
          const fail = (reason) => JSON.stringify({svg:0,canvas:0,textLen:0,reason:reason});
          if (typeof input === 'undefined' || input == null) return fail('no-input');
          const html = new TextDecoder('utf-8').decode(input);
          const frame = document.createElement('iframe');
          frame.setAttribute('style', 'width:960px;height:640px;border:0');
          document.body.appendChild(frame);
          await new Promise((resolve) => {
            let settled = false;
            const done = () => { if (!settled) { settled = true; resolve(); } };
            frame.addEventListener('load', done);
            setTimeout(done, 12000);
            frame.srcdoc = html;
          });
          const graphic = /markmap|mermaid|chart\\.js|chartjs|\\/npm\\/three@|three\\.module|echarts|plotly|\\/npm\\/d3@|p5(?:\\.min)?\\.js|cytoscape|vis-network|vega/i.test(html);
          const metrics = await new Promise((resolve) => {
            const start = Date.now();
            const sample = () => {
              let doc = null;
              try { doc = frame.contentDocument; } catch (e) { doc = null; }
              const svg = doc ? doc.querySelectorAll('svg').length : 0;
              const canvas = doc ? doc.querySelectorAll('canvas').length : 0;
              const text = doc && doc.body ? String(doc.body.innerText || '').replace(/\\s+/g, ' ').trim() : '';
              const elapsed = Date.now() - start;
              if (doc == null && elapsed < 500) {
                setTimeout(sample, 50);
                return;
              }
              if (graphic && svg === 0 && canvas === 0 && elapsed < 4000) {
                setTimeout(sample, 100);
                return;
              }
              resolve({svg:svg, canvas:canvas, textLen:text.length, textHead:text.slice(0, 60)});
            };
            sample();
          });
          frame.remove();
          return metrics;
        })();
        """.trim();

    private HtmlHarnessSmoke() {
    }

    public record Outcome(String text, boolean failed) {
    }

    /**
     * 写入参数里的 html/htm 路径；供 run 收尾收集「本轮改过哪些页面」。
     *
     * @return {@code null} 表示这次写入不是 html 页
     */
    public static String htmlPathOf(JsonNode params) {
        if (params == null) {
            return null;
        }
        String path = params.path("path").asText("").trim();
        if (path.isEmpty()) {
            return null;
        }
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        return (normalized.endsWith(".html") || normalized.endsWith(".htm")) ? path : null;
    }

    /**
     * run 收尾对单个 html 跑冒烟（iframe 打开 + 指标回报）。
     *
     * @return {@code null} 表示该文件已不是 harness 页，调用方安静跳过
     */
    public static Outcome smokeFile(
        AgentTool webview,
        WorkspaceSandbox sandbox,
        String path,
        CancellationToken token
    ) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String content = contentFor(null, sandbox, path);
        if (!isHarness(path, content)) {
            return null;
        }
        if (token != null && token.isCancelled()) {
            return new Outcome("[html_smoke] " + path + " 取消", false);
        }
        if (webview == null) {
            return new Outcome("[html_smoke] " + path + " 跳过（没有 webview_exec）", false);
        }
        String receipt;
        try {
            ObjectNode args = MAPPER.createObjectNode();
            args.put("code", PROBE_JS);
            args.put("input_path", path);
            args.put("timeout_ms", "30000");
            ToolExecutionResult probed = webview.execute(
                "html-smoke",
                args,
                token == null ? new CancellationToken() : token,
                update -> { }
            );
            receipt = probed == null ? "" : probed.getText();
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new Outcome("[html_smoke] " + path + " 失败。webview_exec 异常：" + clip(message, 240), true);
        }
        Verdict verdict = interpret(content, receipt);
        return new Outcome("[html_smoke] " + path + " " + verdict.summary(), !verdict.ok());
    }

    private static String contentFor(JsonNode params, WorkspaceSandbox sandbox, String path) {
        if (params != null && params.has("content") && !params.get("content").isNull()) {
            return params.path("content").asText("");
        }
        if (sandbox == null || path == null || path.isBlank()) {
            return "";
        }
        try {
            Path file = sandbox.resolveWrite(path);
            if (!Files.isRegularFile(file)) {
                return "";
            }
            return PathIo.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException e) {
            return "";
        }
    }

    static boolean isHarness(String path, String content) {
        if (path == null || content == null) {
            return false;
        }
        String normalized = path.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!normalized.endsWith(".html") && !normalized.endsWith(".htm")) {
            return false;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.contains("<!doctype") || lower.contains("<html");
    }

    static boolean expectsGraphic(String content) {
        String lower = content == null ? "" : content.toLowerCase(Locale.ROOT);
        return lower.contains("markmap")
            || lower.contains("mermaid")
            || lower.contains("chart.js")
            || lower.contains("chartjs")
            || lower.contains("/npm/three@")
            || lower.contains("three.module")
            || lower.contains("echarts")
            || lower.contains("plotly")
            || lower.contains("/npm/d3@")
            || lower.contains("p5.min.js")
            || lower.contains("p5.js")
            || lower.contains("cytoscape")
            || lower.contains("vis-network")
            || lower.contains("vega");
    }

    static Verdict interpret(String html, String receipt) {
        if (receipt == null || receipt.isBlank()) {
            return new Verdict(false, "html_smoke: 失败。webview_exec 没有回执。");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(receipt);
        } catch (Exception e) {
            return new Verdict(false, "html_smoke: 失败。回执不是 JSON：" + clip(receipt, 180));
        }
        if (root.path("ok").isBoolean() && !root.path("ok").asBoolean()) {
            String error = root.path("error").asText(root.path("message").asText("webview_exec 失败"));
            return new Verdict(false, "html_smoke: 失败。" + clip(error, 240));
        }
        String console = seriousConsole(root.path("console"));
        JsonNode metrics = metricsOf(root);
        if (metrics == null) {
            String extra = console.isEmpty() ? "" : " 控制台：" + console;
            return new Verdict(false, "html_smoke: 失败。没有页面指标。" + extra);
        }
        if (metrics.path("reason").asText("").equals("no-input")) {
            return new Verdict(false, "html_smoke: 失败。浏览器没有读到 HTML 文件。");
        }
        int svg = metrics.path("svg").asInt(0);
        int canvas = metrics.path("canvas").asInt(0);
        int textLen = metrics.path("textLen").asInt(0);
        boolean graphic = expectsGraphic(html);
        boolean blank = graphic ? (svg == 0 && canvas == 0) : (svg == 0 && canvas == 0 && textLen < 8);
        if (!console.isEmpty() || blank) {
            String why = blank
                ? (graphic ? "页面没有画出图（svg=0 canvas=0）。" : "页面几乎是空的。")
                : "控制台有错误。";
            String tail = console.isEmpty() ? "" : " 控制台：" + console;
            return new Verdict(
                false,
                "html_smoke: 失败。" + why
                    + " svg=" + svg + " canvas=" + canvas + " textLen=" + textLen
                    + tail
                    + " 请按 find_caps 的 web_lib 示例改，不要手写库的内部 API。"
            );
        }
        return new Verdict(
            true,
            "html_smoke: 通过 svg=" + svg + " canvas=" + canvas + " textLen=" + textLen
        );
    }

    private static JsonNode metricsOf(JsonNode root) {
        JsonNode preview = root.get("resultPreview");
        if (preview == null || preview.isNull()) {
            if (root.has("svg") || root.has("textLen")) {
                return root;
            }
            return null;
        }
        if (preview.isObject()) {
            return preview;
        }
        String text = preview.asText("").trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            JsonNode parsed = MAPPER.readTree(text);
            if (parsed.isObject() && (parsed.has("svg") || parsed.has("textLen") || parsed.has("reason"))) {
                return parsed;
            }
        } catch (JsonProcessingException ignored) {
            return null;
        }
        return null;
    }

    private static String seriousConsole(JsonNode console) {
        if (console == null || !console.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode line : console) {
            String text = line.asText("");
            if (!isSeriousConsole(text)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(clip(text, 160));
            if (sb.length() > 360) {
                break;
            }
        }
        return sb.toString();
    }

    private static boolean isSeriousConsole(String line) {
        String lower = line == null ? "" : line.toLowerCase(Locale.ROOT);
        if (lower.contains("favicon")) {
            return false;
        }
        if (lower.contains("failed to load")) {
            return lower.contains(".js") || lower.contains("script");
        }
        return lower.contains("[error]")
            || lower.contains("uncaught")
            || lower.contains("syntaxerror")
            || lower.contains("referenceerror")
            || lower.contains("typeerror");
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.replace('\n', ' ').trim();
        if (flat.length() <= max) {
            return flat;
        }
        return flat.substring(0, max) + "…";
    }

    record Verdict(boolean ok, String summary) {
    }
}
