package com.agent1.javaagent.weizhi.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** weizhi-web 资产解析回退链：weizhi 旧布局 → agent1 同级仓库 → 向上查找。 */
class DesktopBootstrapAssetsTest {

    @Test
    void prefersLegacyLayoutInsideWeizhiRepo(@TempDir Path dir) throws Exception {
        Path weizhiRepo = dir.resolve("weizhi");
        writeAssets(weizhiRepo.resolve(DesktopBootstrapAssets.LEGACY_ASSET_DIR));
        Path resolved = DesktopBootstrapAssets.resolveAssetRoot(weizhiRepo, dir.resolve("nowhere"));
        assertEquals(weizhiRepo.resolve(DesktopBootstrapAssets.LEGACY_ASSET_DIR), resolved);
    }

    @Test
    void fallsBackToSiblingAgent1Repo(@TempDir Path dir) throws Exception {
        Path weizhiRepo = dir.resolve("weizhi");
        Files.createDirectories(weizhiRepo);
        writeAssets(dir.resolve("agent1").resolve(DesktopBootstrapAssets.AGENT1_ASSET_DIR));
        Path resolved = DesktopBootstrapAssets.resolveAssetRoot(weizhiRepo, dir.resolve("nowhere"));
        assertEquals(
            dir.resolve("agent1").resolve(DesktopBootstrapAssets.AGENT1_ASSET_DIR).normalize(),
            resolved
        );
    }

    @Test
    void walksUpFromUserDirToFindAgent1Repo(@TempDir Path dir) throws Exception {
        Path weizhiElsewhere = dir.resolve("checkouts/weizhi");
        Files.createDirectories(weizhiElsewhere);
        writeAssets(dir.resolve("agent1").resolve(DesktopBootstrapAssets.AGENT1_ASSET_DIR));
        // 工作目录在 agent1 仓库内的深层目录，weizhi 仓库在别处且无资产
        Path userDir = dir.resolve("agent1/java_agent/weizhi-bridge");
        Path resolved = DesktopBootstrapAssets.resolveAssetRoot(weizhiElsewhere, userDir);
        assertEquals(
            dir.resolve("agent1").resolve(DesktopBootstrapAssets.AGENT1_ASSET_DIR).normalize(),
            resolved
        );
    }

    @Test
    void loadHtmlDocumentInjectsBridgeShim(@TempDir Path dir) throws Exception {
        Path weizhiRepo = dir.resolve("weizhi");
        writeAssets(weizhiRepo.resolve(DesktopBootstrapAssets.LEGACY_ASSET_DIR));
        String html = new DesktopBootstrapAssets(weizhiRepo).loadHtmlDocument();
        assertTrue(html.contains("window.NativeBridge"), "应注入 CDP NativeBridge 垫片");
        assertTrue(html.contains("bridge-body"), "应内联 bridge.js 正文");
    }

    @Test
    void throwsWhenAssetsMissingEverywhere(@TempDir Path dir) throws Exception {
        Path weizhiRepo = dir.resolve("weizhi");
        Files.createDirectories(weizhiRepo);
        IOException e = assertThrows(
            IOException.class,
            () -> DesktopBootstrapAssets.resolveAssetRoot(weizhiRepo, dir.resolve("nowhere"))
        );
        assertTrue(e.getMessage().contains("未找到 weizhi-web 资产"));
    }

    private static void writeAssets(Path assetRoot) throws IOException {
        Files.createDirectories(assetRoot);
        Files.writeString(assetRoot.resolve("bridge.js"), "/* bridge-body */");
        Files.writeString(
            assetRoot.resolve("bootstrap.html"),
            "<html><script>/*__BRIDGE_JS__*/</script></html>"
        );
    }
}
