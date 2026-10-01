package com.agent1.javaagent.web;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 手写的 HTML 正文抽取：建一棵轻量 DOM，丢掉导航/脚本，再按文本密度选主内容。
 */
public final class HtmlArticleExtractor {

    public static final class Article {
        private final String title;
        private final String text;

        public Article(String title, String text) {
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

    private static final Set<String> VOID_TAGS = Set.of(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link",
        "meta", "param", "source", "track", "wbr"
    );
    private static final Set<String> RAW_TAGS = Set.of("script", "style", "noscript");
    private static final Set<String> DROP_TAGS = Set.of(
        "script", "style", "noscript", "svg", "canvas", "iframe", "object", "embed",
        "form", "button", "input", "select", "textarea", "option", "nav", "footer",
        "header", "aside", "dialog", "template", "link", "meta", "head"
    );
    private static final Set<String> DROP_ROLES = Set.of(
        "navigation", "banner", "contentinfo", "complementary", "search", "menubar", "menu"
    );
    private static final Set<String> CANDIDATE_TAGS = Set.of(
        "article", "main", "section", "div", "td", "body"
    );
    private static final Pattern NOISE = Pattern.compile(
        "(?i)(?:^|[\\s_|-])(?:comments?|sidebar|menu|navbar|nav(?:igation)?|footer|header|"
            + "share|social|related|recommend(?:ation)?s?|breadcrumb|cookie|popup|copyright|ads?|"
            + "advert(?:isement)?s?|sponsor|pagination|pager|subscribe|newsletter|toolbar)"
            + "(?:$|[\\s_|-])"
    );
    private static final Pattern HARD_NOISE = Pattern.compile(
        "(?i)(?:^|[\\s_|-])(?:comments?|sidebar|navbar|nav(?:igation)?|footer|copyright|ads?|"
            + "advert(?:isement)?s?|cookie|popup)(?:$|[\\s_|-])"
    );
    private static final Pattern POSITIVE = Pattern.compile(
        "(?i)(?:^|[\\s_|-])(?:article|content|main|post|entry|story|text)(?:$|[\\s_|-])"
    );
    private static final Map<String, String> ENTITIES = entityMap();

    private HtmlArticleExtractor() {
    }

    public static Article extract(String html) {
        if (html == null || html.isBlank()) {
            return new Article("", "");
        }
        Element root = parse(html);
        String title = titleOf(root);
        prune(root);
        measure(root);
        Element best = bestContent(root);
        String text = best == null ? "" : cleanup(render(best));
        if (text.length() < 20) {
            text = "";
        }
        return new Article(collapse(title), text);
    }

    private static String titleOf(Element root) {
        String social = metaContent(root, "og:title", "twitter:title");
        if (!social.isBlank()) {
            return social;
        }
        String named = metaByName(root, "title");
        if (!named.isBlank()) {
            return named;
        }
        String title = textOfFirst(root, "title");
        if (!title.isBlank()) {
            return title;
        }
        return textOfFirst(root, "h1");
    }

    private static String metaContent(Element el, String... keys) {
        if ("meta".equals(el.tag)) {
            String hint = (el.property + " " + el.attrName).toLowerCase(Locale.ROOT);
            String content = collapse(el.content);
            for (String key : keys) {
                if (hint.contains(key) && !content.isBlank()) {
                    return content;
                }
            }
        }
        for (Object child : el.children) {
            if (child instanceof Element nested) {
                String found = metaContent(nested, keys);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }

    private static String metaByName(Element el, String name) {
        if ("meta".equals(el.tag) && name.equalsIgnoreCase(el.attrName)) {
            return collapse(el.content);
        }
        for (Object child : el.children) {
            if (child instanceof Element nested) {
                String found = metaByName(nested, name);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }

    private static String textOfFirst(Element el, String tag) {
        if (tag.equals(el.tag)) {
            return collapse(render(el));
        }
        for (Object child : el.children) {
            if (child instanceof Element nested) {
                String found = textOfFirst(nested, tag);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }

    private static void prune(Element el) {
        Iterator<Object> it = el.children.iterator();
        while (it.hasNext()) {
            Object child = it.next();
            if (!(child instanceof Element nested)) {
                continue;
            }
            if (shouldDrop(nested)) {
                it.remove();
            } else {
                prune(nested);
            }
        }
    }

    private static boolean shouldDrop(Element el) {
        if (DROP_TAGS.contains(el.tag) || DROP_ROLES.contains(el.role.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (el.hidden || "true".equalsIgnoreCase(el.ariaHidden)) {
            return true;
        }
        String style = el.style.toLowerCase(Locale.ROOT).replace(" ", "");
        if (style.contains("display:none") || style.contains("visibility:hidden")) {
            return true;
        }
        String hint = el.id + " " + el.className;
        if (!NOISE.matcher(hint).find()) {
            return false;
        }
        if (POSITIVE.matcher(hint).find() && !HARD_NOISE.matcher(hint).find()) {
            return false;
        }
        return true;
    }

    private static void measure(Element el) {
        int text = 0;
        int link = 0;
        int blocks = blockWeight(el.tag);
        for (Object child : el.children) {
            if (child instanceof String s) {
                text += visibleLen(s);
            } else if (child instanceof Element nested) {
                measure(nested);
                text += nested.textChars;
                link += nested.linkChars;
                blocks += nested.blocks;
            }
        }
        el.textChars = text;
        el.linkChars = "a".equals(el.tag) ? text : link;
        el.blocks = blocks;
    }

    private static int blockWeight(String tag) {
        if ("p".equals(tag) || "li".equals(tag) || "br".equals(tag)
            || "blockquote".equals(tag) || "pre".equals(tag) || "tr".equals(tag)) {
            return 1;
        }
        return tag.length() == 2 && tag.charAt(0) == 'h' && tag.charAt(1) >= '1' && tag.charAt(1) <= '6'
            ? 1 : 0;
    }

    private static Element bestContent(Element root) {
        Best best = new Best();
        consider(root, 0, best);
        if (best.element == null || best.element.textChars < 20) {
            return null;
        }
        return best.element;
    }

    private static void consider(Element el, int depth, Best best) {
        if (CANDIDATE_TAGS.contains(el.tag)) {
            double score = score(el);
            if (score > 0 && (best.element == null || score > best.score
                || (score == best.score && depth > best.depth))) {
                best.element = el;
                best.score = score;
                best.depth = depth;
            }
        }
        for (Object child : el.children) {
            if (child instanceof Element nested) {
                consider(nested, depth + 1, best);
            }
        }
    }

    private static double score(Element el) {
        if (el.textChars < 20) {
            return 0;
        }
        double density = (double) el.linkChars / el.textChars;
        boolean landmark = "article".equals(el.tag) || "main".equals(el.tag)
            || el.itemprop.toLowerCase(Locale.ROOT).contains("articlebody");
        if (density > 0.65 && !landmark) {
            return 0;
        }
        double value = el.textChars * (1.0 - density) + el.blocks * 20.0;
        if ("article".equals(el.tag) || "main".equals(el.tag)) {
            value *= 1.6;
        }
        if (el.itemprop.toLowerCase(Locale.ROOT).contains("articlebody")) {
            value *= 2;
        }
        if (POSITIVE.matcher(el.id + " " + el.className).find()) {
            value *= 1.3;
        }
        return value;
    }

    private static String render(Element el) {
        StringBuilder sb = new StringBuilder();
        renderInto(el, sb, false);
        return sb.toString();
    }

    private static void renderInto(Element el, StringBuilder sb, boolean pre) {
        if ("br".equals(el.tag)) {
            newline(sb);
            return;
        }
        boolean block = isBlock(el.tag);
        boolean preChild = pre || "pre".equals(el.tag);
        if (block) {
            newline(sb);
        }
        if ("li".equals(el.tag)) {
            sb.append("- ");
        }
        for (Object child : el.children) {
            if (child instanceof String text) {
                appendText(sb, text, preChild);
            } else if (child instanceof Element nested) {
                renderInto(nested, sb, preChild);
            }
        }
        if (block) {
            newline(sb);
        }
    }

    private static boolean isBlock(String tag) {
        return "p".equals(tag) || "div".equals(tag) || "section".equals(tag) || "article".equals(tag)
            || "main".equals(tag) || "li".equals(tag) || "ul".equals(tag) || "ol".equals(tag)
            || "blockquote".equals(tag) || "pre".equals(tag) || "tr".equals(tag) || "table".equals(tag)
            || "h1".equals(tag) || "h2".equals(tag) || "h3".equals(tag) || "h4".equals(tag)
            || "h5".equals(tag) || "h6".equals(tag) || "figcaption".equals(tag) || "body".equals(tag);
    }

    private static void newline(StringBuilder sb) {
        int n = sb.length();
        if (n == 0 || sb.charAt(n - 1) == '\n') {
            return;
        }
        sb.append('\n');
    }

    private static void appendText(StringBuilder sb, String raw, boolean pre) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        if (pre) {
            sb.append(raw);
            return;
        }
        String text = raw.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            char last = sb.charAt(sb.length() - 1);
            if (last != '\n' && last != ' ') {
                sb.append(' ');
            }
        }
        sb.append(text);
    }

    private static String cleanup(String raw) {
        String[] lines = raw.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean blank = false;
        for (String line : lines) {
            String text = line.replace('\u00A0', ' ').trim().replaceAll("[ \\t]{2,}", " ");
            if (text.isEmpty()) {
                if (sb.length() > 0 && !blank) {
                    sb.append('\n');
                    blank = true;
                }
                continue;
            }
            blank = false;
            sb.append(text).append('\n');
        }
        return sb.toString().trim();
    }

    private static int visibleLen(String raw) {
        return raw.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim().length();
    }

    private static String collapse(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static Element parse(String html) {
        Element root = new Element("#document");
        Deque<Element> stack = new ArrayDeque<>();
        stack.push(root);
        int i = 0;
        int n = html.length();
        while (i < n) {
            char c = html.charAt(i);
            if (c != '<') {
                int j = html.indexOf('<', i);
                if (j < 0) {
                    j = n;
                }
                String text = decode(html.substring(i, j));
                if (!text.isEmpty()) {
                    stack.peek().children.add(text);
                }
                i = j;
                continue;
            }
            if (html.startsWith("<!--", i)) {
                int j = html.indexOf("-->", i + 4);
                i = j < 0 ? n : j + 3;
                continue;
            }
            if (html.startsWith("<![CDATA[", i)) {
                int j = html.indexOf("]]>", i + 9);
                int end = j < 0 ? n : j;
                stack.peek().children.add(decode(html.substring(i + 9, end)));
                i = j < 0 ? n : j + 3;
                continue;
            }
            if (html.startsWith("<!", i) || html.startsWith("<?", i)) {
                int j = html.indexOf('>', i + 2);
                i = j < 0 ? n : j + 1;
                continue;
            }
            if (html.startsWith("</", i)) {
                int j = html.indexOf('>', i + 2);
                if (j < 0) {
                    break;
                }
                popUntil(stack, tagToken(html.substring(i + 2, j)));
                i = j + 1;
                continue;
            }
            int j = html.indexOf('>', i + 1);
            if (j < 0) {
                break;
            }
            String inside = html.substring(i + 1, j);
            i = j + 1;
            boolean selfClose = inside.endsWith("/");
            if (selfClose) {
                inside = inside.substring(0, inside.length() - 1);
            }
            String name = tagToken(inside);
            if (name.isEmpty()) {
                continue;
            }
            Element el = new Element(name);
            readAttrs(el, inside);
            stack.peek().children.add(el);
            if (RAW_TAGS.contains(name)) {
                i = skipRaw(html, i, name);
                continue;
            }
            if (!selfClose && !VOID_TAGS.contains(name)) {
                stack.push(el);
            }
        }
        return root;
    }

    private static int skipRaw(String html, int i, String tag) {
        String close = "</" + tag;
        int n = html.length();
        for (int j = i; j + close.length() <= n; j++) {
            if (html.regionMatches(true, j, close, 0, close.length())) {
                int gt = html.indexOf('>', j + close.length());
                return gt < 0 ? n : gt + 1;
            }
        }
        return n;
    }

    private static void popUntil(Deque<Element> stack, String name) {
        if (name.isEmpty() || stack.size() <= 1) {
            return;
        }
        boolean found = false;
        for (Element el : stack) {
            if (el.tag.equals(name)) {
                found = true;
                break;
            }
        }
        if (!found) {
            return;
        }
        while (stack.size() > 1 && !stack.peek().tag.equals(name)) {
            stack.pop();
        }
        if (stack.size() > 1) {
            stack.pop();
        }
    }

    private static String tagToken(String inside) {
        String s = inside.trim();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '/') {
                break;
            }
            i++;
        }
        return s.substring(0, i).toLowerCase(Locale.ROOT);
    }

    private static void readAttrs(Element el, String inside) {
        int i = 0;
        while (i < inside.length() && !Character.isWhitespace(inside.charAt(i))) {
            i++;
        }
        while (i < inside.length()) {
            while (i < inside.length() && Character.isWhitespace(inside.charAt(i))) {
                i++;
            }
            if (i >= inside.length()) {
                break;
            }
            int start = i;
            while (i < inside.length()) {
                char c = inside.charAt(i);
                if (c == '=' || Character.isWhitespace(c)) {
                    break;
                }
                i++;
            }
            String key = inside.substring(start, i).trim().toLowerCase(Locale.ROOT);
            String value = "";
            while (i < inside.length() && Character.isWhitespace(inside.charAt(i))) {
                i++;
            }
            if (i < inside.length() && inside.charAt(i) == '=') {
                i++;
                while (i < inside.length() && Character.isWhitespace(inside.charAt(i))) {
                    i++;
                }
                if (i < inside.length() && (inside.charAt(i) == '"' || inside.charAt(i) == '\'')) {
                    char quote = inside.charAt(i);
                    i++;
                    int end = inside.indexOf(quote, i);
                    if (end < 0) {
                        end = inside.length();
                    }
                    value = inside.substring(i, end);
                    i = Math.min(end + 1, inside.length());
                } else {
                    int end = i;
                    while (end < inside.length() && !Character.isWhitespace(inside.charAt(end))) {
                        end++;
                    }
                    value = inside.substring(i, end);
                    i = end;
                }
            }
            applyAttr(el, key, decode(value));
        }
    }

    private static void applyAttr(Element el, String key, String value) {
        switch (key) {
            case "id" -> el.id = value;
            case "class" -> el.className = value;
            case "role" -> el.role = value;
            case "itemprop" -> el.itemprop = value;
            case "name" -> el.attrName = value;
            case "property" -> el.property = value;
            case "content" -> el.content = value;
            case "style" -> el.style = value;
            case "aria-hidden" -> el.ariaHidden = value;
            case "hidden" -> el.hidden = true;
            default -> {
                // 正文抽取只用得到上面这些属性。
            }
        }
    }

    static String decode(String raw) {
        if (raw == null || raw.indexOf('&') < 0) {
            return raw == null ? "" : raw;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '&') {
                sb.append(c);
                continue;
            }
            int semi = raw.indexOf(';', i + 1);
            if (semi < 0 || semi - i > 16) {
                sb.append(c);
                continue;
            }
            String entity = raw.substring(i + 1, semi);
            String replacement = ENTITIES.get(entity);
            if (replacement == null && !entity.isEmpty() && entity.charAt(0) == '#') {
                replacement = numericEntity(entity.substring(1));
            }
            if (replacement == null) {
                sb.append(c);
                continue;
            }
            sb.append(replacement);
            i = semi;
        }
        return sb.toString();
    }

    private static String numericEntity(String body) {
        try {
            int code = body.startsWith("x") || body.startsWith("X")
                ? Integer.parseInt(body.substring(1), 16)
                : Integer.parseInt(body, 10);
            if (code <= 0 || code > 0x10FFFF) {
                return null;
            }
            return new String(Character.toChars(code));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, String> entityMap() {
        Map<String, String> map = new HashMap<>();
        map.put("amp", "&");
        map.put("lt", "<");
        map.put("gt", ">");
        map.put("quot", "\"");
        map.put("apos", "'");
        map.put("nbsp", "\u00A0");
        map.put("mdash", "\u2014");
        map.put("ndash", "\u2013");
        map.put("hellip", "\u2026");
        map.put("copy", "\u00A9");
        map.put("laquo", "\u00AB");
        map.put("raquo", "\u00BB");
        return map;
    }

    private static final class Element {
        private final String tag;
        private final List<Object> children = new ArrayList<>();
        private String id = "";
        private String className = "";
        private String role = "";
        private String itemprop = "";
        private String attrName = "";
        private String property = "";
        private String content = "";
        private String style = "";
        private String ariaHidden = "";
        private boolean hidden;
        private int textChars;
        private int linkChars;
        private int blocks;

        private Element(String tag) {
            this.tag = tag;
        }
    }

    private static final class Best {
        private Element element;
        private double score;
        private int depth = -1;
    }
}
