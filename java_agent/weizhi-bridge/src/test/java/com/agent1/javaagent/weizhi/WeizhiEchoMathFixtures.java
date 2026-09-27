package com.agent1.javaagent.weizhi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** UC-07：从已构建的 Weizhi {@code echo_math} 插件目录取样（无需 COS）。 */
final class WeizhiEchoMathFixtures {

    private WeizhiEchoMathFixtures() {
    }

    static Optional<Path> pluginDir() {
        Path repo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (repo.getNameCount() == 0 || !Files.isDirectory(repo)) {
            repo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        Path dir = repo.resolve("build/plugins/echo_math").normalize();
        if (!Files.isRegularFile(dir.resolve("manifest.json"))) {
            return Optional.empty();
        }
        String soName = soFileName();
        if (!Files.isRegularFile(dir.resolve(soName))) {
            return Optional.empty();
        }
        return Optional.of(dir);
    }

    static String soFileName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return "libecho_math.dylib";
        }
        if (os.contains("win")) {
            return "echo_math.dll";
        }
        return "libecho_math.so";
    }
}
