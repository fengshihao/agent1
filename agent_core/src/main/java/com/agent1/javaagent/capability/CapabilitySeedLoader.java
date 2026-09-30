package com.agent1.javaagent.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 从 classpath seed JSONL 加载能力条目。 */
public final class CapabilitySeedLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SEED_RESOURCE = "/agent-home/capabilities/search-index.seed.jsonl";

    private CapabilitySeedLoader() {
    }

    public static List<CapabilityRecord> loadBundledSeed() {
        List<CapabilityRecord> out = new ArrayList<>();
        try (InputStream in = CapabilitySeedLoader.class.getResourceAsStream(SEED_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing bundled seed: " + SEED_RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    out.add(parseLine(line));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("read capability seed failed", e);
        }
        return out;
    }

    static CapabilityRecord parseLine(String jsonLine) throws Exception {
        JsonNode node = MAPPER.readTree(jsonLine);
        String tags = tagsToCsv(node.get("tags"));
        return new CapabilityRecord(
            node.path("id").asText(""),
            node.path("kind").asText(""),
            node.path("title").asText(""),
            node.path("summary").asText(""),
            tags,
            node.path("platforms").asText("any"),
            node.path("entry").asText(""),
            node.path("doc_path").asText(""),
            node.path("doc_anchor").asText(""),
            node.has("requires") ? node.get("requires").toString() : "",
            node.path("source").asText("bundled"),
            node.path("weight").asDouble(1.0)
        );
    }

    private static String tagsToCsv(JsonNode tagsNode) {
        if (tagsNode == null || !tagsNode.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode t : tagsNode) {
            String text = t.asText("").trim();
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
}
