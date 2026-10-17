package com.agent1.javaagent.catalog;

import java.nio.file.Files;
import java.nio.file.Path;

/** agentRoot 下 catalog 运行时路径（7.3 native 插件目录等）。 */
public final class AgentCatalogPaths {

    private AgentCatalogPaths() {
    }

    public static Path nativePluginsDir(Path agentRoot) {
        Path root = agentRoot.toAbsolutePath().normalize();
        return root.resolve("shared/catalog/native").resolve(CatalogPlatform.currentLabel());
    }

    public static Path resolveExistingNativePluginsDir(Path agentRoot) {
        Path platformDir = nativePluginsDir(agentRoot);
        if (Files.isDirectory(platformDir)) {
            return platformDir;
        }
        Path generic = agentRoot.resolve("shared/catalog/native");
        return Files.isDirectory(generic) ? generic : null;
    }
}
