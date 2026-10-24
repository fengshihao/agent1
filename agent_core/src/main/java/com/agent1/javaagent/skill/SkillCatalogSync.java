package com.agent1.javaagent.skill;

import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Skill 目录缓存：默认 bundled；可用 {@code AGENT1_SKILL_CATALOG_URL} 拉远程
 * {@code skills/registry/stable.json} 覆盖缓存（失败保留上一份）。
 */
public final class SkillCatalogSync {

    public static final String ENV_CATALOG_URL = "AGENT1_SKILL_CATALOG_URL";
    public static final String DEFAULT_CATALOG_URL =
        "https://agentone.guoyoutech.cn/skills/registry/stable.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(SkillCatalogSync.class.getName());
    private static final int TIMEOUT_MS = 15_000;
    private static final int MAX_BYTES = 512 * 1024;

    private SkillCatalogSync() {
    }

    public static Path cacheFile(Path agentRoot) {
        return agentRoot.toAbsolutePath().normalize().resolve("sync/skills-catalog-stable.json");
    }

    public static void refreshAsync(Path agentRoot) {
        if (agentRoot == null) {
            return;
        }
        Thread thread = new Thread(() -> refresh(agentRoot), "skills-catalog-sync");
        thread.setDaemon(true);
        thread.start();
    }

    public static void refresh(Path agentRoot) {
        String url = catalogUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            String json = fetch(url);
            JsonNode node = MAPPER.readTree(json);
            Path cache = cacheFile(agentRoot);
            Path parent = cache.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            PathIo.writeString(cache, json);
            LOG.info("skills catalog cache updated from " + url);
            if (!node.path("categories").isArray()) {
                LOG.warning("skills catalog missing categories array");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "skills catalog refresh kept cache: " + e.getMessage(), e);
        }
    }

    public static String promptSection(Path agentRoot) {
        JsonNode catalog = readEffectiveCatalog(agentRoot);
        return SkillCatalogDocument.renderMarkdown(catalog);
    }

    static JsonNode readEffectiveCatalog(Path agentRoot) {
        if (agentRoot != null) {
            Path cache = cacheFile(agentRoot);
            if (Files.isRegularFile(cache)) {
                try {
                    return MAPPER.readTree(PathIo.readString(cache));
                } catch (IOException e) {
                    LOG.log(Level.FINE, "read skills catalog cache failed", e);
                }
            }
        }
        try {
            return SkillCatalogDocument.loadBundled();
        } catch (IOException e) {
            return MAPPER.createObjectNode();
        }
    }

    static String catalogUrl() {
        String env = System.getenv(ENV_CATALOG_URL);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String prop = System.getProperty("agent1.skill.catalog.url", "").trim();
        if (!prop.isEmpty()) {
            return prop;
        }
        return DEFAULT_CATALOG_URL;
    }

    private static String fetch(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestMethod("GET");
        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + code);
        }
        try (InputStream in = connection.getInputStream()) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new IOException("catalog too large");
            }
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
