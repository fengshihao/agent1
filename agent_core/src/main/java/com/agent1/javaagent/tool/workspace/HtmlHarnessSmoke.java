package com.agent1.javaagent.tool.workspace;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 写入完整 HTML 页后，宿主用已注册的 {@code webview_exec} 在 iframe 里打开并回报指标。
 * 模型不需要再记得去测。没有 WebView 时只附加跳过说明，不把写入标成失败。
 */
public final class HtmlHarnessSmoke {

    static final int LARGE_HTML_CHARS = 6_000;
    static final int FAT_STYLE_CHARS = 1_500;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern STYLE_BLOCK = Pattern.compile("(?is)<style[^>]*>(.*?)</style>");
    private static final String COACH_MISSING_SOURCE =
        "HTML 壳应保持很短。正文放到同目录的 Markdown、Mermaid 或 JSON，再用 find_caps 的 web_lib 示例渲染。"
            + "样式用条目里的 CSS URL，不要内联大段 style。";

    /**
     * 在引导页里开 iframe 写 srcdoc。不能 document.write，否则会拆掉 WebView 与宿主的桥。
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
          await new Promise((r) => setTimeout(r, 4000));
          let doc = null;
          try { doc = frame.contentDocument; } catch (e) { doc = null; }
          const svg = doc ? doc.querySelectorAll('svg').length : 0;
          const canvas = doc ? doc.querySelectorAll('canvas').length : 0;
          const text = doc && doc.body ? String(doc.body.innerText || '').replace(/\\s+/g, ' ').trim() : '';
          frame.remove();
          return JSON.stringify({svg:svg, canvas:canvas, textLen:text.length, textHead:text.slice(0, 60)});
        })();
        """.trim();

    private HtmlHarnessSmoke() {
    }

    public record Outcome(String text, boolean failed) {
    }

    /**
     * @return {@code null} 表示这次写入不是 HTML 页，调用方保持原回执
     */
    public static Outcome augment(
        AgentTool webview,
        WorkspaceSandbox sandbox,
        JsonNode writeParams,
        String writeResultText,
        CancellationToken token
    ) {
        if (writeParams == null) {
            return null;
        }
        String path = writeParams.path("path").asText("").trim();
        String content = contentFor(writeParams, sandbox, path);
        if (!isHarness(path, content)) {
            return null;
        }
        String base = writeResultText == null ? "" : writeResultText;
        StringBuilder out = new StringBuilder(base);
        String sourceAdvice = missingSourceAdvice(sandbox, path, content);
        if (sourceAdvice != null) {
            out.append("\n\n---\n[coach] html.missing_source: ").append(sourceAdvice);
        }
        if (token != null && token.isCancelled()) {
            return new Outcome(out.toString(), false);
        }
        if (webview == null) {
            out.append("\nhtml_smoke: 跳过（没有 webview_exec），未在浏览器里打开。");
            return new Outcome(out.toString(), false);
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
            out.append("\nhtml_smoke: 失败。webview_exec 异常：").append(clip(message, 240));
            return new Outcome(out.toString(), true);
        }
        Verdict verdict = interpret(content, receipt);
        out.append("\n").append(verdict.summary());
        return new Outcome(out.toString(), !verdict.ok());
    }

    private static String contentFor(JsonNode params, WorkspaceSandbox sandbox, String path) {
        if (params.has("content") && !params.get("content").isNull()) {
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
            return Files.readString(file);
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

    static String missingSourceAdvice(WorkspaceSandbox sandbox, String path, String content) {
        if (content == null) {
            return null;
        }
        boolean fat = content.length() >= LARGE_HTML_CHARS || inlineStyleChars(content) >= FAT_STYLE_CHARS;
        if (!fat || hasStructuredSibling(sandbox, path)) {
            return null;
        }
        return COACH_MISSING_SOURCE;
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
        } catch (Exception ignored) {
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

    private static int inlineStyleChars(String content) {
        int total = 0;
        Matcher matcher = STYLE_BLOCK.matcher(content);
        while (matcher.find()) {
            total += matcher.group(1).length();
        }
        return total;
    }

    private static boolean hasStructuredSibling(WorkspaceSandbox sandbox, String htmlPath) {
        if (sandbox == null || htmlPath == null || htmlPath.isBlank()) {
            return false;
        }
        final Path file;
        try {
            file = sandbox.resolveWrite(htmlPath);
        } catch (SecurityException e) {
            return false;
        }
        Path dir = file.getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(path -> {
                if (path.equals(file) || !Files.isRegularFile(path)) {
                    return false;
                }
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".md")
                    || name.endsWith(".mmd")
                    || name.endsWith(".json")
                    || name.endsWith(".csv")
                    || name.endsWith(".yml")
                    || name.endsWith(".yaml");
            });
        } catch (IOException e) {
            return false;
        }
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
