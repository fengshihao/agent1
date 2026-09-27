package com.agent1.javaagent.catalog.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.catalog.CatalogDigest;
import com.agent1.javaagent.catalog.CatalogPlatform;
import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import okio.Buffer;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * UC-07（无 COS）：Mock HTTP 同步 native 条目 → {@code shared/catalog/native/<platform>/}，
 * 供 Weizhi {@code nativePluginDir} 加载。
 */
class CatalogSyncServiceNativeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void uc07SyncNativeEchoMathFiles(@TempDir Path agentRoot) throws Exception {
        Path weizhiPlugin = resolveWeizhiEchoMathDir();
        assumeTrue(weizhiPlugin != null, "需要 AGENT1_WEIZHI_REPO 下已 build 的 echo_math 插件");

        byte[] manifestBytes = Files.readAllBytes(weizhiPlugin.resolve("manifest.json"));
        String soName = soFileName();
        byte[] soBytes = Files.readAllBytes(weizhiPlugin.resolve(soName));
        String digestManifest = CatalogDigest.sha256Prefix(manifestBytes);
        String digestSo = CatalogDigest.sha256Prefix(soBytes);
        String platform = CatalogPlatform.currentLabel();

        AgentHomeBootstrap.ensure(agentRoot);
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            String base = server.url("catalog/").toString();
            String manifestUrl = server.url("/catalog-index.json").toString();
            String manifest = """
                {
                  "schemaVersion": 1,
                  "catalogId": "uc07-native",
                  "baseUrl": "%s",
                  "items": [
                    {
                      "id": "native.echo_math.manifest",
                      "kind": "native",
                      "version": "1",
                      "platform": "%s",
                      "digest": "%s",
                      "path": "native/%s/echo_math/manifest.json"
                    },
                    {
                      "id": "native.echo_math.so",
                      "kind": "native",
                      "version": "1",
                      "platform": "%s",
                      "digest": "%s",
                      "path": "native/%s/echo_math/%s"
                    }
                  ]
                }
                """.formatted(
                base,
                platform, digestManifest, platform,
                platform, digestSo, platform, soName
            );

            configureManifestUrl(agentRoot, manifestUrl);
            server.enqueue(new MockResponse().setBody(manifest));
            server.enqueue(new MockResponse().setBody(manifest));
            server.enqueue(new MockResponse().setBody(new Buffer().write(manifestBytes)));
            server.enqueue(new MockResponse().setBody(new Buffer().write(soBytes)));

            CatalogSyncService service = new CatalogSyncService(agentRoot, new OkHttpClient());
            assertEquals(2, service.check().pending().size());

            server.enqueue(new MockResponse().setBody(manifest));
            server.enqueue(new MockResponse().setBody(new Buffer().write(manifestBytes)));
            server.enqueue(new MockResponse().setBody(new Buffer().write(soBytes)));
            var apply = service.apply(java.util.List.of(
                "native.echo_math.manifest",
                "native.echo_math.so"
            ));
            assertEquals(2, apply.appliedIds().size());

            Path nativeRoot = agentRoot.resolve("shared/catalog/native").resolve(platform);
            assertTrue(Files.isRegularFile(nativeRoot.resolve("echo_math/manifest.json")));
            assertTrue(Files.isRegularFile(nativeRoot.resolve("echo_math/" + soName)));
        }
    }

    private static Path resolveWeizhiEchoMathDir() throws Exception {
        String env = System.getenv("AGENT1_WEIZHI_REPO");
        Path repo = env != null && !env.isBlank()
            ? Path.of(env.trim())
            : Path.of(System.getProperty("user.dir")).resolve("../weizhi").normalize();
        Path dir = repo.resolve("build/plugins/echo_math");
        if (!Files.isDirectory(dir)) {
            return null;
        }
        if (!Files.isRegularFile(dir.resolve("manifest.json")) || !Files.isRegularFile(dir.resolve(soFileName()))) {
            return null;
        }
        return dir;
    }

    private static String soFileName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return "libecho_math.dylib";
        }
        if (os.contains("win")) {
            return "echo_math.dll";
        }
        return "libecho_math.so";
    }

    private static void configureManifestUrl(Path agentRoot, String manifestUrl) throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree(PathIo.readString(agentRoot.resolve("agent.manifest.json")));
        ObjectNode catalog = MAPPER.createObjectNode();
        catalog.put("manifestUrl", manifestUrl);
        root.set("catalog", catalog);
        PathIo.writeString(
            agentRoot.resolve("agent.manifest.json"),
            MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root)
        );
    }
}
