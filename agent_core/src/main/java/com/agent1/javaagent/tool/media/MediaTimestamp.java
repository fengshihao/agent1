package com.agent1.javaagent.tool.media;

/**
 * 把 ffmpeg 风格的 {@code -ss} 收成微秒。
 * 接受秒数（{@code 3}、{@code 3.5}）或 {@code [[HH:]MM:]SS[.mmm]}。
 */
public final class MediaTimestamp {

    private MediaTimestamp() {
    }

    public static long toMicros(String ss) {
        if (ss == null || ss.isBlank()) {
            return 0L;
        }
        String text = ss.trim();
        double seconds;
        try {
            if (text.indexOf(':') >= 0) {
                String[] parts = text.split(":");
                if (parts.length < 2 || parts.length > 3) {
                    throw new IllegalArgumentException("bad argument: ss");
                }
                seconds = 0;
                for (String part : parts) {
                    seconds = seconds * 60 + Double.parseDouble(part.trim());
                }
            } else {
                seconds = Double.parseDouble(text);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad argument: ss");
        }
        if (seconds < 0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) {
            throw new IllegalArgumentException("bad argument: ss");
        }
        return Math.round(seconds * 1_000_000d);
    }
}
