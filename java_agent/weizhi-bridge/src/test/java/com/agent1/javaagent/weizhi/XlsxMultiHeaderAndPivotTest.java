package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * xlsx@1.5.0：多级表头合成视图（effectiveHeaders）与透视表结构读回（getPivotTables）。
 */
class XlsxMultiHeaderAndPivotTest {

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
    void effectiveHeadersUnfoldsMergedMultiLevelHeader(@TempDir Path agentRoot, @TempDir Path workspace)
        throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path home = WeizhiBundledPackages.weizhiHome(agentRoot);
        Files.createDirectories(home);
        WeizhiSourcePackages.installFromSource(home, "xlsx");
        WeizhiSourcePackages.installFromSource(home, "xlsx-build");

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot, home);
        // 两级表头：上半年(销售额/成本) 下半年(销售额/成本)；数据区 A 列纵向合并
        String js =
            """
            import { buildXlsx } from "weizhi:xlsx-build";
            export default buildXlsx("ml.xlsx", (b) => {
              b.sheet("报表")
                .set("A1", "上半年").merge("A1:B1")
                .set("C1", "下半年").merge("C1:D1")
                .set("A2", "销售额").set("B2", "成本")
                .set("C2", "销售额").set("D2", "成本")
                .set("A3", "华北").merge("A3:A4")
                .set("B3", 100).set("C3", 120).set("D3", 80)
                .set("B4", 90).set("C4", 110).set("D4", 70);
            });
            """;
        try (var engine = withRoot.open(workspace)) {
            assertTrue(engine.eval(js, 60_000, null).contains("\"ok\":true"));
        }

        String read =
            """
            import { readXlsx } from "weizhi:xlsx";
            const sh = readXlsx("ml.xlsx").sheet("报表");
            const auto = sh.effectiveHeaders();            // 自动探测（纵向合并 A3:A4 → 表头 2 行）
            const one = sh.effectiveHeaders({ headerRows: 1 });
            export default { auto, one, merges: sh.getMerges() };
            """;
        String out;
        try (var engine = withRoot.open(workspace)) {
            out = engine.eval(read, 60_000, null);
        }
        // 自动探测
        assertTrue(out.contains("上半年 > 销售额"), out);
        assertTrue(out.contains("上半年 > 成本"), out);
        assertTrue(out.contains("下半年 > 销售额"), out);
        assertTrue(out.contains("下半年 > 成本"), out);
        assertTrue(out.contains("\"headerRows\":2"), out);
        assertTrue(out.contains("\"dataRef\":\"B3\""), out);
        // 显式 1 行：横向合并区域也展开（A/B 共享「上半年」）
        assertTrue(out.contains("\"one\":"), out);
    }

    @Test
    void readsPivotTableStructure(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path home = WeizhiBundledPackages.weizhiHome(agentRoot);
        Files.createDirectories(home);
        WeizhiSourcePackages.installFromSource(home, "xlsx");
        WeizhiSourcePackages.installFromSource(home, "xlsx-build");

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot, home);
        String build =
            """
            import { buildXlsx } from "weizhi:xlsx-build";
            export default buildXlsx("pt.xlsx", (b) => {
              b.sheet("数据").headers(["区域", "产品", "季度", "销售额"])
                .rows([["华北", "A", "Q1", 100]]);
            });
            """;
        try (var engine = withRoot.open(workspace)) {
            assertTrue(engine.eval(build, 60_000, null).contains("\"ok\":true"));
        }

        injectPivot(workspace.resolve("pt.xlsx"), workspace.resolve("pt-pivot.xlsx"));

        String js =
            """
            import { readXlsx } from "weizhi:xlsx";
            const sh = readXlsx("pt-pivot.xlsx").sheet("数据");
            export default { pivots: sh.getPivotTables(), head: sh.head(2) };
            """;
        String out;
        try (var engine = withRoot.open(workspace)) {
            out = engine.eval(js, 60_000, null);
        }
        assertTrue(out.contains("\"name\":\"透视1\""), out);
        assertTrue(out.contains("\"rowFields\":[\"区域\"]"), out);
        assertTrue(out.contains("\"colFields\":[\"季度\"]"), out);
        assertTrue(out.contains("\"pageFields\":[\"产品\"]"), out);
        assertTrue(out.contains("\"field\":\"销售额\""), out);
        assertTrue(out.contains("求和项:销售额"), out);
        assertTrue(out.contains("\"dataCaption\":\"值\""), out);
        assertTrue(out.contains("\"fields\":[\"区域\",\"产品\",\"季度\",\"销售额\"]"), out);
    }

    /** 注入最小 pivotTable + pivotCacheDefinition 与 rels（解析目标，无需 Excel 校验）。 */
    private static void injectPivot(Path src, Path dest) throws Exception {
        Map<String, byte[]> parts = readZip(src);
        String ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

        parts.put(
            "xl/pivotTables/pivotTable1.xml",
            ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<pivotTableDefinition xmlns=\"" + ns + "\" name=\"透视1\" dataCaption=\"值\">"
                + "<rowFields count=\"1\"><field x=\"0\"/></rowFields>"
                + "<colFields count=\"1\"><field x=\"2\"/></colFields>"
                + "<pageFields count=\"1\"><pageField fld=\"1\" hier=\"-1\"/></pageFields>"
                + "<dataFields count=\"1\"><dataField name=\"求和项:销售额\" fld=\"3\" baseField=\"0\" baseItem=\"0\"/>"
                + "</dataFields></pivotTableDefinition>").getBytes(StandardCharsets.UTF_8)
        );
        parts.put(
            "xl/pivotTables/_rels/pivotTable1.xml.rels",
            ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/pivotCacheDefinition\" Target=\"../pivotCache/pivotCacheDefinition1.xml\"/>"
                + "</Relationships>").getBytes(StandardCharsets.UTF_8)
        );
        parts.put(
            "xl/pivotCache/pivotCacheDefinition1.xml",
            ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<pivotCacheDefinition xmlns=\"" + ns + "\" cacheId=\"1\" refreshOnLoad=\"1\">"
                + "<cacheSource type=\"worksheet\"><worksheetSource ref=\"A1:D2\" sheet=\"数据\"/></cacheSource>"
                + "<cacheFields count=\"4\">"
                + "<cacheField name=\"区域\" numFmtId=\"0\"><sharedItems count=\"1\"><s v=\"华北\"/></sharedItems></cacheField>"
                + "<cacheField name=\"产品\" numFmtId=\"0\"><sharedItems count=\"1\"><s v=\"A\"/></sharedItems></cacheField>"
                + "<cacheField name=\"季度\" numFmtId=\"0\"><sharedItems count=\"1\"><s v=\"Q1\"/></sharedItems></cacheField>"
                + "<cacheField name=\"销售额\" numFmtId=\"0\"><sharedItems containsSemiMixedTypes=\"0\" containsString=\"0\" containsNumber=\"1\" containsInteger=\"1\" minValue=\"100\" maxValue=\"100\"/></cacheField>"
                + "</cacheFields></pivotCacheDefinition>").getBytes(StandardCharsets.UTF_8)
        );
        parts.put(
            "xl/worksheets/_rels/sheet1.xml.rels",
            ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/pivotTable\" Target=\"../pivotTables/pivotTable1.xml\"/>"
                + "</Relationships>").getBytes(StandardCharsets.UTF_8)
        );

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(dest))) {
            for (Map.Entry<String, byte[]> e : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
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
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (var in = Files.newInputStream(zipFile); ZipInputStream zis = new ZipInputStream(in)) {
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
