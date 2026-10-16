package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.weizhi.packages.PackageRegistryClient;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 不拷贝内置包，从 agentone.guoyoutech.cn 安装 docx / pptx 后再执行。 */
class OfficeRegistryInstallTest {

    @BeforeAll
    static void requireWeizhiAndRegistry() throws Exception {
        Path repo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (repo.getNameCount() == 0 || !Files.isDirectory(repo)) {
            repo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        assumeTrue(WeizhiJniBootstrap.tryLoad(repo), "libweizhijni not built");
        assumeTrue(registryReady(), "package registry unreachable");
    }

    @Test
    void installsDocxAndPptxFromRegistry(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path home = agentRoot.resolve("weizhi-home");
        Files.createDirectories(home);
        WeizhiScriptEngineFactory factory = new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions()
                .installDesktopCaps(true)
                .packageRoot(home.toString())
                .mcpAgentRoot(agentRoot),
            null
        );
        Path md = workspace.resolve("notes/brief.md");
        Files.createDirectories(md.getParent());
        Files.writeString(md, "# Hello\n\n- item one\n");
        String js = """
            import { markdownToDocx } from "weizhi:docx";
            import { renderPptx } from "weizhi:pptx";
            markdownToDocx({ inputPath: 'notes/brief.md', outputPath: 'out/brief.docx', title: 'Brief' });
            renderPptx({
              title: '季度复盘',
              slides: [{ layout: 'title', title: '季度复盘', subtitle: '云端' }]
            }, 'out/q3.pptx');
            export default 'ok';
            """;
        try (var engine = factory.open(workspace)) {
            String out = engine.eval(js, 180_000, null);
            assertTrue(out.contains("ok"), out);
        }
        assertTrue(Files.size(workspace.resolve("out/brief.docx")) > 100L);
        assertTrue(Files.size(workspace.resolve("out/q3.pptx")) > 100L);
        assertTrue(Files.isRegularFile(home.resolve("packages/docx@1.0.0/manifest.json")));
        assertTrue(Files.isRegularFile(home.resolve("packages/pptx@1.0.0/manifest.json")));
        var hits = CapabilityIndexStore.search(agentRoot, "Markdown 转 Word", List.of(), "any", 5);
        assertTrue(hits.stream().anyMatch(hit -> "docx.markdownToDocx".equals(hit.id())));
    }

    private static boolean registryReady() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(PackageRegistryClient.DEFAULT_REGISTRY_URL)
                .toURL()
                .openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            connection.setRequestMethod("GET");
            return connection.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
