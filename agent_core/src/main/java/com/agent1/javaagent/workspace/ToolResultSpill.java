package com.agent1.javaagent.workspace;

import com.agent1.javaagent.tool.ToolExecutionResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** 工具结果超过内联上限时写入工作区 {@code .spill/}（08-工具）。 */
public final class ToolResultSpill {

    public static final int MAX_INLINE_BYTES = 4096;
    public static final int PREVIEW_CHARS = 280;

    private ToolResultSpill() {
    }

    /**
     * @param toolName {@code read_file} 不做二次 spill
     */
    public static ToolExecutionResult maybeSpill(
        WorkspaceSandbox sandbox,
        String toolName,
        ToolExecutionResult result
    ) {
        if (sandbox == null || result == null || "read_file".equals(toolName)) {
            return result;
        }
        String text = result.getText();
        if (text == null) {
            return result;
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_INLINE_BYTES) {
            return result;
        }
        String spillName = UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ".txt";
        try {
            Path spillDir = sandbox.resolve(".spill");
            Files.createDirectories(spillDir);
            Path spillFile = spillDir.resolve(spillName);
            Files.writeString(spillFile, text, StandardCharsets.UTF_8);
            String relative = sandbox.relativize(spillFile);
            String preview = preview(text);
            String message =
                "结果过大（" + bytes.length + " 字节），已写入工作区文件: " + relative + "\n预览: " + preview;
            return ToolExecutionResult.text(message);
        } catch (SecurityException | IOException e) {
            String preview = preview(text);
            return ToolExecutionResult.text(
                "结果过大且 spill 失败（" + e.getMessage() + "）。预览: " + preview
            );
        }
    }

    static String preview(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n");
        if (normalized.length() <= PREVIEW_CHARS) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_CHARS) + "…";
    }
}
