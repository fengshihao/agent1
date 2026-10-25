package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.weizhi.packages.PackageInstaller;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本地回环 registry：验证 ①自定义 registry 地址（不必上云/不碰生产）②只升不降
 * （清单旧版本不覆盖本地新版本）。纯 JVM，不需要 libweizhijni。
 */
class WeizhiPackageSyncLocalRegistryTest {

    private static HttpServer server;
    private static String base;
    private static final Map<String, AtomicInteger> HITS = new ConcurrentHashMap<>();
    private static byte[] packageZip;

    @BeforeAll
    static void startLocalRegistry() throws Exception {
        packageZip = zipPackage();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", WeizhiPackageSyncLocalRegistryTest::serve);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopLocalRegistry() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void serve(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        HITS.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
        byte[] body;
        if (path.endsWith(".zip")) {
            body = packageZip;
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
        } else if (path.endsWith(".json")) {
            body = manifestJson().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
        exchange.close();
    }

    /** 极简 html2pptx 包 zip：manifest + entry。 */
    private static byte[] zipPackage() throws Exception {
        Path dir = Files.createTempDirectory("local-registry-pkg");
        Files.writeString(dir.resolve("manifest.json"),
            "{\"name\":\"html2pptx\",\"version\":\"0.2.1\",\"entry\":\"html2pptx.js\"}");
        Files.writeString(dir.resolve("html2pptx.js"), "export const tag = 'local-0.2.1';\n");
        var bos = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(Files.readAllBytes(dir.resolve("manifest.json")));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("html2pptx.js"));
            zip.write(Files.readAllBytes(dir.resolve("html2pptx.js")));
            zip.closeEntry();
        }
        return bos.toByteArray();
    }

    private static String sha256Hex(byte[] bytes) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String manifestJson() throws IOException {
        return "{\"schema_version\":1,\"channel\":\"stable\",\"packages\":[{"
            + "\"name\":\"html2pptx\",\"version\":\"0.2.1\","
            + "\"artifact\":{\"url\":\"" + base + "/html2pptx-0.2.1.zip\","
            + "\"sha256\":\"" + sha256Hex(packageZip) + "\",\"size\":" + packageZip.length + "},"
            + "\"capability\":{\"title\":\"HTML2PPTX\",\"summary\":\"local\",\"entry\":\"import ... from \\\"weizhi:html2pptx\\\"\"}"
            + "}]}";
    }

    private static String withLocalRegistry(Runnable runnable) {
        String previous = System.getProperty(WeizhiPackageSync.PROP_URL);
        System.setProperty(WeizhiPackageSync.PROP_URL, base + "/registry.json");
        try {
            runnable.run();
        } finally {
            if (previous == null) {
                System.clearProperty(WeizhiPackageSync.PROP_URL);
            } else {
                System.setProperty(WeizhiPackageSync.PROP_URL, previous);
            }
        }
        return base;
    }

    @Test
    void keepsNewerLocalVersionInsteadOfDowngrading(@TempDir Path agentRoot, @TempDir Path home) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        installFakePackage(home, "0.9.9");
        HITS.clear();
        withLocalRegistry(() -> WeizhiPackageSync.beforeEval(
            "import { renderHtmlDeck } from \"weizhi:html2pptx\"", home.toString(), agentRoot));
        assertEquals("0.9.9", PackageInstaller.readIndex(home).get("html2pptx"), "本地更高版本不应被清单旧版本回退");
        assertEquals(0, zipHits(), "降级保护下不应下载 zip");
        assertTrue(Files.isRegularFile(home.resolve("packages/html2pptx@0.9.9/html2pptx.js")));
    }

    @Test
    void beforeEvalDoesNotEnsureBundledOnlyPackageAlreadyOnDisk(
        @TempDir Path agentRoot,
        @TempDir Path home) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        installFakePackage(home, "video", "0.6.0");
        HITS.clear();
        withLocalRegistry(() -> WeizhiPackageSync.beforeEval(
            "import { probe } from \"weizhi:video\"", home.toString(), agentRoot));
        assertEquals("0.6.0", PackageInstaller.readIndex(home).get("video"));
        assertEquals(0, zipHits(), "video 不在 registry 清单时不应拉 zip");
    }

    @Test
    void installsFromLocalRegistryWhenMissing(@TempDir Path agentRoot, @TempDir Path home) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Files.createDirectories(home);
        withLocalRegistry(() -> WeizhiPackageSync.beforeEval(
            "import { renderHtmlDeck } from \"weizhi:html2pptx\"", home.toString(), agentRoot));
        assertEquals("0.2.1", PackageInstaller.readIndex(home).get("html2pptx"), "缺失时应从本地 registry 安装");
        Path js = home.resolve("packages/html2pptx@0.2.1/html2pptx.js");
        assertTrue(Files.isRegularFile(js), "包文件应落盘");
        assertEquals("export const tag = 'local-0.2.1';\n", Files.readString(js));
    }

    @Test
    void comparesSemverTriples() {
        assertEquals(Integer.valueOf(1), WeizhiPackageSync.compareVersion("0.2.1", "0.2.0"));
        assertEquals(Integer.valueOf(0), WeizhiPackageSync.compareVersion("1.0.0", "1.0.0"));
        assertEquals(Integer.valueOf(-1), WeizhiPackageSync.compareVersion("0.2.0", "0.10.0"));
        assertEquals(null, WeizhiPackageSync.compareVersion("0.2", "0.2.0"));
        assertEquals(null, WeizhiPackageSync.compareVersion("dev", "0.2.0"));
    }

    @Test
    void resolvesRegistryUrlOverride() {
        String previous = System.getProperty(WeizhiPackageSync.PROP_URL);
        System.setProperty(WeizhiPackageSync.PROP_URL, "http://example.test/stable.json");
        try {
            assertEquals("http://example.test/stable.json", WeizhiPackageSync.registryUrl());
        } finally {
            if (previous == null) {
                System.clearProperty(WeizhiPackageSync.PROP_URL);
            } else {
                System.setProperty(WeizhiPackageSync.PROP_URL, previous);
            }
        }
    }

    private static int zipHits() {
        AtomicInteger hits = HITS.get("/html2pptx-0.2.1.zip");
        return hits == null ? 0 : hits.get();
    }

    private static void installFakePackage(Path home, String version) throws Exception {
        installFakePackage(home, "html2pptx", version);
    }

    private static void installFakePackage(Path home, String name, String version) throws Exception {
        Path dir = Files.createTempDirectory("fake-pkg");
        String entry = name + ".js";
        Files.writeString(dir.resolve("manifest.json"),
            "{\"name\":\"" + name + "\",\"version\":\"" + version + "\",\"entry\":\"" + entry + "\"}");
        Files.writeString(dir.resolve(entry), "export const tag = 'local-" + version + "';\n");
        PackageInstaller.installPackageDirectory(home, name, version, dir);
    }
}
