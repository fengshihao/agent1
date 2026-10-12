package com.agent1.javaagent.tool.media;

import java.io.IOException;
import java.nio.file.Path;

/** 把一张图片按长边上限写成 JPEG。实现在各宿主，不进 QuickJS。 */
public interface ImageResizer {

    ImageSize resize(Path source, Path dest, int maxEdge) throws IOException;

    record ImageSize(int width, int height) {
    }
}
