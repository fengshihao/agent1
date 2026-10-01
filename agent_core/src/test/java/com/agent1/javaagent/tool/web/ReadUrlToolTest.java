package com.agent1.javaagent.tool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.Charset;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.Test;

class ReadUrlToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ARTICLE = """
        <html><head>
        <meta property="og:title" content="杭州周末">
        </head><body>
        <nav><a href="/a">首页导航</a></nav>
        <article>
        <p>周六早上从西湖出发，沿着苏堤走到花港观鱼，再坐船到湖心亭，风很轻。</p>
        <p>下午去中国美院象山校区看展览，晚上回河坊街吃片儿川。</p>
        </article>
        <footer>版权所有 不要出现</footer>
        </body></html>
        """;

    @Test
    void readsTitleAndArticleFromHtml() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(html(ARTICLE));
            server.start();
            ReadUrlTool tool = tool(true);

            ToolExecutionResult result = tool.execute(
                "c1",
                params(server.url("/story").toString(), 0),
                new CancellationToken(),
                null
            );

            String text = result.getText();
            assertTrue(text.contains("TITLE: 杭州周末"), text);
            assertTrue(text.contains("花港观鱼"), text);
            assertFalse(text.contains("首页导航"), text);
            assertFalse(text.contains("版权所有"), text);
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test
    void followsRedirectThenExtracts() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse()
                .setResponseCode(302)
                .setHeader("Location", server.url("/page").toString()));
            server.enqueue(html(ARTICLE));
            ReadUrlTool tool = tool(true);

            String text = tool.execute(
                "c1",
                params(server.url("/start").toString(), 0),
                new CancellationToken(),
                null
            ).getText();

            assertTrue(text.contains("/page"), text);
            assertTrue(text.contains("片儿川"), text);
            assertEquals(2, server.getRequestCount());
        }
    }

    @Test
    void decodesMetaCharset() throws Exception {
        String html = """
            <html><head><meta charset="gbk"></head><body><article>
            <p>你好，这是一段足够长的中文正文，用来确认字符集和正文抽取都工作，再写到西湖边的苏堤上。</p>
            </article></body></html>
            """;
        byte[] gbk = html.getBytes(Charset.forName("GBK"));
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody(new Buffer().write(gbk)));
            server.start();

            String text = tool(true).execute(
                "c1",
                params(server.url("/gbk").toString(), 0),
                new CancellationToken(),
                null
            ).getText();

            assertTrue(text.contains("你好"), text);
            assertTrue(text.contains("西湖边"), text);
        }
    }

    @Test
    void rejectsBinaryAndHttpError() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                .setHeader("Content-Type", "image/png")
                .setBody("not-text"));
            server.enqueue(new MockResponse().setResponseCode(404));
            server.start();
            ReadUrlTool tool = tool(true);
            String base = server.url("/x").toString();

            String binary = tool.execute("c1", params(base, 0), new CancellationToken(), null).getText();
            String missing = tool.execute("c2", params(base, 0), new CancellationToken(), null).getText();

            assertTrue(binary.contains("不是可读取的文本页面"), binary);
            assertTrue(missing.contains("HTTP 404"), missing);
        }
    }

    @Test
    void truncatesLongArticle() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(html(ARTICLE));
            server.start();

            String text = tool(true).execute(
                "c1",
                params(server.url("/story").toString(), 40),
                new CancellationToken(),
                null
            ).getText();

            assertTrue(text.contains("已截断"), text);
        }
    }

    @Test
    void refusesPrivateRedirectAndLoopbackByDefault() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "http://169.254.169.254/latest"));
            server.start();

            String redirected = tool(true).execute(
                "c1",
                params(server.url("/go").toString(), 0),
                new CancellationToken(),
                null
            ).getText();
            String loopback = tool(false).execute(
                "c2",
                params("http://127.0.0.1/secret", 0),
                new CancellationToken(),
                null
            ).getText();

            assertTrue(redirected.contains("拒绝访问"), redirected);
            assertTrue(loopback.contains("拒绝访问"), loopback);
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test
    void returnsPlainTextAsContent() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody("这是一段纯文本正文，没有 HTML 标签。"));
            server.start();

            String text = tool(true).execute(
                "c1",
                params(server.url("/note").toString(), 0),
                new CancellationToken(),
                null
            ).getText();

            assertTrue(text.contains("TITLE: (none)"), text);
            assertTrue(text.contains("纯文本正文"), text);
        }
    }

    private static ReadUrlTool tool(boolean allowLoopback) {
        OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build();
        return new ReadUrlTool(client, allowLoopback);
    }

    private static MockResponse html(String body) {
        return new MockResponse()
            .setHeader("Content-Type", "text/html; charset=utf-8")
            .setBody(body);
    }

    private static ObjectNode params(String url, int maxChars) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("url", url);
        if (maxChars > 0) {
            node.put("max_chars", maxChars);
        }
        return node;
    }
}
