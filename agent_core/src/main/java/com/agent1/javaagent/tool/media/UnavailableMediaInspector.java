package com.agent1.javaagent.tool.media;

import java.nio.file.Path;

/** 桌面生产力路径尚未接入音视频处理。 */
public final class UnavailableMediaInspector implements MediaInspector {

    public static final UnavailableMediaInspector INSTANCE = new UnavailableMediaInspector();

    private UnavailableMediaInspector() {
    }

    @Override
    public MediaInfo probe(Path source) {
        throw unsupported();
    }

    @Override
    public ImageResizer.ImageSize extractFrame(Path source, Path dest, long timeUs) {
        throw unsupported();
    }

    @Override
    public void extractAudio(Path source, Path dest, AudioExtractFormat format) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("未实现：桌面音视频处理尚未接入");
    }
}
