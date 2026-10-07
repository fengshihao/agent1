package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.catalog.OfficeCatalogScripts;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        assumeTrue(OfficeCatalogScripts.isOfficeReady(agentRoot), "office catalog scripts not installed");

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot);
        String js =
            """
            import { renderPptx } from "pptx.js";
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
        assumeTrue(OfficeCatalogScripts.isOfficeReady(agentRoot), "office catalog scripts not installed");

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot);
        String js =
            """
            import { buildPptx } from "pptx-build.js";
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

    private static WeizhiScriptEngineFactory engineWithRoot(Path agentRoot) {
        return new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions().installDesktopCaps(true),
            agentRoot
        );
    }

    private static boolean zipEntryContains(Path zipFile, String entryName, String needle) throws Exception {
        try (InputStream in = Files.newInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.getName().equals(entryName)) {
                    continue;
                }
                byte[] data = zis.readAllBytes();
                String text = new String(data, StandardCharsets.UTF_8);
                return text.contains(needle);
            }
        }
        return false;
    }
}
