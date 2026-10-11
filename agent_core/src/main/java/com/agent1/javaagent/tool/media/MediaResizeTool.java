package com.agent1.javaagent.tool.media;

import com.agent1.javaagent.tool.anno.Tool;
import com.agent1.javaagent.tool.anno.ToolParam;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 工作区图片按长边缩放并写成 JPEG。字节留在宿主进程。
 * 不要调用微智 {@code android.media.resize}（已删除）。
 */
public final class MediaResizeTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkspaceSandbox sandbox;
    private final ImageResizer resizer;

    public MediaResizeTool(WorkspaceSandbox sandbox, ImageResizer resizer) {
        this.sandbox = sandbox;
        this.resizer = resizer == null ? UnavailableImageResizer.INSTANCE : resizer;
    }

    @Tool(
        name = "media_resize",
        description = "把工作区图片按长边上限缩放，写成 JPEG。"
            + "path 与 output 都是工作区相对路径。不要调用 android.media.resize。",
        readOnly = false,
        concurrencySafe = false,
        timeoutMs = 120_000L
    )
    public String mediaResize(
        @ToolParam(name = "path", description = "输入图片，工作区相对路径") String path,
        @ToolParam(name = "output", description = "输出 JPEG，工作区相对路径，须以 .jpg 或 .jpeg 结尾") String output,
        @ToolParam(name = "maxEdge", description = "长边上限像素，1–4096。已更小则保持原尺寸") int maxEdge
    ) {
        try {
            ImageFit.checkMaxEdge(maxEdge);
            requireJpegOutput(output);
            Path source = sandbox.resolveRead(path);
            if (!Files.isRegularFile(source)) {
                return "错误：文件不存在: " + path;
            }
            Path dest = sandbox.resolveWrite(output);
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ImageResizer.ImageSize size = resizer.resize(source, dest, maxEdge);
            ObjectNode node = MAPPER.createObjectNode();
            node.put("path", sandbox.displayPath(dest));
            node.put("width", size.width());
            node.put("height", size.height());
            return node.toString();
        } catch (SecurityException | IllegalArgumentException | UnsupportedOperationException e) {
            return "错误：" + message(e);
        } catch (IOException e) {
            return "错误：" + message(e);
        }
    }

    private static void requireJpegOutput(String output) {
        if (output == null) {
            throw new IllegalArgumentException("bad argument: media_resize: output");
        }
        String name = output.trim().toLowerCase(java.util.Locale.ROOT);
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg")) {
            throw new IllegalArgumentException("bad argument: media_resize: output 须为 .jpg 或 .jpeg");
        }
    }

    private static String message(Exception e) {
        String text = e.getMessage();
        return text == null || text.isBlank() ? e.getClass().getSimpleName() : text;
    }
}
