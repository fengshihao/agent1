package com.agent1.javaagent.tool.media;

import java.util.Locale;

/** 从视频容器抽出音轨时的输出格式（宿主 native 实现）。 */
public enum AudioExtractFormat {
    M4A,
    AAC;

    public static AudioExtractFormat parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return M4A;
        }
        String n = raw.trim().toLowerCase(Locale.ROOT);
        if ("m4a".equals(n) || "mp4".equals(n) || "audio/mp4".equals(n)) {
            return M4A;
        }
        if ("aac".equals(n) || "audio/aac".equals(n)) {
            return AAC;
        }
        throw new IllegalArgumentException("bad argument: extractAudio: format 仅支持 m4a 或 aac");
    }
}
