package com.agent1.javaagent.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.Optional;

/** catalog-index.json 单条目（阶段 5.1）。 */
public final class CatalogItem {

    private final String id;
    private final String kind;
    private final String version;
    private final String digest;
    private final String path;
    private final Optional<String> platform;
    private final long sizeBytes;

    public CatalogItem(
        String id,
        String kind,
        String version,
        String digest,
        String path,
        String platform,
        long sizeBytes
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.version = version == null ? "" : version;
        this.digest = Objects.requireNonNull(digest, "digest");
        this.path = Objects.requireNonNull(path, "path");
        this.platform = Optional.ofNullable(platform).filter(p -> !p.isBlank());
        this.sizeBytes = sizeBytes;
    }

    public static CatalogItem fromJson(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("catalog item must be object");
        }
        String id = requiredText(node, "id");
        String kind = requiredText(node, "kind");
        String version = node.path("version").asText("");
        String digest = requiredText(node, "digest");
        String path = requiredText(node, "path");
        String platform = node.path("platform").asText(null);
        long size = node.path("sizeBytes").asLong(0);
        return new CatalogItem(id, kind, version, digest, path, platform, size);
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing catalog item field: " + field);
        }
        return value.trim();
    }

    public String id() {
        return id;
    }

    public String kind() {
        return kind;
    }

    public String version() {
        return version;
    }

    public String digest() {
        return digest;
    }

    public String path() {
        return path;
    }

    public Optional<String> platform() {
        return platform;
    }

    public long sizeBytes() {
        return sizeBytes;
    }
}
