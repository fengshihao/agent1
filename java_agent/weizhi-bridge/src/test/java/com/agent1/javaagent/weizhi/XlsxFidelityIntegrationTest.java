package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import java.io.InputStream;
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
 * 保真改写（xlsx@1.2.0）：readXlsx → 编辑 → save 只替换被改 sheet 的
 * sheetData/mergeCells/dimension，其余 parts 与未编辑 cell 的原 XML 逐字节保留。
 */
class XlsxFidelityIntegrationTest {

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
    void preserveStylesColsAndForeignParts(@TempDir Path agentRoot, @TempDir Path workspace)
        throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path home = WeizhiBundledPackages.weizhiHome(agentRoot);
        Files.createDirectories(home);
        WeizhiSourcePackages.installCloudOnlyFromSource(home);

        // 1) 基础 xlsx
        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot, home);
        String build =
            """
            import { buildXlsx } from "weizhi:xlsx-build";
            export default buildXlsx("base.xlsx", (b) => {
              b.sheet("汇总")
                .headers(["项目", "金额"])
                .rows([["差旅", 1000], ["餐饮", 200]])
                .formula("C2", "B2*1.13", 1130);
            });
            """;
        try (var engine = withRoot.open(workspace)) {
            assertTrue(engine.eval(build, 60_000, null).contains("\"ok\":true"));
        }

        // 2) 注入样式/列宽/外部 part：A1 加 s="7"；cols；theme1.xml；Content_Types Override
        Path base = workspace.resolve("base.xlsx");
        Path styled = workspace.resolve("styled.xlsx");
        injectStyleArtifacts(base, styled);

        // 3) JS：读入 → 编辑 → 保真 save
        String js =
            """
            import { readXlsx } from "weizhi:xlsx";
            const wb = readXlsx("styled.xlsx");
            const sh = wb.sheet("汇总");
            sh.set("B3", 999);
            sh.setFormula("D3", "B3*2", 1998);
            sh.merge("E5:F6");
            export default { saved: wb.save("out/styled-edited.xlsx").ok };
            """;
        String out;
        try (var engine = withRoot.open(workspace)) {
            out = engine.eval(js, 60_000, null);
        }
        assertTrue(out.contains("\"saved\":true"), out);

        Path edited = workspace.resolve("out/styled-edited.xlsx");
        Map<String, byte[]> editedParts = readZip(edited);
        Map<String, byte[]> styledParts = readZip(styled);

        // 3a. 未编辑 cell 的原 XML 逐字节保留（含样式索引 s="7"、公式缓存）
        String sheet1 = new String(editedParts.get("xl/worksheets/sheet1.xml"), StandardCharsets.UTF_8);
        assertTrue(sheet1.contains("s=\"7\""), "A1 style index lost: " + sheet1);
        assertTrue(sheet1.contains("B2*1.13"), "untouched formula lost: " + sheet1);
        // 3b. 列宽等 sheet 级结构保留
        assertTrue(
            sheet1.contains("<cols>") && sheet1.contains("customWidth=\"1\""),
            "cols lost: " + sheet1
        );
        // 3c. 编辑生效
        assertTrue(sheet1.contains("<v>999</v>"), sheet1);
        assertTrue(sheet1.contains("B3*2"), sheet1);
        assertTrue(sheet1.contains("<mergeCell ref=\"E5:F6\"/>"), sheet1);
        // 3d. 外部 part 逐字节保留
        byte[] themeIn = styledParts.get("xl/theme/theme1.xml");
        byte[] themeOut = editedParts.get("xl/theme/theme1.xml");
        assertTrue(themeIn != null && themeOut != null, "theme1.xml missing");
        assertTrue(java.util.Arrays.equals(themeIn, themeOut), "theme1.xml changed");
        // 3e. styles.xml 未被重写（仍是注入版）
        assertTrue(
            new String(editedParts.get("xl/styles.xml"), StandardCharsets.UTF_8)
                .contains("fontId=\"7\""),
            "styles.xml was rewritten"
        );
        // 3f. patch 不应生成 chart/drawing parts
        assertFalse(editedParts.containsKey("xl/charts/chart1.xml"));
        assertFalse(editedParts.containsKey("xl/drawings/drawing1.xml"));

        // 4) 无编辑的读-存循环：内容级与输入一致（幂等）
        String idempotent =
            """
            import { readXlsx } from "weizhi:xlsx";
            const wb = readXlsx("styled.xlsx");
            export default wb.save("out/styled-idem.xlsx").ok;
            """;
        try (var engine = withRoot.open(workspace)) {
            assertTrue(engine.eval(idempotent, 60_000, null).contains("true"));
        }
        Map<String, byte[]> idemParts = readZip(workspace.resolve("out/styled-idem.xlsx"));
        for (Map.Entry<String, byte[]> e : styledParts.entrySet()) {
            byte[] actual = idemParts.get(e.getKey());
            assertTrue(
                actual != null && java.util.Arrays.equals(e.getValue(), actual),
                "no-edit save changed part: " + e.getKey()
            );
        }
    }

    @Test
    void loadedWorkbookRejectsChartAndSheetSurgery(@TempDir Path agentRoot, @TempDir Path workspace)
        throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path home = WeizhiBundledPackages.weizhiHome(agentRoot);
        Files.createDirectories(home);
        WeizhiSourcePackages.installFromSource(home, "xlsx");
        WeizhiSourcePackages.installFromSource(home, "xlsx-build");

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot, home);
        String build =
            """
            import { buildXlsx } from "weizhi:xlsx-build";
            export default buildXlsx("base2.xlsx", (b) => {
              b.sheet("汇总").headers(["a"]).rows([["x"]]);
            });
            """;
        try (var engine = withRoot.open(workspace)) {
            assertTrue(engine.eval(build, 60_000, null).contains("\"ok\":true"));
        }

        String chartOnLoaded =
            """
            import { readXlsx } from "weizhi:xlsx";
            const wb = readXlsx("base2.xlsx");
            wb.sheet("汇总").addChart({ type: "bar",
              series: [{ name: "v", categories: "A1:A1", values: "A1:A1" }] });
            export default wb.save("out/should-fail.xlsx").ok;
            """;
        try (var engine = withRoot.open(workspace)) {
            String out = engine.eval(chartOnLoaded, 60_000, null);
            assertTrue(out.contains("addChart on a workbook loaded"), out);
        } catch (RuntimeException e) {
            String message = String.valueOf(e.getMessage());
            assertTrue(
                message.contains("addChart on a workbook loaded"),
                "unexpected error: " + message
            );
        }
        assertFalse(Files.exists(workspace.resolve("out/should-fail.xlsx")));
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

    /** 解包 → 给 A1 注入 s="7"、加 cols、补 theme1.xml 与 Content_Types Override → 重打包。 */
    private static void injectStyleArtifacts(Path src, Path dest) throws Exception {
        Map<String, byte[]> parts = readZip(src);
        String sheet = new String(parts.get("xl/worksheets/sheet1.xml"), StandardCharsets.UTF_8);
        sheet = sheet.replace(
            "<c r=\"A1\"",
            "<c r=\"A1\" s=\"7\""
        );
        sheet = sheet.replace(
            "<sheetData>",
            "<cols><col min=\"1\" max=\"1\" width=\"30\" customWidth=\"1\"/></cols><sheetData>"
        );
        parts.put("xl/worksheets/sheet1.xml", sheet.getBytes(StandardCharsets.UTF_8));

        parts.put(
            "xl/theme/theme1.xml",
            ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
                + " name=\"Test\"/>").getBytes(StandardCharsets.UTF_8)
        );
        String ct = new String(parts.get("[Content_Types].xml"), StandardCharsets.UTF_8);
        ct = ct.replace(
            "</Types>",
            "<Override PartName=\"/xl/theme/theme1.xml\""
                + " ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/></Types>"
        );
        parts.put("[Content_Types].xml", ct.getBytes(StandardCharsets.UTF_8));

        String styles = new String(parts.get("xl/styles.xml"), StandardCharsets.UTF_8);
        styles = styles.replace(
            "<cellXfs count=\"1\">",
            "<cellXfs count=\"2\">"
        );
        styles = styles.replace(
            "</cellXfs>",
            "<xf numFmtId=\"0\" fontId=\"7\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>"
        );
        parts.put("xl/styles.xml", styles.getBytes(StandardCharsets.UTF_8));

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(dest))) {
            for (Map.Entry<String, byte[]> e : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
    }

    private static Map<String, byte[]> readZip(Path zipFile) throws Exception {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                out.put(entry.getName(), zis.readAllBytes());
            }
        }
        return out;
    }
}
