package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Agent1 bootstrap + pptx.js + renderPptx / buildPptx（对齐 weizhi OfficeTest PPT 段）。 */
class PptxOfficeIntegrationTest {

    private static WeizhiScriptEngineFactory factory;

    @BeforeAll
    static void requireWeizhi() {
        Path repo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (repo.getNameCount() == 0 || !Files.isDirectory(repo)) {
            repo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        assumeTrue(WeizhiJniBootstrap.tryLoad(repo), "libweizhijni not built");
        factory = new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions().installDesktopCaps(true),
            null
        );
    }

    @Test
    void renderPptxViaPptxJs(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiTestPackages.syncFromFixture(agentRoot);

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot);
        String js =
            """
            import { renderPptx } from "weizhi:pptx";
            export default renderPptx({
              title: "季度复盘",
              theme: "briefing",
              slides: [
                { layout: "title", title: "季度复盘", subtitle: "2026 Q3" },
                { layout: "bullets", title: "结论", items: ["收入 +12%"] }
              ]
            }, "out/q3.pptx");
            """;
        try (var engine = withRoot.open(workspace)) {
            String out = engine.eval(js, 30_000, null);
            assertTrue(out.contains("\"ok\":true"), out);
            assertTrue(out.contains("out/q3.pptx"), out);
        }
        Path pptx = workspace.resolve("out/q3.pptx");
        assertTrue(Files.isRegularFile(pptx));
        assertTrue(Files.size(pptx) > 100L);
        assertTrue(zipEntryContains(pptx, "ppt/slides/slide1.xml", "季度复盘"));
        assertTrue(zipEntryContains(pptx, "ppt/slides/slide2.xml", "收入 +12%"));
    }

    @Test
    void buildPptxViaPptxBuildJs(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiTestPackages.syncFromFixture(agentRoot);

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot);
        String js =
            """
            import { buildPptx } from "weizhi:pptx-build";
            export default buildPptx({ title: "B", theme: "briefing" }, (b) => {
              b.title("Built", "Sub").bullets("要点", ["一", "二"]);
            }, "out/built.pptx");
            """;
        try (var engine = withRoot.open(workspace)) {
            String out = engine.eval(js, 30_000, null);
            assertTrue(out.contains("\"ok\":true"), out);
        }
        assertTrue(zipEntryContains(workspace.resolve("out/built.pptx"), "ppt/slides/slide1.xml", "Built"));
    }

    @Test
    void contentBoxesStayWithTheText(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiTestPackages.syncFromFixture(agentRoot);

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot);
        String js =
            """
            import { renderPptx } from "weizhi:pptx";
            export default renderPptx({
              title: "fit",
              theme: "editorial",
              slides: [
                { layout: "bullets", title: "结论", items: ["收入 +12%", "留存持平"] },
                { layout: "cards", title: "重点", items: [
                  { title: "产品", text: "留存稳住" },
                  { title: "销售", text: "新客增加" }
                ]},
                { layout: "stat", title: "指标", items: [{ value: "12%", label: "收入" }] },
                { layout: "callout", title: "一句", text: "先把框收到字旁边。" },
                { layout: "steps", title: "流程", items: ["发现", "修复", "上线"] }
              ]
            }, "out/fit.pptx");
            """;
        try (var engine = withRoot.open(workspace)) {
            String out = engine.eval(js, 30_000, null);
            assertTrue(out.contains("\"ok\":true"), out);
        }
        Path pptx = workspace.resolve("out/fit.pptx");
        assertTrue(maxPresetCy(zipText(pptx, "ppt/slides/slide1.xml"), "roundRect") < 3_200_000);
        assertTrue(maxPresetCy(zipText(pptx, "ppt/slides/slide2.xml"), "roundRect") < 3_200_000);
        assertTrue(maxPresetCy(zipText(pptx, "ppt/slides/slide3.xml"), "roundRect") < 3_200_000);
        assertTrue(maxPresetCy(zipText(pptx, "ppt/slides/slide4.xml"), "roundRect") < 3_200_000);
        assertTrue(maxPresetCy(zipText(pptx, "ppt/slides/slide5.xml"), "chevron") < 1_400_000);
    }

    @Test
    void renderSampleDecksWhenRequested(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        String dir = System.getenv("PPTX_SAMPLE_DIR");
        assumeTrue(dir != null && !dir.isBlank(), "PPTX_SAMPLE_DIR not set");
        AgentHomeBootstrap.ensure(agentRoot);
        WeizhiTestPackages.syncFromFixture(agentRoot);
        Path out = Path.of(dir);
        Files.createDirectories(out);
        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot);
        String js =
            """
            import { renderPptx } from "weizhi:pptx";
            const decks = [
              ["editorial.pptx", "editorial"],
              ["harbor.pptx", "harbor"],
              ["dusk.pptx", "dusk"]
            ];
            const slides = [
              { layout: "title", title: "季度复盘", subtitle: "把字放进刚好的框里" },
              { layout: "bullets", title: "结论", items: ["收入同比 +12%", "留存与上季持平", "新客成本下降"] },
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
              { layout: "twoColumn", title: "取舍", leftTitle: "继续", rightTitle: "停下",
                left: ["做深主路径", "补齐新手引导"], right: ["铺新功能", "加长报表"] },
              { layout: "callout", title: "一句", text: "少而准的字，配上刚好的框。" },
              { layout: "steps", title: "下一步", items: ["复盘", "收敛", "上线"] },
              { layout: "table", title: "对照", rows: [["项", "上季", "本季"], ["收入", "100", "112"], ["故障", "4", "1"]] }
            ];
            const written = [];
            for (const pair of decks) {
              const path = pair[0];
              renderPptx({ title: "季度复盘", theme: pair[1], slides: slides }, path);
              written.push(path);
            }
            export default written.join(",");
            """;
        try (var engine = withRoot.open(out)) {
            String result = engine.eval(js, 30_000, null);
            assertTrue(result.contains("editorial.pptx"), result);
        }
        assertTrue(Files.size(out.resolve("editorial.pptx")) > 100L);
        assertTrue(Files.size(out.resolve("harbor.pptx")) > 100L);
        assertTrue(Files.size(out.resolve("dusk.pptx")) > 100L);
    }

    private static WeizhiScriptEngineFactory engineWithRoot(Path agentRoot) {
        return new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions().installDesktopCaps(true),
            agentRoot
        );
    }

    private static boolean zipEntryContains(Path zipFile, String entryName, String needle) throws Exception {
        String text = zipText(zipFile, entryName);
        return text != null && text.contains(needle);
    }

    private static String zipText(Path zipFile, String entryName) throws Exception {
        try (InputStream in = Files.newInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.getName().equals(entryName)) {
                    continue;
                }
                return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static int maxPresetCy(String xml, String preset) {
        int max = 0;
        if (xml == null) {
            return max;
        }
        String[] parts = xml.split("<p:sp>");
        Pattern cy = Pattern.compile("cy=\"(\\d+)\"");
        for (String part : parts) {
            if (!part.contains("prst=\"" + preset + "\"")) {
                continue;
            }
            Matcher matcher = cy.matcher(part);
            if (matcher.find()) {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            }
        }
        return max;
    }
}
