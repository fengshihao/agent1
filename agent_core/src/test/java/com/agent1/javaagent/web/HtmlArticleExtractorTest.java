package com.agent1.javaagent.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HtmlArticleExtractorTest {

    @Test
    void extractsArticleAndDropsChrome() {
        String html = """
            <html><head>
            <meta property="og:title" content="杭州周末">
            <title>被忽略的标题</title>
            </head><body>
            <nav><a href="/a">首页导航</a><a href="/b">更多新闻</a></nav>
            <div class="sidebar"><a href="/x">相关链接一</a><a href="/y">相关链接二</a></div>
            <article>
            <h1>杭州周末</h1>
            <p>周六早上从西湖出发，沿着苏堤走到花港观鱼，再坐船到湖心亭，风很轻。</p>
            <p>下午去中国美院象山校区看展览，晚上回河坊街吃片儿川。</p>
            </article>
            <footer>版权所有 不要出现</footer>
            <script>var secret = "SECRET_TOKEN_123";</script>
            <div style="display:none">隐藏广告词不要出现</div>
            </body></html>
            """;

        HtmlArticleExtractor.Article article = HtmlArticleExtractor.extract(html);

        assertEquals("杭州周末", article.title());
        assertTrue(article.text().contains("花港观鱼"));
        assertTrue(article.text().contains("片儿川"));
        assertFalse(article.text().contains("首页导航"));
        assertFalse(article.text().contains("相关链接"));
        assertFalse(article.text().contains("版权所有"));
        assertFalse(article.text().contains("SECRET_TOKEN_123"));
        assertFalse(article.text().contains("隐藏广告词"));
    }

    @Test
    void scoresDenseDivWhenPageHasNoArticleTag() {
        String html = """
            <html><body>
            <div class="menu"><a href="/a">菜单甲</a><a href="/b">菜单乙</a><a href="/c">菜单丙</a></div>
            <div class="post">
            周六早上从西湖出发，沿着苏堤走到花港观鱼，再坐船到湖心亭，岸边的人不多。
            下午去中国美院象山校区看展览，晚上回河坊街吃片儿川，汤面很烫。
            </div>
            </body></html>
            """;

        HtmlArticleExtractor.Article article = HtmlArticleExtractor.extract(html);

        assertTrue(article.text().contains("象山校区"));
        assertFalse(article.text().contains("菜单甲"));
    }

    @Test
    void decodesEntitiesAndPrefersArticleBody() {
        String html = """
            <html><body>
            <div class="sidebar"><a href="/a">侧栏链接甲</a><a href="/b">侧栏链接乙</a></div>
            <div itemprop="articleBody">
            <p>Tom &amp; Jerry &lt;cat&gt; &#20320;&#22909;，这段话要写得足够长才能被当成正文而不是导航碎片，所以再补一句西湖边的风很轻。</p>
            </div>
            </body></html>
            """;

        HtmlArticleExtractor.Article article = HtmlArticleExtractor.extract(html);

        assertTrue(article.text().contains("Tom & Jerry <cat> 你好"));
        assertFalse(article.text().contains("侧栏链接"));
    }

    @Test
    void emptyHtmlYieldsEmptyArticle() {
        HtmlArticleExtractor.Article article = HtmlArticleExtractor.extract("  ");
        assertEquals("", article.title());
        assertEquals("", article.text());
    }
}
