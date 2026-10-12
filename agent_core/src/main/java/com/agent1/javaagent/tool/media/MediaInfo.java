package com.agent1.javaagent.tool.media;

/** 工作区媒体文件的探测结果。字段取不到则为 null。 */
public record MediaInfo(
    String kind,
    long sizeBytes,
    Long durationMs,
    Integer width,
    Integer height,
    String mime
) {
    public static final String KIND_IMAGE = "image";
    public static final String KIND_VIDEO = "video";
    public static final String KIND_AUDIO = "audio";
    public static final String KIND_UNKNOWN = "unknown";
}
