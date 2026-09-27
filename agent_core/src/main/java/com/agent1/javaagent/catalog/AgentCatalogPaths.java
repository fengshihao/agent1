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

    /** QuickJS {@code loadScript} / {@code import './leaf.js'} 根（7.2，kind: script）。 */
    public static Path catalogScriptsDir(Path agentRoot) {
        return agentRoot.toAbsolutePath().normalize().resolve("shared/catalog/scripts");
    }

    /** kind: js_lib 落盘目录（08）；运行时通过 sync 镜像到 {@link #catalogScriptsDir} 供 loadScript。 */
    public static Path catalogJsLibsDir(Path agentRoot) {
        return agentRoot.toAbsolutePath().normalize().resolve("shared/catalog/libs/js");
    }

    public static Path resolveCatalogScriptFolder(Path agentRoot) {
        Path scripts = catalogScriptsDir(agentRoot);
        return Files.isDirectory(scripts) ? scripts : null;
    }
}
