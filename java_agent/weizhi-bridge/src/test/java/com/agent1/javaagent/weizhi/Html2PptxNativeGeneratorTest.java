package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.ScriptEngine;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code geometryToPptx}（20-A T3）：几何 JSON → 原生 OOXML，纯 QuickJS、不需要
 * webview/Chrome。直接消费 T2 落下的几何 fixture，断言 slide XML 里的原生
 * {@code p:sp} 文本框、{@code roundRect}、渐变背景、边框色条、svgBlip 图片与
 * 行内文本去重；错误路径与 needsScreenshot 记录单独覆盖。
 */
class Html2PptxNativeGeneratorTest {

    private static final String FIXTURE_RESOURCE = "/html2pptx/extract-deck-geometry-fixture.json";

    @BeforeAll
    static void requireNative() {
        Path repo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (repo.getNameCount() == 0 || !Files.isDirectory(repo)) {
            repo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        assumeTrue(WeizhiJniBootstrap.tryLoad(repo), "libweizhijni not built; run weizhi ./scripts/build.sh");
    }

    /** fixture → pptx：原生文本框/形状/图片/去重全链路断言。 */
    @Test
    void convertsFixtureGeometryToNativePptx(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiTestPackages.syncFromFixture(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        try (InputStream in = getClass().getResourceAsStream(FIXTURE_RESOURCE)) {
            assertNotNull(in, "几何 fixture 缺失：" + FIXTURE_RESOURCE);
            Files.copy(in, workspace.resolve("deck-geometry.json"));
        }

        ScriptEngine engine = new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions().installDesktopCaps(true), agentRoot
        ).open(workspace);
        try (engine) {
            String out = engine.evalForAgent("""
                import { geometryToPptx } from "weizhi:html2pptx";
                export default await geometryToPptx({ geometryPath: "deck-geometry.json", output: "out/native.pptx" });
                """, null, 60_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out.contains("\"ok\":true"), out);
            assertTrue(out.contains("\"slides\":4"), out);
            assertTrue(out.contains("\"pictures\":2"), out);
            assertTrue(out.contains("\"shapes\":32"), out);
            assertTrue(out.contains("\"reason\":\"shadow\""), "第三卡阴影应记入 needsScreenshot: " + out);
        }

        Path pptx = workspace.resolve("out/native.pptx");
        assertTrue(Files.isRegularFile(pptx), "应生成 pptx");
        try (ZipFile zip = new ZipFile(pptx.toFile())) {
            assertTrue(zipText(zip, "ppt/presentation.xml").contains("cx=\"12192000\" cy=\"6858000\""));

            // 封面：渐变背景（135deg→ang 2700000，双色）落整页最底层矩形（WPS 对 p:bg gradFill 不可靠）+ deco 射线渐变 + h1 原生文本框
            String s1 = zipText(zip, "ppt/slides/slide1.xml");
            assertTrue(s1.contains("name=\"background\""), "渐变页背景应为整页底层矩形");
            assertTrue(s1.contains("<a:lin ang=\"2700000\""), "封面渐变角度应为 2700000");
            assertTrue(s1.contains("val=\"1a1423\"") && s1.contains("val=\"312846\""), "封面渐变双色");
            assertTrue(s1.contains("<a:path path=\"circle\""), "radial deco 应为射线渐变而非纯色近似");
            assertTrue(s1.contains("prst=\"roundRect\"") && s1.contains("fmla=\"val 25000\""),
                "deco 50% 圆角折算 130px/520px → adj 25000");
            assertTrue(s1.contains("<a:alpha val=\"25000\"/>"), "deco 首色 alpha 0.25 应保留");
            assertTrue(s1.contains("wrap=\"none\""), "h1 单行文本不应折行");
            assertTrue(s1.contains("<a:off x=\"1047750\""), "h1 x=110px → EMU 1047750");
            assertTrue(s1.contains("sz=\"6600\"") && s1.contains("b=\"1\""), "h1 88px/800 → 6600 磅加粗");
            assertTrue(s1.contains("val=\"115000\""), "h1 行距 101.2/88 → 115%");
            assertTrue(s1.contains("val=\"f6f1ea\"") && s1.contains("typeface=\"Helvetica\"")
                && s1.contains("typeface=\"PingFang SC\""), "颜色与 latin/ea 字体族");

            // 卡片页：纯色页底、roundRect 18px、上边框色条、阴影近似、em 斜体 run 并入 h2
            String s2 = zipText(zip, "ppt/slides/slide2.xml");
            assertTrue(s2.contains("<a:srgbClr val=\"faf9f6\""), "页底 #faf9f6");
            assertTrue(s2.contains("fmla=\"val 9799\""), "卡片圆角 18/183.69 → adj 9799");
            assertEquals(1, countOf(s2, "val=\"7dd3c0\""), "首卡上边框色条");
            assertEquals(1, countOf(s2, "val=\"8b7ec8\""), "第三卡上边框色条");
            assertTrue(s2.contains("cy=\"57150\""), "上边框 6px 高色条");
            assertTrue(s2.contains("<a:outerShdw blurRad=\"304800\" dist=\"114300\" dir=\"5400000\""),
                "阴影 32px 模糊/12px 下移 → outerShdw");
            assertTrue(s2.contains("<a:alpha val=\"8000\"/>"), "阴影 alpha 0.08");
            assertEquals(1, countOf(s2, "<a:t>样式</a:t>"), "em 斜体 run 应只在 h2 出现一次（行内去重）");
            assertTrue(s2.contains("i=\"1\""), "em 斜体");
            assertEquals(3, countOf(s2, "prst=\"roundRect\""), "三张卡片");

            // 图表页：canvas 位图 + svg（svgBlip 扩展 + 1×1 透明 PNG 兜底）+ 合并 run、svg text 去重
            String s3 = zipText(zip, "ppt/slides/slide3.xml");
            assertEquals(2, countOf(s3, "<p:pic>"), "canvas + svg 两张图片");
            assertTrue(s3.contains("asvg:svgBlip"), "svg 走 svgBlip 扩展");
            assertEquals(0, countOf(s3, "<a:t>61%</a:t>"), "svg 内 text 不重复出文本框");
            assertEquals(1, countOf(s3, "<a:t>相邻 同款 合并</a:t>"), "相邻同款 span 合并单 run");
            String rels3 = zipText(zip, "ppt/slides/_rels/slide3.xml.rels");
            assertTrue(rels3.contains("image1.png") && rels3.contains("svg-fallback.png")
                && rels3.contains(".svg"), "图片关系齐全");
            assertTrue(zipText(zip, "[Content_Types].xml").contains("Extension=\"svg\""));

            // 媒体件：canvas PNG 魔数 + 兜底 PNG 是 1×1 透明 + svg 是合法 utf8 文本
            ZipEntry canvas = zip.getEntry("ppt/media/image1.png");
            assertNotNull(canvas, "canvas 位图应落 media");
            byte[] canvasBytes = zip.getInputStream(canvas).readAllBytes();
            assertEquals(0x89, canvasBytes[0] & 0xff, "PNG 魔数");
            ZipEntry fallback = zip.getEntry("ppt/media/svg-fallback.png");
            assertNotNull(fallback, "svg 兜底 PNG 应存在");
            assertTrue(fallback.getSize() < 200, "兜底应为 1×1 透明 PNG（<200 字节）");
            String svg = new String(zip.getInputStream(zip.getEntry("ppt/media/image3.svg")).readAllBytes(),
                StandardCharsets.UTF_8);
            assertTrue(svg.contains("<svg") && svg.contains("circle"), "svg 件应保留原始矢量");
        }
    }

    /** 默认输出名：deck-geometry.json → deck.pptx；内联 geometry 缺 output 报错。 */
    @Test
    void defaultsOutputNameAndRejectsInlineWithoutOutput(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiTestPackages.syncFromFixture(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        try (InputStream in = getClass().getResourceAsStream(FIXTURE_RESOURCE)) {
            Files.copy(in, workspace.resolve("deck-geometry.json"));
        }

        ScriptEngine engine = new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions().installDesktopCaps(true), agentRoot
        ).open(workspace);
        try (engine) {
            String out = engine.evalForAgent("""
                import { geometryToPptx } from "weizhi:html2pptx";
                export default await geometryToPptx({ geometryPath: "deck-geometry.json" });
                """, null, 60_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out.contains("\"ok\":true"), out);
            assertTrue(out.contains("deck.pptx"), "默认输出应为 deck.pptx: " + out);
            assertTrue(Files.isRegularFile(workspace.resolve("deck.pptx")));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> engine.evalForAgent("""
                import { geometryToPptx } from "weizhi:html2pptx";
                export default await geometryToPptx({
                  geometry: { version: 1, slides: [] },
                  output: "out/empty.pptx"
                });
                """, null, 60_000, new CancellationToken(), "jobs/run2.js"));
            assertTrue(ex.getMessage().contains("slides"), ex.getMessage());

            RuntimeException ex2 = assertThrows(RuntimeException.class, () -> engine.evalForAgent("""
                import { geometryToPptx } from "weizhi:html2pptx";
                export default await geometryToPptx({
                  geometry: { version: 1, slides: [{ slideW: 1280, slideH: 720, elements: [] }] }
                });
                """, null, 60_000, new CancellationToken(), "jobs/run3.js"));
            assertTrue(ex2.getMessage().contains("output"), ex2.getMessage());
        }
    }

    /** 远程图片与超限图片：不出 p:pic，记入 needsScreenshot 由 T4 区域截图补。 */
    @Test
    void recordsRemoteAndOmittedImagesAsScreenshotNeeds(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiTestPackages.syncFromFixture(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));

        ScriptEngine engine = new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions().installDesktopCaps(true), agentRoot
        ).open(workspace);
        try (engine) {
            String out = engine.evalForAgent("""
                import { geometryToPptx } from "weizhi:html2pptx";
                export default await geometryToPptx({
                  output: "out/needs.pptx",
                  geometry: { version: 1, slides: [{ slideW: 1280, slideH: 720, elements: [
                    { tag: "img", rect: { x: 10, y: 10, w: 100, h: 50 },
                      font: { size: 16, weight: "400", color: "#000000", align: "start", lineHeight: 0 },
                      imgSrc: "https://example.com/a.png" },
                    { tag: "canvas", rect: { x: 10, y: 80, w: 100, h: 50 },
                      font: { size: 16, weight: "400", color: "#000000", align: "start", lineHeight: 0 },
                      imgOmitted: true }
                  ]}] }
                });
                """, null, 60_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out.contains("\"ok\":true"), out);
            assertTrue(out.contains("\"reason\":\"remote image\""), out);
            assertTrue(out.contains("\"reason\":\"imgOmitted\""), out);
        }
        try (ZipFile zip = new ZipFile(workspace.resolve("out/needs.pptx").toFile())) {
            String s1 = zipText(zip, "ppt/slides/slide1.xml");
            assertEquals(0, countOf(s1, "<p:pic>"), "远程/超限图片不应出 p:pic");
        }
    }

    /**
     * echarts svg chartShapes → 原生矢量形状（0.2.6）：rect/circle/line → prstGeom、
     * path（含 A 弧）→ custGeom 贝塞尔、text → 文本框、svg 渐变 → gradFill；
     * chart 区域不出 clip 截图（p:pic）。
     */
    @Test
    void convertsChartShapesToNativeVectorShapes(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiTestPackages.syncFromFixture(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));

        ScriptEngine engine = new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions().installDesktopCaps(true), agentRoot
        ).open(workspace);
        try (engine) {
            String out = engine.evalForAgent("""
                import { geometryToPptx } from "weizhi:html2pptx";
                export default await geometryToPptx({
                  output: "out/chart.pptx",
                  geometry: { version: 1, slides: [{
                    slideW: 1280, slideH: 720, bg: "#ffffff",
                    elements: [
                      { tag: "h2", rect: { x: 96, y: 60, w: 500, h: 44 },
                        font: { size: 32, weight: "700", color: "#1a1423", align: "start", lineHeight: 44, family: "Helvetica" },
                        textRuns: [{ text: "月度指标", size: 32, weight: "700", color: "#1a1423" }] }
                    ],
                    chartShapes: [{
                      rect: { x: 96, y: 200, w: 500, h: 300 },
                      viewBox: { w: 500, h: 300 },
                      shapes: [
                        { t: "rect", x: 40, y: 100, w: 60, h: 150, fill: { color: "#7dd3c0" } },
                        { t: "rect", x: 120, y: 60, w: 60, h: 190,
                          fill: { grad: { ang: 0, stops: [
                            { pos: 0, color: "#8b7ec8", alpha: 1 }, { pos: 1, color: "#4a90d9", alpha: 1 } ] } } },
                        { t: "circle", cx: 400, cy: 80, r: 8, fill: { color: "#4a90d9" } },
                        { t: "line", x1: 20, y1: 250, x2: 480, y2: 250, stroke: { color: "#999999" }, strokeW: 1 },
                        { t: "path", d: [["M", 20, 220], ["A", 50, 50, 0, 1, 1, 120, 220], ["Z"]], fill: { color: "#e8b86d" } },
                        { t: "text", x: 200, y: 20, w: 80, h: 16, size: 14, color: "#666666",
                          anchor: "middle", weight: "400", text: "月度" }
                      ]
                    }]
                  }] }
                });
                """, null, 60_000, new CancellationToken(), "jobs/run.js");
            assertTrue(out.contains("\"ok\":true"), out);
            assertTrue(out.contains("\"shapes\":7"), "h2 + 6 chart shapes: " + out);
        }

        try (ZipFile zip = new ZipFile(workspace.resolve("out/chart.pptx").toFile())) {
            String s1 = zipText(zip, "ppt/slides/slide1.xml");
            // rect 柱：svg x=40 → slide x=96+40=136 → EMU 1295400
            assertTrue(s1.contains("<a:off x=\"1295400\""), "柱 x 应换算到 slide 坐标: " + s1.substring(0, 400));
            assertTrue(s1.contains("val=\"7dd3c0\""), "柱纯色填充");
            // 渐变柱：svg ang 0 → OOXML lin ang=0
            assertTrue(s1.contains("<a:lin ang=\"0\""), "svg 渐变角度直接映射");
            assertTrue(s1.contains("val=\"8b7ec8\"") && s1.contains("val=\"4a90d9\""), "渐变双色 stops");
            // circle → ellipse：(96+400-8, 200+80-8) = (488,272) → EMU 4648200/2590800
            assertTrue(s1.contains("prst=\"ellipse\"") && s1.contains("x=\"4648200\""), "圆 → ellipse 原生形状");
            // 网格线 → prst line
            assertTrue(s1.contains("prst=\"line\""), "line → 直线形状");
            // 饼图 path → custGeom + A 弧转贝塞尔 + close
            assertTrue(s1.contains("<a:custGeom>"), "path → 自定义几何");
            assertTrue(countOf(s1, "<a:cubicBezTo>") >= 2, "半圆弧至少两段贝塞尔");
            assertTrue(s1.contains("<a:close/>"), "Z → close");
            assertTrue(s1.contains("val=\"e8b86d\""), "饼图填充");
            // 文本标签：anchor middle → ctr
            assertTrue(s1.contains("algn=\"ctr\"") && s1.contains("<a:t>月度</a:t>"), "text-anchor middle → 居中");
            // chart 区域不走截图
            assertEquals(0, countOf(s1, "<p:pic>"), "chart 原生形状不应出截图");
            assertEquals(0, countOf(s1, "html2pptx-clip"), "无 clip 元素名");
        }
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static String zipText(ZipFile zip, String entryName) throws Exception {
        ZipEntry entry = zip.getEntry(entryName);
        assertNotNull(entry, "缺少 zip 条目 " + entryName);
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
