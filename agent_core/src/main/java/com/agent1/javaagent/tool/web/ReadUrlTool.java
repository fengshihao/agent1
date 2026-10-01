package com.agent1.javaagent.tool.web;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.agent1.javaagent.web.HtmlArticleExtractor;
import com.agent1.javaagent.web.PublicHttpUrl;
import com.agent1.javaagent.web.TavilyPageExtract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 读取公开 http(s) 页面的标题和正文。配置了 {@code TAVILY_API_KEY} 时，先用 Tavily Extract
 * 抽同一个 URL；失败再在本地抓取，并用 {@link HtmlArticleExtractor} 抽正文。
 */
public final class ReadUrlTool implements AgentTool {
    static final int MAX_BYTES = 1_500_000;
    private static final int MAX_REDIRECTS = 5;
    private static final int DEFAULT_MAX_CHARS = 12_000;
    private static final int HARD_MAX_CHARS = 32_000;
    private static final String USER_AGENT = "Agent1ReadUrl/1.0";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern CHARSET = Pattern.compile(
        "(?i)charset\\s*=\\s*[\"']?([A-Za-z0-9._-]+)"
    );

    private final OkHttpClient http;
    private final boolean allowLoopback;
    private final TavilyPageExtract tavily;

    public ReadUrlTool() {
        this(defaultClient(), false, TavilyPageExtract.fromEnvironment());
    }

    /** {@code allowLoopback} 只给测试里的本地假服务器用。不调用 Tavily。 */
    public ReadUrlTool(OkHttpClient http, boolean allowLoopback) {
        this(http, allowLoopback, TavilyPageExtract.disabled());
    }

    public ReadUrlTool(OkHttpClient http, boolean allowLoopback, TavilyPageExtract tavily) {
        this.http = http;
        this.allowLoopback = allowLoopback;
        this.tavily = tavily == null ? TavilyPageExtract.disabled() : tavily;
    }

    @Override
    public String name() {
        return "read_url";
    }

    @Override
    public String description() {
        return """
            Fetch a public http(s) URL and return the page title plus main article text. \
            When TAVILY_API_KEY is set, Tavily Extract runs first on that same URL; \
            if it fails, a local fetch extracts the article instead. \
            Refuses loopback, private, and link-local hosts. For workspace files use read_file.
            """.trim();
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set(
            "url",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Public http or https page URL.")
        );
        properties.set(
            "max_chars",
            MAPPER.createObjectNode()
                .put("type", "integer")
                .put("description", "Max characters of extracted text. Default 12000, max 32000.")
        );
        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode().add("url"));
        return schema;
    }

    @Override
    public ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) {
        if (parameters == null || !parameters.has("url")) {
            return ToolExecutionResult.text("错误：url 不能为空");
        }
        String rawUrl = parameters.path("url").asText("").trim();
        int maxChars = parameters.path("max_chars").asInt(DEFAULT_MAX_CHARS);
        if (maxChars <= 0) {
            maxChars = DEFAULT_MAX_CHARS;
        }
        maxChars = Math.min(maxChars, HARD_MAX_CHARS);
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }
        boolean tavilyFailed = false;
        try {
            URI parsed = PublicHttpUrl.parse(rawUrl);
            PublicHttpUrl.checkPublic(parsed, allowLoopback);
            String pageUrl = parsed.toString();
            if (tavily.enabled()) {
                Optional<TavilyPageExtract.Extracted> remote = tavily.extract(http, pageUrl);
                if (remote.isPresent()) {
                    TavilyPageExtract.Extracted extracted = remote.get();
                    return ToolExecutionResult.text(render(
                        pageUrl,
                        extracted.title(),
                        extracted.text(),
                        maxChars,
                        "tavily"
                    ));
                }
                tavilyFailed = true;
            }
            Fetched fetched = fetch(pageUrl);
            return ToolExecutionResult.text(format(fetched, maxChars));
        } catch (IllegalArgumentException e) {
            String message = e.getMessage() == null ? "读取失败" : e.getMessage();
            if (tavilyFailed) {
                return ToolExecutionResult.text("错误：Tavily 抽取失败，本地读取也失败：" + message);
            }
            return ToolExecutionResult.text("错误：" + message);
        } catch (IOException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (tavilyFailed) {
                return ToolExecutionResult.text("错误：Tavily 抽取失败，本地读取也失败：" + message);
            }
            return ToolExecutionResult.text("错误：请求失败：" + message);
        }
    }

    private Fetched fetch(String rawUrl) throws IOException {
        String current = PublicHttpUrl.parse(rawUrl).toString();
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            URI uri = PublicHttpUrl.parse(current);
            PublicHttpUrl.checkPublic(uri, allowLoopback);
            HttpUrl httpUrl = HttpUrl.parse(uri.toString());
            if (httpUrl == null) {
                throw new IllegalArgumentException("url 无法解析");
            }
            Request request = new Request.Builder()
                .url(httpUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.8")
                .get()
                .build();
            try (Response response = http.newCall(request).execute()) {
                int code = response.code();
                if (code >= 300 && code < 400) {
                    String location = response.header("Location");
                    if (location == null || location.isBlank()) {
                        throw new IllegalArgumentException("重定向缺少 Location");
                    }
                    HttpUrl next = httpUrl.resolve(location);
                    if (next == null) {
                        throw new IllegalArgumentException("重定向地址无法解析");
                    }
                    if (hop == MAX_REDIRECTS) {
                        throw new IllegalArgumentException("重定向次数过多");
                    }
                    current = next.toString();
                    continue;
                }
                if (code < 200 || code >= 300) {
                    throw new IllegalArgumentException("HTTP " + code);
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IllegalArgumentException("响应为空");
                }
                long advertised = body.contentLength();
                if (advertised > MAX_BYTES) {
                    throw new IllegalArgumentException("页面超过 " + MAX_BYTES + " 字节");
                }
                MediaType mediaType = body.contentType();
                String contentType = mediaType == null ? "" : mediaType.toString();
                byte[] bytes = readAtMost(body.byteStream(), MAX_BYTES);
                return new Fetched(httpUrl.toString(), contentType, bytes);
            }
        }
        throw new IllegalArgumentException("重定向次数过多");
    }

    private static String format(Fetched fetched, int maxChars) {
        Kind kind = kindOf(fetched.contentType, fetched.body);
        if (kind == Kind.REJECT) {
            throw new IllegalArgumentException("不是可读取的文本页面");
        }
        Charset charset = charsetOf(fetched.contentType, fetched.body);
        String decoded = new String(stripBom(fetched.body), charset);
        String title = "";
        String text;
        if (kind == Kind.HTML) {
            HtmlArticleExtractor.Article article = HtmlArticleExtractor.extract(decoded);
            title = article.title();
            text = article.text();
        } else {
            text = decoded.trim();
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("未能抽出正文");
        }
        return render(fetched.finalUrl, title, text, maxChars, "local");
    }

    private static String render(String url, String title, String text, int maxChars, String source) {
        boolean truncated = false;
        String body = text;
        if (body.length() > maxChars) {
            int cut = body.lastIndexOf('\n', maxChars);
            if (cut < maxChars / 2) {
                cut = maxChars;
            }
            body = body.substring(0, cut).trim();
            truncated = true;
        }
        StringBuilder out = new StringBuilder();
        out.append("URL: ").append(url).append('\n');
        out.append("TITLE: ").append(title == null || title.isBlank() ? "(none)" : title).append('\n');
        out.append("SOURCE: ").append(source).append('\n');
        out.append("CONTENT:\n").append(body);
        if (truncated) {
            out.append("\n\n（已截断，可增大 max_chars）");
        }
        return out.toString();
    }

    private static Kind kindOf(String contentType, byte[] body) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        int semi = ct.indexOf(';');
        String mime = semi < 0 ? ct.trim() : ct.substring(0, semi).trim();
        if (mime.contains("html") || "application/xhtml+xml".equals(mime)) {
            return Kind.HTML;
        }
        if (mime.startsWith("text/")
            || "application/json".equals(mime)
            || "application/xml".equals(mime)
            || mime.endsWith("+xml")
            || "application/javascript".equals(mime)) {
            return Kind.PLAIN;
        }
        if (mime.isEmpty() || "application/octet-stream".equals(mime)) {
            if (looksLikeHtml(body)) {
                return Kind.HTML;
            }
            if (looksLikeText(body)) {
                return Kind.PLAIN;
            }
        }
        return Kind.REJECT;
    }

    private static boolean looksLikeHtml(byte[] body) {
        String head = new String(body, 0, Math.min(body.length, 512), StandardCharsets.ISO_8859_1)
            .toLowerCase(Locale.ROOT);
        return head.contains("<html") || head.contains("<body") || head.contains("<article")
            || head.contains("<!doctype html");
    }

    private static boolean looksLikeText(byte[] body) {
        int n = Math.min(body.length, 512);
        for (int i = 0; i < n; i++) {
            if (body[i] == 0) {
                return false;
            }
        }
        return n > 0;
    }

    static Charset charsetOf(String contentType, byte[] body) {
        Matcher header = CHARSET.matcher(contentType == null ? "" : contentType);
        if (header.find() && Charset.isSupported(header.group(1))) {
            return Charset.forName(header.group(1));
        }
        int n = Math.min(body.length, 8192);
        String head = new String(body, 0, n, StandardCharsets.ISO_8859_1);
        Matcher meta = CHARSET.matcher(head);
        if (meta.find() && Charset.isSupported(meta.group(1))) {
            return Charset.forName(meta.group(1));
        }
        return StandardCharsets.UTF_8;
    }

    private static byte[] stripBom(byte[] body) {
        if (body.length >= 3
            && (body[0] & 0xff) == 0xef
            && (body[1] & 0xff) == 0xbb
            && (body[2] & 0xff) == 0xbf) {
            byte[] stripped = new byte[body.length - 3];
            System.arraycopy(body, 3, stripped, 0, stripped.length);
            return stripped;
        }
        return body;
    }

    private static byte[] readAtMost(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            if (total + n > max) {
                throw new IllegalArgumentException("页面超过 " + max + " 字节");
            }
            out.write(buf, 0, n);
            total += n;
        }
        return out.toByteArray();
    }

    private static OkHttpClient defaultClient() {
        return new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .dns(publicDns(false))
            .build();
    }

    /** 连接时再查一次地址，避免解析结果在检查之后被改成内网。 */
    private static Dns publicDns(boolean allowLoopback) {
        return hostname -> {
            List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
            for (InetAddress address : addresses) {
                if (PublicHttpUrl.isDisallowed(address, allowLoopback)) {
                    throw new UnknownHostException("拒绝访问本机或内网地址");
                }
            }
            return addresses;
        };
    }

    private enum Kind {
        HTML, PLAIN, REJECT
    }

    private static final class Fetched {
        private final String finalUrl;
        private final String contentType;
        private final byte[] body;

        private Fetched(String finalUrl, String contentType, byte[] body) {
            this.finalUrl = finalUrl;
            this.contentType = contentType;
            this.body = body;
        }
    }
}
