package com.agent1.javaagent.catalog;

import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 宿主 WebView 依赖清单（仅 shell URL；vendor 在发布时 baked 进 shell.html），APK 不内置 JS。 */
public final class HostWebRegistrySync {

    public static final String OFFICIAL_HOST = WebLibRegistrySync.OFFICIAL_HOST;
    public static final String DEFAULT_REGISTRY_URL =
        "https://agentone.guoyoutech.cn/host-web/registry/stable.json";

    public static final String DEFAULT_SHELL_URL =
        "https://agentone.guoyoutech.cn/host-web/shell/docx-preview/1/shell.html?v=20251025b";

    public static final String DEFAULT_MD_SHELL_URL =
        "https://agentone.guoyoutech.cn/host-web/molan-embed/1/shell.html?v=20251027b";

    static final String URL_ENV = "AGENT1_HOST_WEB_REGISTRY_URL";
    static final String ENV = "AGENT1_HOST_WEB_REGISTRY";

    private static final Logger LOG = Logger.getLogger(HostWebRegistrySync.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MANIFEST_TIMEOUT_MS = 15_000;
    private static final int MAX_MANIFEST_BYTES = 64 * 1024;

    private HostWebRegistrySync() {
    }

    public record DocxPreviewUrls(String shell) {
    }

    public record MdPreviewUrls(String shell) {
    }

    public static void refreshAsync(Path agentRoot) {
        if (agentRoot == null || disabled()) {
            return;
        }
        Thread thread = new Thread(() -> refresh(agentRoot), "host-web-registry");
        thread.setDaemon(true);
        thread.start();
    }

    public static void refresh(Path agentRoot) {
        if (agentRoot == null || disabled()) {
            return;
        }
        try {
            String manifest = fetchManifest();
            validateManifest(manifest);
            writeCache(agentRoot, manifest);
            LOG.info("host-web registry updated");
        } catch (Exception e) {
            String detail = e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
            LOG.log(Level.WARNING, "host-web registry kept cache/defaults: " + detail, e);
        }
    }

    public static DocxPreviewUrls docxPreviewUrls(Path agentRoot) {
        String manifest = readCache(agentRoot);
        if (manifest != null) {
            try {
                return parseDocxPreviewUrls(manifest);
            } catch (IOException ignored) {
                // fall through
            }
        }
        return new DocxPreviewUrls(DEFAULT_SHELL_URL);
    }

    public static MdPreviewUrls mdPreviewUrls(Path agentRoot) {
        String manifest = readCache(agentRoot);
        if (manifest != null) {
            try {
                return parseMdPreviewUrls(manifest);
            } catch (IOException ignored) {
                // fall through
            }
        }
        return new MdPreviewUrls(DEFAULT_MD_SHELL_URL);
    }

    static Path cachePath(Path agentRoot) {
        return agentRoot.resolve("runtime").resolve("host-web-stable.json");
    }

    static void writeCache(Path agentRoot, String manifest) throws IOException {
        Path path = cachePath(agentRoot);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        PathIo.writeString(path, manifest, StandardCharsets.UTF_8);
    }

    static String readCache(Path agentRoot) {
        if (agentRoot == null) {
            return null;
        }
        try {
            Path path = cachePath(agentRoot);
            if (!Files.isRegularFile(path)) {
                return null;
            }
            return PathIo.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    static DocxPreviewUrls parseDocxPreviewUrls(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        if (root.path("schema_version").asInt(0) != 1) {
            throw new IOException("schema_version must be 1");
        }
        JsonNode block = root.path("docx_preview");
        String shell = requireHttpsUrl(block.path("shell").asText(""), "docx_preview.shell");
        return new DocxPreviewUrls(shell);
    }

    static MdPreviewUrls parseMdPreviewUrls(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        if (root.path("schema_version").asInt(0) != 1) {
            throw new IOException("schema_version must be 1");
        }
        JsonNode block = root.path("md_preview");
        if (!block.isObject() || block.path("shell").asText("").isBlank()) {
            return new MdPreviewUrls(DEFAULT_MD_SHELL_URL);
        }
        String shell = requireHttpsUrl(block.path("shell").asText(""), "md_preview.shell");
        return new MdPreviewUrls(shell);
    }

    static void validateManifest(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        if (root.path("schema_version").asInt(0) != 1) {
            throw new IOException("schema_version must be 1");
        }
        parseDocxPreviewUrls(json);
        JsonNode md = root.path("md_preview");
        if (!md.isObject() || md.path("shell").asText("").isBlank()) {
            throw new IOException("missing md_preview.shell");
        }
        requireHttpsUrl(md.path("shell").asText(""), "md_preview.shell");
    }

    static String requireHttpsUrl(String raw, String field) throws IOException {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new IOException("missing " + field);
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid url for " + field, e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException(field + " must be https");
        }
        String host = uri.getHost() == null ? "" : uri.getHost();
        if (!OFFICIAL_HOST.equalsIgnoreCase(host)) {
            throw new IOException(field + " host not allowed: " + host);
        }
        if (uri.getUserInfo() != null || uri.getRawFragment() != null) {
            throw new IOException(field + " must not contain credentials or hash");
        }
        return trimmed;
    }

    static URI registryUri() {
        String raw = System.getenv(URL_ENV);
        if (raw == null || raw.isBlank()) {
            raw = DEFAULT_REGISTRY_URL;
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("host-web registry url is invalid", e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        String host = uri.getHost() == null ? "" : uri.getHost();
        boolean loopback = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
        if ("http".equalsIgnoreCase(scheme) && !loopback) {
            throw new IllegalArgumentException("host-web registry http only allowed for loopback");
        }
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("host-web registry must be http or https");
        }
        if (uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("host-web registry must not contain credentials, query, or hash");
        }
        if (!loopback && !OFFICIAL_HOST.equalsIgnoreCase(host)) {
            throw new IllegalArgumentException("host-web registry host not allowed: " + host);
        }
        return uri;
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
                throw new IOException("host-web registry HTTP " + status);
            }
            try (InputStream in = connection.getInputStream()) {
                byte[] body = in.readNBytes(MAX_MANIFEST_BYTES + 1);
                if (body.length > MAX_MANIFEST_BYTES) {
                    throw new IOException("too large: host-web registry");
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
}
