package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.agent1.javaagent.agent.AgentHomeBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 云端-only {@code weizhi:xlsx-raw}：unpack → fs 改 XML → validate → pack。 */
class XlsxRawOfficeIntegrationTest {

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
    void unpackEditValidatePack(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path home = WeizhiBundledPackages.weizhiHome(agentRoot);
        Files.createDirectories(home);
        WeizhiSourcePackages.installFromSource(home, "xlsx");
        WeizhiSourcePackages.installFromSource(home, "xlsx-build");
        WeizhiSourcePackages.installFromSource(home, "xlsx-raw");

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot, home);
        String js =
            """
            import { unpackXlsx, packXlsx, validateXlsx } from "weizhi:xlsx-raw";
            import { readXlsx } from "weizhi:xlsx";
            import { buildXlsx } from "weizhi:xlsx-build";

            buildXlsx("in.xlsx", (b) => {
              b.sheet("数据").headers(["名称", "数量"]).rows([["甲", 1], ["乙", 2]]);
            });

            const unpacked = unpackXlsx("in.xlsx", { workDir: "tmp/raw" });
            const before = validateXlsx({ dir: unpacked.dir });
            if (!before.ok) throw new Error("validate before failed: " + JSON.stringify(before.errors));

            // 直接改 sharedStrings：把「甲」替换为「丙」
            const stringsPath = unpacked.dir + "/xl/sharedStrings.xml";
            let xml = require("fs").readFileSync(stringsPath).toString();
            xml = xml.replace("甲", "丙");
            require("fs").writeFileSync(stringsPath, xml);

            const after = validateXlsx({ dir: unpacked.dir });
            const packed = packXlsx(unpacked.dir, "out/raw-edited.xlsx");
            const sh = readXlsx("out/raw-edited.xlsx").sheet("数据");
            export default {
              validateOk: after.ok,
              packedOk: packed.ok,
              a2: sh.get("A2").value,
            };
            """;
        String out;
        try (var engine = withRoot.open(workspace)) {
            out = engine.eval(js, 60_000, null);
        }
        assertTrue(out.contains("\"validateOk\":true"), out);
        assertTrue(out.contains("\"packedOk\":true"), out);
        assertTrue(out.contains("丙"), out);
        assertFalse(out.contains("甲"), out);
        assertTrue(Files.size(workspace.resolve("out/raw-edited.xlsx")) > 100L);
    }

    @Test
    void validateRejectsMalformedXml(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path home = WeizhiBundledPackages.weizhiHome(agentRoot);
        Files.createDirectories(home);
        WeizhiSourcePackages.installFromSource(home, "xlsx");
        WeizhiSourcePackages.installFromSource(home, "xlsx-build");
        WeizhiSourcePackages.installFromSource(home, "xlsx-raw");

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot, home);
        String js =
            """
            import { unpackXlsx, validateXlsx } from "weizhi:xlsx-raw";
            import { buildXlsx } from "weizhi:xlsx-build";
            buildXlsx("in2.xlsx", (b) => b.sheet("S").headers(["a"]).rows([["x"]]));
            const unpacked = unpackXlsx("in2.xlsx", { workDir: "tmp/raw2" });
            const p = unpacked.dir + "/xl/worksheets/sheet1.xml";
            require("fs").writeFileSync(p, "<worksheet><sheetData>unclosed");
            const v = validateXlsx({ dir: unpacked.dir });
            export default { ok: v.ok, codes: v.errors.map(function (e) { return e.code; }) };
            """;
        String out;
        try (var engine = withRoot.open(workspace)) {
            out = engine.eval(js, 60_000, null);
        }
        assertFalse(out.contains("\"ok\":true"), out);
        assertTrue(out.contains("xml_malformed"), out);
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
}
