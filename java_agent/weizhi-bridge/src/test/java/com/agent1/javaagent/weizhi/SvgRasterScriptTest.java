package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.MutableScriptToolBridge;
import com.agent1.javaagent.script.ScriptEngine;
import com.agent1.javaagent.script.ScriptToolBridge;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code svgToImage} 在 QuickJS 里调用 {@code $tools.webview_exec}，再把回执里的 Base64 写成二进制图片。
 * WebView 本身用假回执，不依赖 Chromium。
 */
class SvgRasterScriptTest {

    private static final String PNG_B64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    private static final String JPEG_B64 =
        "/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAkGBxISEhUSExMVFRUXGB0YGBgYGBgYGBgYGBgYGBgYGBgYHiggGBolHRcXITEhJSkrLi4uFx8zODMtNygtLisBCgoKDg0OGhAQGy0lICUtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLf/AABEIAAMABAMBIgACEQEDEQH/xAAbAAABBQEBAAAAAAAAAAAAAAADAAIEBQYBB//EABQBAQAAAAAAAAAAAAAAAAAAAAD/xAAUAQEAAAAAAAAAAAAAAAAAAAAA/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAwDAQACEQMRAD8AvoA//9k=";

    @BeforeAll
    static void requireNative() {
        Path repo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (repo.getNameCount() == 0 || !Files.isDirectory(repo)) {
            repo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        assumeTrue(WeizhiJniBootstrap.tryLoad(repo), "libweizhijni not built; run weizhi ./scripts/build.sh");
    }

    @Test
    void svgToImageWritesPngFromWebViewBase64(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("icon.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");

        AtomicReference<Map<String, Object>> seen = new AtomicReference<>();
        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> {
            seen.set(arguments);
            try {
                Path dir = workspace.resolve("tmp/webview_exec");
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("mock.b64"), PNG_B64);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return "{\"ok\":true,\"outputPath\":\"tmp/webview_exec/mock.b64\",\"resultType\":\"string\"}";
        });
        try (engine) {
            Files.writeString(workspace.resolve("jobs/run.js"), """
                import { svgToImage } from './svg-raster.js';
                export default await svgToImage({
                  svgPath: 'icon.svg',
                  outputPath: 'out/icon.png',
                  width: 320,
                  length: 180,
                  format: 'png'
                });
                """);
            String out = engine.evalForAgent(
                Files.readString(workspace.resolve("jobs/run.js")),
                null,
                20_000,
                new CancellationToken(),
                "jobs/run.js"
            );
            assertTrue(out.contains("\"ok\":true"), out);
            assertTrue(out.contains("out/icon.png"), out);
            assertTrue(out.contains("\"width\":320"), out);
            assertTrue(out.contains("\"height\":180"), out);
            byte[] png = Files.readAllBytes(workspace.resolve("out/icon.png"));
            assertEquals((byte) 0x89, png[0]);
            assertEquals((byte) 'P', png[1]);
            assertEquals((byte) 'N', png[2]);
            assertEquals((byte) 'G', png[3]);
            assertTrue(String.valueOf(seen.get().get("input_path")).contains("icon.svg"));
            String code = String.valueOf(seen.get().get("code"));
            assertTrue(code.contains("canvas.width = 320"), code);
            assertTrue(code.contains("canvas.height = 180"), code);
            assertTrue(code.contains("image/png"), code);
        }
    }

    @Test
    void svgToImageWritesJpegUsingSize(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("mark.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");

        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> {
            try {
                Path dir = workspace.resolve("tmp/webview_exec");
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("mock.b64"), JPEG_B64);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return "{\"ok\":true,\"outputPath\":\"tmp/webview_exec/mock.b64\",\"resultType\":\"string\"}";
        });
        try (engine) {
            Files.writeString(workspace.resolve("jobs/run.js"), """
                import { svgToImage } from './svg-raster.js';
                export default await svgToImage({
                  svgPath: 'mark.svg',
                  outputPath: 'out/mark.jpeg',
                  size: 64,
                  format: 'jpeg'
                });
                """);
            String out = engine.evalForAgent(
                Files.readString(workspace.resolve("jobs/run.js")),
                null,
                20_000,
                new CancellationToken(),
                "jobs/run.js"
            );
            assertTrue(out.contains("\"format\":\"jpg\""), out);
            assertTrue(out.contains("\"width\":64"), out);
            assertTrue(out.contains("\"height\":64"), out);
            byte[] jpeg = Files.readAllBytes(workspace.resolve("out/mark.jpeg"));
            assertEquals((byte) 0xff, jpeg[0]);
            assertEquals((byte) 0xd8, jpeg[1]);
            assertEquals((byte) 0xff, jpeg[2]);
        }
    }

    @Test
    void svgToImageRejectsMissingSizeBeforeWebView(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        AtomicInteger calls = new AtomicInteger();
        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> {
            calls.incrementAndGet();
            return "{\"ok\":false,\"error\":\"should not run\"}";
        });
        try (engine) {
            Files.writeString(workspace.resolve("jobs/run.js"), """
                import { svgToImage } from './svg-raster.js';
                export default await svgToImage({ svgPath: 'icon.svg', format: 'gif' });
                """);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> engine.evalForAgent(
                Files.readString(workspace.resolve("jobs/run.js")),
                null,
                20_000,
                new CancellationToken(),
                "jobs/run.js"
            ));
            assertTrue(ex.getMessage().contains("format"), ex.getMessage());
            assertEquals(0, calls.get());
        }
    }

    private static ScriptEngine open(Path agentRoot, Path workspace, ToolCall call) {
        MutableScriptToolBridge bridge = new MutableScriptToolBridge();
        bridge.set(new ScriptToolBridge() {
            @Override
            public Set<String> exposedNames() {
                return Set.of("webview_exec");
            }

            @Override
            public String call(String toolName, Map<String, Object> arguments) {
                return call.apply(toolName, arguments);
            }
        });
        return new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions().installDesktopCaps(true).scriptToolBridge(bridge),
            agentRoot
        ).open(workspace);
    }

    @FunctionalInterface
    private interface ToolCall {
        String apply(String toolName, Map<String, Object> arguments);
    }
}
