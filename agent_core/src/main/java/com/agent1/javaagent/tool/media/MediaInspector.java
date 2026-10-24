package com.agent1.javaagent.tool.media;

import java.io.IOException;
import java.nio.file.Path;

/** 探测音视频，或从视频抽出一帧 JPEG。实现在各宿主。 */
public interface MediaInspector {

    MediaInfo probe(Path source) throws IOException;

    /**
     * @param timeUs 相对片头的时间，微秒
     */
    ImageResizer.ImageSize extractFrame(Path source, Path dest, long timeUs) throws IOException;

    /**
     * 从视频容器抽出音轨（不重编码视频轨）。
     *
     * @param format {@link AudioExtractFormat}
     */
    void extractAudio(Path source, Path dest, AudioExtractFormat format) throws IOException;
}
