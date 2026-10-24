package com.agent1.javaagent.tool.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.anno.AnnotatedTools;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaToolsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temp;

    @Test
    void parsesFfmpegStyleTimestamps() {
        assertEquals(0L, MediaTimestamp.toMicros(null));
        assertEquals(0L, MediaTimestamp.toMicros("  "));
        assertEquals(3_000_000L, MediaTimestamp.toMicros("3"));
        assertEquals(3_500_000L, MediaTimestamp.toMicros("3.5"));
        assertEquals(3_000_000L, MediaTimestamp.toMicros("00:00:03.000"));
        assertEquals(62_000_000L, MediaTimestamp.toMicros("01:02"));
        assertThrows(IllegalArgumentException.class, () -> MediaTimestamp.toMicros("-1"));
        assertThrows(IllegalArgumentException.class, () -> MediaTimestamp.toMicros("1:2:3:4"));
    }

    @Test
    void rejectsOversizedInput() {
        assertThrows(IllegalArgumentException.class, () -> MediaLimits.checkInputSize(MediaLimits.MAX_INPUT_BYTES + 1));
    }

    @Test
    void probeReportsAudioAndVideo() throws Exception {
        Path root = workspace();
        Files.writeString(root.resolve("clip.mp4"), "video");
        Files.writeString(root.resolve("voice.m4a"), "audio");
        AgentTool probe = tool(root, "media_probe");

        JsonNode video = MAPPER.readTree(call(probe, params("clip.mp4")));
        assertEquals("video", video.get("kind").asText());
        assertEquals(1500, video.get("durationMs").asInt());
        assertEquals(320, video.get("width").asInt());
        assertTrue(video.get("sizeBytes").asLong() > 0);

        JsonNode audio = MAPPER.readTree(call(probe, params("voice.m4a")));
        assertEquals("audio", audio.get("kind").asText());
        assertEquals(800, audio.get("durationMs").asInt());
        assertTrue(audio.get("width") == null || audio.get("width").isNull());
    }

    @Test
    void extractFrameWritesJpegAtTimestamp() throws Exception {
        Path root = workspace();
        Files.writeString(root.resolve("clip.mp4"), "video");
        AgentTool extract = tool(root, "media_extract_frame");
        ObjectNode params = params("clip.mp4");
        params.put("output", "out/frame.jpg");
        params.put("ss", "00:00:01.500");

        JsonNode json = MAPPER.readTree(call(extract, params));
        assertEquals("out/frame.jpg", json.get("path").asText());
        assertEquals(1_500_000L, json.get("timeUs").asLong());
        assertEquals(320, json.get("width").asInt());
        byte[] head = Files.readAllBytes(root.resolve("out/frame.jpg"));
        assertEquals((byte) 0xFF, head[0]);
        assertEquals((byte) 0xD8, head[1]);
    }

    @Test
    void rejectsMissingEscapeAndBadOutput() throws Exception {
        Path root = workspace();
        Files.writeString(root.resolve("clip.mp4"), "video");
        AgentTool extract = tool(root, "media_extract_frame");
        AgentTool probe = tool(root, "media_probe");

        assertTrue(call(probe, params("missing.mp4")).contains("文件不存在"));
        assertTrue(call(probe, params("../secret.mp4")).startsWith("错误："));
        ObjectNode png = params("clip.mp4");
        png.put("output", "out/frame.png");
        assertTrue(call(extract, png).contains("output"));
        ObjectNode badTime = params("clip.mp4");
        badTime.put("output", "out/frame.jpg");
        badTime.put("ss", "nope");
        assertTrue(call(extract, badTime).contains("ss"));
    }

    @Test
    void desktopReportsUnimplemented() throws Exception {
        Path root = workspace();
        Files.writeString(root.resolve("clip.mp4"), "video");
        AgentTool probe = tool(root, "media_probe", UnavailableMediaInspector.INSTANCE);
        assertTrue(call(probe, params("clip.mp4")).contains("未实现"));
        AgentTool extractAudio = tool(root, "media_extract_audio", UnavailableMediaInspector.INSTANCE);
        ObjectNode audioParams = params("clip.mp4");
        audioParams.put("output", "out/audio.m4a");
        assertTrue(call(extractAudio, audioParams).contains("未实现"));
    }

    @Test
    void extractAudioWritesM4aAndValidatesOutput() throws Exception {
        Path root = workspace();
        Files.writeString(root.resolve("clip.mp4"), "video");
        AgentTool extractAudio = tool(root, "media_extract_audio");

        ObjectNode good = params("clip.mp4");
        good.put("output", "out/audio.m4a");
        JsonNode json = MAPPER.readTree(call(extractAudio, good));
        assertEquals("out/audio.m4a", json.get("path").asText());
        assertEquals("m4a", json.get("format").asText());
        assertTrue(json.get("sizeBytes").asLong() > 0);

        ObjectNode badExt = params("clip.mp4");
        badExt.put("output", "out/audio.mp3");
        assertTrue(call(extractAudio, badExt).contains(".m4a"));

        ObjectNode badFormat = params("clip.mp4");
        badFormat.put("output", "out/audio.m4a");
        badFormat.put("format", "mp3");
        assertTrue(call(extractAudio, badFormat).contains("format"));
    }

    private Path workspace() throws Exception {
        Path root = temp.resolve("workspace");
        Files.createDirectories(root);
        return root;
    }

    private static AgentTool tool(Path root, String name) {
        return tool(root, name, new FakeInspector());
    }

    private static AgentTool tool(Path root, String name, MediaInspector inspector) {
        List<AgentTool> tools = AnnotatedTools.from(new MediaTools(new WorkspaceSandbox(root), inspector));
        return tools.stream().filter(tool -> name.equals(tool.name())).findFirst().orElseThrow();
    }

    private static String call(AgentTool tool, ObjectNode params) throws Exception {
        return tool.execute("id", params, new CancellationToken(), ignored -> {}).getText();
    }

    private static ObjectNode params(String path) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("path", path);
        return params;
    }

    private static final class FakeInspector implements MediaInspector {
        @Override
        public MediaInfo probe(Path source) {
            String name = source.getFileName().toString();
            long size = source.toFile().length();
            if (name.endsWith(".m4a")) {
                return new MediaInfo(MediaInfo.KIND_AUDIO, size, 800L, null, null, "audio/mp4");
            }
            return new MediaInfo(MediaInfo.KIND_VIDEO, size, 1500L, 320, 180, "video/mp4");
        }

        @Override
        public ImageResizer.ImageSize extractFrame(Path source, Path dest, long timeUs) throws java.io.IOException {
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(dest, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
            return new ImageResizer.ImageSize(320, 180);
        }

        @Override
        public void extractAudio(Path source, Path dest, AudioExtractFormat format) throws java.io.IOException {
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(dest, new byte[] {0x00, 0x01, 0x02});
        }
    }
}
