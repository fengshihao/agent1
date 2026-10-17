package com.agent1.javaagent.tool.web;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.agent1.javaagent.web.PublicHttpUrl;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 下载公开图片到 workspace（20-A T6）：仅 https、拒绝本机/内网地址、
 * ≤ max_mb（默认 10）、魔数校验（png/jpg/gif/webp/svg），扩展名按真实类型定。
 * 供 deck HTML（html2pptx 自动内联）与 docx/pptx addImage 共用素材。
 */
public final class ImageDownloadTool implements AgentTool {

    public static final String TOOL_NAME = "image_download";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MIN_MB = 1;
    private static final int MAX_MB = 50;
    private static final int DEFAULT_MB = 10;
    private static final String USER_AGENT = "Agent1ImageDownload/1.0";

    private final WorkspaceSandbox sandbox;
    private final OkHttpClient http;
    /** 只给测试里的本地假服务器用。 */
    private final boolean allowLoopback;
    /** 生产仅 https；测试假服务器是 http。 */
    private final boolean requireHttps;

    public ImageDownloadTool(WorkspaceSandbox sandbox) {
        this(sandbox, defaultClient(), false, true);
    }

    ImageDownloadTool(WorkspaceSandbox sandbox, OkHttpClient http, boolean allowLoopback, boolean requireHttps) {
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
            Download a public https image into the workspace (png/jpg/gif/webp/svg, \
            up to 10 MB by default). The file type is verified by magic bytes. \
            Use paths from image_search, then reference the downloaded file in decks \
            (html2pptx inlines workspace images automatically) or documents. \
            Cite the source page in the final deliverable.
            """.trim();
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("required", MAPPER.createArrayNode().add("url"));
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set(
            "url",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Public https image URL, from image_search results.")
        );
        properties.set(
            "path",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Workspace-relative output path. Default imports/<name>.<ext by type>.")
        );
        properties.set(
            "max_mb",
            MAPPER.createObjectNode()
                .put("type", "integer")
                .put("description", "Size cap in MB, 1-50, default 10.")
        );
        schema.set("properties", properties);
        return schema;
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
        String rawUrl = parameters == null ? "" : parameters.path("url").asText("").trim();
        if (rawUrl.isEmpty()) {
            return ToolExecutionResult.text("错误：url 不能为空");
        }
        int maxMb = clampMaxMb(parameters);
        long maxBytes = (long) maxMb * 1024 * 1024;
        String rawPath = parameters == null ? "" : parameters.path("path").asText("").trim();

        final URI uri;
        try {
            uri = PublicHttpUrl.parse(rawUrl);
            if (requireHttps && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("image_download 只支持 https 图片地址");
            }
            PublicHttpUrl.checkPublic(uri, allowLoopback);
        } catch (IllegalArgumentException e) {
            return ToolExecutionResult.text("错误：" + e.getMessage());
        }

        okhttp3.HttpUrl target = okhttp3.HttpUrl.get(uri);
        if (target == null) {
            return ToolExecutionResult.text("错误：url 无法解析");
        }
        Request request = new Request.Builder()
            .url(target)
            .header("User-Agent", USER_AGENT)
            .get()
            .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return ToolExecutionResult.text("错误：下载失败 HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                return ToolExecutionResult.text("错误：下载响应为空");
            }
            long declared = body.contentLength();
            if (declared > maxBytes) {
                return ToolExecutionResult.text("错误：图片超过大小上限 " + maxMb + "MB（声明 " + declared + " 字节）");
            }
            byte[] bytes;
            try (InputStream in = body.byteStream()) {
                bytes = readCapped(in, maxBytes);
            }
            if (bytes == null) {
                return ToolExecutionResult.text("错误：图片超过大小上限 " + maxMb + "MB");
            }
            String type = sniffType(bytes);
            if (type == null) {
                return ToolExecutionResult.text("错误：不是支持的图片（png/jpg/gif/webp/svg，按内容校验）");
            }
            Path dest = sandbox.resolveWrite(rawPath.isEmpty() ? defaultPath(uri, type) : rawPath);
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(dest, bytes);
            ObjectNode node = MAPPER.createObjectNode();
            node.put("path", sandbox.displayPath(dest));
            node.put("bytes", bytes.length);
            node.put("type", type);
            node.put("source", rawUrl);
            return ToolExecutionResult.text(node.toString());
        } catch (IOException | SecurityException e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                ? "下载失败"
                : e.getMessage();
            return ToolExecutionResult.text("错误：" + message);
        }
    }

    private static int clampMaxMb(JsonNode parameters) {
        if (parameters == null || !parameters.has("max_mb") || !parameters.get("max_mb").canConvertToInt()) {
            return DEFAULT_MB;
        }
        int v = parameters.get("max_mb").asInt(DEFAULT_MB);
        return Math.max(MIN_MB, Math.min(v, MAX_MB));
    }

    /** 读满上限即断流返回 null（服务器谎报 Content-Length 时兜底）。 */
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

    /** 魔数嗅探；svg 按文本前缀（容忍 BOM 与空白）。 */
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

    private static String defaultPath(URI uri, String type) {
        String raw = uri.getPath();
        String stem = "";
        if (raw != null) {
            int slash = raw.lastIndexOf('/');
            String last = slash >= 0 ? raw.substring(slash + 1) : raw;
            int dot = last.lastIndexOf('.');
            if (dot > 0) {
                last = last.substring(0, dot);
            }
            last = last.replaceAll("[^A-Za-z0-9._-]", "");
            stem = last.length() > 48 ? last.substring(0, 48) : last;
        }
        if (stem.isEmpty()) {
            stem = "image";
        }
        return "imports/" + stem.toLowerCase(Locale.ROOT) + "." + type;
    }
}
