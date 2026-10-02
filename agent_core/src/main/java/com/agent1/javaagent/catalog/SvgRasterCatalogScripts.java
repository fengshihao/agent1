package com.agent1.javaagent.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 将 {@code svg-raster.js} 同步到 {@code shared/catalog/scripts}，供 execute_script import。 */
public final class SvgRasterCatalogScripts {

    public static final String SCRIPT_NAME = "svg-raster.js";

    private SvgRasterCatalogScripts() {
    }

    public static void ensure(Path agentRoot) {
        if (agentRoot == null) {
            return;
        }
        Path scripts = AgentCatalogPaths.catalogScriptsDir(agentRoot);
        try {
            Files.createDirectories(scripts);
        } catch (IOException e) {
            throw new IllegalStateException("create catalog scripts dir failed: " + scripts, e);
        }
        Path target = scripts.resolve(SCRIPT_NAME);
        if (isNonEmptyFile(target)) {
            return;
        }
        copyFromClasspath(target);
    }

    public static boolean isReady(Path agentRoot) {
        if (agentRoot == null) {
            return false;
        }
        return isNonEmptyFile(AgentCatalogPaths.catalogScriptsDir(agentRoot).resolve(SCRIPT_NAME));
    }

    private static void copyFromClasspath(Path target) {
        String resource = "/agent-home/catalog/scripts/" + SCRIPT_NAME;
        URL url = SvgRasterCatalogScripts.class.getResource(resource);
        if (url == null) {
            return;
        }
        try (InputStream in = url.openStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("copy svg raster script failed: " + target, e);
        }
    }

    private static boolean isNonEmptyFile(Path path) {
        try {
            return Files.isRegularFile(path) && Files.size(path) > 0L;
        } catch (IOException e) {
            return false;
        }
    }
}
