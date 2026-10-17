package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.MutableScriptToolBridge;
import com.agent1.javaagent.script.ScriptEngine;
import com.agent1.javaagent.script.ScriptToolBridge;
import com.agent1.javaagent.weizhi.desktop.DesktopChromiumLocator;
import com.agent1.javaagent.weizhi.desktop.WebViewExecSupport;
import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code renderHtmlDeck}：QuickJS 编排 + webview_exec 截图 + pptx 组包。
 * WebView 用假回执测编排与组包；真 Chromium 端到端单列一测。
 */
class Html2PptxScriptTest {

    private static final String PNG_B64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    private static final String DECK_HTML = """
        <!DOCTYPE html><html><head><meta charset="utf-8"><style>
        .slide{width:1280px;height:720px}
        </style></head><body>
        <section class="slide"><h1>第一页</h1><img src="pic.png"></section>
        <section class="slide"><h1>第二页</h1></section>
        </body></html>
        """;

    @BeforeAll
    static void requireNative() {
        Path repo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (repo.getNameCount() == 0 || !Files.isDirectory(repo)) {
            repo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        assumeTrue(WeizhiJniBootstrap.tryLoad(repo), "libweizhijni not built; run weizhi ./scripts/build.sh");
    }

    @Test
    void packsPictureDeckFromWebViewShots(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("deck.html"), DECK_HTML);
        Files.write(workspace.resolve("pic.png"), java.util.Base64.getDecoder().decode(PNG_B64));

        AtomicReference<Map<String, Object>> seen = new AtomicReference<>();
        AtomicReference<String> fedHtml = new AtomicReference<>();
        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> {
            seen.set(arguments);
            String input = String.valueOf(arguments.get("input_path"));
            if (input.endsWith(".html")) {
                try {
                    fedHtml.set(Files.readString(workspace.resolve(input)));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            String shots = "{\"slides\":["
                + shotJson(1280, 720) + "," + shotJson(1280, 720) + "]}";
            return mockWebViewReceipt(workspace, arguments, shots);
        });
        try (engine) {
            String out = engine.evalForAgent("""
                import { renderHtmlDeck } from "weizhi:html2pptx";
                export default await renderHtmlDeck({ htmlPath: "deck.html", output: "out/deck.pptx" });
                """, null, 30_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out.contains("\"ok\":true"), out);
            assertTrue(out.contains("out/deck.pptx"), out);
            assertTrue(out.contains("\"slides\":2"), out);
        }
        String inputPath = String.valueOf(seen.get().get("input_path"));
        assertTrue(inputPath.endsWith(".html"), "应传入 HTML 输入: " + inputPath);
        String code = String.valueOf(seen.get().get("code"));
        assertTrue(code.contains(".slide"), code);
        assertTrue(code.contains("html-to-image"), code);
        // 相对路径图片应被内联为 data URI（srcdoc 无 base URL）
        String html = String.valueOf(fedHtml.get());
        assertTrue(html.contains("data:image/png;base64,"), "workspace 图片应内联");
        assertTrue(!html.contains("src=\"pic.png\""), "原相对路径引用应被替换");

        Path pptx = workspace.resolve("out/deck.pptx");
        assertTrue(Files.isRegularFile(pptx), "应生成 pptx");
        try (ZipFile zip = new ZipFile(pptx.toFile())) {
            String slide1 = zipText(zip, "ppt/slides/slide1.xml");
            assertTrue(slide1.contains("<p:pic>"), "slide1 应是图片页");
            assertTrue(slide1.contains("r:embed=\"rId2\""));
            assertTrue(zipText(zip, "ppt/presentation.xml").contains("cx=\"12192000\" cy=\"6858000\""));
            assertTrue(zip.getEntry("ppt/media/image1.png") != null, "应有首页图片");
            assertTrue(zip.getEntry("ppt/media/image2.png") != null, "应有第二页图片");
            assertTrue(zip.getEntry("ppt/media/image2.png") != null, "应有第二页图片");
            assertTrue(zipText(zip, "docProps/core.xml").contains("Presentation"));
        }
        // 输入副本与截图回执应清理；tmp/html2pptx-render-* 组包暂存目录与 pptx.js 一致保留
        try (var list = Files.list(workspace.resolve("tmp"))) {
            assertEquals(0, list.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("html2pptx-input-") || n.startsWith("html2pptx-shots-");
            }).count());
        }
    }

    @Test
    void rejectsShotsWithoutImageMagic(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("deck.html"), DECK_HTML);

        // 合法 base64 但不是 PNG（文本 "notapng"）
        String notPng = java.util.Base64.getEncoder().encodeToString("notapng".getBytes(StandardCharsets.UTF_8));
        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) ->
            mockWebViewReceipt(workspace, arguments, "{\"slides\":[{\"w\":1280,\"h\":720,\"b64\":\"" + notPng + "\"}]}")
        );
        try (engine) {
            RuntimeException ex = assertThrows(RuntimeException.class, () -> engine.evalForAgent("""
                import { renderHtmlDeck } from "weizhi:html2pptx";
                export default await renderHtmlDeck({ htmlPath: "deck.html", output: "out/bad.pptx" });
                """, null, 30_000, new CancellationToken(), "jobs/run.js"));
            assertTrue(ex.getMessage().contains("PNG"), ex.getMessage());
        }
    }

    @Test
    void requiresHtmlBeforeWebView(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        AtomicInteger calls = new AtomicInteger();
        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> {
            calls.incrementAndGet();
            return "{\"ok\":false,\"error\":\"should not run\"}";
        });
        try (engine) {
            RuntimeException ex = assertThrows(RuntimeException.class, () -> engine.evalForAgent("""
                import { renderHtmlDeck } from "weizhi:html2pptx";
                export default await renderHtmlDeck({ output: "out/x.pptx" });
                """, null, 30_000, new CancellationToken(), "jobs/run.js"));
            assertTrue(ex.getMessage().contains("html"), ex.getMessage());
            assertEquals(0, calls.get());
        }
    }

    /** 端到端：真 Chromium 渲染截图，验证 CDN/iframe/分块回传整链路。 */
    @Test
    void rendersDeckThroughRealChromium(@TempDir Path agentRoot) throws Exception {
        assumeTrue(CdpWebViewRuntime.isAvailable(), "需要本机 Chromium（CDP）");
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("deck.html"), DECK_HTML);
        Files.write(workspace.resolve("pic.png"), java.util.Base64.getDecoder().decode(PNG_B64));

        Path repo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (repo.getNameCount() == 0 || !Files.isDirectory(repo)) {
            repo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        CdpWebViewRuntime runtime = CdpWebViewRuntime.getInstance(repo);
        com.agent1.javaagent.workspace.WorkspaceSandbox sandbox =
            new com.agent1.javaagent.workspace.WorkspaceSandbox(workspace);

        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> WebViewExecSupport.execute(
            runtime,
            sandbox,
            String.valueOf(arguments.get("code")),
            null,
            arguments.get("input_path") == null ? null : String.valueOf(arguments.get("input_path")),
            arguments.get("output_path") == null ? null : String.valueOf(arguments.get("output_path")),
            String.valueOf(arguments.get("timeout_ms"))
        ));
        try (engine) {
            String out = engine.evalForAgent("""
                import { renderHtmlDeck } from "weizhi:html2pptx";
                export default await renderHtmlDeck({
                  htmlPath: "deck.html",
                  output: "out/real.pptx",
                  scale: 2
                });
                """, null, 300_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out.contains("\"ok\":true"), out);
        }
        Path pptx = workspace.resolve("out/real.pptx");
        assertTrue(Files.isRegularFile(pptx));
        assertTrue(Files.size(pptx) > 10_000, "两页截图 pptx 应显著大于空壳");
        try (ZipFile zip = new ZipFile(pptx.toFile())) {
            ZipEntry image = zip.getEntry("ppt/media/image1.png");
            assertTrue(image != null && image.getSize() > 2_000, "首页截图应有实际内容");
        }
    }

    /** 样例输出：HTML2PPTX_SAMPLE_DIR 设置时渲染 html2pptx 版与 pptx.js 版对比件。 */
    @Test
    void rendersSampleDecksForComparison(@TempDir Path agentRoot) throws Exception {
        assumeTrue(CdpWebViewRuntime.isAvailable(), "需要本机 Chromium（CDP）");
        String dir = System.getenv("HTML2PPTX_SAMPLE_DIR");
        assumeTrue(dir != null && !dir.isBlank(), "HTML2PPTX_SAMPLE_DIR not set");
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path out = Path.of(dir);
        Files.createDirectories(out);
        Files.writeString(out.resolve("sample-deck.html"), SAMPLE_DECK_HTML);

        Path repo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (repo.getNameCount() == 0 || !Files.isDirectory(repo)) {
            repo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        CdpWebViewRuntime runtime = CdpWebViewRuntime.getInstance(repo);
        com.agent1.javaagent.workspace.WorkspaceSandbox sandbox =
            new com.agent1.javaagent.workspace.WorkspaceSandbox(out);

        ScriptEngine engine = open(agentRoot, out, (toolName, arguments) -> WebViewExecSupport.execute(
            runtime,
            sandbox,
            String.valueOf(arguments.get("code")),
            null,
            arguments.get("input_path") == null ? null : String.valueOf(arguments.get("input_path")),
            arguments.get("output_path") == null ? null : String.valueOf(arguments.get("output_path")),
            String.valueOf(arguments.get("timeout_ms"))
        ));
        try (engine) {
            String out1 = engine.evalForAgent("""
                import { renderHtmlDeck } from "weizhi:html2pptx";
                export default await renderHtmlDeck({
                  htmlPath: "sample-deck.html",
                  output: "html2pptx-对比样例.pptx"
                });
                """, null, 300_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out1.contains("\"ok\":true"), out1);
            String out2 = engine.evalForAgent("""
                import { renderPptx } from "weizhi:pptx";
                export default renderPptx({
                  title: "Agent1 季度复盘",
                  theme: "dusk",
                  slides: [
                    { layout: "title", title: "Agent1 季度复盘", subtitle: "2026 Q3 · 产品部" },
                    { layout: "cards", title: "三件事", items: [
                      { title: "产品", text: "核心路径留存稳住，没有再掉。" },
                      { title: "销售", text: "新客增加，获客成本下降。" },
                      { title: "交付", text: "故障恢复时间缩短到半天。" }
                    ]},
                    { layout: "stat", title: "指标", items: [
                      { value: "12%", label: "收入" },
                      { value: "98%", label: "留存" },
                      { value: "0.5d", label: "恢复" }
                    ]},
                    { layout: "callout", title: "一句", text: "少而准的字，配上刚好的框。" }
                  ]
                }, "pptx-对比样例.pptx");
                """, null, 60_000, new CancellationToken(), "jobs/run2.js");
            assertTrue(out2.contains("\"ok\":true"), out2);
        }
        assertTrue(Files.size(out.resolve("html2pptx-对比样例.pptx")) > 10_000);
        assertTrue(Files.size(out.resolve("pptx-对比样例.pptx")) > 100);
    }

    private static final String SAMPLE_DECK_HTML = """
        <!DOCTYPE html><html><head><meta charset="utf-8"><title>Agent1 季度复盘</title><style>
        *{box-sizing:border-box;margin:0}
        body{font-family:Helvetica,Arial,'PingFang SC','Microsoft YaHei',sans-serif}
        .slide{width:1280px;height:720px;overflow:hidden;position:relative}
        .cover{background:linear-gradient(135deg,#1a1423 0%,#2a2140 55%,#312846 100%);color:#f6f1ea;
          display:flex;flex-direction:column;justify-content:center;padding:0 110px}
        .cover .kicker{color:#e8b86d;font-size:22px;letter-spacing:8px;margin-bottom:28px}
        .cover h1{font-size:88px;font-weight:800;line-height:1.15}
        .cover .rule{width:96px;height:6px;background:linear-gradient(90deg,#e8b86d,#7dd3c0);margin:36px 0;border-radius:3px}
        .cover .sub{font-size:30px;color:#c4b8a8}
        .cover .deco{position:absolute;right:-160px;top:-160px;width:520px;height:520px;border-radius:50%;
          background:radial-gradient(circle,rgba(125,211,192,.25),transparent 65%)}
        .cards{background:#faf9f6;padding:0 96px;display:flex;flex-direction:column;justify-content:center;min-height:720px}
        .cards h2{font-size:44px;color:#122033;margin-bottom:52px}
        .cards h2 em{font-style:normal;color:#b8860b;border-bottom:6px solid #e8b86d}
        .grid{display:grid;grid-template-columns:repeat(3,1fr);gap:36px;align-items:stretch}
        .card{background:#fff;border-radius:18px;padding:44px 32px;border-top:6px solid #7dd3c0;
          box-shadow:0 12px 32px rgba(18,32,51,.08)}
        .card:nth-child(2){border-top-color:#e8b86d}
        .card:nth-child(3){border-top-color:#8b7ec8}
        .card .ico{font-size:48px;margin-bottom:22px}
        .card h3{font-size:28px;color:#122033;margin-bottom:14px}
        .card p{font-size:21px;color:#5b6b7c;line-height:1.7}
        .cards .footnote{margin-top:52px;color:#9aa5b1;font-size:18px;letter-spacing:2px}
        .chart{background:#ffffff;padding:64px 88px;display:flex;gap:56px;align-items:center}
        .chart h2{font-size:40px;color:#122033;margin-bottom:8px}
        .chart .takeaway{flex:0 0 380px}
        .chart .takeaway p{font-size:24px;color:#5b6b7c;line-height:1.7;margin:20px 0}
        .chart .takeaway .big{color:#2c7a7b;font-size:34px;font-weight:800}
        #bar{width:660px;height:520px}
        .ending{background:linear-gradient(135deg,#122033,#1a1423);color:#f6f1ea;display:flex;
          flex-direction:column;justify-content:center;align-items:center;text-align:center}
        .ending .big{font-size:140px;font-weight:800;background:linear-gradient(90deg,#e8b86d,#7dd3c0);
          -webkit-background-clip:text;background-clip:text;color:transparent}
        .ending p{font-size:28px;color:#c4b8a8;margin-top:24px}
        </style></head><body>
        <section class="slide cover">
          <div class="deco"></div>
          <div class="kicker">AGENT1 · Q3 REVIEW</div>
          <h1>季度复盘</h1>
          <div class="rule"></div>
          <div class="sub">2026 Q3 · 产品部</div>
        </section>
        <section class="slide cards">
          <div>
            <h2>这个季度<em>三件事</em></h2>
            <div class="grid">
              <div class="card"><div class="ico">🛠</div><h3>产品</h3><p>核心路径留存稳住，没有再掉。</p></div>
              <div class="card"><div class="ico">📈</div><h3>销售</h3><p>新客增加，获客成本下降。</p></div>
              <div class="card"><div class="ico">🚀</div><h3>交付</h3><p>故障恢复时间缩短到半天。</p></div>
            </div>
            <div class="footnote">AGENT1 · Q3 REVIEW · 02</div>
          </div>
        </section>
        <section class="slide chart">
          <div id="bar"></div>
          <div class="takeaway">
            <h2>收入结构</h2>
            <p>订阅收入连续三季上升，本季占比首次过半。</p>
            <p class="big">+12% YoY</p>
            <p>一次性项目占比下降，结构更健康。</p>
          </div>
        </section>
        <section class="slide ending">
          <div class="big">12%</div>
          <p>少而准的字，自由排版的表达。</p>
        </section>
        <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.1/dist/echarts.min.js"></script>
        <script>
        var chart = echarts.init(document.getElementById('bar'), null, { renderer: 'canvas' });
        chart.setOption({
          animation: false,
          legend: { top: 0, textStyle: { fontSize: 18, color: '#5b6b7c' } },
          grid: { left: 48, right: 24, top: 56, bottom: 40 },
          xAxis: { type: 'category', data: ['Q4·25', 'Q1·26', 'Q2·26', 'Q3·26'],
            axisLabel: { fontSize: 18, color: '#5b6b7c' } },
          yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf2f7' } } },
          series: [
            { name: '订阅', type: 'bar', stack: 'x', data: [38, 44, 52, 61],
              itemStyle: { color: '#2c7a7b', borderRadius: [0, 0, 0, 0] }, barWidth: 56 },
            { name: '项目', type: 'bar', stack: 'x', data: [42, 40, 36, 31],
              itemStyle: { color: '#e8b86d', borderRadius: [8, 8, 0, 0] } }
          ]
        });
        </script>
        </body></html>
        """;

    private static String shotJson(int w, int h) {
        return "{\"w\":" + w + ",\"h\":" + h + ",\"b64\":\"" + PNG_B64 + "\"}";
    }

    /** 模拟 webview_exec：把回执 JSON 写到包传入的 output_path，返回成功回执。 */
    private static String mockWebViewReceipt(Path workspace, Map<String, Object> arguments, String shots) {
        try {
            String outputPath = String.valueOf(arguments.get("output_path"));
            Path target = workspace.resolve(outputPath);
            Files.createDirectories(target.getParent());
            Files.writeString(target, shots);
            return "{\"ok\":true,\"outputPath\":\"" + outputPath + "\",\"resultType\":\"string\"}";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ScriptEngine open(Path agentRoot, Path workspace, ToolCall call) {
        MutableScriptToolBridge bridge = new MutableScriptToolBridge();
        bridge.set(new ScriptToolBridge() {
            @Override
            public Set<String> exposedNames() {
                return Set.of("webview_exec");
            }

            @Override
            public String call(String toolName, Map<String, Object> arguments) {
                return call.apply(toolName, arguments);
            }
        });
        return new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions().installDesktopCaps(true).scriptToolBridge(bridge),
            agentRoot
        ).open(workspace);
    }

    @FunctionalInterface
    private interface ToolCall {
        String apply(String toolName, Map<String, Object> arguments);
    }

    private static String zipText(ZipFile zip, String entryName) throws Exception {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) {
            return "";
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
