package com.agent1.javaagent.tool.web;

import com.agent1.javaagent.config.AgentRuntimeDefaults;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Tavily {@code POST /search}。密钥只放在 Authorization 头，不写入结果文本。 */
public final class TavilyWebSearchClient {

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_SNIPPET_CHARS = 400;
    private static final int MAX_OUTPUT_CHARS = 6000;

    private final String apiKey;
    private final String baseUrl;
    private final OkHttpClient httpClient;

    public TavilyWebSearchClient(String apiKey, String baseUrl) {
        this(
            apiKey,
            baseUrl,
            new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        );
    }

    TavilyWebSearchClient(String apiKey, String baseUrl, OkHttpClient httpClient) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.isEmpty()) {
            trimmed = AgentRuntimeDefaults.DEFAULT_TAVILY_BASE_URL;
        }
        this.baseUrl = trimmed.replaceAll("/+$", "");
        this.httpClient = httpClient;
    }

    public String search(String query, int maxResults) throws IOException {
        if (apiKey.isEmpty()) {
            throw new IOException("未配置 Tavily API Key");
        }
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            throw new IOException("query 不能为空");
        }
        int limit = Math.max(1, Math.min(maxResults <= 0 ? 5 : maxResults, 8));
        ObjectNode body = MAPPER.createObjectNode();
        body.put("query", q);
        body.put("max_results", limit);
        body.put("search_depth", "basic");
        body.put("include_answer", true);
        return postSearch(q, body);
    }

    /**
     * 搜图（20-A T6）：同一 {@code POST /search} 传 {@code include_images:true}，
     * 返回图片 URL 清单与来源页，供 {@code image_search} 工具使用。
     */
    public String searchImages(String query, int maxResults) throws IOException {
        if (apiKey.isEmpty()) {
            throw new IOException("未配置 Tavily API Key");
        }
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            throw new IOException("query 不能为空");
        }
        int limit = Math.max(1, Math.min(maxResults <= 0 ? 8 : maxResults, 16));
        ObjectNode body = MAPPER.createObjectNode();
        body.put("query", q);
        body.put("max_results", limit);
        body.put("search_depth", "basic");
        body.put("include_answer", false);
        body.put("include_images", true);
        Request request = new Request.Builder()
            .url(baseUrl + "/search")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(MAPPER.writeValueAsBytes(body), JSON))
            .build();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String raw = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new IOException("Tavily HTTP " + response.code() + ": " + clip(raw, 280));
            }
            return formatImages(q, raw);
        }
    }

    private String postSearch(String query, ObjectNode body) throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/search")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(MAPPER.writeValueAsBytes(body), JSON))
            .build();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String raw = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new IOException("Tavily HTTP " + response.code() + ": " + clip(raw, 280));
            }
            return format(query, raw);
        }
    }

    static String format(String query, String raw) throws IOException {
        JsonNode root;
        try {
            root = MAPPER.readTree(raw == null ? "" : raw);
        } catch (IOException e) {
            throw new IOException("Tavily 返回无法解析", e);
        }
        StringBuilder text = new StringBuilder();
        text.append("web_search: ").append(query).append('\n');
        JsonNode answer = root.get("answer");
        if (answer != null && answer.isTextual() && !answer.asText().isBlank()) {
            text.append("answer: ").append(answer.asText().trim()).append('\n');
        }
        JsonNode results = root.get("results");
        int count = results != null && results.isArray() ? results.size() : 0;
        if (count == 0 && (answer == null || !answer.isTextual() || answer.asText().isBlank())) {
            text.append("未找到与「").append(query).append("」相关的网页结果。");
            return text.toString();
        }
        if (count > 0) {
            text.append("results: ").append(count).append('\n');
            int index = 1;
            for (JsonNode item : results) {
                String title = item.path("title").asText("").trim();
                String url = item.path("url").asText("").trim();
                String content = clip(item.path("content").asText("").trim(), MAX_SNIPPET_CHARS);
                text.append(index).append(". ");
                text.append(title.isEmpty() ? "(无标题)" : title);
                if (!url.isEmpty()) {
                    text.append("\n   ").append(url);
                }
                if (!content.isEmpty()) {
                    text.append("\n   ").append(content);
                }
                text.append('\n');
                index++;
                if (text.length() >= MAX_OUTPUT_CHARS) {
                    break;
                }
            }
        }
        if (text.length() > MAX_OUTPUT_CHARS) {
            return text.substring(0, MAX_OUTPUT_CHARS) + "…";
        }
        return text.toString().trim();
    }

    /** 解析 {@code include_images:true} 的响应：images 数组（字符串或对象）+ 来源页 results。 */
    static String formatImages(String query, String raw) throws IOException {
        JsonNode root;
        try {
            root = MAPPER.readTree(raw == null ? "" : raw);
        } catch (IOException e) {
            throw new IOException("Tavily 返回无法解析", e);
        }
        StringBuilder text = new StringBuilder();
        text.append("image_search: ").append(query).append('\n');
        JsonNode images = root.get("images");
        int imageCount = 0;
        int index = 1;
        if (images != null && images.isArray()) {
            java.util.List<ImageSearchUrlPolicy.RankedImage> ranked = new java.util.ArrayList<>();
            for (JsonNode item : images) {
                String url;
                String desc = "";
                if (item.isTextual()) {
                    url = item.asText("").trim();
                } else {
                    url = item.path("url").asText("").trim();
                    desc = item.path("description").asText("").trim();
                }
                if (url.isEmpty() || ImageSearchUrlPolicy.isBlockedForDownload(url)) {
                    continue;
                }
                ranked.add(new ImageSearchUrlPolicy.RankedImage(url, desc, ImageSearchUrlPolicy.scoreImageUrl(url)));
            }
            for (ImageSearchUrlPolicy.RankedImage row : ImageSearchUrlPolicy.sortCandidates(ranked)) {
                imageCount++;
                text.append(index).append(". ").append(row.url()).append('\n');
                if (!row.description().isEmpty()) {
                    text.append("   ").append(clip(row.description(), 160)).append('\n');
                }
                index++;
                if (text.length() >= MAX_OUTPUT_CHARS) {
                    break;
                }
            }
        }
        if (imageCount == 0) {
            text.append("未找到与「").append(query).append("」相关的图片。");
            return text.toString();
        }
        JsonNode results = root.get("results");
        if (results != null && results.isArray() && !results.isEmpty()) {
            text.append("来源页（产物里标注出处用）:");
            int cited = 0;
            for (JsonNode item : results) {
                String url = item.path("url").asText("").trim();
                if (url.isEmpty()) {
                    continue;
                }
                text.append(' ').append(url);
                cited++;
                if (cited >= 3 || text.length() >= MAX_OUTPUT_CHARS) {
                    break;
                }
            }
            text.append('\n');
        }
        text.append("提示：用 images_download({ items:[{url}], dir, name_prefix }) 下载；产物请标注来源。");
        if (text.length() > MAX_OUTPUT_CHARS) {
            return text.substring(0, MAX_OUTPUT_CHARS) + "…";
        }
        return text.toString().trim();
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max) + "…";
    }
}
