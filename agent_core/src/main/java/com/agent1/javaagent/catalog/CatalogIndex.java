package com.agent1.javaagent.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 远程 catalog-index.json（阶段 5.1）。 */
public final class CatalogIndex {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int schemaVersion;
    private final String catalogId;
    private final String baseUrl;
    private final List<CatalogItem> items;

    public CatalogIndex(int schemaVersion, String catalogId, String baseUrl, List<CatalogItem> items) {
        this.schemaVersion = schemaVersion;
        this.catalogId = catalogId == null ? "" : catalogId;
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl").trim();
        this.items = List.copyOf(items);
    }

    public static CatalogIndex parse(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        return fromJson(root);
    }

    public static CatalogIndex fromJson(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("catalog index must be object");
        }
        int schema = root.path("schemaVersion").asInt(1);
        String catalogId = root.path("catalogId").asText("");
        String baseUrl = root.path("baseUrl").asText(null);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("missing baseUrl");
        }
        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray()) {
            throw new IllegalArgumentException("items must be array");
        }
        List<CatalogItem> items = new ArrayList<>();
        for (JsonNode node : itemsNode) {
            items.add(CatalogItem.fromJson(node));
        }
        return new CatalogIndex(schema, catalogId, baseUrl, items);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String catalogId() {
        return catalogId;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public List<CatalogItem> items() {
        return items;
    }

    public CatalogItem findById(String id) {
        for (CatalogItem item : items) {
            if (item.id().equals(id)) {
                return item;
            }
        }
        return null;
    }

    public List<CatalogItem> itemsForPlatform(String platformLabel) {
        List<CatalogItem> out = new ArrayList<>();
        for (CatalogItem item : items) {
            if (item.platform().isEmpty() || item.platform().get().equalsIgnoreCase(platformLabel)) {
                out.add(item);
            }
        }
        return Collections.unmodifiableList(out);
    }
}
