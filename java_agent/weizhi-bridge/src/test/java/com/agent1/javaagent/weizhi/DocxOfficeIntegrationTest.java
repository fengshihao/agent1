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

/** Agent1 catalog 上的 docx.js：markdownToDocx 与标题层次。 */
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

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot);
        String js =
            """
            import { markdownToDocx } from "docx.js";
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

    @Test
    void markdownHeadingHierarchy(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        assumeTrue(OfficeCatalogScripts.isOfficeReady(agentRoot), "docx.js not installed");

        Path md = workspace.resolve("notes/headings.md");
        Files.createDirectories(md.getParent());
        Files.writeString(
            md,
            """
            # 一级标题

            正文段落 twelve pt。

            ## 二级标题

            另一段正文。
            """
        );

        WeizhiScriptEngineFactory withRoot = engineWithRoot(agentRoot);
        String js =
            """
            import { markdownToDocx, readDocx } from "docx.js";
            markdownToDocx({
              inputPath: 'notes/headings.md',
              outputPath: 'out/headings.docx',
              defaultStyle: { sizePt: 12 }
            });
            var doc = readDocx('out/headings.docx');
            var heads = doc.headings();
            var paras = doc.listBlocks({ type: 'paragraph' });
            export default {
              h1: doc.getBlockStyle(heads[0].index).effective.sizePt,
              h2: doc.getBlockStyle(heads[1].index).effective.sizePt,
              body: doc.getBlockStyle(paras[0].index).effective.sizePt
            };
            """;
        String out;
        try (var engine = withRoot.open(workspace)) {
            out = engine.eval(js, 30_000, null);
        }
        assertTrue(out.contains("\"h1\":22"), out);
        assertTrue(out.contains("\"h2\":16"), out);
        assertTrue(out.contains("\"body\":12"), out);

        Path docx = workspace.resolve("out/headings.docx");
        assertTrue(zipEntryContains(docx, "word/styles.xml", "Heading1"));
        assertTrue(zipEntryContains(docx, "word/document.xml", "w:sz w:val=\"44\""));
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
