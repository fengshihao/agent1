package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.catalog.AgentCatalogPaths;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.ScriptEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** UC-07：catalog native 目录 + 真实插件加载（非 enableNativeMock）。 */
class WeizhiNativeCatalogIntegrationTest {

    @BeforeAll
    static void requireNative() {
        Path repo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (repo.getNameCount() == 0 || !Files.isDirectory(repo)) {
            repo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        assumeTrue(WeizhiJniBootstrap.tryLoad(repo), "libweizhijni not built");
        assumeTrue(WeizhiEchoMathFixtures.pluginDir().isPresent(), "echo_math plugin not built");
    }

    @Test
    void uc07EnsureNativeFromCatalogNativeDir(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        Path pluginSrc = WeizhiEchoMathFixtures.pluginDir().orElseThrow();
        Path platformDir = AgentCatalogPaths.nativePluginsDir(agentRoot);
        Path dest = platformDir.resolve("echo_math");
        Files.createDirectories(dest);
        Files.copy(pluginSrc.resolve("manifest.json"), dest.resolve("manifest.json"), StandardCopyOption.REPLACE_EXISTING);
        String soName = WeizhiEchoMathFixtures.soFileName();
        Files.copy(pluginSrc.resolve(soName), dest.resolve(soName), StandardCopyOption.REPLACE_EXISTING);

        assertTrue(AgentCatalogPaths.resolveExistingNativePluginsDir(agentRoot) != null);

        WeizhiScriptEngineFactory factory = new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions()
                .nativePluginDir(platformDir.toString())
                .installDesktopCaps(false)
        );
        try (ScriptEngine engine = factory.open(workspace)) {
            String out = engine.eval(
                "const p = await host.ensureNative('echo_math'); p.add(20, 22)",
                15_000,
                new CancellationToken()
            );
            assertEquals("42", out);
        }
    }
}
