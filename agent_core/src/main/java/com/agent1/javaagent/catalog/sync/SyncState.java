package com.agent1.javaagent.catalog.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.agent1.javaagent.util.PathIo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** {@code sync/state.json}（阶段 5.2）。 */
public final class SyncState {

    public static final int SCHEMA_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int schemaVersion;
    private final String manifestUrl;
    private final String lastCheckAt;
    private final String lastApplyAt;
    private final Map<String, InstalledItem> items;

    public SyncState(
        int schemaVersion,
        String manifestUrl,
        String lastCheckAt,
        String lastApplyAt,
        Map<String, InstalledItem> items
    ) {
        this.schemaVersion = schemaVersion;
        this.manifestUrl = manifestUrl == null ? "" : manifestUrl;
        this.lastCheckAt = lastCheckAt;
        this.lastApplyAt = lastApplyAt;
        this.items = Map.copyOf(items);
    }

    public record InstalledItem(String version, String digest, String installedAt, String relativePath) {
        static InstalledItem fromJson(JsonNode node) {
            return new InstalledItem(
                node.path("version").asText(""),
                node.path("digest").asText(""),
                node.path("installedAt").asText(""),
                node.path("relativePath").asText("")
            );
        }

        ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode node = mapper.createObjectNode();
            node.put("version", version);
            node.put("digest", digest);
            node.put("installedAt", installedAt);
            node.put("relativePath", relativePath);
            return node;
        }
    }

    public static SyncState empty(String manifestUrl) {
        return new SyncState(SCHEMA_VERSION, manifestUrl, null, null, Map.of());
    }

    public static SyncState load(Path agentRoot) throws IOException {
        Path file = agentRoot.resolve("sync/state.json");
        if (!Files.isRegularFile(file)) {
            return empty("");
        }
        JsonNode root = MAPPER.readTree(PathIo.readString(file));
        int schema = root.path("schemaVersion").asInt(SCHEMA_VERSION);
        String manifestUrl = root.path("manifestUrl").asText("");
        String lastCheck = root.path("lastCheckAt").asText(null);
        String lastApply = root.path("lastApplyAt").asText(null);
        Map<String, InstalledItem> items = new LinkedHashMap<>();
        JsonNode itemsNode = root.path("items");
        if (itemsNode.isObject()) {
            itemsNode.fields().forEachRemaining(entry -> {
                items.put(entry.getKey(), InstalledItem.fromJson(entry.getValue()));
            });
        }
        return new SyncState(schema, manifestUrl, lastCheck, lastApply, items);
    }

    public void save(Path agentRoot) throws IOException {
        Path file = agentRoot.resolve("sync/state.json");
        Files.createDirectories(agentRoot.resolve("sync"));
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", schemaVersion);
        root.put("manifestUrl", manifestUrl);
        if (lastCheckAt != null) {
            root.put("lastCheckAt", lastCheckAt);
        }
        if (lastApplyAt != null) {
            root.put("lastApplyAt", lastApplyAt);
        }
        ObjectNode itemsNode = MAPPER.createObjectNode();
        for (Map.Entry<String, InstalledItem> entry : items.entrySet()) {
            itemsNode.set(entry.getKey(), entry.getValue().toJson(MAPPER));
        }
        root.set("items", itemsNode);
        PathIo.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    public SyncState withCheckTime(String manifestUrl, Instant when) {
        return new SyncState(schemaVersion, manifestUrl, when.toString(), lastApplyAt, items);
    }

    public SyncState withApplyTime(Instant when, Map<String, InstalledItem> newItems) {
        return new SyncState(schemaVersion, manifestUrl, lastCheckAt, when.toString(), newItems);
    }

    public Optional<InstalledItem> installed(String id) {
        return Optional.ofNullable(items.get(id));
    }

    public Map<String, InstalledItem> items() {
        return items;
    }

    public String manifestUrl() {
        return manifestUrl;
    }
}
