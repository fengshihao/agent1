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
import com.agent1.javaagent.weizhi.desktop.WebViewExecSupport;
import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
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
 * {@code renderChart} 在 QuickJS 里编排 {@code $tools.webview_exec}：
 * payload 文件 → input_path、CDN 渲染 code、回执落盘 → PNG 魔数 / SVG 校验后写工作区。
 * WebView 用假回执，不依赖 Chromium；真实 CDN 渲染见 CDP e2e。
 */
class ChartScriptTest {

    private static final String PNG_B64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @BeforeAll
    static void requireNative() {
        Path repo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (repo.getNameCount() == 0 || !Files.isDirectory(repo)) {
            repo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        assumeTrue(WeizhiJniBootstrap.tryLoad(repo), "libweizhijni not built; run weizhi ./scripts/build.sh");
    }

    @Test
    void renderChartEchartsPngOrchestratesWebViewExec(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        WeizhiTestPackages.installFromSource(WeizhiBundledPackages.weizhiHome(agentRoot), "chart");

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
                import { renderChart } from "weizhi:chart";
                export default await renderChart({
                  lib: "echarts",
                  spec: { xAxis: { type: "category", data: ["1月", "2月"] }, series: [{ type: "bar", data: [10, 20] }] },
                  width: 640,
                  height: 320,
                  scale: 2,
                  output: "out/chart.png"
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
            assertTrue(out.contains("out/chart.png"), out);
            assertTrue(out.contains("\"width\":1280"), out);
            assertTrue(out.contains("\"height\":640"), out);
            byte[] png = Files.readAllBytes(workspace.resolve("out/chart.png"));
            assertEquals((byte) 0x89, png[0]);
            assertEquals((byte) 'P', png[1]);
            assertEquals((byte) 'N', png[2]);
            assertEquals((byte) 'G', png[3]);

            String code = String.valueOf(seen.get().get("code"));
            assertTrue(code.contains("echarts.init"), code);
            assertTrue(code.contains("renderToSVGString"), code);
            assertTrue(code.contains("canvas.width = payload.width * payload.scale"), code);
            String inputPath = String.valueOf(seen.get().get("input_path"));
            assertTrue(inputPath.startsWith("tmp/chart-input-"), inputPath);
            String payload = Files.readString(workspace.resolve(inputPath));
            assertTrue(payload.contains("\"lib\":\"echarts\""), payload);
            assertTrue(payload.contains("1月"), payload);
            assertTrue(payload.contains("echarts@5.5.1"), payload);
        }
    }

    @Test
    void renderChartMermaidSvgWritesText(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        WeizhiTestPackages.installFromSource(WeizhiBundledPackages.weizhiHome(agentRoot), "chart");

        AtomicReference<Map<String, Object>> seen = new AtomicReference<>();
        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> {
            seen.set(arguments);
            try {
                Path dir = workspace.resolve("tmp/webview_exec");
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("mock.b64"),
                    "<svg xmlns=\"http://www.w3.org/2000/svg\"><g/></svg>");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return "{\"ok\":true,\"outputPath\":\"tmp/webview_exec/mock.b64\",\"resultType\":\"string\"}";
        });
        try (engine) {
            Files.writeString(workspace.resolve("jobs/run.js"), """
                import { renderChart } from "weizhi:chart";
                export default await renderChart({
                  lib: "mermaid",
                  text: "graph TD; A-->B",
                  format: "svg",
                  output: "out/flow.svg"
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
            assertTrue(out.contains("\"format\":\"svg\""), out);
            String svg = Files.readString(workspace.resolve("out/flow.svg"));
            assertTrue(svg.startsWith("<svg"), svg);

            String code = String.valueOf(seen.get().get("code"));
            assertTrue(code.contains("mermaid.render"), code);
            String payload = Files.readString(workspace.resolve(String.valueOf(seen.get().get("input_path"))));
            assertTrue(payload.contains("\"lib\":\"mermaid\""), payload);
            assertTrue(payload.contains("graph TD; A-->B"), payload);
        }
    }

    @Test
    void renderChartValidatesArgsBeforeWebView(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        WeizhiTestPackages.installFromSource(WeizhiBundledPackages.weizhiHome(agentRoot), "chart");

        AtomicInteger calls = new AtomicInteger();
        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> {
            calls.incrementAndGet();
            return "{\"ok\":false,\"error\":\"should not run\"}";
        });
        try (engine) {
            Files.writeString(workspace.resolve("jobs/run.js"), """
                import { renderChart } from "weizhi:chart";
                export default await renderChart({ lib: "echarts", spec: {}, format: "gif", output: "out/x.gif" });
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

            Files.writeString(workspace.resolve("jobs/run.js"), """
                import { renderChart } from "weizhi:chart";
                export default await renderChart({ lib: "mermaid", output: "out/y.svg", format: "svg" });
                """);
            RuntimeException ex2 = assertThrows(RuntimeException.class, () -> engine.evalForAgent(
                Files.readString(workspace.resolve("jobs/run.js")),
                null,
                20_000,
                new CancellationToken(),
                "jobs/run.js"
            ));
            assertTrue(ex2.getMessage().contains("text"), ex2.getMessage());
            assertEquals(0, calls.get());
        }
    }

    /** 真实链路 e2e：本机 Chromium CDP + CDN echarts/mermaid。无 Chromium / 无网络时跳过。 */
    @Test
    void renderChartRealEchartsPngAndMermaidSvgViaCdp(@TempDir Path agentRoot) throws Exception {
        Path repo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (repo.getNameCount() == 0 || !Files.isDirectory(repo)) {
            repo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        assumeTrue(CdpWebViewRuntime.isAvailable(), "需要本机 Chromium（CDP）");
        assumeTrue(Files.isDirectory(repo.resolve("android")), "需要 weizhi 仓库");

        AgentHomeBootstrap.ensure(agentRoot);
        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        WeizhiTestPackages.installFromSource(WeizhiBundledPackages.weizhiHome(agentRoot), "chart");

        CdpWebViewRuntime runtime = CdpWebViewRuntime.getInstance(repo);
        com.agent1.javaagent.workspace.WorkspaceSandbox sandbox =
            new com.agent1.javaagent.workspace.WorkspaceSandbox(workspace);
        ScriptEngine engine = open(agentRoot, workspace, (toolName, arguments) -> WebViewExecSupport.execute(
            runtime,
            sandbox,
            String.valueOf(arguments.get("code")),
            null,
            arguments.get("input_path") == null ? null : String.valueOf(arguments.get("input_path")),
            arguments.get("output_path") == null ? null : String.valueOf(arguments.get("output_path")),
            String.valueOf(arguments.get("timeout_ms"))
        ));
        try (engine) {
            String png = engine.evalForAgent("""
                import { renderChart } from "weizhi:chart";
                export default await renderChart({
                  lib: "echarts",
                  spec: {
                    animation: false,
                    xAxis: { type: "category", data: ["1月", "2月", "3月"] },
                    yAxis: { type: "value" },
                    series: [{ type: "bar", data: [10, 20, 15] }]
                  },
                  width: 640,
                  height: 320,
                  scale: 2,
                  output: "out/real-chart.png"
                });
                """, null, 300_000, new CancellationToken(), "jobs/real1.js");
            assertTrue(png.contains("\"ok\":true"), png);
            String svg = engine.evalForAgent("""
                import { renderChart } from "weizhi:chart";
                export default await renderChart({
                  lib: "mermaid",
                  text: "graph TD; A[开始] --> B[结束]",
                  format: "svg",
                  output: "out/real-flow.svg"
                });
                """, null, 300_000, new CancellationToken(), "jobs/real2.js");
            assertTrue(svg.contains("\"ok\":true"), svg);
        }
        byte[] bytes = Files.readAllBytes(workspace.resolve("out/real-chart.png"));
        assertTrue(bytes.length > 1_000, "PNG 太小: " + bytes.length);
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals((byte) 'P', bytes[1]);
        assertEquals((byte) 'N', bytes[2]);
        String svgText = Files.readString(workspace.resolve("out/real-flow.svg"));
        assertTrue(svgText.startsWith("<svg"), svgText.substring(0, Math.min(80, svgText.length())));
        assertTrue(svgText.contains("结束"), "mermaid 中文标签缺失");
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
