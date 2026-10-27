package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 云端-only {@code weizhi:xlsx}：不经 classpath bundled，从仓内真源安装后跑读写与公式。
 */
class XlsxOfficeIntegrationTest {

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
    void createReadAndSetFormulaFromSourcePackages(@TempDir Path agentRoot, @TempDir Path workspace)
        throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path home = WeizhiBundledPackages.weizhiHome(agentRoot);
        Files.createDirectories(home);
        WeizhiSourcePackages.installCloudOnlyFromSource(home);

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot, home);
        String js =
            """
            import { Workbook, readXlsx, renderXlsx } from "weizhi:xlsx";
            import { buildXlsx } from "weizhi:xlsx-build";

            buildXlsx("out/budget.xlsx", (b) => {
              b.title("预算")
                .sheet("汇总")
                .headers(["项目", "金额", "税率", "含税"])
                .rows([["差旅", 1000, 0.13], ["餐饮", 200, 0.06]])
                .formula("D2", "B2*(1+C2)", 1130)
                .formula("D3", "B3*(1+C3)", 212);
            });

            const wb = readXlsx("out/budget.xlsx");
            const sh = wb.sheet("汇总");
            sh.set("B2", 1200);
            sh.setFormula("D2", "B2*(1+C2)", 1356);
            const saved = wb.save("out/budget-edited.xlsx");
            const again = readXlsx("out/budget-edited.xlsx");
            const cell = again.sheet("汇总").get("D2");
            const summary = again.sheet("汇总").summary();
            export default {
              savedOk: saved.ok,
              formula: cell.formula,
              amount: again.sheet("汇总").get("B2").value,
              headers: summary.headers,
              formulas: summary.formulas
            };
            """;
        String out;
        try (var engine = withRoot.open(workspace)) {
            out = engine.eval(js, 60_000, null);
        }
        assertTrue(out.contains("\"savedOk\":true"), out);
        assertTrue(out.contains("B2*(1+C2)"), out);
        assertTrue(out.contains("\"amount\":1200"), out);
        assertTrue(out.contains("差旅") || out.contains("项目"), out);
        assertTrue(Files.isRegularFile(workspace.resolve("out/budget.xlsx")));
        Path edited = workspace.resolve("out/budget-edited.xlsx");
        assertTrue(Files.size(edited) > 100L);
        String sheetXml = readZipEntry(edited, "xl/worksheets/sheet1.xml");
        assertTrue(
            sheetXml.contains("B2*(1+C2)"),
            "sheet1.xml missing formula, entries/xml=" + listZipEntries(edited) + " / " + sheetXml
        );
    }

    @Test
    void csvRoundTripAndGrep(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path home = WeizhiBundledPackages.weizhiHome(agentRoot);
        Files.createDirectories(home);
        WeizhiSourcePackages.installFromSource(home, "xlsx");

        Path csv = workspace.resolve("data/sales.csv");
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, "区域,销售额\n华北,100\n华南,80\n");

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot, home);
        String js =
            """
            import { csvToXlsx, readXlsx } from "weizhi:xlsx";
            csvToXlsx({ inputPath: "data/sales.csv", outputPath: "out/sales.xlsx", sheetName: "销售" });
            const sh = readXlsx("out/sales.xlsx").sheet("销售");
            const hits = sh.grep("华北");
            export default { name: sh.name, hit: hits.matches[0].ref, view: sh.textView({ maxRows: 5 }) };
            """;
        String out;
        try (var engine = withRoot.open(workspace)) {
            out = engine.eval(js, 60_000, null);
        }
        assertTrue(out.contains("\"name\":\"销售\""), out);
        assertTrue(out.contains("\"hit\":\"A2\"") || out.contains("A2"), out);
        assertTrue(out.contains("华北"), out);
    }

    @Test
    void cloudOnlyPackagesAreNotOnBundledList() {
        assertTrue(WeizhiSourcePackages.isCloudOnly("xlsx"));
        assertTrue(WeizhiSourcePackages.isCloudOnly("xlsx-build"));
        assertTrue(
            WeizhiBundledPackages.BUNDLED_PACKAGE_NAMES.stream().noneMatch(WeizhiSourcePackages::isCloudOnly),
            "cloud-only xlsx must not be classpath-bundled"
        );
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

    @Test
    void chartAndMergeRoundTrip(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path home = WeizhiBundledPackages.weizhiHome(agentRoot);
        Files.createDirectories(home);
        WeizhiSourcePackages.installCloudOnlyFromSource(home);

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot, home);
        String js =
            """
            import { buildXlsx } from "weizhi:xlsx-build";
            import { readXlsx } from "weizhi:xlsx";

            buildXlsx("out/chart.xlsx", (b) => {
              b.sheet("月度")
                .merge("A1:C1")
                .headers(["月份", "销售额", "成本"])
                .rows([["1月", 100, 60], ["2月", 130, 70], ["3月", 120, 65]])
                .chart({
                  type: "bar",
                  title: "月度对比",
                  anchor: "E2",
                  series: [{ name: "销售额", categories: "A2:A4", values: "B2:B4" }]
                })
                .chart({
                  type: "pie",
                  anchor: "E18",
                  series: [{ nameRef: "B1", categories: "A2:A4", values: "B2:B4" }]
                });
            });
            const sh = readXlsx("out/chart.xlsx").sheet("月度");
            export default { merges: sh.getMerges(), headers: sh.headers() };
            """;
        String out;
        try (var engine = withRoot.open(workspace)) {
            out = engine.eval(js, 60_000, null);
        }
        assertTrue(out.contains("A1:C1"), out);
        assertTrue(out.contains("销售额") && out.contains("月份"), out);

        Path xlsx = workspace.resolve("out/chart.xlsx");
        assertTrue(Files.size(xlsx) > 100L);

        String sheet1 = readZipEntry(xlsx, "xl/worksheets/sheet1.xml");
        assertTrue(sheet1.contains("<mergeCell ref=\"A1:C1\"/>"), sheet1);
        assertTrue(sheet1.contains("<drawing r:id=\"rId1\"/>"), sheet1);
        assertWellFormed(sheet1);

        String chart1 = readZipEntry(xlsx, "xl/charts/chart1.xml");
        assertTrue(chart1.contains("<c:barChart>"), chart1);
        assertTrue(chart1.contains("'月度'!$B$2:$B$4"), chart1);
        assertTrue(chart1.contains("<c:v>100</c:v>") && chart1.contains("<c:v>130</c:v>"), chart1);
        assertWellFormed(chart1);

        String chart2 = readZipEntry(xlsx, "xl/charts/chart2.xml");
        assertTrue(chart2.contains("<c:pieChart>"), chart2);
        assertWellFormed(chart2);

        String drawing = readZipEntry(xlsx, "xl/drawings/drawing1.xml");
        assertTrue(drawing.contains("r:id=\"rId1\"") && drawing.contains("r:id=\"rId2\""), drawing);
        assertWellFormed(drawing);

        String drawingRels = readZipEntry(xlsx, "xl/drawings/_rels/drawing1.xml.rels");
        assertTrue(drawingRels.contains("../charts/chart1.xml"), drawingRels);
        assertTrue(drawingRels.contains("../charts/chart2.xml"), drawingRels);

        String sheetRels = readZipEntry(xlsx, "xl/worksheets/_rels/sheet1.xml.rels");
        assertTrue(sheetRels.contains("../drawings/drawing1.xml"), sheetRels);

        String contentTypes = readZipEntry(xlsx, "[Content_Types].xml");
        assertTrue(contentTypes.contains("drawingml.chart+xml"), contentTypes);
        assertTrue(contentTypes.contains("/xl/drawings/drawing1.xml"), contentTypes);
    }

    private static void assertWellFormed(String xml) throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try (var in = new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            factory.newDocumentBuilder().parse(in);
        }
    }

    private static String readZipEntry(Path zipFile, String entryName) throws Exception {
        try (InputStream in = Files.newInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }

    private static String listZipEntries(Path zipFile) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = Files.newInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(entry.getName());
                zis.readAllBytes();
            }
        }
        return sb.toString();
    }
}
