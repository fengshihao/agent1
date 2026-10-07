package com.agent1.javaagent.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 把 Agent1 自带的 Office 脚本装进 {@code shared/catalog/scripts}。
 * 真源是 classpath {@code agent-home/catalog/scripts}；Android 另有一份
 * {@code assets/office}，由 {@code AndroidOfficeCatalogSync} 写入同一目录。
 * Weizhi 引擎不附带这些脚本。
 */
public final class OfficeCatalogScripts {

    public static final List<String> OFFICE_SCRIPT_NAMES =
            List.of("docx.js", "docx-raw.js", "docx-build.js", "pptx.js", "pptx-build.js");

    private OfficeCatalogScripts() {
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
        for (String name : OFFICE_SCRIPT_NAMES) {
            Path target = scripts.resolve(name);
            if (isNonEmptyFile(target)) {
                continue;
            }
            copyFromClasspath(name, target);
        }
    }

    public static boolean isOfficeReady(Path agentRoot) {
        Path scripts = AgentCatalogPaths.catalogScriptsDir(agentRoot);
        for (String name : OFFICE_SCRIPT_NAMES) {
            if (!isNonEmptyFile(scripts.resolve(name))) {
                return false;
            }
        }
        return true;
    }

    private static void copyFromClasspath(String name, Path target) {
        String resource = "/agent-home/catalog/scripts/" + name;
        URL url = OfficeCatalogScripts.class.getResource(resource);
        if (url == null) {
            return;
        }
        try (InputStream in = url.openStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("copy office script failed: " + target, e);
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
