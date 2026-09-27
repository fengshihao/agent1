package com.agent1.javaagent.catalog.sync;

import com.agent1.javaagent.catalog.CatalogItem;
import java.nio.file.Path;

/** 条目落盘路径（相对 agentRoot，对齐 08）。 */
public final class CatalogInstallPaths {

    private CatalogInstallPaths() {
    }

    public static Path catalogFile(Path agentRoot, CatalogItem item) {
        Path catalogRoot = agentRoot.resolve("shared/catalog").normalize();
        String relative = item.path().replace('\\', '/');
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return catalogRoot.resolve(relative).normalize();
    }

    public static String relativeFromAgentRoot(Path agentRoot, Path absoluteFile) {
        Path root = agentRoot.toAbsolutePath().normalize();
        Path file = absoluteFile.toAbsolutePath().normalize();
        return root.relativize(file).toString().replace('\\', '/');
    }
}
