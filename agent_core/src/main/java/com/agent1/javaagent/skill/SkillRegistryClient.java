package com.agent1.javaagent.skill;

import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 从统一 registry（{@code artifacts[]} 中 {@code kind=skill}）下载 zip 安装到 skills-home。
 * 对称 {@link com.weizhi.packages.PackageRegistryClient}，但不依赖 weizhi-home。
 */
public final class SkillRegistryClient {

    public static final String DEFAULT_REGISTRY_URL = "https://agentone.guoyoutech.cn/registry/stable.json";
    public static final String OFFICIAL_HOST = "agentone.guoyoutech.cn";

    public static final String ENV_REGISTRY_URL = "AGENT1_SKILL_REGISTRY_URL";
    public static final String ENV_DISABLED = "AGENT1_SKILL_REGISTRY";
    public static final String PROP_REGISTRY_URL = "agent1.skill.registry.url";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(SkillRegistryClient.class.getName());
    private static final int MANIFEST_TIMEOUT_MS = 30_000;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final int MAX_ARTIFACT_BYTES = 8 * 1024 * 1024;

    private SkillRegistryClient() {
    }

    public record SkillArtifact(String name, String version, String artifactUrl, String sha256, long size) {
    }

    public record EnsureResult(List<String> installed, List<String> unchanged) {
        public EnsureResult {
            installed = List.copyOf(installed);
            unchanged = List.copyOf(unchanged);
        }
    }

    /** 清单中有点名且本地未装或清单版本更高时安装；本地版本更高时保留（只升不降）。 */
    public static EnsureResult ensureInstalled(Path agentRoot, Collection<String> names) throws IOException {
        if (agentRoot == null) {
            throw new IllegalArgumentException("agentRoot");
        }
        if (names == null || names.isEmpty()) {
            return new EnsureResult(List.of(), List.of());
        }
        if (disabled()) {
            throw new IOException("skill registry disabled");
        }
        String manifestJson = fetchManifest(agentRoot);
        Map<String, SkillArtifact> registry = artifactsByName(manifestJson);
        List<String> installed = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();
        Map<String, String> local = SkillsHomeInstaller.readIndex(agentRoot);
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String trimmed = name.trim();
            SkillArtifact row = registry.get(trimmed);
            if (row == null) {
                throw new IOException("skill not in registry: " + trimmed);
            }
            String have = local.get(trimmed);
            if (have != null && !have.isEmpty()) {
                int cmp = compareSemver(have, row.version);
                if (cmp >= 0) {
                    Path dir = SkillsHomeInstaller.installedDir(agentRoot, trimmed);
                    if (dir != null && Files.isRegularFile(dir.resolve("SKILL.md"))) {
                        unchanged.add(trimmed);
                        continue;
                    }
                }
            }
            byte[] zip = downloadArtifact(row);
            verifySha256(zip, row.sha256);
            SkillsHomeInstaller.installFromZip(agentRoot, row.name, row.version, zip);
            installed.add(trimmed);
        }
        if (!installed.isEmpty()) {
            SkillsCapabilitySync.syncCapabilityCards(agentRoot);
        }
        return new EnsureResult(installed, unchanged);
    }

    static Map<String, SkillArtifact> artifactsByName(String manifestJson) throws IOException {
        JsonNode root = MAPPER.readTree(manifestJson);
        JsonNode artifacts = root.path("artifacts");
        if (!artifacts.isArray()) {
            throw new IOException("registry missing artifacts");
        }
        Map<String, SkillArtifact> map = new LinkedHashMap<>();
        for (JsonNode node : artifacts) {
            if (!"skill".equalsIgnoreCase(node.path("kind").asText(""))) {
                continue;
            }
            SkillArtifact row = parseArtifact(node);
            if (row != null) {
                map.put(row.name, row);
            }
        }
        return map;
    }

    static SkillArtifact parseArtifact(JsonNode node) throws IOException {
        String name = node.path("name").asText("").trim();
        String version = node.path("version").asText("").trim();
        if (name.isEmpty() || version.isEmpty()) {
            return null;
        }
        JsonNode artifact = node.path("artifact");
        String url = artifact.path("url").asText("").trim();
        String sha = artifact.path("sha256").asText("").trim().toLowerCase(Locale.ROOT);
        if (url.isEmpty() || !sha.matches("[0-9a-f]{64}")) {
            throw new IOException("bad skill artifact for " + name);
        }
        long size = artifact.path("size").asLong(0L);
        assertUrlAllowed(url);
        return new SkillArtifact(name, version, url, sha, size);
    }

    static String registryUrl() {
        String env = System.getenv(ENV_REGISTRY_URL);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String prop = System.getProperty(PROP_REGISTRY_URL, "").trim();
        if (!prop.isEmpty()) {
            return prop;
        }
        return DEFAULT_REGISTRY_URL;
    }

    static boolean disabled() {
        String env = System.getenv(ENV_DISABLED);
        return env != null && ("0".equals(env.trim()) || "off".equalsIgnoreCase(env.trim()));
    }

    private static String fetchManifest(Path agentRoot) throws IOException {
        String url = registryUrl();
        assertUrlAllowed(url);
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(MANIFEST_TIMEOUT_MS);
        connection.setReadTimeout(MANIFEST_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("registry HTTP " + code);
        }
        try (InputStream in = connection.getInputStream()) {
            byte[] bytes = in.readNBytes(MAX_MANIFEST_BYTES + 1);
            if (bytes.length > MAX_MANIFEST_BYTES) {
                throw new IOException("registry manifest too large");
            }
            String json = new String(bytes, StandardCharsets.UTF_8);
            Path cache = agentRoot.resolve("sync/skill-registry-stable.json");
            Path parent = cache.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            PathIo.writeString(cache, json);
            return json;
        }
    }

    private static byte[] downloadArtifact(SkillArtifact row) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(row.artifactUrl).toURL().openConnection();
        connection.setConnectTimeout(MANIFEST_TIMEOUT_MS);
        connection.setReadTimeout(MANIFEST_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("artifact HTTP " + code + " for " + row.name);
        }
        try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buf)) >= 0) {
                total += read;
                if (total > MAX_ARTIFACT_BYTES) {
                    throw new IOException("skill zip too large: " + row.name);
                }
                out.write(buf, 0, read);
            }
            byte[] zip = out.toByteArray();
            if (row.size > 0 && zip.length != row.size) {
                throw new IOException("skill zip size mismatch: " + row.name);
            }
            return zip;
        }
    }

    private static void verifySha256(byte[] zip, String expected) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String got = hex(digest.digest(zip));
            if (!got.equalsIgnoreCase(expected)) {
                throw new IOException("sha256 mismatch");
            }
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("sha256 unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    static void assertUrlAllowed(String url) throws IOException {
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IOException("invalid artifact url");
        }
        String registry = registryUrl();
        String registryHost = URI.create(registry).getHost();
        if (host.equals(OFFICIAL_HOST) || (registryHost != null && host.equals(registryHost))) {
            return;
        }
        boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host);
        if (loopback && registryHost != null && (registryHost.equals("localhost") || registryHost.equals("127.0.0.1"))) {
            return;
        }
        throw new IOException("artifact host not allowed: " + host);
    }

    /** 简单 x.y.z 比较；无法解析时返回 null，调用方按「需安装」处理。 */
    static int compareSemver(String a, String b) {
        int[] va = parseVersionTriple(a);
        int[] vb = parseVersionTriple(b);
        if (va == null || vb == null) {
            return a.equals(b) ? 0 : -1;
        }
        for (int i = 0; i < 3; i++) {
            if (va[i] != vb[i]) {
                return va[i] < vb[i] ? -1 : 1;
            }
        }
        return 0;
    }

    private static int[] parseVersionTriple(String version) {
        if (version == null) {
            return null;
        }
        String[] parts = version.trim().split("\\.");
        if (parts.length != 3) {
            return null;
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            if (!parts[i].matches("\\d+")) {
                return null;
            }
            out[i] = Integer.parseInt(parts[i]);
        }
        return out;
    }
}
