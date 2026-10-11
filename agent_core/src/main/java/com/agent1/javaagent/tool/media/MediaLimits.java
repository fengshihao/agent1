package com.agent1.javaagent.tool.media;

/** 媒体文件按路径处理时的软上限。不沿用 WebView 的 20MB 注入限制。 */
public final class MediaLimits {

    public static final long MAX_INPUT_BYTES = 512L * 1024 * 1024;

    private MediaLimits() {
    }

    public static void checkInputSize(long bytes) {
        if (bytes > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("media: 文件超过 512MB，请先剪短");
        }
    }
}
