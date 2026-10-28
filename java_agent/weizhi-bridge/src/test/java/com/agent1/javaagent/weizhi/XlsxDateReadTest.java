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
 * xlsx@1.4.0：日期/时间识别——styles.xml 的 numFmt（自定义 formatCode + 内置 ID）
 * 判定日期格，把序列数字还原成可读 "YYYY-MM-DD[ HH:mm:ss]"。
 */
class XlsxDateReadTest {

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
    void readsDatesFromNumberFormats(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path home = WeizhiBundledPackages.weizhiHome(agentRoot);
        Files.createDirectories(home);
        WeizhiSourcePackages.installFromSource(home, "xlsx");
        WeizhiSourcePackages.installFromSource(home, "xlsx-build");

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot, home);
        String build =
            """
            import { buildXlsx } from "weizhi:xlsx-build";
            export default buildXlsx("dates.xlsx", (b) => { b.sheet("S"); });
            """;
        try (var engine = withRoot.open(workspace)) {
            assertTrue(engine.eval(build, 60_000, null).contains("\"ok\":true"));
        }

        // 注入日期样式与数据：s=1 自定义 yyyy-mm-dd；s=2 内置 14（m/d/yy）；s=3 纯时间
        Path input = workspace.resolve("dates.xlsx");
        Path styled = workspace.resolve("dates-styled.xlsx");
        injectDates(input, styled);

        String js =
            """
            import { readXlsx } from "weizhi:xlsx";
            const sh = readXlsx("dates-styled.xlsx").sheet("S");
            const plain = readXlsx("dates-styled.xlsx", { dates: false }).sheet("S");
            export default {
              a1: sh.get("A1"),
              b1: sh.get("B1"),
              c1: sh.get("C1"),
              d1: sh.get("D1"),
              plainA1: plain.get("A1").value,
              view: sh.textView({ maxRows: 2 }),
            };
            """;
        String out;
        try (var engine = withRoot.open(workspace)) {
            out = engine.eval(js, 60_000, null);
        }
        // 2026-10-27 序列值；+14:30；纯时间 06:00:00；普通数字不受影响
        assertTrue(out.contains("\"type\":\"date\""), out);
        assertTrue(out.contains("2026-10-27 14:30:00"), out);
        assertTrue(out.contains("06:00:00"), out);
        assertTrue(out.contains("\"value\":123") || out.contains("\"value\":123,"), out);
        // dates:false 时保持原始序列数字
        long serial20261027 = LocalDate2026_10_27_serial();
        assertTrue(out.contains("\"plainA1\":" + serial20261027), out);

        // 日期在 textView 里可读
        assertTrue(out.contains("2026-10-27"), out);
    }

    private static long LocalDate2026_10_27_serial() {
        long days = java.time.LocalDate.of(2026, 10, 27).toEpochDay();
        // Excel 序列：1970-01-01 == 25569
        return days + 25569;
    }

    /** 解包 → 注入日期样式 + 数据行 → 重打包。 */
    private static void injectDates(Path src, Path dest) throws Exception {
        Map<String, byte[]> parts = readZip(src);

        // styles.xml：自定义 numFmts + 扩展 cellXfs
        String styles = new String(parts.get("xl/styles.xml"), StandardCharsets.UTF_8);
        styles = styles.replace(
            "<styleSheet xmlns=\"" + "http://schemas.openxmlformats.org/spreadsheetml/2006/main" + "\">",
            "<styleSheet xmlns=\"" + "http://schemas.openxmlformats.org/spreadsheetml/2006/main" + "\">"
                + "<numFmts count=\"1\">"
                + "<numFmt numFmtId=\"164\" formatCode=\"yyyy\\-mm\\-dd;@\"/>"
                + "<numFmt numFmtId=\"165\" formatCode=\"hh:mm:ss\"/>"
                + "</numFmts>"
        );
        styles = styles.replace(
            "<cellXfs count=\"1\">",
            "<cellXfs count=\"4\">"
        );
        styles = styles.replace(
            "</cellXfs>",
            "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                + "<xf numFmtId=\"14\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                + "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                + "</cellXfs>"
        );
        parts.put("xl/styles.xml", styles.getBytes(StandardCharsets.UTF_8));

        double serial = LocalDate2026_10_27_serial();
        String row =
            "<row r=\"1\">"
                + "<c r=\"A1\" s=\"1\"><v>" + serial + "</v></c>"
                + "<c r=\"B1\" s=\"1\"><v>" + (serial + 52200.0 / 86400.0) + "</v></c>"
                + "<c r=\"C1\" s=\"3\"><v>0.25</v></c>"
                + "<c r=\"D1\"><v>123</v></c>"
                + "</row>";
        String sheet = new String(parts.get("xl/worksheets/sheet1.xml"), StandardCharsets.UTF_8);
        if (sheet.contains("<sheetData/>")) {
            sheet = sheet.replace("<sheetData/>", "<sheetData>" + row + "</sheetData>");
        } else {
            sheet = sheet.replace("<sheetData>", "<sheetData>" + row);
        }
        parts.put("xl/worksheets/sheet1.xml", sheet.getBytes(StandardCharsets.UTF_8));

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
