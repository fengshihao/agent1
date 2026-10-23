package com.agent1.javaagent.tool.web;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.agent1.javaagent.tool.script.ExecuteScriptTool;
import com.agent1.javaagent.web.PublicHttpUrl;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 批量下载公开图片到 workspace（20-A T6 演进）：仅 https、魔数校验、并行下载。
 * 单张也用 {@code items: [{url}]}。勿用 bash cp 整理。
 */
public final class ImagesDownloadTool implements AgentTool {

    public static final String TOOL_NAME = "images_download";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_DIR = "imports";
    private static final int MIN_MB = 1;
    private static final int MAX_MB = 50;
    private static final int DEFAULT_MB = 10;
    private static final String USER_AGENT = "Agent1ImagesDownload/1.0";
    static final int MAX_BATCH_SIZE = 32;
    static final int DOWNLOAD_PARALLELISM = 6;

    private final WorkspaceSandbox sandbox;
    private final OkHttpClient http;
    private final boolean allowLoopback;
    private final boolean requireHttps;

    public ImagesDownloadTool(WorkspaceSandbox sandbox) {
        this(sandbox, defaultClient(), false, true);
    }

    ImagesDownloadTool(WorkspaceSandbox sandbox, OkHttpClient http, boolean allowLoopback, boolean requireHttps) {
        this.sandbox = sandbox;
        this.http = http;
        this.allowLoopback = allowLoopback;
        this.requireHttps = requireHttps;
    }

    private static OkHttpClient defaultClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return """
            Download one or more public https images into the workspace (png/jpg/gif/webp/svg, \
            up to 10 MB each). Required: items [{url, path?}, ...]. Optional dir (default imports), \
            name_prefix (e.g. hero → hero-01.png). If path omitted, files are numbered under dir. \
            Extension follows magic bytes, not the URL. Returns total, succeeded, and per-item \
            ok, index, path, width, height, size, type, source (error when ok is false). \
            Do not use bash cp/mv. \
            Cite sources in the deliverable.
            """.trim();
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("required", MAPPER.createArrayNode().add("items"));
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set(
            "items",
            MAPPER.createObjectNode()
                .put("type", "array")
                .put(
                    "description",
                    "Each element: { url (https), path? (workspace-relative override) }. Max "
                        + MAX_BATCH_SIZE + " per call."
                )
        );
        properties.set(
            "dir",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Output directory under workspace, default \"imports\".")
        );
        properties.set(
            "name_prefix",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Optional filename stem: prefix-01.ext, prefix-02.ext, …")
        );
        properties.set(
            "max_mb",
            MAPPER.createObjectNode()
                .put("type", "integer")
                .put("description", "Per-file size cap in MB, 1-50, default 10.")
        );
        schema.set("properties", properties);
        return schema;
    }

    @Override
    public long suggestedTimeoutMs(JsonNode parameters, long fallbackMs) {
        int n = countItems(parameters);
        if (n <= 1) {
            return Math.max(fallbackMs, 60_000L);
        }
        int waves = (n + DOWNLOAD_PARALLELISM - 1) / DOWNLOAD_PARALLELISM;
        long estimate = 30_000L + (long) waves * 25_000L;
        return Math.min(ExecuteScriptTool.MAX_TIMEOUT_MS, Math.max(fallbackMs, estimate));
    }

    private static int countItems(JsonNode parameters) {
        if (parameters == null) {
            return 0;
        }
        JsonNode items = parameters.get("items");
        return items != null && items.isArray() ? items.size() : 0;
    }

    @Override
    public ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) {
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }
        String paramError = validateParameters(parameters);
        if (paramError != null) {
            return ToolExecutionResult.text("错误：" + paramError);
        }
        int maxMb = clampMaxMb(parameters);
        String dir = normalizeDir(parameters.path("dir").asText(DEFAULT_DIR));
        String namePrefix = parameters.path("name_prefix").asText("").trim();
        List<DownloadItem> items = parseItems(parameters.get("items"));
        if (items.size() > MAX_BATCH_SIZE) {
            return ToolExecutionResult.text("错误：items 最多 " + MAX_BATCH_SIZE + " 项");
        }
        return executeAll(items, dir, namePrefix, maxMb, cancellationToken);
    }

    static String validateParameters(JsonNode parameters) {
        if (parameters == null || parameters.isMissingNode()) {
            return "请提供 items 数组，每项含 url（https）";
        }
        JsonNode items = parameters.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return "items 不能为空";
        }
        int index = 0;
        for (JsonNode item : items) {
            if (item == null || !item.isObject()) {
                return "items[" + index + "] 必须是对象";
            }
            String url = item.path("url").asText("").trim();
            if (url.isEmpty()) {
                return "items[" + index + "].url 不能为空";
            }
            index++;
        }
        return null;
    }

    private ToolExecutionResult executeAll(
        List<DownloadItem> items,
        String dir,
        String namePrefix,
        int maxMb,
        CancellationToken cancellationToken
    ) {
        ExecutorService pool = Executors.newFixedThreadPool(
            Math.min(DOWNLOAD_PARALLELISM, items.size())
        );
        try {
            List<Future<ItemOutcome>> futures = new ArrayList<>(items.size());
            for (DownloadItem item : items) {
                if (cancellationToken.isCancelled()) {
                    return ToolExecutionResult.text("错误：执行已取消");
                }
                futures.add(pool.submit(() -> downloadOne(item, dir, namePrefix, maxMb)));
            }
            int succeeded = 0;
            ArrayNode resultItems = MAPPER.createArrayNode();
            for (Future<ItemOutcome> future : futures) {
                if (cancellationToken.isCancelled()) {
                    return ToolExecutionResult.text("错误：执行已取消");
                }
                ItemOutcome outcome;
                try {
                    outcome = future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ToolExecutionResult.text("错误：执行已取消");
                } catch (ExecutionException e) {
                    outcome = ItemOutcome.failure(0, "", "下载失败");
                }
                resultItems.add(outcome.toJson());
                if (outcome.ok) {
                    succeeded++;
                }
            }
            ObjectNode root = MAPPER.createObjectNode();
            root.put("ok", succeeded == items.size());
            root.put("dir", dir);
            root.put("total", items.size());
            root.put("succeeded", succeeded);
            root.set("items", resultItems);
            return ToolExecutionResult.text(root.toString());
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<DownloadItem> parseItems(JsonNode itemsNode) {
        List<DownloadItem> out = new ArrayList<>();
        int index = 0;
        for (JsonNode node : itemsNode) {
            index++;
            String url = node.path("url").asText("").trim();
            String path = node.path("path").asText("").trim();
            out.add(new DownloadItem(index, url, path));
        }
        return out;
    }

    private ItemOutcome downloadOne(DownloadItem item, String dir, String namePrefix, int maxMb) {
        long maxBytes = (long) maxMb * 1024 * 1024;
        final URI uri;
        try {
            uri = PublicHttpUrl.parse(item.url);
            if (requireHttps && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("images_download 只支持 https 图片地址");
            }
            PublicHttpUrl.checkPublic(uri, allowLoopback);
        } catch (IllegalArgumentException e) {
            return ItemOutcome.failure(item.index, item.url, e.getMessage());
        }

        okhttp3.HttpUrl target = okhttp3.HttpUrl.get(uri);
        if (target == null) {
            return ItemOutcome.failure(item.index, item.url, "url 无法解析");
        }
        Request request = new Request.Builder()
            .url(target)
            .header("User-Agent", USER_AGENT)
            .get()
            .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return ItemOutcome.failure(item.index, item.url, "下载失败 HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                return ItemOutcome.failure(item.index, item.url, "下载响应为空");
            }
            long declared = body.contentLength();
            if (declared > maxBytes) {
                return ItemOutcome.failure(
                    item.index,
                    item.url,
                    "图片超过大小上限 " + maxMb + "MB（声明 " + declared + " 字节）"
                );
            }
            byte[] bytes;
            try (InputStream in = body.byteStream()) {
                bytes = readCapped(in, maxBytes);
            }
            if (bytes == null) {
                return ItemOutcome.failure(item.index, item.url, "图片超过大小上限 " + maxMb + "MB");
            }
            String type = sniffType(bytes);
            if (type == null) {
                return ItemOutcome.failure(item.index, item.url, "不是支持的图片（png/jpg/gif/webp/svg）");
            }
            String relative = item.explicitPath.isEmpty()
                ? buildDefaultPath(dir, namePrefix, item.index, type)
                : ensureExtension(item.explicitPath, type);
            Path dest = sandbox.resolveWrite(relative);
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(dest, bytes);
            String display = sandbox.displayPath(dest);
            int[] wh = ImageRasterDimensions.tryRead(bytes, type);
            int width = wh == null ? 0 : wh[0];
            int height = wh == null ? 0 : wh[1];
            return ItemOutcome.success(item.index, item.url, display, bytes.length, type, width, height);
        } catch (IOException | SecurityException e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                ? "下载失败"
                : e.getMessage();
            return ItemOutcome.failure(item.index, item.url, message);
        }
    }

    static String normalizeDir(String raw) {
        String d = raw == null ? "" : raw.trim().replace('\\', '/');
        if (d.isEmpty()) {
            return DEFAULT_DIR;
        }
        while (d.startsWith("./")) {
            d = d.substring(2);
        }
        while (d.startsWith("/")) {
            d = d.substring(1);
        }
        while (d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        return d.isEmpty() ? DEFAULT_DIR : d;
    }

    static String buildDefaultPath(String dir, String namePrefix, int index, String type) {
        String stem = namePrefix.isEmpty()
            ? String.format(Locale.ROOT, "%02d", index)
            : namePrefix + "-" + String.format(Locale.ROOT, "%02d", index);
        return dir + "/" + stem + "." + type;
    }

    static String ensureExtension(String path, String type) {
        String p = path.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        String file = slash >= 0 ? p.substring(slash + 1) : p;
        int dot = file.lastIndexOf('.');
        if (dot > 0) {
            return p;
        }
        return p + "." + type;
    }

    private record DownloadItem(int index, String url, String explicitPath) {
    }

    private static final class ItemOutcome {
        final boolean ok;
        final int index;
        final String source;
        final String path;
        final long byteLength;
        final String type;
        final int width;
        final int height;
        final String error;

        private ItemOutcome(
            boolean ok,
            int index,
            String source,
            String path,
            long byteLength,
            String type,
            int width,
            int height,
            String error
        ) {
            this.ok = ok;
            this.index = index;
            this.source = source;
            this.path = path;
            this.byteLength = byteLength;
            this.type = type;
            this.width = width;
            this.height = height;
            this.error = error;
        }

        static ItemOutcome success(
            int index,
            String source,
            String path,
            long byteLength,
            String type,
            int width,
            int height
        ) {
            return new ItemOutcome(true, index, source, path, byteLength, type, width, height, null);
        }

        static ItemOutcome failure(int index, String source, String error) {
            return new ItemOutcome(false, index, source, "", 0, "", 0, 0, error);
        }

        ObjectNode toJson() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("ok", ok);
            node.put("index", index);
            node.put("source", source);
            if (ok) {
                node.put("path", path);
                node.put("size", formatByteSize(byteLength));
                node.put("type", type);
                if (width > 0 && height > 0) {
                    node.put("width", width);
                    node.put("height", height);
                }
            } else {
                node.put("path", "");
                node.put("size", "0 B");
                node.put("type", "");
                node.put("error", error == null ? "下载失败" : error);
            }
            return node;
        }
    }

    private static int clampMaxMb(JsonNode parameters) {
        if (parameters == null || !parameters.has("max_mb") || !parameters.get("max_mb").canConvertToInt()) {
            return DEFAULT_MB;
        }
        int v = parameters.get("max_mb").asInt(DEFAULT_MB);
        return Math.max(MIN_MB, Math.min(v, MAX_MB));
    }

    private static byte[] readCapped(InputStream in, long maxBytes) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) >= 0) {
            total += n;
            if (total > maxBytes) {
                return null;
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    static String formatByteSize(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    static String sniffType(byte[] b) {
        if (b.length >= 8
            && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        if (b.length >= 3 && b[0] == (byte) 0xff && b[1] == (byte) 0xd8 && b[2] == (byte) 0xff) {
            return "jpg";
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        String head = new String(b, 0, Math.min(b.length, 512), StandardCharsets.UTF_8).trim();
        if (head.startsWith("\uFEFF")) {
            head = head.substring(1).trim();
        }
        if (head.startsWith("<?xml") || head.startsWith("<svg")) {
            return "svg";
        }
        return null;
    }
}
