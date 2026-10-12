package com.agent1.javaagent.tool.media;

import java.nio.file.Path;

/** 桌面生产力路径尚未接入缩放。 */
public final class UnavailableImageResizer implements ImageResizer {

    public static final UnavailableImageResizer INSTANCE = new UnavailableImageResizer();

    private UnavailableImageResizer() {
    }

    @Override
    public ImageSize resize(Path source, Path dest, int maxEdge) {
        throw new UnsupportedOperationException("未实现：桌面图片缩放尚未接入");
    }
}
