package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.catalog.OfficeCatalogScripts;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** weizhi#8：Agent1 bootstrap + docx.js + markdownToDocx smoke（对齐 weizhi OfficeTest）。 */
class DocxOfficeIntegrationTest {

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
    void markdownToDocxViaDocxJs(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        assumeTrue(OfficeCatalogScripts.isOfficeReady(agentRoot), "docx.js not installed");

        Path md = workspace.resolve("notes/brief.md");
        Files.createDirectories(md.getParent());
        Files.writeString(md, "# Hello\n\n- item one\n");

        WeizhiScriptEngineFactory withRoot = new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions().installDesktopCaps(true),
            agentRoot
        );
        String js =
            """
            import { markdownToDocx } from './docx.js';
            export default markdownToDocx({
              inputPath: 'notes/brief.md',
              outputPath: 'out/brief.docx',
              title: 'Brief'
            });
            """;
        try (var engine = withRoot.open(workspace)) {
            String out = engine.eval(js, 30_000, null);
            assertTrue(out.contains("\"ok\":true"), out);
            assertTrue(out.contains("out/brief.docx"), out);
        }
        assertTrue(Files.isRegularFile(workspace.resolve("out/brief.docx")));
        assertTrue(Files.size(workspace.resolve("out/brief.docx")) > 100L);
    }
}
