package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.skill.SkillsBundledInstall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weizhi.packages.PackageInstaller;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 将 classpath {@code agent-home/weizhi-packages/<name>/} 安装到
 * {@code <agentRoot>/weizhi-home}，供 {@link com.weizhi.WeizhiEngine#setPackageRoot} 使用。
 * 源码目录无版本号；版本只读各包 {@code manifest.json}，安装后目录为 {@code <name>@<version>}。
 * 官方 Skill 与包分离，由 {@link SkillsBundledInstall} 写入 {@code skills-home}。
 */
public final class WeizhiBundledPackages {

    public static final List<String> BUNDLED_PACKAGE_NAMES =
        List.of(
            "docx",
            "docx-raw",
            "docx-build",
            "pptx",
            "pptx-build",
            "svg-raster",
            "html2pptx",
            "image",
            "audio");

    private static final String RESOURCE_ROOT = "agent-home/weizhi-packages/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 启动后台线程与 engine open 可能并发安装同一批包，串行化避免互相删目录。 */
    private static final Object INSTALL_LOCK = new Object();

    private WeizhiBundledPackages() {
    }

    public static Path weizhiHome(Path agentRoot) {
        if (agentRoot == null) {
            throw new IllegalArgumentException("agentRoot");
        }
        return PackageInstaller.weizhiHome(agentRoot.toAbsolutePath().normalize());
    }

    /**
     * @return absolute weizhi-home path for {@code setPackageRoot}
     */
    public static String syncFromClasspath(Path agentRoot) throws IOException {
        Path home = weizhiHome(agentRoot);
        synchronized (INSTALL_LOCK) {
            Files.createDirectories(home);
            for (String name : BUNDLED_PACKAGE_NAMES) {
                installBundledPackage(home, name);
            }
        }
        SkillsBundledInstall.syncFromClasspath(agentRoot);
        return home.toAbsolutePath().toString();
    }

    public static boolean isOfficeReady(Path agentRoot) {
        return isPackageInstalled(agentRoot, "docx")
            && isPackageInstalled(agentRoot, "docx-raw")
            && isPackageInstalled(agentRoot, "docx-build")
            && isPackageInstalled(agentRoot, "pptx")
            && isPackageInstalled(agentRoot, "pptx-build");
    }

    public static boolean isSvgRasterReady(Path agentRoot) {
        return isPackageInstalled(agentRoot, "svg-raster");
    }

    /**
     * 读取 classpath 上 {@code weizhi-packages/<name>/manifest.json} 的 version。
     */
    public static String bundledVersion(String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name");
        }
        String resourcePath = RESOURCE_ROOT + name + "/manifest.json";
        ClassLoader loader = WeizhiBundledPackages.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("missing resource: " + resourcePath);
            }
            JsonNode node = MAPPER.readTree(in);
            String version = node.path("version").asText("").trim();
            if (version.isEmpty()) {
                throw new IOException(name + ": manifest.version required");
            }
            String manifestName = node.path("name").asText("").trim();
            if (!name.equals(manifestName)) {
                throw new IOException(name + ": manifest.name must match directory (" + manifestName + ")");
            }
            return version;
        }
    }

    private static boolean isPackageInstalled(Path agentRoot, String name) {
        try {
            Path home = weizhiHome(agentRoot);
            Path entry = home.resolve("packages").resolve(name + "@" + bundledVersion(name));
            return Files.isRegularFile(entry.resolve("manifest.json"));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static void installBundledPackage(Path weizhiHome, String name) throws IOException {
        String prefix = RESOURCE_ROOT + name;
        Path temp = Files.createTempDirectory("weizhi-bundled-pkg-");
        try {
            String entryName = entryFileName(name);
            copyResourceFile(prefix + "/manifest.json", temp.resolve("manifest.json"));
            String version = readManifestVersion(temp.resolve("manifest.json"), name);
            copyResourceFile(prefix + "/" + entryName, temp.resolve(entryName));
            PackageInstaller.installPackageDirectory(weizhiHome, name, version, temp);
        } finally {
            deleteRecursive(temp);
        }
    }

    private static String readManifestVersion(Path manifest, String expectedName) throws IOException {
        JsonNode node = MAPPER.readValue(manifest.toFile(), JsonNode.class);
        String manifestName = node.path("name").asText("").trim();
        if (!expectedName.equals(manifestName)) {
            throw new IOException(expectedName + ": manifest.name must match directory (" + manifestName + ")");
        }
        String version = node.path("version").asText("").trim();
        if (version.isEmpty()) {
            throw new IOException(expectedName + ": manifest.version required");
        }
        return version;
    }

    private static String entryFileName(String packageName) {
        return switch (packageName) {
            case "docx" -> "docx.js";
            case "docx-raw" -> "docx-raw.js";
            case "docx-build" -> "docx-build.js";
            case "pptx" -> "pptx.js";
            case "pptx-build" -> "pptx-build.js";
            case "svg-raster" -> "svg-raster.js";
            case "html2pptx" -> "html2pptx.js";
            case "image" -> "image.js";
            case "audio" -> "audio.js";
            default -> throw new IllegalArgumentException("unknown package: " + packageName);
        };
    }

    private static void copyResourceFile(String resourcePath, Path target) throws IOException {
        ClassLoader loader = WeizhiBundledPackages.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("missing resource: " + resourcePath);
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }
}
