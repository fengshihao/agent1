package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.MutableScriptToolBridge;
import com.agent1.javaagent.script.ScriptEngine;
import com.agent1.javaagent.script.ScriptToolBridge;
import com.agent1.javaagent.weizhi.desktop.WebViewExecSupport;
import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code extractDeckGeometry} 的 CDP 端到端与样式快照（20-A T2）。
 *
 * 真 Chromium 渲染「渐变封面 / 卡片 / 图表（canvas + svg，纯内联无 CDN）」三页
 * deck，断言提取 JSON 的关键矩形与颜色；输出 JSON 以 fixture 形式提交在
 * {@code src/test/resources/html2pptx/}，之后每次真机跑都与之结构化对比防回归。
 * fixture 基线断言不依赖 Chrome/JNI，CI 也能守住提取器的输出语义。
 *
 * fixture 更新：Chrome 版本变化导致渐变/阴影序列化或字体度量漂移时，用
 * {@code HTML2PPTX_UPDATE_FIXTURES=1} 重跑本类即可把新 JSON 写回 resources。
 */
class Html2PptxExtractTest {

    private static final String FIXTURE_RESOURCE = "/html2pptx/extract-deck-geometry-fixture.json";
    private static final String UPDATE_FIXTURES_ENV = "HTML2PPTX_UPDATE_FIXTURES";
    private static final String GENERATOR = "html2pptx@0.2.0 extractDeckGeometry";
    private static final double RECT_TOLERANCE_PX = 6.0;

    /** 三页 deck：渐变封面（出界 deco）+ 卡片页（圆角/边框/阴影/em 斜体 run）+ 图表页（canvas+svg）。 */
    private static final String EXTRACT_DECK_HTML = """
        <!DOCTYPE html><html><head><meta charset="utf-8"><title>几何提取端到端</title><style>
        *{box-sizing:border-box;margin:0}
        body{font-family:Helvetica,Arial,'PingFang SC',sans-serif}
        .slide{width:1280px;height:720px;overflow:hidden;position:relative}
        .cover{background:linear-gradient(135deg,#1a1423 0%,#312846 100%);color:#f6f1ea;
          display:flex;flex-direction:column;justify-content:center;padding:0 110px}
        .cover .deco{position:absolute;right:-160px;top:-160px;width:520px;height:520px;border-radius:50%;
          background:radial-gradient(circle,rgba(125,211,192,.25),transparent 65%)}
        .cover .kicker{color:#e8b86d;font-size:22px;letter-spacing:8px;margin-bottom:28px}
        .cover h1{font-size:88px;font-weight:800;line-height:1.15}
        .cover .rule{width:96px;height:6px;background:linear-gradient(90deg,#e8b86d,#7dd3c0);border-radius:3px;margin:36px 0}
        .cover .sub{font-size:30px;color:#c4b8a8}
        .cards{background:#faf9f6;padding:0 96px;display:flex;flex-direction:column;justify-content:center;min-height:720px}
        .cards h2{font-size:44px;color:#122033;margin-bottom:48px}
        .grid{display:grid;grid-template-columns:repeat(3,1fr);gap:36px}
        .card{background:#fff;border-radius:18px;padding:44px 32px;border-top:6px solid #7dd3c0}
        .card:nth-child(2){border-top-color:#e8b86d}
        .card:nth-child(3){border-top-color:#8b7ec8;box-shadow:0 12px 32px rgba(18,32,51,.08)}
        .card h3{font-size:28px;color:#122033;margin-bottom:14px}
        .card p{font-size:21px;color:#5b6b7c;line-height:1.7}
        .chart{background:#ffffff;padding:64px 88px;display:flex;gap:24px;align-items:center}
        .chart .takeaway{flex:0 0 260px}
        .chart h2{font-size:40px;color:#122033;margin-bottom:8px}
        .chart p{font-size:20px;color:#5b6b7c;line-height:1.7;margin:16px 0}
        .chart .big{color:#2c7a7b;font-size:30px;font-weight:800}
        #bars{width:560px;height:400px}
        </style></head><body>
        <section class="slide cover">
          <div class="deco"></div>
          <div class="kicker">EXTRACT · E2E</div>
          <h1>几何快照</h1>
          <div class="rule"></div>
          <div class="sub">2026 Q4 · 端到端</div>
        </section>
        <section class="slide cards">
          <div>
            <h2>卡片<em>样式</em></h2>
            <div class="grid">
              <div class="card"><h3>产品</h3><p>留存稳住。</p></div>
              <div class="card"><h3>销售</h3><p>新客增加。</p></div>
              <div class="card"><h3>交付</h3><p>恢复更快。</p></div>
            </div>
          </div>
        </section>
        <section class="slide chart">
          <canvas id="bars" width="560" height="400"></canvas>
          <svg width="220" height="220" viewBox="0 0 300 300">
            <circle cx="150" cy="150" r="110" fill="none" stroke="#edf2f7" stroke-width="36"/>
            <circle cx="150" cy="150" r="110" fill="none" stroke="#2c7a7b" stroke-width="36"
              stroke-dasharray="486 207" transform="rotate(-90 150 150)" stroke-linecap="round"/>
            <text x="150" y="162" text-anchor="middle" font-size="44" fill="#122033">61%</text>
          </svg>
          <div class="takeaway">
            <h2>收入结构</h2>
            <p>订阅连续三季上升。</p>
            <p class="big">+12% YoY</p>
            <p><span>相邻</span><span>同款</span>合并</p>
          </div>
        </section>
        <script>
        (function () {
          var c = document.getElementById('bars');
          if (!c) { return; }
          var g = c.getContext('2d');
          g.strokeStyle = '#edf2f7';
          g.lineWidth = 1;
          for (var y = 40; y <= 360; y += 64) {
            g.beginPath();
            g.moveTo(48, y);
            g.lineTo(540, y);
            g.stroke();
          }
          var bars = [
            { v: 38, color: '#2c7a7b' },
            { v: 44, color: '#2c7a7b' },
            { v: 52, color: '#2c7a7b' },
            { v: 61, color: '#e8b86d' }
          ];
          for (var i = 0; i < bars.length; i++) {
            var x = 88 + i * 116;
            var bh = bars[i].v * 5;
            g.fillStyle = bars[i].color;
            g.fillRect(x, 360 - bh, 56, bh);
          }
          g.fillStyle = '#5b6b7c';
          g.font = '16px sans-serif';
          g.fillText('Q3·26', 88, 388);
        })();
        </script>
        </body></html>
        """;

    /** fixture 基线：只读 resources 里的快照断言关键事实，无需 Chrome/JNI，CI 常跑。 */
    @Test
    void fixtureBaselineHolds() throws Exception {
        assertKeyFacts(readFixture(), "fixture");
    }

    /** CDP 端到端：真 Chromium 渲染提取，关键矩形/颜色断言后与 fixture 结构化对比。 */
    @Test
    void extractsGeometryThroughCdpAndMatchesFixture(@TempDir Path agentRoot) throws Exception {
        Path repo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (repo.getNameCount() == 0 || !Files.isDirectory(repo)) {
            repo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        assumeTrue(WeizhiJniBootstrap.tryLoad(repo), "libweizhijni not built; run weizhi ./scripts/build.sh");
        assumeTrue(CdpWebViewRuntime.isAvailable(), "需要本机 Chromium（CDP）");

        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiBundledPackages.syncFromClasspath(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("extract-deck.html"), EXTRACT_DECK_HTML);

        CdpWebViewRuntime runtime = CdpWebViewRuntime.getInstance(repo);
        WorkspaceSandbox sandbox = new WorkspaceSandbox(workspace);
        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> WebViewExecSupport.execute(
            runtime,
            sandbox,
            String.valueOf(arguments.get("code")),
            null,
            arguments.get("input_path") == null ? null : String.valueOf(arguments.get("input_path")),
            arguments.get("output_path") == null ? null : String.valueOf(arguments.get("output_path")),
            String.valueOf(arguments.get("timeout_ms"))
        ));
        JsonObject live;
        try (engine) {
            String out = engine.evalForAgent("""
                import { extractDeckGeometry } from "weizhi:html2pptx";
                export default await extractDeckGeometry({ htmlPath: "extract-deck.html" });
                """, null, 300_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out.contains("\"ok\":true"), out);
            assertTrue(out.contains("\"slides\":3"), out);
        }
        Path geo = workspace.resolve("extract-deck-geometry.json");
        assertTrue(Files.isRegularFile(geo), "默认输出应为 extract-deck-geometry.json");
        live = JsonParser.parseString(Files.readString(geo)).getAsJsonObject();

        assertKeyFacts(live, "live");

        if ("1".equals(System.getenv(UPDATE_FIXTURES_ENV))) {
            Path target = fixtureRepoPath();
            Files.createDirectories(target.getParent());
            Files.copy(geo, target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        assertMatchesFixture(live, readFixture());
    }

    /** 关键事实断言：对 live 提取结果与 fixture 快照同样适用。 */
    private static void assertKeyFacts(JsonObject root, String src) throws Exception {
        assertEquals(1, root.get("version").getAsInt(), src);
        assertEquals(GENERATOR, root.get("generator").getAsString(), src);
        JsonArray slides = root.getAsJsonArray("slides");
        assertEquals(3, slides.size(), src);

        // 封面：渐变背景 + 出界 deco 保留 + h1 左中区域 + 88px/800/#f6f1ea + 渐变 rule
        JsonObject cover = slides.get(0).getAsJsonObject();
        assertEquals(1280, cover.get("slideW").getAsInt(), src);
        assertEquals(720, cover.get("slideH").getAsInt(), src);
        assertTrue(cover.get("bgImage").getAsString().contains("linear-gradient"), src + " 封面应有渐变背景");
        assertTrue(cover.getAsJsonArray("elements").size() >= 5, src + " 封面元素数应 >= 5");
        JsonObject deco = element(cover, "div", "deco", src);
        assertRect(deco, 900, 960, -180, -140, 518, 522, 518, 522, src + " 出界 deco 应按 520px 且越出上边界保留");
        JsonObject kicker = element(cover, "div", "kicker", src);
        assertEquals("#e8b86d", kicker.getAsJsonObject("font").get("color").getAsString(), src);
        assertTrue(near(kicker.getAsJsonObject("font").get("size").getAsDouble(), 22, 1), src);
        JsonObject h1 = element(cover, "h1", null, src);
        // h1 是 flex 列里的块级元素，宽度撑满内容区（1280-2×110）
        assertRect(h1, 100, 140, 150, 400, 1000, 1100, 80, 140, src + " 封面 h1 应在左中区域");
        assertEquals("几何快照", h1.getAsJsonArray("textRuns").get(0).getAsJsonObject().get("text").getAsString(), src);
        assertTrue(near(h1.getAsJsonObject("font").get("size").getAsDouble(), 88, 1), src + " h1 字号应 88px");
        assertEquals("800", h1.getAsJsonObject("font").get("weight").getAsString(), src);
        assertEquals("#f6f1ea", h1.getAsJsonObject("font").get("color").getAsString(), src);
        assertTrue(h1.getAsJsonObject("font").get("family").getAsString().contains("Helvetica"), src);
        JsonObject rule = element(cover, "div", "rule", src);
        assertTrue(near(rect(rule).get("w").getAsDouble(), 96, 1), src);
        assertTrue(near(rect(rule).get("h").getAsDouble(), 6, 0.5), src);
        assertTrue(near(rule.get("radius").getAsDouble(), 3, 0.3), src);
        assertTrue(rule.get("bgImage").getAsString().contains("linear-gradient"), src);
        JsonObject sub = element(cover, "div", "sub", src);
        assertEquals("#c4b8a8", sub.getAsJsonObject("font").get("color").getAsString(), src);

        // 卡片页：三卡等宽横排、radius 18、bg #ffffff、上边框 6px 三色、第三卡带阴影、h2 拆出斜体 run
        JsonObject cards = slides.get(1).getAsJsonObject();
        assertEquals("#faf9f6", cards.get("bg").getAsString(), src);
        List<JsonObject> cardEls = elementsWithCls(cards, "card");
        assertEquals(3, cardEls.size(), src);
        String[] topColors = {"#7dd3c0", "#e8b86d", "#8b7ec8"};
        for (int i = 0; i < 3; i++) {
            JsonObject card = cardEls.get(i);
            assertTrue(near(card.get("radius").getAsDouble(), 18, 0.1), src + " 卡片圆角应 18px");
            assertEquals("#ffffff", card.get("bg").getAsString(), src);
            JsonObject top = card.getAsJsonObject("border").getAsJsonObject("top");
            assertTrue(near(top.get("w").getAsDouble(), 6, 0.1), src);
            assertEquals(topColors[i], top.get("color").getAsString(), src);
        }
        assertTrue(cardEls.get(0).get("rect").getAsJsonObject().get("x").getAsDouble()
                < cardEls.get(1).get("rect").getAsJsonObject().get("x").getAsDouble()
            && cardEls.get(1).get("rect").getAsJsonObject().get("x").getAsDouble()
                < cardEls.get(2).get("rect").getAsJsonObject().get("x").getAsDouble(),
            src + " 三卡应横向排布");
        // 内容宽 1280-2×96=1088，三列两缝：card 宽 =（1088-72)/3 ≈ 338.67
        double cardW = cardEls.get(0).get("rect").getAsJsonObject().get("w").getAsDouble();
        assertTrue(near(cardW, 338.67, 1.5), src + " 卡片宽应为 338.67，实际 " + cardW);
        assertTrue(cardEls.get(2).has("shadow"), src + " 第三卡应带阴影");
        JsonObject h2 = element(cards, "h2", null, src);
        JsonArray h2Runs = h2.getAsJsonArray("textRuns");
        assertEquals(2, h2Runs.size(), src + " em 斜体应拆成独立 run");
        assertEquals("卡片", h2Runs.get(0).getAsJsonObject().get("text").getAsString(), src);
        assertEquals("样式", h2Runs.get(1).getAsJsonObject().get("text").getAsString(), src);
        assertTrue(h2Runs.get(1).getAsJsonObject().get("italic").getAsBoolean(), src);

        // 图表页：canvas 出 data URI PNG、svg 出 data URI、大数字 run 加粗变色、相邻同款 run 合并
        JsonObject chart = slides.get(2).getAsJsonObject();
        assertEquals("#ffffff", chart.get("bg").getAsString(), src);
        assertTrue(chart.getAsJsonArray("elements").size() >= 6, src + " 图表页元素数应 >= 6");
        JsonObject canvas = element(chart, "canvas", null, src);
        assertTrue(near(rect(canvas).get("w").getAsDouble(), 560, 1.5), src);
        assertTrue(near(rect(canvas).get("h").getAsDouble(), 400, 1.5), src);
        String canvasSrc = canvas.get("imgSrc").getAsString();
        assertTrue(canvasSrc.startsWith("data:image/png;base64,"), src + " canvas 应导出 PNG data URI");
        assertTrue(canvasSrc.length() > 4000, src + " canvas data URI 应有实际绘图内容，长度 " + canvasSrc.length());
        JsonObject svg = element(chart, "svg", null, src);
        assertTrue(near(rect(svg).get("w").getAsDouble(), 220, 1.5), src);
        String svgSrc = svg.get("imgSrc").getAsString();
        assertTrue(svgSrc.startsWith("data:image/svg+xml"), src + " svg 应导出 svg data URI");
        String svgText = URLDecoder.decode(svgSrc, StandardCharsets.UTF_8);
        assertTrue(svgText.contains("<svg") && svgText.contains("circle"), src);
        assertEquals("收入结构", element(chart, "h2", null, src).getAsJsonArray("textRuns")
            .get(0).getAsJsonObject().get("text").getAsString(), src);
        JsonObject big = element(chart, "p", "big", src);
        JsonArray bigRuns = big.getAsJsonArray("textRuns");
        assertEquals(1, bigRuns.size(), src);
        assertEquals("+12% YoY", bigRuns.get(0).getAsJsonObject().get("text").getAsString(), src);
        assertEquals("800", bigRuns.get(0).getAsJsonObject().get("weight").getAsString(), src);
        assertEquals("#2c7a7b", bigRuns.get(0).getAsJsonObject().get("color").getAsString(), src);
        JsonObject merged = elementWithRunText(chart, "p", "相邻 同款 合并", src);
        assertEquals(1, merged.getAsJsonArray("textRuns").size(), src + " 相邻同款 span 应合并为单 run");
    }

    /** live 与 fixture 的结构化对比：序列/数量/颜色/字号精确比对，矩形留字体度量容差。 */
    private static void assertMatchesFixture(JsonObject live, JsonObject fixture) {
        assertEquals(fixture.get("generator").getAsString(), live.get("generator").getAsString(),
            "generator 与 fixture 不一致（提取器版本变化，HTML2PPTX_UPDATE_FIXTURES=1 重生成）");
        JsonArray ls = live.getAsJsonArray("slides");
        JsonArray fs = fixture.getAsJsonArray("slides");
        assertEquals(fs.size(), ls.size(), "slide 数与 fixture 不一致");
        for (int i = 0; i < fs.size(); i++) {
            String where = "第 " + (i + 1) + " 页";
            JsonObject l = ls.get(i).getAsJsonObject();
            JsonObject f = fs.get(i).getAsJsonObject();
            assertEquals(f.get("slideW").getAsInt(), l.get("slideW").getAsInt(), where + " slideW");
            assertEquals(f.get("slideH").getAsInt(), l.get("slideH").getAsInt(), where + " slideH");
            assertSameOptional(f, l, "bg", where);
            assertSameOptional(f, l, "bgImage", where + " bgImage（Chrome 渐变序列化变化？HTML2PPTX_UPDATE_FIXTURES=1 重生成）");
            JsonArray le = l.getAsJsonArray("elements");
            JsonArray fe = f.getAsJsonArray("elements");
            assertEquals(fe.size(), le.size(), where + " 元素数与 fixture 不一致（保留/跳过规则回归？）");
            for (int j = 0; j < fe.size(); j++) {
                assertElementMatches(fe.get(j).getAsJsonObject(), le.get(j).getAsJsonObject(),
                    where + " 元素[" + j + "]");
            }
        }
    }

    private static void assertElementMatches(JsonObject f, JsonObject l, String where) {
        assertEquals(f.get("tag").getAsString(), l.get("tag").getAsString(), where + " tag");
        assertSameOptional(f, l, "cls", where);
        JsonObject fr = rect(f);
        JsonObject lr = rect(l);
        for (String k : new String[] {"x", "y", "w", "h"}) {
            assertTrue(near(fr.get(k).getAsDouble(), lr.get(k).getAsDouble(), RECT_TOLERANCE_PX),
                where + " rect." + k + " 与 fixture 偏差超 " + RECT_TOLERANCE_PX + "px: fixture="
                    + fr.get(k) + " live=" + lr.get(k) + "（字体回退差异或布局回归）");
        }
        assertSameOptional(f, l, "bg", where);
        assertSameOptional(f, l, "bgImage", where);
        assertSameOptional(f, l, "shadow", where);
        assertBorderMatches(f, l, where);
        assertRadiusMatches(f, l, where);
        assertFontMatches(f.getAsJsonObject("font"), l.getAsJsonObject("font"), where);
        JsonArray fr2 = f.has("textRuns") ? f.getAsJsonArray("textRuns") : null;
        JsonArray lr2 = l.has("textRuns") ? l.getAsJsonArray("textRuns") : null;
        if (fr2 == null || lr2 == null) {
            assertTrue(fr2 == null && lr2 == null, where + " textRuns 有无应一致");
        } else {
            assertEquals(fr2.size(), lr2.size(), where + " textRuns 数与 fixture 不一致（合并规则回归？）");
            for (int i = 0; i < fr2.size(); i++) {
                JsonObject frun = fr2.get(i).getAsJsonObject();
                JsonObject lrun = lr2.get(i).getAsJsonObject();
                assertEquals(frun.get("text").getAsString(), lrun.get("text").getAsString(), where + " run 文本");
                assertTrue(near(frun.get("size").getAsDouble(), lrun.get("size").getAsDouble(), 1.2), where + " run 字号");
                assertEquals(frun.get("weight").getAsString(), lrun.get("weight").getAsString(), where + " run 字重");
                assertEquals(frun.get("color").getAsString(), lrun.get("color").getAsString(), where + " run 颜色");
                assertEquals(frun.get("italic").getAsBoolean(), lrun.get("italic").getAsBoolean(), where + " run 斜体");
                assertEquals(frun.get("underline").getAsBoolean(), lrun.get("underline").getAsBoolean(), where + " run 下划线");
            }
        }
        // 图片引用只比对 data URI 前缀（canvas 光栅随 Chrome 版本有字节级抖动）
        String fimg = textOrNull(f, "imgSrc");
        String limg = textOrNull(l, "imgSrc");
        if (fimg == null || limg == null) {
            assertTrue(fimg == null && limg == null, where + " imgSrc 有无应一致");
        } else {
            assertEquals(dataUriPrefix(fimg), dataUriPrefix(limg), where + " imgSrc 类型");
        }
        assertEquals(f.has("imgOmitted"), l.has("imgOmitted"), where + " imgOmitted 应一致");
    }

    private static void assertBorderMatches(JsonObject f, JsonObject l, String where) {
        JsonObject fb = f.has("border") ? f.getAsJsonObject("border") : null;
        JsonObject lb = l.has("border") ? l.getAsJsonObject("border") : null;
        if (fb == null || lb == null) {
            assertTrue(fb == null && lb == null, where + " border 有无应一致");
            return;
        }
        assertEquals(fb.keySet(), lb.keySet(), where + " border 边集合");
        for (String side : fb.keySet()) {
            JsonObject fs2 = fb.getAsJsonObject(side);
            JsonObject ls2 = lb.getAsJsonObject(side);
            assertTrue(near(fs2.get("w").getAsDouble(), ls2.get("w").getAsDouble(), 0.6), where + " border." + side + ".w");
            assertEquals(fs2.get("color").getAsString(), ls2.get("color").getAsString(), where + " border." + side + ".color");
        }
    }

    private static void assertRadiusMatches(JsonObject f, JsonObject l, String where) {
        boolean fHas = f.has("radius");
        assertTrue(fHas == l.has("radius"), where + " radius 有无应一致");
        if (!fHas) {
            return;
        }
        if (f.get("radius").isJsonPrimitive()) {
            assertTrue(l.get("radius").isJsonPrimitive(), where + " radius 形状应一致");
            assertTrue(near(f.get("radius").getAsDouble(), l.get("radius").getAsDouble(), 0.6), where + " radius");
            return;
        }
        JsonObject fr3 = f.getAsJsonObject("radius");
        JsonObject lr3 = l.getAsJsonObject("radius");
        for (String corner : fr3.keySet()) {
            assertTrue(near(fr3.get(corner).getAsDouble(), lr3.get(corner).getAsDouble(), 0.6),
                where + " radius." + corner);
        }
    }

    private static void assertFontMatches(JsonObject f, JsonObject l, String where) {
        assertTrue(near(f.get("size").getAsDouble(), l.get("size").getAsDouble(), 1.2), where + " font.size");
        assertEquals(f.get("weight").getAsString(), l.get("weight").getAsString(), where + " font.weight");
        assertEquals(f.get("color").getAsString(), l.get("color").getAsString(), where + " font.color");
        assertEquals(f.get("align").getAsString(), l.get("align").getAsString(), where + " font.align");
        assertTrue(near(f.get("lineHeight").getAsDouble(), l.get("lineHeight").getAsDouble(), 1.5),
            where + " font.lineHeight");
    }

    private static void assertSameOptional(JsonObject f, JsonObject l, String name, String where) {
        String fv = textOrNull(f, name);
        String lv = textOrNull(l, name);
        assertEquals(fv, lv, where + " " + name);
    }

    private static void assertRect(JsonObject el, double xMin, double xMax, double yMin, double yMax,
                                   double wMin, double wMax, double hMin, double hMax, String what) {
        JsonObject r = rect(el);
        double x = r.get("x").getAsDouble();
        double y = r.get("y").getAsDouble();
        double w = r.get("w").getAsDouble();
        double h = r.get("h").getAsDouble();
        assertTrue(x >= xMin && x <= xMax, what + ": x=" + x);
        assertTrue(y >= yMin && y <= yMax, what + ": y=" + y);
        assertTrue(w >= wMin && w <= wMax, what + ": w=" + w);
        assertTrue(h >= hMin && h <= hMax, what + ": h=" + h);
    }

    private static JsonObject rect(JsonObject el) {
        return el.getAsJsonObject("rect");
    }

    private static JsonObject readFixture() throws Exception {
        try (InputStream in = Html2PptxExtractTest.class.getResourceAsStream(FIXTURE_RESOURCE)) {
            assertNotNull(in, "fixture 缺失：" + FIXTURE_RESOURCE
                + "（设 HTML2PPTX_UPDATE_FIXTURES=1 在真 Chrome 下重跑生成后提交）");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** fixture 仓库路径：从 user.dir 向上找 weizhi-bridge 模块目录再进 src/test/resources。 */
    private static Path fixtureRepoPath() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path module = dir;
        while (module != null && !"weizhi-bridge".equals(module.getFileName().toString())) {
            module = module.getParent();
        }
        return (module != null ? module : dir).resolve("src/test/resources/html2pptx/extract-deck-geometry-fixture.json");
    }

    private static JsonObject element(JsonObject slide, String tag, String clsPrefix, String src) {
        for (JsonElement e : slide.getAsJsonArray("elements")) {
            JsonObject o = e.getAsJsonObject();
            if (!tag.equals(o.get("tag").getAsString())) {
                continue;
            }
            if (clsPrefix == null || (o.has("cls") && o.get("cls").getAsString().startsWith(clsPrefix))) {
                return o;
            }
        }
        return fail(src + " 缺少元素 tag=" + tag + " cls=" + clsPrefix);
    }

    private static JsonObject elementWithRunText(JsonObject slide, String tag, String runText, String src) {
        for (JsonElement e : slide.getAsJsonArray("elements")) {
            JsonObject o = e.getAsJsonObject();
            if (!tag.equals(o.get("tag").getAsString()) || !o.has("textRuns")) {
                continue;
            }
            for (JsonElement run : o.getAsJsonArray("textRuns")) {
                if (runText.equals(run.getAsJsonObject().get("text").getAsString())) {
                    return o;
                }
            }
        }
        return fail(src + " 缺少含 run 文本 «" + runText + "» 的 " + tag);
    }

    private static List<JsonObject> elementsWithCls(JsonObject slide, String clsPrefix) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement e : slide.getAsJsonArray("elements")) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("cls") && o.get("cls").getAsString().startsWith(clsPrefix)) {
                out.add(o);
            }
        }
        return out;
    }

    private static String textOrNull(JsonObject o, String name) {
        return o.has(name) && !o.get(name).isJsonNull() ? o.get(name).getAsString() : null;
    }

    private static String dataUriPrefix(String uri) {
        int cut = uri.indexOf(',');
        return cut > 0 ? uri.substring(0, cut) : uri;
    }

    private static boolean near(double expected, double actual, double tol) {
        return Math.abs(expected - actual) <= tol;
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
}
