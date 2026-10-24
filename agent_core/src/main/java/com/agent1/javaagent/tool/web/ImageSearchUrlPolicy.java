package com.agent1.javaagent.tool.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 搜图结果排序与下载前置校验：优先国内/常见 CDN，屏蔽真机常连不上的社交爬虫图床。
 */
public final class ImageSearchUrlPolicy {

    private ImageSearchUrlPolicy() {
    }

    public record RankedImage(String url, String description, int score) {
    }

    /** 不参与下载、也不在搜图列表里推荐（instagram 爬虫、Twitter 等）。 */
    public static boolean isBlockedForDownload(String url) {
        return blockedReason(url) != null;
    }

    public static String blockedReason(String url) {
        String host = hostOf(url);
        if (host.isEmpty()) {
            return "url 无法解析";
        }
        if (hostContains(host, "lookaside.instagram", "instagram.com", "cdninstagram.com")) {
            return "图源为 Instagram，当前环境无法稳定下载，请换其他链接";
        }
        if (hostContains(host, "twimg.com", "twitter.com", "x.com", "t.co")) {
            return "图源为 Twitter/X，当前环境无法稳定下载，请换其他链接";
        }
        if (hostContains(host, "facebook.com", "fbcdn.net")) {
            return "图源为 Facebook，当前环境无法稳定下载，请换其他链接";
        }
        return null;
    }

    /** 分数越高越靠前展示；同分时保持 Tavily 原始顺序（稳定排序）。 */
    public static int scoreImageUrl(String url) {
        String host = hostOf(url);
        if (host.isEmpty()) {
            return -500;
        }
        if (isBlockedForDownload(url)) {
            return -10_000;
        }
        int score = 0;
        String scheme = schemeOf(url);
        if ("https".equalsIgnoreCase(scheme)) {
            score += 40;
        } else if ("http".equalsIgnoreCase(scheme)) {
            score -= 120;
        } else {
            score -= 200;
        }
        if (host.endsWith(".cn")) {
            score += 90;
        }
        if (hostContains(
            host,
            "itc.cn",
            "huitu.com",
            "xhby.net",
            "news.cn",
            "sinaimg",
            "sinacloud",
            "alicdn.com",
            "bdstatic.com",
            "byteimg.com",
            "qq.com",
            "163.com",
            "sohu.com",
            "ifeng.com",
            "people.com.cn",
            "cctv.com",
            "cntv.cn",
            "zhimg.com",
            "qpic.cn"
        )) {
            score += 70;
        }
        if (hostContains(host, "wikimedia.org", "wikipedia.org")) {
            score -= 25;
        }
        if (hostContains(host, "googleusercontent.com", "blogger.com", "blogspot.")) {
            score -= 35;
        }
        return score;
    }

    public static List<RankedImage> sortCandidates(List<RankedImage> input) {
        List<RankedImage> out = new ArrayList<>(input);
        out.sort(Comparator.comparingInt(RankedImage::score).reversed());
        return out;
    }

    static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String schemeOf(String url) {
        try {
            return URI.create(url.trim()).getScheme();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static boolean hostContains(String host, String... needles) {
        for (String needle : needles) {
            if (host.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
