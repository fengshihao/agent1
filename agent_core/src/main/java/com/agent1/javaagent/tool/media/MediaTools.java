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
 * 工作区音视频：探测元数据，或从视频抽出一帧 JPEG。
 * 按路径读写，不把文件读进 QuickJS，也不走 WebView。
 */
public final class MediaTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkspaceSandbox sandbox;
    private final MediaInspector inspector;

    public MediaTools(WorkspaceSandbox sandbox, MediaInspector inspector) {
        this.sandbox = sandbox;
        this.inspector = inspector == null ? UnavailableMediaInspector.INSTANCE : inspector;
    }

    @Tool(
        name = "media_probe",
        description = "探测工作区里的图片、视频或音频：种类、时长、宽高、MIME、字节数。"
            + "path 是工作区相对路径。不做转码。",
        readOnly = true,
        concurrencySafe = true,
        timeoutMs = 120_000L
    )
    public String mediaProbe(
        @ToolParam(name = "path", description = "工作区相对路径") String path
    ) {
        try {
            Path source = requireFile(path);
            return toJson(sandbox.displayPath(source), inspector.probe(source)).toString();
        } catch (SecurityException | IllegalArgumentException | UnsupportedOperationException e) {
            return "错误：" + message(e);
        } catch (IOException e) {
            return "错误：" + message(e);
        }
    }

    @Tool(
        name = "media_extract_frame",
        description = "从工作区视频抽出一帧，写成 JPEG。ss 为秒数或 HH:MM:SS.mmm，省略则取片头。"
            + "纯音频没有画面。不要把整文件塞进 webview_exec。",
        readOnly = false,
        concurrencySafe = false,
        timeoutMs = 120_000L
    )
    public String mediaExtractFrame(
        @ToolParam(name = "path", description = "输入视频，工作区相对路径") String path,
        @ToolParam(name = "output", description = "输出 JPEG，须以 .jpg 或 .jpeg 结尾") String output,
        @ToolParam(name = "ss", required = false, description = "时间点。秒数如 3.5，或 00:00:03.000。默认 0") String ss
    ) {
        try {
            requireJpegOutput(output);
            long timeUs = MediaTimestamp.toMicros(ss);
            Path source = requireFile(path);
            Path dest = sandbox.resolveWrite(output);
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ImageResizer.ImageSize size = inspector.extractFrame(source, dest, timeUs);
            ObjectNode node = MAPPER.createObjectNode();
            node.put("path", sandbox.displayPath(dest));
            node.put("width", size.width());
            node.put("height", size.height());
            node.put("timeUs", timeUs);
            return node.toString();
        } catch (SecurityException | IllegalArgumentException | UnsupportedOperationException e) {
            return "错误：" + message(e);
        } catch (IOException e) {
            return "错误：" + message(e);
        }
    }

    private Path requireFile(String path) throws IOException {
        Path source = sandbox.resolveRead(path);
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("文件不存在: " + path);
        }
        MediaLimits.checkInputSize(Files.size(source));
        return source;
    }

    private static ObjectNode toJson(String path, MediaInfo info) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("path", path);
        node.put("kind", info.kind() == null ? MediaInfo.KIND_UNKNOWN : info.kind());
        node.put("sizeBytes", info.sizeBytes());
        if (info.durationMs() != null) {
            node.put("durationMs", info.durationMs());
        }
        if (info.width() != null) {
            node.put("width", info.width());
        }
        if (info.height() != null) {
            node.put("height", info.height());
        }
        if (info.mime() != null && !info.mime().isBlank()) {
            node.put("mime", info.mime());
        }
        return node;
    }

    private static void requireJpegOutput(String output) {
        if (output == null) {
            throw new IllegalArgumentException("bad argument: media_extract_frame: output");
        }
        String name = output.trim().toLowerCase(java.util.Locale.ROOT);
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg")) {
            throw new IllegalArgumentException("bad argument: media_extract_frame: output 须为 .jpg 或 .jpeg");
        }
    }

    private static String message(Exception e) {
        String text = e.getMessage();
        return text == null || text.isBlank() ? e.getClass().getSimpleName() : text;
    }
}
