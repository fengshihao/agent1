package com.agent1.javaagent.tool.web;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从已下载的字节嗅探宽高（不解码整图）。 */
final class ImageRasterDimensions {

    private static final Pattern SVG_WIDTH = Pattern.compile(
        "width\\s*=\\s*[\"']([0-9.]+)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SVG_HEIGHT = Pattern.compile(
        "height\\s*=\\s*[\"']([0-9.]+)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SVG_VIEW_BOX = Pattern.compile(
        "viewBox\\s*=\\s*[\"']\\s*[0-9.]+\\s+[0-9.]+\\s+([0-9.]+)\\s+([0-9.]+)",
        Pattern.CASE_INSENSITIVE
    );

    private ImageRasterDimensions() {
    }

    /**
     * @return {@code [width, height]} 或 {@code null} 无法解析
     */
    static int[] tryRead(byte[] bytes, String type) {
        if (bytes == null || bytes.length == 0 || type == null || type.isEmpty()) {
            return null;
        }
        return switch (type) {
            case "png" -> png(bytes);
            case "jpg" -> jpeg(bytes);
            case "gif" -> gif(bytes);
            case "webp" -> webp(bytes);
            case "svg" -> svg(bytes);
            default -> null;
        };
    }

    private static int[] png(byte[] b) {
        if (b.length < 24) {
            return null;
        }
        int w = readIntBE(b, 16);
        int h = readIntBE(b, 20);
        return positive(w, h);
    }

    private static int[] gif(byte[] b) {
        if (b.length < 10) {
            return null;
        }
        int w = readU16LE(b, 6);
        int h = readU16LE(b, 8);
        return positive(w, h);
    }

    private static int[] jpeg(byte[] b) {
        int i = 2;
        while (i + 9 < b.length) {
            if (b[i] != (byte) 0xff) {
                i++;
                continue;
            }
            int marker = b[i + 1] & 0xff;
            if (marker == 0xd8) {
                i += 2;
                continue;
            }
            if (marker == 0xd9 || marker == 0x01) {
                break;
            }
            if (i + 3 >= b.length) {
                break;
            }
            int segmentLen = readU16BE(b, i + 2);
            if (segmentLen < 2) {
                break;
            }
            boolean sof = marker == 0xc0 || marker == 0xc1 || marker == 0xc2 || marker == 0xc3
                || marker == 0xc5 || marker == 0xc6 || marker == 0xc7
                || marker == 0xc9 || marker == 0xca || marker == 0xcb
                || marker == 0xcd || marker == 0xce || marker == 0xcf;
            if (sof && i + 7 < b.length) {
                int h = readU16BE(b, i + 5);
                int w = readU16BE(b, i + 7);
                return positive(w, h);
            }
            i += 2 + segmentLen;
        }
        return null;
    }

    private static int[] webp(byte[] b) {
        if (b.length < 30 || b[0] != 'R' || b[1] != 'I' || b[2] != 'F' || b[3] != 'F') {
            return null;
        }
        if (b[8] != 'W' || b[9] != 'E' || b[10] != 'B' || b[11] != 'P') {
            return null;
        }
        String fourCc = new String(b, 12, 4, StandardCharsets.US_ASCII);
        if ("VP8X".equals(fourCc) && b.length >= 30) {
            int w = 1 + (b[24] & 0xff) + ((b[25] & 0xff) << 8) + ((b[26] & 0xff) << 16);
            int h = 1 + (b[27] & 0xff) + ((b[28] & 0xff) << 8) + ((b[29] & 0xff) << 16);
            return positive(w, h);
        }
        if ("VP8L".equals(fourCc)) {
            int bits = (b[21] & 0xff) | ((b[22] & 0xff) << 8) | ((b[23] & 0xff) << 16) | ((b[24] & 0xff) << 24);
            int w = (bits & 0x3fff) + 1;
            int h = ((bits >> 14) & 0x3fff) + 1;
            return positive(w, h);
        }
        if ("VP8 ".equals(fourCc) && b.length >= 30) {
            int w = b[26] & 0xff | (b[27] & 0xff) << 8;
            int h = b[28] & 0xff | (b[29] & 0xff) << 8;
            return positive(w, h);
        }
        return null;
    }

    private static int[] svg(byte[] b) {
        String head = new String(b, 0, Math.min(b.length, 4096), StandardCharsets.UTF_8);
        Matcher box = SVG_VIEW_BOX.matcher(head);
        if (box.find()) {
            int w = parseDim(box.group(1));
            int h = parseDim(box.group(2));
            return positive(w, h);
        }
        Matcher wM = SVG_WIDTH.matcher(head);
        Matcher hM = SVG_HEIGHT.matcher(head);
        if (wM.find() && hM.find()) {
            return positive(parseDim(wM.group(1)), parseDim(hM.group(1)));
        }
        return null;
    }

    private static int parseDim(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, (int) Math.round(Double.parseDouble(raw.trim())));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int[] positive(int w, int h) {
        if (w <= 0 || h <= 0) {
            return null;
        }
        return new int[] {w, h};
    }

    private static int readIntBE(byte[] b, int off) {
        return ((b[off] & 0xff) << 24)
            | ((b[off + 1] & 0xff) << 16)
            | ((b[off + 2] & 0xff) << 8)
            | (b[off + 3] & 0xff);
    }

    private static int readU16BE(byte[] b, int off) {
        return ((b[off] & 0xff) << 8) | (b[off + 1] & 0xff);
    }

    private static int readU16LE(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }
}
