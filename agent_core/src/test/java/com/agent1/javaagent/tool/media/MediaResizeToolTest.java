package com.agent1.javaagent.tool.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.anno.AnnotatedTools;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaResizeToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temp;

    @Test
    void fitShrinksLongEdge() {
        assertEquals(80, ImageFit.fit(320, 160, 80)[0]);
        assertEquals(40, ImageFit.fit(320, 160, 80)[1]);
        assertEquals(40, ImageFit.fit(160, 320, 80)[0]);
        assertEquals(80, ImageFit.fit(160, 320, 80)[1]);
        assertEquals(40, ImageFit.fit(40, 20, 80)[0]);
    }

    @Test
    void writesJpegWithinMaxEdge() throws Exception {
        Path root = temp.resolve("workspace");
        Files.createDirectories(root);
        Path source = root.resolve("in.png");
        ImageIO.write(new BufferedImage(320, 160, BufferedImage.TYPE_INT_RGB), "png", source.toFile());
        AgentTool tool = tool(root, new JdkImageResizer());

        String text = tool.execute("id", params("in.png", "out/small.jpg", 80), new CancellationToken(), ignored -> {})
            .getText();

        JsonNode json = MAPPER.readTree(text);
        assertEquals("out/small.jpg", json.get("path").asText());
        assertEquals(80, json.get("width").asInt());
        assertEquals(40, json.get("height").asInt());
        BufferedImage written = ImageIO.read(root.resolve("out/small.jpg").toFile());
        assertTrue(written.getWidth() <= 80);
        assertTrue(written.getHeight() <= 80);
        assertEquals(80, Math.max(written.getWidth(), written.getHeight()));
        byte[] head = Files.readAllBytes(root.resolve("out/small.jpg"));
        assertEquals((byte) 0xFF, head[0]);
        assertEquals((byte) 0xD8, head[1]);
    }

    @Test
    void rejectsEscapeMissingFileAndBadEdge() throws Exception {
        Path root = temp.resolve("workspace");
        Files.createDirectories(root);
        Files.writeString(root.resolve("note.txt"), "not an image");
        AgentTool tool = tool(root, new JdkImageResizer());

        assertTrue(call(tool, "../secret.jpg", "out.jpg", 100).startsWith("错误："));
        assertTrue(call(tool, "missing.jpg", "out.jpg", 100).contains("文件不存在"));
        assertTrue(call(tool, "note.txt", "out.jpg", 100).contains("not an image"));
        assertTrue(call(tool, "note.txt", "out.png", 100).contains("output"));
        assertTrue(call(tool, "note.txt", "out.jpg", 0).contains("maxEdge"));
        assertTrue(call(tool, "note.txt", "out.jpg", 4097).contains("maxEdge"));
    }

    @Test
    void desktopResizerReportsUnimplemented() throws Exception {
        Path root = temp.resolve("workspace");
        Files.createDirectories(root);
        Files.writeString(root.resolve("in.jpg"), "x");
        AgentTool tool = tool(root, UnavailableImageResizer.INSTANCE);
        assertTrue(call(tool, "in.jpg", "out.jpg", 100).contains("未实现"));
    }

    private static AgentTool tool(Path root, ImageResizer resizer) {
        List<AgentTool> tools = AnnotatedTools.from(new MediaResizeTool(new WorkspaceSandbox(root), resizer));
        assertEquals("media_resize", tools.get(0).name());
        return tools.get(0);
    }

    private static String call(AgentTool tool, String path, String output, int maxEdge) throws Exception {
        return tool.execute("id", params(path, output, maxEdge), new CancellationToken(), ignored -> {}).getText();
    }

    private static ObjectNode params(String path, String output, int maxEdge) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("path", path);
        params.put("output", output);
        params.put("maxEdge", maxEdge);
        return params;
    }

    /** 单测里的缩放器，行为与 Android Bitmap 路径一致：按 ImageFit 出 JPEG。 */
    private static final class JdkImageResizer implements ImageResizer {
        @Override
        public ImageSize resize(Path source, Path dest, int maxEdge) throws java.io.IOException {
            BufferedImage image = ImageIO.read(source.toFile());
            if (image == null) {
                throw new IllegalArgumentException("bad argument: media_resize: not an image");
            }
            int[] fitted = ImageFit.fit(image.getWidth(), image.getHeight(), maxEdge);
            BufferedImage out = new BufferedImage(fitted[0], fitted[1], BufferedImage.TYPE_INT_RGB);
            var graphics = out.createGraphics();
            graphics.drawImage(image, 0, 0, fitted[0], fitted[1], null);
            graphics.dispose();
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!ImageIO.write(out, "jpg", dest.toFile())) {
                throw new java.io.IOException("media_resize: compress failed");
            }
            return new ImageSize(fitted[0], fitted[1]);
        }
    }
}
