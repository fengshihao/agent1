package com.agent1.javaagent.tool.media;

/** 按长边上限计算目标像素，不解码图片。 */
public final class ImageFit {

    public static final int MIN_EDGE = 1;
    public static final int MAX_EDGE_LIMIT = 4096;

    private ImageFit() {
    }

    public static void checkMaxEdge(int maxEdge) {
        if (maxEdge < MIN_EDGE || maxEdge > MAX_EDGE_LIMIT) {
            throw new IllegalArgumentException("bad argument: media_resize: maxEdge");
        }
    }

    /**
     * 长边收到 {@code maxEdge}，短边按比例取整，至少 1。已经不大于上限时保持原尺寸。
     */
    public static int[] fit(int width, int height, int maxEdge) {
        checkMaxEdge(maxEdge);
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("bad argument: media_resize: size");
        }
        int longEdge = Math.max(width, height);
        if (longEdge <= maxEdge) {
            return new int[] {width, height};
        }
        if (width >= height) {
            int fittedHeight = Math.max(1, (int) Math.round(height * (maxEdge / (double) width)));
            return new int[] {maxEdge, fittedHeight};
        }
        int fittedWidth = Math.max(1, (int) Math.round(width * (maxEdge / (double) height)));
        return new int[] {fittedWidth, maxEdge};
    }
}
