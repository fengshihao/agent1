package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.capability.CapabilityRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weizhi.packages.PackageInstaller;
import com.weizhi.packages.PackageRegistryClient;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 跑脚本前按 {@code weizhi:} 名字安装官方包，并把清单里的当前卡片写入 find_caps。
 * 清单每个 name 只有一行当前版本，索引按这份清单整批替换 {@code source=registry} 的行。
 *
 * <p>版本策略：registry 只升不降——本地已装版本高于清单版本时保留本地（内置
 * classpath 包常比线上新），不会发生「清单 0.2.0 覆盖本地 0.2.1」的回退。
 * registry 地址可用 {@code AGENT1_WEIZHI_PACKAGE_REGISTRY_URL} 环境变量或
 * {@code agent1.weizhi.package.registry.url} 系统属性覆盖（本地/测试 registry）；
 * 覆盖时 zip 白名单自动加入该地址的 host，缓存写到独立文件不污染生产缓存。
 */
public final class WeizhiPackageSync {

    public static final String SOURCE = "registry";
    static final String ENV = "AGENT1_WEIZHI_PACKAGE_REGISTRY";
    static final String ENV_URL = "AGENT1_WEIZHI_PACKAGE_REGISTRY_URL";
    static final String PROP_URL = "agent1.weizhi.package.registry.url";

    private static final Logger LOG = Logger.getLogger(WeizhiPackageSync.class.getName());

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PACKAGE_NAME = Pattern.compile("weizhi:([a-z0-9][a-z0-9._-]*)");
    private static final int MANIFEST_TIMEOUT_MS = 15_000;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;

    private WeizhiPackageSync() {
    }

    public static List<String> packageNames(String source) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Matcher matcher = PACKAGE_NAME.matcher(source);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return List.copyOf(names);
    }

    /** 只刷新 find_caps。失败时保留已有索引。 */
    public static void refreshIndex(Path agentRoot) {
        if (agentRoot == null || disabled()) {
            if (disabled()) {
                LOG.info("registry index skipped: disabled");
            }
            return;
        }
        try {
            String manifest = fetchManifest();
            int cards = cardsFromManifest(manifest).size();
            applyManifest(agentRoot, manifest);
            LOG.info("registry index updated cards=" + cards);
        } catch (Exception e) {
            String detail = e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
            LOG.log(Level.WARNING, "registry index kept previous cards: " + detail, e);
        }
    }

    /** 启动时后台拉清单，不挡住宿主起来。 */
    public static void refreshIndexAsync(Path agentRoot) {
        if (agentRoot == null || disabled()) {
            return;
        }
        Thread thread = new Thread(() -> refreshIndex(agentRoot), "weizhi-registry-index");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 安装脚本点名的包，并用来源清单替换 find_caps 里的 registry 卡。
     * 这些包已经装在本地且网络失败时，不打断脚本。
     * 本地版本高于清单版本时跳过该包（只升不降）。
     */
    public static void beforeEval(String source, String weizhiHome, Path agentRoot) {
        if (disabled() || weizhiHome == null || weizhiHome.isBlank()) {
            return;
        }
        List<String> names = packageNames(source);
        if (names.isEmpty()) {
            return;
        }
        Path home = Path.of(weizhiHome);
        try {
            String manifest = fetchManifest();
            if (agentRoot != null) {
                applyManifest(agentRoot, manifest);
            }
            List<String> ensureNames = namesNeedingEnsure(home, names, manifest);
            if (ensureNames.isEmpty()) {
                return;
            }
            PackageRegistryClient.EnsureOptions options = new PackageRegistryClient.EnsureOptions();
            options.registryUrl = registryUrl();
            options.allowedHosts = registryHosts();
            options.cacheFile = registryCacheFile(home);
            PackageRegistryClient.EnsureResult ensured =
                PackageRegistryClient.ensureInstalled(home, ensureNames, options);
        } catch (IOException e) {
            if (allInstalled(home, names)) {
                LOG.info("package ensure offline, using installed " + names);
                return;
            }
            String message = e.getMessage() == null || e.getMessage().isBlank()
                ? "package ensure failed"
                : e.getMessage();
            LOG.log(Level.WARNING, "package ensure failed " + names + ": " + message, e);
            throw new RuntimeException(message, e);
        }
    }

    /**
     * 过滤出需要 ensure 的包名：未安装/版本解析失败（保持旧的「同步清单版本」行为）
     * 或清单版本 ≥ 本地版本。清单版本更低时保留本地（防止线上滞后回退内置新包）。
     * 清单里没有该名但本地已装（如 {@code weizhi:video} 随 AAR syncBundled、不上云
     * registry）则跳过 ensure。
     */
    static List<String> namesNeedingEnsure(Path home, List<String> names, String manifestJson) {
        Map<String, String> registry = registryVersions(manifestJson);
        Map<String, String> installed;
        try {
            installed = PackageInstaller.readIndex(home);
        } catch (IOException e) {
            return names;
        }
        List<String> need = new ArrayList<>();
        for (String name : names) {
            String want = registry.get(name);
            String have = installed.get(name);
            if (have == null || have.isBlank()) {
                need.add(name);
                continue;
            }
            if (want == null) {
                continue;
            }
            Integer cmp = compareVersion(want, have);
            if (cmp == null) {
                need.add(name);
                continue;
            }
            if (cmp < 0) {
                LOG.info("registry " + name + "@" + want + " 低于本地 " + name + "@" + have + "，保留本地版本");
                continue;
            }
            need.add(name);
        }
        return need;
    }

    /** 清单里 name → version（解析失败按空表处理，交由 ensure 的原报错路径）。 */
    static Map<String, String> registryVersions(String manifestJson) {
        Map<String, String> out = new LinkedHashMap<>();
        if (manifestJson == null || manifestJson.isEmpty()) {
            return out;
        }
        try {
            JsonNode packages = MAPPER.readTree(manifestJson).path("packages");
            if (!packages.isArray()) {
                packages = MAPPER.readTree(manifestJson).path("artifacts");
            }
            for (JsonNode pkg : packages) {
                String name = pkg.path("name").asText("").trim();
                String version = pkg.path("version").asText("").trim();
                if (!name.isEmpty() && !version.isEmpty()) {
                    out.put(name, version);
                }
            }
        } catch (IOException e) {
            return Map.of();
        }
        return out;
    }

    /** 三段语义化版本比较；任一侧不是 x.y.z 数字时返回 null（调用方回退旧行为）。 */
    static Integer compareVersion(String a, String b) {
        int[] va = parseVersionTriple(a);
        int[] vb = parseVersionTriple(b);
        if (va == null || vb == null) {
            return null;
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
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }

    /** registry 地址：环境变量 → 系统属性（测试用）→ SDK 默认生产地址。 */
    static String registryUrl() {
        String env = System.getenv(ENV_URL);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String prop = System.getProperty(PROP_URL);
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        return PackageRegistryClient.DEFAULT_REGISTRY_URL;
    }

    /** 自定义 registry 地址时把该 host 加进 zip 白名单（SDK 默认只放行官方 host）。 */
    static List<String> registryHosts() {
        String url = registryUrl();
        if (url.equals(PackageRegistryClient.DEFAULT_REGISTRY_URL)) {
            return null;
        }
        String host = URI.create(url).getHost();
        if (host == null || host.isBlank() || host.equals(PackageRegistryClient.OFFICIAL_HOST)) {
            return null;
        }
        return List.of(host, PackageRegistryClient.OFFICIAL_HOST);
    }

    /** 自定义 registry 用独立缓存文件，避免测试清单覆盖生产缓存。 */
    static Path registryCacheFile(Path home) {
        if (registryHosts() == null) {
            return null;
        }
        return home.resolve("registry-cache-custom.json");
    }

    static void applyManifest(Path agentRoot, String json) throws IOException {
        CapabilityIndexStore.replaceSource(agentRoot, SOURCE, cardsFromManifest(json));
    }

    static List<CapabilityRecord> cardsFromManifest(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        JsonNode packages = root.path("packages");
        if (!packages.isArray()) {
            packages = root.path("artifacts");
        }
        List<CapabilityRecord> records = new ArrayList<>();
        if (!packages.isArray()) {
            return records;
        }
        for (JsonNode pkg : packages) {
            JsonNode cards = pkg.path("cards");
            if (cards.isArray() && !cards.isEmpty()) {
                for (JsonNode card : cards) {
                    CapabilityRecord record = recordFromCard(card);
                    if (record != null) {
                        records.add(record);
                    }
                }
                continue;
            }
            CapabilityRecord single = recordFromCapability(pkg);
            if (single != null) {
                records.add(single);
            }
        }
        return records;
    }

    private static CapabilityRecord recordFromCard(JsonNode card) {
        String id = card.path("id").asText("").trim();
        String entry = card.path("entry").asText("").trim();
        if (id.isEmpty() || entry.isEmpty()) {
            return null;
        }
        String requires = card.has("params") && !card.get("params").isNull() ? card.get("params").toString() : "";
        return new CapabilityRecord(
            id,
            "weizhi_package",
            card.path("title").asText(""),
            card.path("summary").asText(""),
            tagsToCsv(card.get("tags")),
            platformsToStored(card.get("platforms")),
            entry,
            "",
            "",
            requires,
            SOURCE,
            1.2
        );
    }

    private static CapabilityRecord recordFromCapability(JsonNode pkg) {
        JsonNode capability = pkg.path("capability");
        String entry = capability.path("entry").asText("").trim();
        String name = pkg.path("name").asText("").trim();
        if (entry.isEmpty() || name.isEmpty()) {
            return null;
        }
        String title = capability.path("title").asText("").trim();
        if (title.isEmpty()) {
            title = name;
        }
        return new CapabilityRecord(
            "weizhi_package." + name,
            "weizhi_package",
            title,
            capability.path("summary").asText(""),
            name,
            "any",
            entry,
            "",
            "",
            "",
            SOURCE,
            1.2
        );
    }

    private static String fetchManifest() throws IOException {
        HttpURLConnection connection =
            (HttpURLConnection) URI.create(registryUrl()).toURL().openConnection();
        connection.setConnectTimeout(MANIFEST_TIMEOUT_MS);
        connection.setReadTimeout(MANIFEST_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        int status = connection.getResponseCode();
        if (status != 200) {
            throw new IOException("registry HTTP " + status);
        }
        try (InputStream in = connection.getInputStream()) {
            byte[] body = in.readNBytes(MAX_MANIFEST_BYTES + 1);
            if (body.length > MAX_MANIFEST_BYTES) {
                throw new IOException("too large: registry manifest");
            }
            return new String(body, StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static boolean allInstalled(Path home, List<String> names) {
        try {
            Map<String, String> index = PackageInstaller.readIndex(home);
            for (String name : names) {
                String version = index.get(name);
                if (version == null || version.isBlank()) {
                    return false;
                }
                Path manifest = home.resolve("packages").resolve(name + "@" + version).resolve("manifest.json");
                if (!Files.isRegularFile(manifest)) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean disabled() {
        String value = System.getenv(ENV);
        return value != null && (value.equalsIgnoreCase("off") || value.equals("0"));
    }

    private static String tagsToCsv(JsonNode tagsNode) {
        if (tagsNode == null || !tagsNode.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode tag : tagsNode) {
            String text = tag.asText("").trim();
            if (text.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(text);
        }
        return sb.toString();
    }

    private static String platformsToStored(JsonNode platformsNode) {
        if (platformsNode == null || platformsNode.isMissingNode() || platformsNode.isNull()) {
            return "any";
        }
        if (platformsNode.isTextual()) {
            String text = platformsNode.asText("any").trim();
            return text.isEmpty() ? "any" : text;
        }
        if (!platformsNode.isArray()) {
            return "any";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode platform : platformsNode) {
            String text = platform.asText("").trim().toLowerCase();
            if (text.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(text);
        }
        return sb.length() == 0 ? "any" : sb.toString();
    }
}
