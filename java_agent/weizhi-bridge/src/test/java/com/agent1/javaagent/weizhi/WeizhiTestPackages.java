package com.agent1.javaagent.weizhi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weizhi.packages.PackageInstaller;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * 从固化测试 fixture（classpath {@code weizhi-test-packages/}）安装 package。
 * <p>
 * fixture 是本仓自有的静态快照，agent1 不感知任何外部内容仓；环境变量
 * {@code AGENT1_TEST_PACKAGES_ROOT} 可指向自定义 package 源目录覆盖默认 fixture。
 * 生产路径无 classpath 预装（全云端 registry），本类仅服务集成测试。
 */
public final class WeizhiTestPackages {

    /** fixture 内全部 package（原桌面 bundled 9 个 + 云端-only 4 个）。 */
    public static final List<String> FIXTURE_PACKAGE_NAMES =
        List.of(
            "docx",
            "docx-raw",
            "docx-build",
            "pptx",
            "pptx-build",
            "svg-raster",
            "html2pptx",
            "image",
            "audio",
            "xlsx",
            "xlsx-build",
            "xlsx-raw",
            "chart");

    /** fixture 内云端-only package（不进任何预装，生产只靠 registry）。 */
    public static final List<String> CLOUD_ONLY_FIXTURE_NAMES =
        List.of("xlsx", "xlsx-build", "xlsx-raw", "chart");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FIXTURE_RESOURCE = "weizhi-test-packages";
    private static volatile Path fixtureRoot;

    private WeizhiTestPackages() {
    }

    /**
     * 从测试 fixture 安装一个 package 到 {@code weizhiHome}。
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
        for (String name : CLOUD_ONLY_FIXTURE_NAMES) {
            installFromSource(weizhiHome, name);
        }
    }

    /**
     * 安装 fixture 全部 package 到 {@code <agentRoot>/weizhi-home} 并返回其绝对路径。
     * 集成测试用；等价于历史上的 {@code WeizhiBundledPackages.syncFromClasspath}。
     */
    public static String syncFromFixture(Path agentRoot) throws IOException {
        Path home = PackageInstaller.weizhiHome(agentRoot.toAbsolutePath().normalize());
        Files.createDirectories(home);
        for (String name : FIXTURE_PACKAGE_NAMES) {
            installFromSource(home, name);
        }
        return home.toAbsolutePath().toString();
    }

    public static Path resolvePackageSource(String name) throws IOException {
        Path dir = resolvePackagesSrcRoot().resolve(name);
        if (!Files.isDirectory(dir) || !Files.isRegularFile(dir.resolve("manifest.json"))) {
            throw new IOException("weizhi test package source missing: " + dir);
        }
        return dir.toAbsolutePath().normalize();
    }

    /** 读取 fixture 内某包的 manifest version。 */
    public static String fixtureVersion(String name) throws IOException {
        return readVersion(resolvePackageSource(name).resolve("manifest.json"), name);
    }

    /** fixture 根目录：环境变量 {@code AGENT1_TEST_PACKAGES_ROOT} 优先，否则物化 classpath 固化快照。 */
    public static Path resolvePackagesSrcRoot() throws IOException {
        String env = System.getenv("AGENT1_TEST_PACKAGES_ROOT");
        if (env != null && !env.isBlank()) {
            Path fromEnv = Path.of(env).toAbsolutePath().normalize();
            if (Files.isDirectory(fromEnv)) {
                return fromEnv;
            }
            throw new IOException("AGENT1_TEST_PACKAGES_ROOT not a directory: " + fromEnv);
        }
        Path cached = fixtureRoot;
        if (cached != null) {
            return cached;
        }
        synchronized (WeizhiTestPackages.class) {
            if (fixtureRoot == null) {
                fixtureRoot = materializeClasspathFixture(FIXTURE_RESOURCE, "weizhi-test-packages-");
            }
            return fixtureRoot;
        }
    }

    /** classpath 资源目录可能位于 jar 内，物化到临时目录后再交给 PackageInstaller。 */
    static Path materializeClasspathFixture(String resourceRoot, String tempPrefix) throws IOException {
        ClassLoader loader = WeizhiTestPackages.class.getClassLoader();
        URL base = loader.getResource(resourceRoot);
        if (base == null) {
            throw new IOException(resourceRoot + " missing on test classpath"
                + " (set AGENT1_TEST_PACKAGES_ROOT to override)");
        }
        if (!"file".equalsIgnoreCase(base.getProtocol())) {
            throw new IOException("unsupported classpath location: " + base
                + " (set AGENT1_TEST_PACKAGES_ROOT to override)");
        }
        Path src = Path.of(URI.create(base.toString()));
        Path root = Files.createTempDirectory(tempPrefix);
        try (Stream<Path> walk = Files.walk(src)) {
            for (Path entry : walk.toList()) {
                Path rel = src.relativize(entry);
                Path dest = root.resolve(rel.toString());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(entry, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return root;
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
