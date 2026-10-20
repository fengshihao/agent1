package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.skill.PackageSkills;
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
 * 将 classpath {@code agent-home/weizhi-packages/<name>@<version>/} 安装到
 * {@code <agentRoot>/weizhi-home}，供 {@link com.weizhi.WeizhiEngine#setPackageRoot} 使用。
 * 包内 manifest 声明的 {@code skill} 文件（SKILL.md）一并安装，装完把随包 skill 卡
 * 写入 find_caps（见 {@link PackageSkills}）。
 */
public final class WeizhiBundledPackages {

    public static final String VERSION = "1.0.0";
    public static final String PPTX_VERSION = "1.1.0";
    public static final String HTML2PPTX_VERSION = "0.2.4";

    public static final List<String> BUNDLED_PACKAGE_NAMES =
        List.of("docx", "docx-raw", "docx-build", "pptx", "pptx-build", "svg-raster", "html2pptx");

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
                installBundledPackage(home, name, bundledVersion(name));
            }
        }
        PackageSkills.syncCapabilityCards(agentRoot);
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

    private static boolean isPackageInstalled(Path agentRoot, String name) {
        try {
            Path home = weizhiHome(agentRoot);
            Path entry = home.resolve("packages").resolve(name + "@" + bundledVersion(name));
            return Files.isRegularFile(entry.resolve("manifest.json"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String bundledVersion(String name) {
        if ("pptx".equals(name)) {
            return PPTX_VERSION;
        }
        if ("html2pptx".equals(name)) {
            return HTML2PPTX_VERSION;
        }
        return VERSION;
    }

    private static void installBundledPackage(Path weizhiHome, String name, String version) throws IOException {
        String bundleId = name + "@" + version;
        String prefix = RESOURCE_ROOT + bundleId;
        Path temp = Files.createTempDirectory("weizhi-bundled-pkg-");
        try {
            String entryName = entryFileName(name);
            copyResourceFile(prefix + "/manifest.json", temp.resolve("manifest.json"));
            String skillFile = manifestSkillFile(temp.resolve("manifest.json"));
            if (skillFile != null) {
                copyResourceFile(prefix + "/" + skillFile, temp.resolve(skillFile));
            }
            copyResourceFile(prefix + "/" + entryName, temp.resolve(entryName));
            PackageInstaller.installPackageDirectory(weizhiHome, name, version, temp);
        } finally {
            deleteRecursive(temp);
        }
    }

    /** manifest 里 {@code skill} 字段声明的随包 skill 文件名；没有声明返回 null。 */
    private static String manifestSkillFile(Path manifest) throws IOException {
        JsonNode node = MAPPER.readValue(manifest.toFile(), JsonNode.class);
        String declared = node.path("skill").asText("").trim();
        if (declared.isEmpty() || declared.contains("/") || declared.contains("\\") || declared.contains("..")) {
            return null;
        }
        return declared;
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
