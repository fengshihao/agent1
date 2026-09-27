package com.agent1.javaagent.catalog.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agent1.javaagent.util.PathIo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Manifest URL：env {@code AGENT1_CATALOG_MANIFEST_URL} 优先，其次 agent.manifest.json。 */
public final class CatalogSyncConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CatalogSyncConfig() {
    }

    public static String resolveManifestUrl(Path agentRoot) {
        String fromEnv = System.getenv("AGENT1_CATALOG_MANIFEST_URL");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        Path manifest = agentRoot.resolve("agent.manifest.json");
        if (!Files.isRegularFile(manifest)) {
            return "";
        }
        try {
            JsonNode root = MAPPER.readTree(PathIo.readString(manifest));
            String url = root.path("catalog").path("manifestUrl").asText("");
            return url == null ? "" : url.trim();
        } catch (IOException ignored) {
            return "";
        }
    }
}
