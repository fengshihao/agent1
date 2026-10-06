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
    /** 微智按 API 导出的调用卡。真源：weizhi {@code docs/api-cards.jsonl}。 */
    private static final String WEIZHI_CARDS_RESOURCE = "/agent-home/capabilities/weizhi-api-cards.jsonl";

    private CapabilitySeedLoader() {
    }

    public static List<CapabilityRecord> loadBundledSeed() {
        List<CapabilityRecord> out = new ArrayList<>();
        out.addAll(loadResource(SEED_RESOURCE, false));
        out.addAll(loadResource(WEIZHI_CARDS_RESOURCE, true));
        return out;
    }

    private static List<CapabilityRecord> loadResource(String resource, boolean weizhiCard) {
        List<CapabilityRecord> out = new ArrayList<>();
        try (InputStream in = CapabilitySeedLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing bundled seed: " + resource);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    out.add(weizhiCard ? parseWeizhiCard(line) : parseLine(line));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("read capability seed failed: " + resource, e);
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
            platformsToStored(node.get("platforms")),
            node.path("entry").asText(""),
            node.path("doc_path").asText(""),
            node.path("doc_anchor").asText(""),
            node.has("requires") ? node.get("requires").toString() : "",
            node.path("source").asText("bundled"),
            node.path("weight").asDouble(1.0)
        );
    }

    /** 微智卡片没有 kind / doc_path；{@code params} 放进 requires_json，不作为下一步文档。 */
    static CapabilityRecord parseWeizhiCard(String jsonLine) throws Exception {
        JsonNode node = MAPPER.readTree(jsonLine);
        String id = node.path("id").asText("");
        String kind = id.startsWith("android.") ? "caps" : "catalog_script";
        String requires = node.has("params") && !node.get("params").isNull() ? node.get("params").toString() : "";
        double weight = id.startsWith("docx.") ? 1.2 : 1.1;
        return new CapabilityRecord(
            id,
            kind,
            node.path("title").asText(""),
            node.path("summary").asText(""),
            tagsToCsv(node.get("tags")),
            platformsToStored(node.get("platforms")),
            node.path("entry").asText(""),
            "",
            "",
            requires,
            "weizhi",
            weight
        );
    }

    private static String platformsToStored(JsonNode platformsNode) {
        if (platformsNode == null || platformsNode.isMissingNode()) {
            return "any";
        }
        if (platformsNode.isTextual()) {
            String text = platformsNode.asText("any").trim();
            return text.isEmpty() ? "any" : text;
        }
        if (platformsNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode p : platformsNode) {
                String text = p.asText("").trim().toLowerCase();
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
        return "any";
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
