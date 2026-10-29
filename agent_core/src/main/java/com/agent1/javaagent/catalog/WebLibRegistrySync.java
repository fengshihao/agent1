package com.agent1.javaagent.catalog;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.capability.CapabilityRecord;
import com.agent1.javaagent.config.OfficialCdn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 把官方 WebView 库清单写入 find_caps。
 * 清单只有当前一批 {@code web_lib} 卡片，索引按这份清单整批替换 {@code source=web_lib} 的行。
 * 库文件本身仍在条目里的 CDN，这里不下 zip。
 */
public final class WebLibRegistrySync {

    public static final String SOURCE = "web_lib";

    public static String defaultRegistryUrl() {
        return OfficialCdn.webLibsRegistryUrl();
    }

    public static String officialHost() {
        return OfficialCdn.host();
    }

    static final String ENV = "AGENT1_WEB_LIB_REGISTRY";
    static final String URL_ENV = "AGENT1_WEB_LIB_REGISTRY_URL";

    private static final Logger LOG = Logger.getLogger(WebLibRegistrySync.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MANIFEST_TIMEOUT_MS = 15_000;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;

    private WebLibRegistrySync() {
    }

    /** 只刷新 find_caps。失败时保留已有索引。 */
    public static void refreshIndex(Path agentRoot) {
        if (agentRoot == null || disabled()) {
            if (disabled()) {
                LOG.info("web lib registry skipped: disabled");
            }
            return;
        }
        try {
            String manifest = fetchManifest();
            int cards = cardsFromManifest(manifest).size();
            applyManifest(agentRoot, manifest);
            LOG.info("web lib registry updated cards=" + cards);
        } catch (Exception e) {
            String detail = e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
            LOG.log(Level.WARNING, "web lib registry kept previous cards: " + detail, e);
        }
    }

    /** 启动时后台拉清单，不挡住宿主起来。 */
    public static void refreshIndexAsync(Path agentRoot) {
        if (agentRoot == null || disabled()) {
            return;
        }
        Thread thread = new Thread(() -> refreshIndex(agentRoot), "web-lib-registry");
        thread.setDaemon(true);
        thread.start();
    }

    public static void applyManifest(Path agentRoot, String json) throws IOException {
        CapabilityIndexStore.replaceSource(agentRoot, SOURCE, cardsFromManifest(json));
    }

    static List<CapabilityRecord> cardsFromManifest(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        if (root.path("schema_version").asInt(0) != 1) {
            throw new IOException("schema_version must be 1");
        }
        JsonNode cards = root.path("cards");
        if (!cards.isArray()) {
            throw new IOException("registry missing cards");
        }
        List<CapabilityRecord> records = new ArrayList<>();
        for (JsonNode card : cards) {
            CapabilityRecord record = recordFromCard(card);
            if (record != null) {
                records.add(record);
            }
        }
        if (cards.size() > 0 && records.isEmpty()) {
            throw new IOException("registry has no web_lib cards");
        }
        return records;
    }

    static URI registryUri() {
        String raw = System.getenv(URL_ENV);
        if (raw == null || raw.isBlank()) {
            raw = defaultRegistryUrl();
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("web lib registry url is invalid", e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        String host = uri.getHost() == null ? "" : uri.getHost();
        boolean loopback = OfficialCdn.isLoopback(host);
        if ("http".equalsIgnoreCase(scheme) && !loopback) {
            throw new IllegalArgumentException("web lib registry http only allowed for loopback");
        }
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("web lib registry must be http or https");
        }
        if (uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("web lib registry must not contain credentials, query, or hash");
        }
        if (!OfficialCdn.isAllowedHost(host)) {
            throw new IllegalArgumentException("web lib registry host not allowed: " + host);
        }
        return uri;
    }

    private static CapabilityRecord recordFromCard(JsonNode card) {
        String id = card.path("id").asText("").trim();
        String kind = card.path("kind").asText("").trim();
        String entry = card.path("entry").asText("").trim();
        if (!"web_lib".equals(kind) || id.isEmpty() || entry.isEmpty() || !id.startsWith("web_lib.")) {
            return null;
        }
        double weight = card.path("weight").asDouble(1.2);
        return new CapabilityRecord(
            id,
            kind,
            card.path("title").asText(""),
            card.path("summary").asText(""),
            tagsToCsv(card.get("tags")),
            platformsToStored(card.get("platforms")),
            entry,
            "",
            "",
            "",
            SOURCE,
            weight
        );
    }

    private static String fetchManifest() throws IOException {
        URI uri = registryUri();
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(MANIFEST_TIMEOUT_MS);
        connection.setReadTimeout(MANIFEST_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != 200) {
                throw new IOException("web lib registry HTTP " + status);
            }
            try (InputStream in = connection.getInputStream()) {
                byte[] body = in.readNBytes(MAX_MANIFEST_BYTES + 1);
                if (body.length > MAX_MANIFEST_BYTES) {
                    throw new IOException("too large: web lib registry");
                }
                return new String(body, StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
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
