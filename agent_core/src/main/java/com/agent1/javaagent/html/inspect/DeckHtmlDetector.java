package com.agent1.javaagent.html.inspect;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * deck.html 检测（21 号规划 T1：预览页 deck 模式）。
 * HTML 里 class 含独立单词 {@code slide} 即视为 deck（与 html2pptx 约定一致）。
 */
public final class DeckHtmlDetector {

    public static final int MAX_SCAN_BYTES = 256 * 1024;

    private static final Pattern SLIDE_CLASS_ATTR = Pattern.compile(
        "class\\s*=\\s*\"[^\"]*\\bslide\\b[^\"]*\"|class\\s*=\\s*'[^']*\\bslide\\b[^']*'"
    );

    private DeckHtmlDetector() {
    }

    public static boolean isDeckHtml(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        return SLIDE_CLASS_ATTR.matcher(html).find();
    }

    public static boolean scanFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] head = readHead(stream);
            if (head.length == 0) {
                return false;
            }
            return isDeckHtml(new String(head, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return false;
        }
    }

    private static byte[] readHead(InputStream stream) throws IOException {
        byte[] buf = new byte[MAX_SCAN_BYTES];
        int off = 0;
        while (off < buf.length) {
            int n = stream.read(buf, off, buf.length - off);
            if (n < 0) {
                break;
            }
            off += n;
        }
        if (off == buf.length) {
            return buf;
        }
        byte[] out = new byte[off];
        System.arraycopy(buf, 0, out, 0, off);
        return out;
    }
}
