package com.agent1.javaagent.weizhi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weizhi.packages.PackageInstaller;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 从 {@code agent1-skills/packages-src/} 安装 package（单测 / 本地联调）。
 * <p>云端-only 包（如 {@code xlsx}）不进 {@link WeizhiBundledPackages#BUNDLED_PACKAGE_NAMES}，
 * 生产路径靠 registry；测试用本类从内容仓源码 {@link PackageInstaller#installPackageDirectory}。
 */
public final class WeizhiSourcePackages {

    /** 不上桌面 classpath 预装、靠 CDN registry 的包名。 */
    public static final List<String> CLOUD_ONLY_PACKAGE_NAMES =
        List.of("xlsx", "xlsx-build", "xlsx-raw");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WeizhiSourcePackages() {
    }

    public static boolean isCloudOnly(String name) {
        return name != null && CLOUD_ONLY_PACKAGE_NAMES.contains(name);
    }

    /**
     * 从仓内真源安装一个 package 到 {@code weizhiHome}。
     *
     * @return installed version
     */
    public static String installFromSource(Path weizhiHome, String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name");
        }
        Path source = resolvePackageSource(name);
        String version = readVersion(source.resolve("manifest.json"), name);
        PackageInstaller.installPackageDirectory(weizhiHome, name, version, source);
        return version;
    }

    public static void installCloudOnlyFromSource(Path weizhiHome) throws IOException {
        for (String name : CLOUD_ONLY_PACKAGE_NAMES) {
            installFromSource(weizhiHome, name);
        }
    }

    public static Path resolvePackageSource(String name) throws IOException {
        Path dir = resolvePackagesSrcRoot().resolve(name);
        if (!Files.isDirectory(dir) || !Files.isRegularFile(dir.resolve("manifest.json"))) {
            throw new IOException("weizhi package source missing: " + dir);
        }
        return dir.toAbsolutePath().normalize();
    }

    /** {@code agent1-skills/packages-src} 目录（环境变量 {@code AGENT1_SKILLS_PACKAGES_ROOT} 可覆盖）。 */
    public static Path resolvePackagesSrcRoot() throws IOException {
        String env = System.getenv("AGENT1_SKILLS_PACKAGES_ROOT");
        if (env != null && !env.isBlank()) {
            Path fromEnv = Path.of(env).toAbsolutePath().normalize();
            if (Files.isDirectory(fromEnv)) {
                return fromEnv;
            }
            throw new IOException("AGENT1_SKILLS_PACKAGES_ROOT not a directory: " + fromEnv);
        }
        Path agent1Root = resolveAgent1RepoRoot();
        Path[] candidates = {
            agent1Root.resolve("agent1-skills/packages-src"),
            agent1Root.resolve("../agent1-skills/packages-src").normalize(),
        };
        for (Path c : candidates) {
            if (Files.isDirectory(c)) {
                return c.toAbsolutePath().normalize();
            }
        }
        throw new IOException(
            "agent1-skills packages-src not found (clone agent1-skills or set AGENT1_SKILLS_PACKAGES_ROOT);"
                + " tried "
                + candidates[0]
                + " and "
                + candidates[1]);
    }

    static Path resolveAgent1RepoRoot() throws IOException {
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path[] candidates = {
            cwd,
            cwd.resolve(".."),
            cwd.resolve("../.."),
            cwd.resolve("../../.."),
            cwd.resolve("../../../.."),
        };
        for (Path c : candidates) {
            Path normalized = c.normalize();
            if (Files.isDirectory(normalized.resolve("agent_core"))
                && Files.isRegularFile(normalized.resolve("AGENTS.md"))) {
                return normalized;
            }
        }
        throw new IOException("agent1 repo root not found from user.dir=" + cwd);
    }

    private static String readVersion(Path manifest, String expectedName) throws IOException {
        JsonNode node = MAPPER.readTree(manifest.toFile());
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
}
