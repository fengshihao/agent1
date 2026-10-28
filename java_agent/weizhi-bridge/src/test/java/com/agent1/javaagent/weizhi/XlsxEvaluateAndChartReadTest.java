package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * xlsx@1.3.0：公式自动求值缓存（D）与图表读回（E）。
 */
class XlsxEvaluateAndChartReadTest {

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
    void formulaAutoCacheAndEvaluate(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path home = WeizhiBundledPackages.weizhiHome(agentRoot);
        Files.createDirectories(home);
        WeizhiTestPackages.installFromSource(home, "xlsx");
        WeizhiTestPackages.installFromSource(home, "xlsx-build");

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot, home);
        String js =
            """
            import { Workbook, renderXlsx } from "weizhi:xlsx";
            const wb = Workbook.create({ title: "ev" });
            const sh = wb.sheet(0);
            sh.set("A1", 10);
            sh.set("B1", 20);
            sh.setFormula("C1", "A1+B1");                      // 自动求值 → 30
            sh.setFormula("D1", "SUM(A1:B1)");                // → 30
            sh.setFormula("E1", 'IF(C1>25, "大", "小")');     // → "大"
            sh.setFormula("F1", "F1+1");                      // 循环引用
            sh.setFormula("G1", "VLOOKUP(1, A1:B1, 2)");      // 未支持函数 → 降级
            const saved = renderXlsx(wb, "out/ev.xlsx");
            export default {
              savedOk: saved.ok,
              c1: sh.evaluate("C1"),
              d1: sh.evaluate("D1"),
              e1: sh.evaluate("E1"),
              f1: sh.evaluate("F1"),
              g1: sh.evaluate("G1"),
              cached: sh.get("C1").value,
            };
            """;
        String out;
        try (var engine = withRoot.open(workspace)) {
            out = engine.eval(js, 60_000, null);
        }
        assertTrue(out.contains("\"savedOk\":true"), out);
        assertTrue(out.contains("\"value\":30"), out);
        assertTrue(out.contains("大"), out);
        assertTrue(out.contains("circular"), out);
        assertTrue(out.contains("unsupported function"), out);
        assertTrue(out.contains("\"cached\":30"), out);

        Map<String, byte[]> parts = readZip(workspace.resolve("out/ev.xlsx"));
        String sheet1 = new String(parts.get("xl/worksheets/sheet1.xml"), StandardCharsets.UTF_8);
        assertTrue(sheet1.contains("<f>A1+B1</f><v>30</v>"), sheet1);
        assertTrue(sheet1.contains("<f>SUM(A1:B1)</f><v>30</v>"), sheet1);
        // 循环 / 未支持：有 <f> 无 <v>（Excel 打开重算）
        assertTrue(sheet1.contains("<f>F1+1</f>") && sheet1.indexOf("<f>F1+1</f><v>") < 0, sheet1);
        assertTrue(sheet1.contains("<f>VLOOKUP(1, A1:B1, 2)</f>"), sheet1);
        assertTrue(sheet1.indexOf("VLOOKUP</f><v>") < 0, sheet1);
    }

    @Test
    void chartReadBackAndFidelitySaveKeepsParts(@TempDir Path agentRoot, @TempDir Path workspace)
        throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path home = WeizhiBundledPackages.weizhiHome(agentRoot);
        Files.createDirectories(home);
        WeizhiTestPackages.installFromSource(home, "xlsx");
        WeizhiTestPackages.installFromSource(home, "xlsx-build");

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot, home);
        String build =
            """
            import { buildXlsx } from "weizhi:xlsx-build";
            export default buildXlsx("seed.xlsx", (b) => {
              b.sheet("汇总")
                .headers(["月份", "销售额"])
                .rows([["1月", 100], ["2月", 130], ["3月", 120]])
                .chart({ type: "bar", title: "月度", anchor: "E2",
                  series: [{ name: "销售额", categories: "A2:A4", values: "B2:B4" }] })
                .chart({ type: "pie", anchor: "E18",
                  series: [{ nameRef: "B1", categories: "A2:A4", values: "B2:B4" }] });
            });
            """;
        try (var engine = withRoot.open(workspace)) {
            assertTrue(engine.eval(build, 60_000, null).contains("\"ok\":true"));
        }

        String js =
            """
            import { readXlsx } from "weizhi:xlsx";
            const wb = readXlsx("seed.xlsx");
            const sh = wb.sheet("汇总");
            const charts = sh.getCharts();
            sh.set("B2", 999);   // 编辑触发保真 patch；原图表 parts 应原样保留
            const saved = wb.save("out/kept.xlsx");
            export default { charts, savedOk: saved.ok };
            """;
        String out;
        try (var engine = withRoot.open(workspace)) {
            out = engine.eval(js, 60_000, null);
        }
        assertTrue(out.contains("\"savedOk\":true"), out);
        // 图表读回
        assertTrue(out.contains("\"type\":\"bar\""), out);
        assertTrue(out.contains("\"type\":\"pie\""), out);
        assertTrue(out.contains("'汇总'!$B$2:$B$4"), out);
        assertTrue(out.contains("'汇总'!$A$2:$A$4"), out);
        assertTrue(out.contains("月度"), out);

        // 保真 save 后原图表 parts 逐字节保留
        Map<String, byte[]> seed = readZip(workspace.resolve("seed.xlsx"));
        Map<String, byte[]> kept = readZip(workspace.resolve("out/kept.xlsx"));
        byte[] chart1 = seed.get("xl/charts/chart1.xml");
        assertTrue(chart1 != null && kept.get("xl/charts/chart1.xml") != null, "chart1 missing");
        assertTrue(java.util.Arrays.equals(chart1, kept.get("xl/charts/chart1.xml")),
            "chart1.xml changed after fidelity save");
        byte[] chart2 = seed.get("xl/charts/chart2.xml");
        assertTrue(java.util.Arrays.equals(chart2, kept.get("xl/charts/chart2.xml")),
            "chart2.xml changed after fidelity save");
        // 编辑生效
        String sheet1 = new String(kept.get("xl/worksheets/sheet1.xml"), StandardCharsets.UTF_8);
        assertTrue(sheet1.contains("<v>999</v>"), sheet1);
    }

    private static WeizhiScriptEngineFactory engineWithRoot(Path agentRoot, Path weizhiHome) {
        return new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions()
                .installDesktopCaps(true)
                .packageRoot(weizhiHome.toAbsolutePath().toString())
                .mcpAgentRoot(agentRoot),
            null
        );
    }

    private static Map<String, byte[]> readZip(Path zipFile) throws Exception {
        Map<String, byte[]> out = new java.util.LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    out.put(entry.getName(), zis.readAllBytes());
                }
            }
        }
        return out;
    }
}
