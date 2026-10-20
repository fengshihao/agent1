package com.agent1.javaagent.modelcatalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * <a href="https://models.dev">models.dev</a> 公开目录；不需要用户 API Key。
 */
public final class ModelsDevCatalogClient {

    public static final String DEFAULT_ENDPOINT = "https://models.dev/api.json";

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final String endpoint;

    public ModelsDevCatalogClient() {
        this(
            new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build(),
            new ObjectMapper(),
            DEFAULT_ENDPOINT
        );
    }

    ModelsDevCatalogClient(OkHttpClient http, ObjectMapper mapper, String endpoint) {
        this.http = http;
        this.mapper = mapper;
        this.endpoint = endpoint;
    }

    public List<CatalogModel> fetch(List<String> providerKeys) throws IOException {
        if (providerKeys == null || providerKeys.isEmpty()) {
            return List.of();
        }
        Request request = new Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .header("User-Agent", "agent1")
            .get()
            .build();
        try (Response response = http.newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw new IOException("模型目录 HTTP " + response.code());
            }
            return parse(text, providerKeys);
        }
    }

    public List<CatalogModel> parse(String body, List<String> providerKeys) throws IOException {
        JsonNode root = mapper.readTree(body);
        Map<String, CatalogModel> merged = new LinkedHashMap<>();
        for (String key : providerKeys) {
            JsonNode models = root.path(key).path("models");
            if (!models.isObject()) {
                continue;
            }
            models.fields().forEachRemaining(entry -> {
                JsonNode obj = entry.getValue();
                if (!obj.isObject()) {
                    return;
                }
                CatalogModel model = toModel(obj);
                if (model == null) {
                    return;
                }
                CatalogModel existing = merged.get(model.id());
                if (existing == null || model.releaseDate().compareTo(existing.releaseDate()) > 0) {
                    merged.put(model.id(), model);
                }
            });
        }
        List<CatalogModel> out = new ArrayList<>(merged.values());
        out.sort(
            Comparator.comparing(CatalogModel::releaseDate, Comparator.reverseOrder())
                .thenComparing(CatalogModel::id)
        );
        return out;
    }

    private static CatalogModel toModel(JsonNode obj) {
        if (!outputsText(obj)) {
            return null;
        }
        String id = text(obj, "id");
        if (id.isEmpty()) {
            return null;
        }
        String name = text(obj, "name");
        if (name.isEmpty()) {
            name = id;
        }
        return new CatalogModel(id, name, text(obj, "release_date"));
    }

    private static boolean outputsText(JsonNode obj) {
        JsonNode output = obj.path("modalities").path("output");
        if (!output.isArray() || output.isEmpty()) {
            return true;
        }
        for (JsonNode element : output) {
            if ("text".equals(element.asText(""))) {
                return true;
            }
        }
        return false;
    }

    private static String text(JsonNode obj, String key) {
        JsonNode node = obj.get(key);
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("").trim();
    }

    public record CatalogModel(String id, String name, String releaseDate) {
    }
}
