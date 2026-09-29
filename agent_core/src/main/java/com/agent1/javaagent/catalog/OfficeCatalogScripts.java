package com.agent1.javaagent.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** 将 Weizhi {@code assets/office/docx*.js} 同步到 {@code shared/catalog/scripts}（Issue weizhi#8）。 */
public final class OfficeCatalogScripts {

    public static final List<String> OFFICE_SCRIPT_NAMES = List.of("docx.js", "docx-raw.js", "docx-build.js");

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
        Path weizhiOffice = resolveWeizhiOfficeDir();
        for (String name : OFFICE_SCRIPT_NAMES) {
            Path target = scripts.resolve(name);
            if (isNonEmptyFile(target)) {
                continue;
            }
            if (weizhiOffice != null) {
                Path src = weizhiOffice.resolve(name);
                if (Files.isRegularFile(src)) {
                    copyFile(src, target);
                    continue;
                }
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

    private static Path resolveWeizhiOfficeDir() {
        String repo = System.getenv("AGENT1_WEIZHI_REPO");
        if (repo != null && !repo.isBlank()) {
            Path office = Path.of(repo.trim()).resolve("assets").resolve("office");
            if (Files.isDirectory(office)) {
                return office.toAbsolutePath().normalize();
            }
        }
        return null;
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

    private static void copyFile(Path src, Path target) {
        try {
            Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("copy " + src + " -> " + target, e);
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
