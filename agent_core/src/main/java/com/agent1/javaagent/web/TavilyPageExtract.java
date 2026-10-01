package com.agent1.javaagent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Optional;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 用 Tavily Extract 抽同一个 URL 的正文。失败时返回空，交给本地抓取。
 */
public final class TavilyPageExtract {

    public static final String DEFAULT_ENDPOINT = "https://api.tavily.com/extract";
    private static final MediaType JSON = MediaType.get("application/json");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MIN_CHARS = 20;
    private static final int MAX_RESPONSE_BYTES = 2_000_000;

    private final String apiKey;
    private final String endpoint;

    public TavilyPageExtract(String apiKey, String endpoint) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.endpoint = endpoint == null ? "" : endpoint.trim();
    }

    public static TavilyPageExtract disabled() {
        return new TavilyPageExtract("", "");
    }

    public static TavilyPageExtract fromEnvironment() {
        String key = System.getenv("TAVILY_API_KEY");
        if (key == null || key.isBlank()) {
            return disabled();
        }
        return new TavilyPageExtract(key, DEFAULT_ENDPOINT);
    }

    public boolean enabled() {
        return !apiKey.isEmpty() && !endpoint.isEmpty();
    }

    /** 成功时带上正文；网络错误、鉴权失败、空结果都算失败。 */
    public Optional<Extracted> extract(OkHttpClient http, String pageUrl) {
        if (!enabled() || pageUrl == null || pageUrl.isBlank()) {
            return Optional.empty();
        }
        ObjectNode body = MAPPER.createObjectNode();
        body.put("urls", pageUrl);
        body.put("extract_depth", "basic");
        body.put("format", "markdown");
        Request request = new Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(body.toString(), JSON))
            .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return Optional.empty();
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                return Optional.empty();
            }
            byte[] bytes = responseBody.bytes();
            if (bytes.length > MAX_RESPONSE_BYTES) {
                return Optional.empty();
            }
            return readContent(MAPPER.readTree(bytes));
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<Extracted> readContent(JsonNode root) {
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            return Optional.empty();
        }
        for (JsonNode item : results) {
            String content = item.path("raw_content").asText("").trim();
            if (content.length() >= MIN_CHARS) {
                return Optional.of(new Extracted(titleOf(content), content));
            }
        }
        return Optional.empty();
    }

    static String titleOf(String markdown) {
        for (String line : markdown.split("\n", -1)) {
            String trimmed = line.trim();
            int level = 0;
            while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                level++;
            }
            if (level >= 1 && level <= 6 && level < trimmed.length() && trimmed.charAt(level) == ' ') {
                return trimmed.substring(level + 1).trim();
            }
        }
        return "";
    }

    public static final class Extracted {
        private final String title;
        private final String text;

        public Extracted(String title, String text) {
            this.title = title == null ? "" : title;
            this.text = text == null ? "" : text;
        }

        public String title() {
            return title;
        }

        public String text() {
            return text;
        }
    }
}
