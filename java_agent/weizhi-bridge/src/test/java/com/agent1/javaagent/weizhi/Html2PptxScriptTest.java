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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

    /** mock webview 回执：一页带 h1 文本块、一页空，模拟真实几何 JSON 的形状。 */
    private static final String MOCK_GEO = """
        {"version":1,"generator":"html2pptx@0.2.0 extractDeckGeometry","slides":[
        {"slideW":1280,"slideH":720,"bg":"#1a1423","elements":[
        {"tag":"h1","cls":null,"rect":{"x":110,"y":300,"w":600,"h":90},
         "font":{"size":88,"weight":"800","color":"#f6f1ea","align":"left","lineHeight":101.2},
         "bg":"#000000","textRuns":[{"text":"第一页","size":88,"weight":"800","color":"#f6f1ea"}]}
        ]},
        {"slideW":1280,"slideH":720,"elements":[]}
        ]}
        """;

    /** 真 Chromium 端到端用 deck：渐变封面（含出界 deco）+ 卡片页。 */
    private static final String GEOMETRY_DECK_HTML = """
        <!DOCTYPE html><html><head><meta charset="utf-8"><title>几何提取</title><style>
        *{box-sizing:border-box;margin:0}
        body{font-family:Helvetica,Arial,'PingFang SC',sans-serif}
        .slide{width:1280px;height:720px;overflow:hidden;position:relative}
        .cover{background:linear-gradient(135deg,#1a1423 0%,#312846 100%);color:#f6f1ea;
          display:flex;flex-direction:column;justify-content:center;padding:0 110px}
        .cover .deco{position:absolute;right:-160px;top:-160px;width:520px;height:520px;border-radius:50%;
          background:radial-gradient(circle,rgba(125,211,192,.25),transparent 65%)}
        .cover .kicker{color:#e8b86d;font-size:22px;letter-spacing:8px;margin-bottom:28px}
        .cover h1{font-size:88px;font-weight:800}
        .cover .rule{width:96px;height:6px;background:linear-gradient(90deg,#e8b86d,#7dd3c0);border-radius:3px;margin:36px 0}
        .cover .sub{font-size:30px;color:#c4b8a8}
        .cards{background:#faf9f6;padding:0 96px;display:flex;flex-direction:column;justify-content:center;min-height:720px}
        .cards h2{font-size:44px;color:#122033;margin-bottom:48px}
        .grid{display:grid;grid-template-columns:repeat(3,1fr);gap:36px}
        .card{background:#fff;border-radius:18px;padding:44px 32px;border-top:6px solid #7dd3c0;
          box-shadow:0 12px 32px rgba(18,32,51,.08)}
        .card:nth-child(2){border-top-color:#e8b86d}
        .card h3{font-size:28px;color:#122033;margin-bottom:14px}
        .card p{font-size:21px;color:#5b6b7c;line-height:1.7}
        </style></head><body>
        <section class="slide cover">
          <div class="deco"></div>
          <div class="kicker">GEOMETRY · TEST</div>
          <h1>季度复盘</h1>
          <div class="rule"></div>
          <div class="sub">2026 Q3 · 产品部</div>
        </section>
        <section class="slide cards">
          <div>
            <h2>三件事</h2>
            <div class="grid">
              <div class="card"><h3>产品</h3><p>留存稳住。</p></div>
              <div class="card"><h3>销售</h3><p>新客增加。</p></div>
              <div class="card"><h3>交付</h3><p>恢复更快。</p></div>
            </div>
          </div>
        </section>
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
        assertTrue(code.contains("writeFile"), "截图应走 writeFile 逐页落盘: " + code);
        assertTrue(code.contains("useWrite"), code);
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
    void returnsOverflowFromWebViewScan(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("deck.html"), DECK_HTML);

        AtomicReference<String> seenCode = new AtomicReference<>();
        // 一页文字越界（真问题）、一页 deco 出界（正常设计）
        String shots = "{\"slides\":[" + shotJson(1280, 720) + "," + shotJson(1280, 720) + "],"
            + "\"over\":[{\"p\":2,\"over\":36,\"el\":\"kpis\",\"text\":true},"
            + "{\"p\":1,\"over\":160,\"el\":\"glow\",\"text\":false}]}";
        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> {
            seenCode.set(String.valueOf(arguments.get("code")));
            return mockWebViewReceipt(workspace, arguments, shots);
        });
        String out;
        try (engine) {
            out = engine.evalForAgent("""
                import { renderHtmlDeck } from "weizhi:html2pptx";
                export default await renderHtmlDeck({ htmlPath: "deck.html", output: "out/deck.pptx" });
                """, null, 30_000, new CancellationToken(), "jobs/run.js");
        }
        assertTrue(out.contains("\"ok\":true"), out);
        assertTrue(out.contains("\"overflow\":[{\"p\":2,\"over\":36,\"el\":\"kpis\",\"text\":true}"), out);
        assertTrue(out.contains("\"el\":\"glow\",\"text\":false"), out);
        // 下发给 webview 的代码应带溢出扫描段，同趟截图零额外往返
        String code = seenCode.get();
        assertTrue(code.contains("scanOverflow"), code);

        // 旧形状回执（无 over 字段）→ overflow 空数组，不报错
        ScriptEngine legacy = open(agentRoot, workspace, (toolName, arguments) ->
            mockWebViewReceipt(workspace, arguments,
                "{\"slides\":[" + shotJson(1280, 720) + "," + shotJson(1280, 720) + "]}"));
        try (legacy) {
            String out2 = legacy.evalForAgent("""
                import { renderHtmlDeck } from "weizhi:html2pptx";
                export default await renderHtmlDeck({ htmlPath: "deck.html", output: "out/deck2.pptx" });
                """, null, 30_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out2.contains("\"overflow\":[]"), out2);
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

    /** extractDeckGeometry：mock 回执测编排——输入内联、提取代码特征、JSON 落盘与清理。 */
    @Test
    void extractsGeometryThroughMockWebView(@TempDir Path agentRoot) throws Exception {
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
            return mockWebViewReceipt(workspace, arguments, MOCK_GEO);
        });
        try (engine) {
            String out = engine.evalForAgent("""
                import { extractDeckGeometry } from "weizhi:html2pptx";
                export default await extractDeckGeometry({ htmlPath: "deck.html" });
                """, null, 30_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out.contains("\"ok\":true"), out);
            assertTrue(out.contains("deck-geometry.json"), out);
            assertTrue(out.contains("\"slides\":2"), out);
            assertTrue(out.contains("\"elements\":1"), out);
        }
        // 提取代码：走 .slide 遍历 + computedStyle，且不依赖截图 CDN 库
        String code = String.valueOf(seen.get().get("code"));
        assertTrue(code.contains(".slide"), code);
        assertTrue(code.contains("getComputedStyle"), code);
        assertTrue(!code.contains("html-to-image"), "几何提取不应注入截图库");
        // workspace 图片同样先内联再喂 webview
        String html = String.valueOf(fedHtml.get());
        assertTrue(html.contains("data:image/png;base64,"), "workspace 图片应内联");
        // 几何 JSON 落盘且结构完整
        Path geo = workspace.resolve("deck-geometry.json");
        assertTrue(Files.isRegularFile(geo), "应生成 deck-geometry.json");
        var root = JsonParser.parseString(Files.readString(geo)).getAsJsonObject();
        assertEquals(1, root.get("version").getAsInt());
        var slides = root.getAsJsonArray("slides");
        assertEquals(2, slides.size());
        var first = slides.get(0).getAsJsonObject();
        assertEquals(1280, first.get("slideW").getAsInt());
        assertEquals(720, first.get("slideH").getAsInt());
        var h1 = firstElement(first, "h1", null);
        assertTrue(h1 != null, "首页应有 h1 元素");
        assertEquals("第一页", h1.getAsJsonArray("textRuns").get(0).getAsJsonObject().get("text").getAsString());
        // 临时文件清理：geo 输入与回执不残留
        try (var list = Files.list(workspace.resolve("tmp"))) {
            assertEquals(0, list.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("html2pptx-geo-input-") || n.startsWith("html2pptx-geo-");
            }).count());
        }
    }

    /** extractDeckGeometry：回执里没有 .slide 页时报可读错误。 */
    @Test
    void rejectsGeometryWithoutSlides(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("deck.html"), DECK_HTML);

        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) ->
            mockWebViewReceipt(workspace, arguments, "{\"version\":1,\"slides\":[]}"));
        try (engine) {
            RuntimeException ex = assertThrows(RuntimeException.class, () -> engine.evalForAgent("""
                import { extractDeckGeometry } from "weizhi:html2pptx";
                export default await extractDeckGeometry({ htmlPath: "deck.html", output: "out/geo.json" });
                """, null, 30_000, new CancellationToken(), "jobs/run.js"));
            assertTrue(ex.getMessage().contains("slide"), ex.getMessage());
        }
    }

    /** 端到端：真 Chromium 渲染并提取几何，验证元素矩形/字体/圆角/渐变背景。 */
    @Test
    void extractsGeometryThroughRealChromium(@TempDir Path agentRoot) throws Exception {
        assumeTrue(CdpWebViewRuntime.isAvailable(), "需要本机 Chromium（CDP）");
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("geo-deck.html"), GEOMETRY_DECK_HTML);

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
                import { extractDeckGeometry } from "weizhi:html2pptx";
                export default await extractDeckGeometry({ htmlPath: "geo-deck.html" });
                """, null, 300_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out.contains("\"ok\":true"), out);
        }
        Path geo = workspace.resolve("geo-deck-geometry.json");
        assertTrue(Files.isRegularFile(geo), "默认输出应为 geo-deck-geometry.json");
        var root = JsonParser.parseString(Files.readString(geo)).getAsJsonObject();
        var slides = root.getAsJsonArray("slides");
        assertEquals(2, slides.size());
        var cover = slides.get(0).getAsJsonObject();
        assertEquals(1280, cover.get("slideW").getAsInt());
        assertEquals(720, cover.get("slideH").getAsInt());
        assertTrue(cover.get("bgImage").getAsString().contains("linear-gradient"), "封面应有渐变背景");
        int coverEls = cover.getAsJsonArray("elements").size();
        assertTrue(coverEls >= 5, "封面元素数应 >= 5，实际 " + coverEls);
        var h1 = firstElement(cover, "h1", null);
        assertTrue(h1 != null, "封面应有 h1");
        var rect = h1.getAsJsonObject("rect");
        assertTrue(rect.get("x").getAsDouble() < 400 && rect.get("y").getAsDouble() < 400,
            "封面 h1 应在左上区域: " + rect);
        assertTrue(rect.get("w").getAsDouble() > 100, "h1 宽度应显著: " + rect);
        assertEquals("季度复盘", h1.getAsJsonArray("textRuns").get(0).getAsJsonObject().get("text").getAsString());
        assertTrue(h1.getAsJsonObject("font").get("size").getAsDouble() >= 80, "h1 字号应接近 88px");
        var cards = slides.get(1).getAsJsonObject();
        var card = firstElement(cards, "div", "card");
        assertTrue(card != null, "卡片页应有 .card 元素");
        assertEquals(18.0, card.get("radius").getAsDouble(), 0.01, "卡片圆角应为 18px");
        assertEquals("#ffffff", card.get("bg").getAsString());
        var topBorder = card.getAsJsonObject("border").getAsJsonObject("top");
        assertEquals(6.0, topBorder.get("w").getAsDouble(), 0.01, "卡片上边框应为 6px");
        assertTrue(topBorder.get("color").getAsString().matches("#[0-9a-f]{6}"), "边框颜色应为 hex");
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

    /** native 模式 mock 回执：页 1 = h1 文本块 + 带 shadow 卡片（clip 覆盖），页 2 空。 */
    private static final String MOCK_NATIVE = """
        {"version":1,"generator":"html2pptx@0.2.0 native capture","slides":[
        {"slideW":1280,"slideH":720,"bg":"#1a1423","elements":[
        {"tag":"h1","rect":{"x":110,"y":300,"w":600,"h":90},
         "font":{"size":88,"weight":"800","color":"#f6f1ea","align":"left","lineHeight":101.2},
         "textRuns":[{"text":"第一页","size":88,"weight":"800","color":"#f6f1ea"}]},
        {"tag":"div","cls":"card","rect":{"x":200,"y":450,"w":300,"h":120},
         "font":{"size":21,"weight":"400","color":"#5b6b7c","align":"left","lineHeight":35.7},
         "bg":"#ffffff","shadow":"rgba(18, 32, 51, 0.08) 0px 12px 32px 0px",
         "textRuns":[{"text":"卡片","size":21,"weight":"400","color":"#5b6b7c"}]}
        ],
        "clips":[{"x":156,"y":406,"w":388,"h":208,"b64":"%s"}]},
        {"slideW":1280,"slideH":720,"elements":[]}
        ]}
        """.formatted(PNG_B64);

    /** renderHtmlDeck mode:"native"（20-A T4）：一次提取 + clip 编排与原生组包。 */
    @Test
    void rendersNativeDeckThroughMockWebView(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("deck.html"), DECK_HTML);

        AtomicReference<Map<String, Object>> seen = new AtomicReference<>();
        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> {
            seen.set(arguments);
            return mockWebViewReceipt(workspace, arguments, MOCK_NATIVE);
        });
        try (engine) {
            String out = engine.evalForAgent("""
                import { renderHtmlDeck } from "weizhi:html2pptx";
                export default await renderHtmlDeck({ htmlPath: "deck.html", output: "out/deck.pptx", mode: "native" });
                """, null, 30_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out.contains("\"ok\":true"), out);
            assertTrue(out.contains("\"mode\":\"native\""), out);
            assertTrue(out.contains("\"fallback\":false"), out);
            assertTrue(out.contains("\"needsScreenshot\":[]"), "clip 应消解全部 needs: " + out);
        }
        // native 代码 = 几何提取 + 截图库 + 整页截图后按区域裁剪
        String code = String.valueOf(seen.get().get("code"));
        assertTrue(code.contains("getComputedStyle"), "native 应做几何提取");
        assertTrue(code.contains("html-to-image"), "区域截图需要 html-to-image");
        assertTrue(code.contains("drawImage"), "clip 应从整页截图裁剪");

        try (ZipFile zip = new ZipFile(workspace.resolve("out/deck.pptx").toFile())) {
            String s1 = zipText(zip, "ppt/slides/slide1.xml");
            assertTrue(s1.contains("<a:t>第一页</a:t>"), "h1 应是原生文本框");
            assertTrue(s1.contains("<p:pic>"), "clip 应作为 p:pic 补真");
            assertTrue(!s1.contains("<a:t>卡片</a:t>"), "clip 覆盖的卡片文本不应重复出文本框");
            assertTrue(!s1.contains("outerShdw"), "clip 覆盖的卡片不应再出阴影近似");
            assertTrue(zip.getEntry("ppt/media/image1.png") != null, "clip PNG 应落 media");
        }
        // native 临时件不残留（tmp/html2pptx-native-<stamp> 组包目录与截图模式一致保留）
        try (var list = Files.list(workspace.resolve("tmp"))) {
            assertEquals(0, list.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("html2pptx-native-input-")
                    || (n.startsWith("html2pptx-native-") && n.endsWith(".json"));
            }).count());
        }
    }

    /** native 失败自动回退截图：同一 output 产出整页截图版，返回值带原因。 */
    @Test
    void fallsBackToScreenshotWhenNativeFails(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("deck.html"), DECK_HTML);

        AtomicInteger calls = new AtomicInteger();
        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> {
            if (calls.incrementAndGet() == 1) {
                return mockWebViewReceipt(workspace, arguments, "not-json");
            }
            String shots = "{\"slides\":[" + shotJson(1280, 720) + "," + shotJson(1280, 720) + "]}";
            return mockWebViewReceipt(workspace, arguments, shots);
        });
        try (engine) {
            String out = engine.evalForAgent("""
                import { renderHtmlDeck } from "weizhi:html2pptx";
                export default await renderHtmlDeck({ htmlPath: "deck.html", output: "out/fb.pptx", mode: "native" });
                """, null, 60_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out.contains("\"ok\":true"), out);
            assertTrue(out.contains("\"fallback\":true"), out);
            assertTrue(out.contains("native 回执不是 JSON"), out);
        }
        assertEquals(2, calls.get(), "应先 native 再回退截图共两次 webview_exec");
        try (ZipFile zip = new ZipFile(workspace.resolve("out/fb.pptx").toFile())) {
            String s1 = zipText(zip, "ppt/slides/slide1.xml");
            assertTrue(s1.contains("<p:pic>") && !s1.contains("<a:t>"), "回退产物应是整页截图版");
        }
    }

    /** mode 只认 screenshot（默认）/native，非法值在任何 webview 调用前报错。 */
    @Test
    void rejectsUnknownRenderMode(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("deck.html"), DECK_HTML);

        AtomicInteger calls = new AtomicInteger();
        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> {
            calls.incrementAndGet();
            return "{\"ok\":false,\"error\":\"should not run\"}";
        });
        try (engine) {
            RuntimeException ex = assertThrows(RuntimeException.class, () -> engine.evalForAgent("""
                import { renderHtmlDeck } from "weizhi:html2pptx";
                export default await renderHtmlDeck({ htmlPath: "deck.html", output: "out/x.pptx", mode: "bogus" });
                """, null, 30_000, new CancellationToken(), "jobs/run.js"));
            assertTrue(ex.getMessage().contains("mode"), ex.getMessage());
        }
        assertEquals(0, calls.get());
    }

    /** 端到端 native（20-A T4）：真 Chromium 提取+区域截图+原生组包，卡片阴影由 clip 补真。 */
    @Test
    void rendersNativeDeckThroughRealChromium(@TempDir Path agentRoot) throws Exception {
        assumeTrue(CdpWebViewRuntime.isAvailable(), "需要本机 Chromium（CDP）");
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("geo-deck.html"), GEOMETRY_DECK_HTML);

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
                  htmlPath: "geo-deck.html",
                  output: "out/native.pptx",
                  mode: "native"
                });
                """, null, 300_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out.contains("\"ok\":true"), out);
            assertTrue(out.contains("\"fallback\":false"), out);
            assertTrue(out.contains("\"needsScreenshot\":[]"), "阴影应由 clip 全部消解: " + out);
        }
        try (ZipFile zip = new ZipFile(workspace.resolve("out/native.pptx").toFile())) {
            String s1 = zipText(zip, "ppt/slides/slide1.xml");
            assertTrue(s1.contains("<a:t>季度复盘</a:t>"), "封面 h1 应是原生文本框");
            assertTrue(!s1.contains("<p:pic>"), "封面无阴影/远程图，不应有 clip");
            String s2 = zipText(zip, "ppt/slides/slide2.xml");
            assertTrue(s2.contains("<a:t>三件事</a:t>"), "h2 应是原生文本框");
            assertTrue(s2.contains("<p:pic>"), "卡片阴影应由 clip p:pic 补真");
        }
    }

    /** 样例输出（20-A T5 三方）：HTML2PPTX_SAMPLE_DIR 设置时渲染 html2pptx 截图版、
     * html2pptx native 版与 pptx.js 版三份对比件。 */
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
            String out3 = engine.evalForAgent("""
                import { renderHtmlDeck } from "weizhi:html2pptx";
                export default await renderHtmlDeck({
                  htmlPath: "sample-deck.html",
                  output: "html2pptx-native-对比样例.pptx",
                  mode: "native"
                });
                """, null, 300_000, new CancellationToken(), "jobs/run3.js");
            assertTrue(out3.contains("\"ok\":true"), out3);
            assertTrue(out3.contains("\"fallback\":false"), out3);
            assertTrue(out3.contains("\"needsScreenshot\":[]"),
                "样例 deck 的阴影/图表都应被 clip 补真: " + out3);
        }
        assertTrue(Files.size(out.resolve("html2pptx-对比样例.pptx")) > 10_000);
        assertTrue(Files.size(out.resolve("pptx-对比样例.pptx")) > 100);
        assertTrue(Files.size(out.resolve("html2pptx-native-对比样例.pptx")) > 10_000);
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

    /** 复杂样例（20-A T5b，用户要求）：覆盖表格/时间线/圆点/远图/KPI 网格/
     * 圆形头像/双图表/引言等 native 尚未验证的特性。8 页超 QuickJS 内存上限
     * （T5b 实测），须显式 HTML2PPTX_COMPLEX_SAMPLE=1 + HTML2PPTX_SAMPLE_DIR 触发，
     * 全量回归默认跳过。 */
    @Test
    void rendersComplexDeckSampleForComparison(@TempDir Path agentRoot) throws Exception {
        assumeTrue(CdpWebViewRuntime.isAvailable(), "需要本机 Chromium（CDP）");
        String dir = System.getenv("HTML2PPTX_SAMPLE_DIR");
        assumeTrue(dir != null && !dir.isBlank(), "HTML2PPTX_SAMPLE_DIR not set");
        assumeTrue("1".equals(System.getenv("HTML2PPTX_COMPLEX_SAMPLE")), "复杂样例须显式开启");
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path out = Path.of(dir);
        Files.createDirectories(out);
        Files.writeString(out.resolve("complex-deck.html"), COMPLEX_DECK_HTML);

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
            String shot = engine.evalForAgent("""
                import { renderHtmlDeck } from "weizhi:html2pptx";
                export default await renderHtmlDeck({
                  htmlPath: "complex-deck.html",
                  output: "complex-截图版.pptx"
                });
                """, null, 600_000, new CancellationToken(), "jobs/run.js");
            assertTrue(shot.contains("\"ok\":true"), shot);
            String nat = engine.evalForAgent("""
                import { renderHtmlDeck } from "weizhi:html2pptx";
                export default await renderHtmlDeck({
                  htmlPath: "complex-deck.html",
                  output: "complex-native版.pptx",
                  mode: "native"
                });
                """, null, 600_000, new CancellationToken(), "jobs/run2.js");
            assertTrue(nat.contains("\"ok\":true"), nat);
        }
        assertTrue(Files.size(out.resolve("complex-截图版.pptx")) > 10_000);
        assertTrue(Files.size(out.resolve("complex-native版.pptx")) > 10_000);
    }

    private static final String COMPLEX_DECK_HTML = """
        <!DOCTYPE html><html><head><meta charset="utf-8"><title>复杂样例</title><style>
        *{box-sizing:border-box;margin:0}
        body{font-family:Helvetica,Arial,'PingFang SC','Microsoft YaHei',sans-serif;color:#1f2937}
        .slide{width:1280px;height:720px;overflow:hidden;position:relative;background:#fff}
        /* P1 封面 */
        .cover{background:linear-gradient(160deg,#0f172a 0%,#1e293b 60%,#312846 100%);color:#f8fafc;
          display:flex;flex-direction:column;justify-content:center;padding:0 110px}
        .cover .deco1{position:absolute;left:-140px;bottom:-180px;width:480px;height:480px;border-radius:50%;
          background:radial-gradient(circle,rgba(125,211,192,.22),transparent 60%)}
        .cover .deco2{position:absolute;right:-100px;top:-120px;width:360px;height:360px;border-radius:50%;
          background:radial-gradient(circle,rgba(232,184,109,.18),transparent 65%)}
        .cover .kicker{color:#e8b86d;font-size:20px;letter-spacing:10px;margin-bottom:24px}
        .cover h1{font-size:76px;font-weight:800;line-height:1.2}
        .cover .sub{font-size:26px;color:#cbd5e1;margin-top:26px}
        .cover .meta{position:absolute;left:110px;bottom:56px;right:110px;display:flex;justify-content:space-between;
          color:#94a3b8;font-size:16px;border-top:1px solid rgba(148,163,184,.35);padding-top:18px}
        /* P2 KPI */
        .kpis{padding:72px 96px}
        .kpis h2{font-size:42px;color:#0f172a;margin-bottom:14px}
        .kpis .desc{color:#64748b;font-size:19px;margin-bottom:44px}
        .kgrid{display:grid;grid-template-columns:repeat(4,1fr);gap:28px}
        .kpi{background:#fff;border:1px solid #e2e8f0;border-radius:16px;padding:28px 24px;
          box-shadow:0 10px 26px rgba(15,23,42,.07);border-top:5px solid #7dd3c0}
        .kpi:nth-child(2){border-top-color:#e8b86d}
        .kpi:nth-child(3){border-top-color:#8b7ec8}
        .kpi:nth-child(4){border-top-color:#f27e7e}
        .kpi .num{font-size:52px;font-weight:800;color:#0f172a}
        .kpi .delta{font-size:17px;margin:8px 0 10px}
        .up{color:#2c7a7b}.down{color:#c0392b}
        .kpi .label{color:#64748b;font-size:17px}
        .kpi .bar{height:6px;border-radius:3px;background:#eef2f7;margin-top:16px;overflow:hidden}
        .kpi .bar i{display:block;height:100%;border-radius:3px;background:linear-gradient(90deg,#7dd3c0,#2c7a7b)}
        /* P3 图文 */
        .split{display:flex;padding:0;height:720px}
        .split .text{flex:0 0 46%;padding:88px 64px 88px 96px;background:#faf9f6}
        .split .text h2{font-size:40px;color:#122033;margin-bottom:26px}
        .split .text p{font-size:20px;line-height:1.8;color:#475569;margin-bottom:18px}
        .split .text ul{list-style:none;margin-top:8px}
        .split .text li{font-size:19px;color:#334155;padding:10px 0 10px 34px;position:relative;
          border-bottom:1px dashed #d8dee8}
        .split .text li::before{content:'';position:absolute;left:4px;top:19px;width:12px;height:12px;
          border-radius:50%;background:#7dd3c0}
        .split .pic{flex:1;position:relative;background:#122033}
        .split .pic img{width:100%;height:100%;object-fit:cover;display:block}
        .split .pic .badge{position:absolute;left:24px;bottom:24px;background:rgba(15,23,42,.82);color:#f8fafc;
          border-radius:12px;padding:14px 20px;font-size:16px}
        /* P4 时间线 */
        .tl{padding:76px 96px;background:#fff}
        .tl h2{font-size:42px;color:#0f172a;margin-bottom:60px}
        .tl .track{position:relative;height:460px}
        .tl .line{position:absolute;left:120px;top:0;bottom:0;width:4px;border-radius:2px;
          background:linear-gradient(180deg,#7dd3c0,#8b7ec8)}
        .tl .item{position:absolute;left:0;width:1040px;display:flex;align-items:flex-start;margin-bottom:0}
        .tl .dot{flex:0 0 240px;display:flex;justify-content:flex-end;padding-right:44px;position:relative}
        .tl .dot i{width:20px;height:20px;border-radius:50%;background:#fff;border:5px solid #7dd3c0;margin-top:2px}
        .tl .item:nth-child(3) .dot i{border-color:#e8b86d}
        .tl .item:nth-child(4) .dot i{border-color:#8b7ec8}
        .tl .item:nth-child(5) .dot i{border-color:#f27e7e}
        .tl .body h3{font-size:23px;color:#0f172a;margin-bottom:6px}
        .tl .body .when{font-size:15px;color:#94a3b8;margin-bottom:6px;letter-spacing:1px}
        .tl .body p{font-size:18px;color:#475569;line-height:1.6;max-width:660px}
        /* P5 表格 */
        .tbl{padding:72px 96px;background:#f8fafc}
        .tbl h2{font-size:40px;color:#0f172a;margin-bottom:40px}
        table{width:100%;border-collapse:collapse;background:#fff;border-radius:14px;overflow:hidden;
          box-shadow:0 8px 24px rgba(15,23,42,.08);font-size:19px}
        thead th{background:#122033;color:#f8fafc;text-align:left;padding:18px 22px;font-weight:600}
        tbody td{padding:15px 22px;border-bottom:1px solid #eef2f7;color:#334155}
        tbody tr:nth-child(even) td{background:#f8fafc}
        td.num{text-align:right;font-variant-numeric:tabular-nums;font-weight:600;color:#0f172a}
        tfoot td{padding:16px 22px;background:#eef7f5;color:#2c7a7b;font-weight:700}
        /* P6 双图表 */
        .charts{padding:64px 88px;display:flex;flex-direction:column}
        .charts h2{font-size:38px;color:#0f172a;margin-bottom:28px}
        .charts .row{display:flex;gap:48px;flex:1}
        #bars{flex:1;height:430px}
        #donut{flex:0 0 430px;height:430px}
        /* P7 引言 */
        .quote{background:linear-gradient(135deg,#1e293b,#0f172a);color:#e2e8f0;display:flex;
          flex-direction:column;justify-content:center;align-items:center;text-align:center}
        .quote .mark{font-size:120px;color:#e8b86d;line-height:.6;font-family:Georgia,serif;margin-bottom:26px}
        .quote blockquote{font-size:34px;line-height:1.75;max-width:900px;font-weight:300}
        .quote .who{display:flex;align-items:center;gap:18px;margin-top:48px}
        .quote .avatar{width:64px;height:64px;border-radius:50%;border:3px solid #7dd3c0;overflow:hidden}
        .quote .avatar img{width:100%;height:100%;object-fit:cover}
        .quote .who .nm{text-align:left}
        .quote .who .nm b{display:block;font-size:20px;color:#f8fafc}
        .quote .who .nm span{font-size:15px;color:#94a3b8}
        /* P8 结尾 */
        .end{background:radial-gradient(circle at 30% 20%,#312846,#122033 70%);color:#f6f1ea;display:flex;
          flex-direction:column;justify-content:center;align-items:center;text-align:center}
        .end .big{font-size:120px;font-weight:800;background:linear-gradient(90deg,#e8b86d,#7dd3c0);
          -webkit-background-clip:text;background-clip:text;color:transparent}
        .end p{font-size:26px;color:#c4b8a8;margin-top:28px}
        .end .tags{margin-top:44px;display:flex;gap:16px}
        .end .tags span{border:1px solid rgba(198,184,168,.5);border-radius:999px;padding:10px 26px;font-size:16px;color:#d8cfc2}
        </style></head><body>
        <section class="slide cover">
          <div class="deco1"></div><div class="deco2"></div>
          <div class="kicker">AGENT1 · 2026 ANNUAL</div>
          <h1>复杂样例<br>全特性验收</h1>
          <div class="sub">表格 · 时间线 · KPI · 图文 · 双图表 · 引言</div>
          <div class="meta"><span>产品部 · 季度汇报</span><span>2026-10-17</span><span>P1 / 8</span></div>
        </section>
        <section class="slide kpis">
          <h2>关键指标</h2>
          <div class="desc">四条业务线的季度表现与目标进度。</div>
          <div class="kgrid">
            <div class="kpi"><div class="num">1,284</div><div class="delta up">▲ 12.4% 环比</div>
              <div class="label">活跃用户</div><div class="bar"><i style="width:82%"></i></div></div>
            <div class="kpi"><div class="num">98.2%</div><div class="delta up">▲ 0.6pt</div>
              <div class="label">七天留存</div><div class="bar"><i style="width:96%"></i></div></div>
            <div class="kpi"><div class="num">¥426w</div><div class="delta down">▼ 3.1% 环比</div>
              <div class="label">订阅收入</div><div class="bar"><i style="width:64%"></i></div></div>
            <div class="kpi"><div class="num">0.5d</div><div class="delta up">▲ 缩短一半</div>
              <div class="label">故障恢复</div><div class="bar"><i style="width:75%"></i></div></div>
          </div>
        </section>
        <section class="slide split">
          <div class="text">
            <h2>图文混排</h2>
            <p>左侧文字列表面板，右侧整幅照片（远程图 + object-fit 裁剪）。</p>
            <ul>
              <li>圆点列表，虚线分隔</li>
              <li>照片右下角浮层徽章</li>
              <li>两栏背景色不同</li>
            </ul>
          </div>
          <div class="pic">
            <img src="https://picsum.photos/seed/agent1/820/720" alt="photo">
            <div class="badge">图 1 · 现场照片（远程加载）</div>
          </div>
        </section>
        <section class="slide tl">
          <h2>季度时间线</h2>
          <div class="track">
            <div class="line"></div>
            <div class="item" style="top:0"><div class="dot"><i></i></div>
              <div class="body"><div class="when">2026-01</div><h3>立项启动</h3>
              <p>团队到位，完成技术选型与首版架构评审。</p></div></div>
            <div class="item" style="top:120px"><div class="dot"><i></i></div>
              <div class="body"><div class="when">2026-04</div><h3>公测上线</h3>
              <p>移动端与桌面端同步开放公测，首批种子用户 2 万。</p></div></div>
            <div class="item" style="top:240px"><div class="dot"><i></i></div>
              <div class="body"><div class="when">2026-07</div><h3>商业化</h3>
              <p>订阅体系上线，首季度收入突破四百万。</p></div></div>
            <div class="item" style="top:360px"><div class="dot"><i></i></div>
              <div class="body"><div class="when">2026-10</div><h3>规模化</h3>
              <p>多租户与私有化部署方案落地。</p></div></div>
          </div>
        </section>
        <section class="slide tbl">
          <h2>经营明细</h2>
          <table>
            <thead><tr><th>业务线</th><th>收入</th><th>成本</th><th>毛利率</th><th>趋势</th></tr></thead>
            <tbody>
              <tr><td>订阅</td><td class="num">¥286w</td><td class="num">¥92w</td><td class="num">67.8%</td><td>▲ 上升</td></tr>
              <tr><td>项目交付</td><td class="num">¥102w</td><td class="num">¥71w</td><td class="num">30.4%</td><td>▼ 下降</td></tr>
              <tr><td>增值服务</td><td class="num">¥38w</td><td class="num">¥12w</td><td class="num">68.4%</td><td>▲ 上升</td></tr>
            </tbody>
            <tfoot><tr><td>合计</td><td class="num">¥426w</td><td class="num">¥175w</td><td class="num">58.9%</td><td>—</td></tr></tfoot>
          </table>
        </section>
        <section class="slide charts">
          <h2>双图表</h2>
          <div class="row"><div id="bars"></div><div id="donut"></div></div>
        </section>
        <section class="slide quote">
          <div class="mark">“</div>
          <blockquote>把排版交给 CSS，把编辑能力还给 PowerPoint——<br>这是自由与可控之间最好的折中。</blockquote>
          <div class="who">
            <div class="avatar"><img src="https://picsum.photos/seed/agent1face/128/128" alt="avatar"></div>
            <div class="nm"><b>产品部</b><span>2026 Q3 复盘</span></div>
          </div>
        </section>
        <section class="slide end">
          <div class="big">THANKS</div>
          <p>复杂样例 · 全特性验收到此结束</p>
          <div class="tags"><span>html2pptx</span><span>native 模式</span><span>2026-10</span></div>
        </section>
        <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.1/dist/echarts.min.js"></script>
        <script>
        var bars = echarts.init(document.getElementById('bars'), null, { renderer: 'canvas' });
        bars.setOption({
          animation: false,
          grid: { left: 56, right: 20, top: 40, bottom: 44 },
          xAxis: { type: 'category', data: ['Q1','Q2','Q3','Q4·E'],
            axisLabel: { fontSize: 17, color: '#64748b' } },
          yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } } },
          series: [
            { name: '订阅', type: 'bar', stack: 'q', data: [120, 186, 286, 360],
              itemStyle: { color: '#2c7a7b' }, barWidth: 64 },
            { name: '项目', type: 'bar', stack: 'q', data: [88, 96, 102, 70],
              itemStyle: { color: '#e8b86d', borderRadius: [8, 8, 0, 0] } },
            { name: '增值', type: 'bar', stack: 'q', data: [12, 24, 38, 60],
              itemStyle: { color: '#8b7ec8', borderRadius: [8, 8, 0, 0] } }
          ]
        });
        var donut = echarts.init(document.getElementById('donut'), null, { renderer: 'canvas' });
        donut.setOption({
          animation: false,
          series: [{ type: 'pie', radius: ['46%', '72%'], center: ['50%', '50%'],
            label: { fontSize: 16, color: '#475569' },
            data: [
              { value: 286, name: '订阅 67%' },
              { value: 102, name: '项目 24%' },
              { value: 38, name: '增值 9%' }
            ],
            itemStyle: { borderColor: '#fff', borderWidth: 3 } }]
        });
        </script>
        </body></html>
        """;

    /** exportDeckPages（T8 HTML 源工作流的预览载体）：每页 PNG 落盘、不组 pptx。 */
    @Test
    void exportsDeckPagesThroughMockWebView(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("deck.html"), DECK_HTML);

        AtomicReference<Map<String, Object>> seen = new AtomicReference<>();
        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> {
            seen.set(arguments);
            String shots = "{\"slides\":[" + shotJson(1280, 720) + "," + shotJson(1280, 720) + "]}";
            return mockWebViewReceipt(workspace, arguments, shots);
        });
        try (engine) {
            String out = engine.evalForAgent("""
                import { exportDeckPages } from "weizhi:html2pptx";
                export default await exportDeckPages({ htmlPath: "deck.html" });
                """, null, 30_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out.contains("\"ok\":true"), out);
            assertTrue(out.contains("\"pages\":2"), out);
            assertTrue(out.contains("deck-pages/page-1.png"), out);
        }
        Path p1 = workspace.resolve("deck-pages/page-1.png");
        Path p2 = workspace.resolve("deck-pages/page-2.png");
        assertTrue(Files.isRegularFile(p1) && Files.isRegularFile(p2), "每页 PNG 应落盘");
        byte[] head = Files.readAllBytes(p1);
        assertEquals(0x89, head[0] & 0xff, "PNG 魔数");
        // 回执临时件清理
        try (var list = Files.list(workspace.resolve("tmp"))) {
            assertEquals(0, list.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("html2pptx-pages-input-") || n.startsWith("html2pptx-pages-");
            }).count());
        }
    }

    private static String shotJson(int w, int h) {
        return "{\"w\":" + w + ",\"h\":" + h + ",\"b64\":\"" + PNG_B64 + "\"}";
    }

    /** 找页内第一个 tag 匹配（clsPrefix 非空时还要求 class 前缀匹配）的元素。 */
    private static JsonObject firstElement(JsonObject slide, String tag, String clsPrefix) {
        for (var el : slide.getAsJsonArray("elements")) {
            JsonObject o = el.getAsJsonObject();
            if (!tag.equals(o.get("tag").getAsString())) {
                continue;
            }
            if (clsPrefix == null || (o.has("cls") && o.get("cls").getAsString().startsWith(clsPrefix))) {
                return o;
            }
        }
        return null;
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
