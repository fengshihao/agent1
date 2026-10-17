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
 */
public final class WeizhiPackageSync {

    public static final String SOURCE = "registry";
    static final String ENV = "AGENT1_WEIZHI_PACKAGE_REGISTRY";

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
            PackageRegistryClient.EnsureOptions options = new PackageRegistryClient.EnsureOptions();
            PackageRegistryClient.ensureInstalled(home, names, options);
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
            (HttpURLConnection) URI.create(PackageRegistryClient.DEFAULT_REGISTRY_URL).toURL().openConnection();
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
