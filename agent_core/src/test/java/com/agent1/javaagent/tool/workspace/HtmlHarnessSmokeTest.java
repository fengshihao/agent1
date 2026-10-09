package com.agent1.javaagent.tool.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.AgentOptions;
import com.agent1.javaagent.core.AgentRuntime;
import com.agent1.javaagent.core.AgentStateSnapshot;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.llm.LlmClient;
import com.agent1.javaagent.llm.LlmStreamListener;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.AssistantResponse;
import com.agent1.javaagent.model.ChatRequest;
import com.agent1.javaagent.model.ToolCall;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlHarnessSmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static AgentTool receiptWebview(AtomicReference<String> probedPath, AtomicInteger calls, String receipt) {
        return new AgentTool() {
            @Override
            public String name() {
                return "webview_exec";
            }

            @Override
            public String description() {
                return "webview";
            }

            @Override
            public JsonNode parametersSchema() {
                return MAPPER.createObjectNode().put("type", "object");
            }

            @Override
            public ToolExecutionResult execute(
                String toolCallId,
                JsonNode parameters,
                CancellationToken cancellationToken,
                ToolUpdateListener onUpdate
            ) {
                if (probedPath != null) {
                    probedPath.set(parameters.path("input_path").asText());
                }
                if (calls != null) {
                    calls.incrementAndGet();
                }
                return ToolExecutionResult.text(receipt);
            }
        };
    }

    @Test
    void plainTextWriteIsNotProbed() {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("path", "notes.md");
        params.put("content", "<html>no</html>");
        assertNull(HtmlHarnessSmoke.htmlPathOf(params));
    }

    @Test
    void graphicPageWithoutSvgFailsAndNamesTheLibrary() {
        String html = "<!DOCTYPE html><html><script src='https://cdn.jsdelivr.net/npm/mermaid@11.4.0/dist/mermaid.min.js'></script></html>";
        String receipt = """
            {"ok":true,"resultType":"string","resultPreview":"{\\"svg\\":0,\\"canvas\\":0,\\"textLen\\":12}","console":["[error] Uncaught ReferenceError: mermaid is not defined"]}
            """.trim();
        HtmlHarnessSmoke.Verdict verdict = HtmlHarnessSmoke.interpret(html, receipt);
        assertFalse(verdict.ok());
        assertTrue(verdict.summary().contains("没有画出图"));
        assertTrue(verdict.summary().contains("ReferenceError"));
        assertTrue(verdict.summary().contains("web_lib"));
    }

    @Test
    void renderedSvgPassesAndIgnoresFaviconNoise() {
        String html = "<!DOCTYPE html><html><body><svg></svg></body></html>";
        String receipt = """
            {"ok":true,"resultPreview":"{\\"svg\\":1,\\"canvas\\":0,\\"textLen\\":4}","console":["Failed to load resource favicon.ico"]}
            """.trim();
        HtmlHarnessSmoke.Verdict verdict = HtmlHarnessSmoke.interpret(html, receipt);
        assertTrue(verdict.ok());
        assertTrue(verdict.summary().contains("html_smoke: 通过"));
        assertTrue(HtmlHarnessSmoke.PROBE_JS.contains("\\s+"));
        assertFalse(HtmlHarnessSmoke.PROBE_JS.contains("\\\\s+"));
    }

    @Test
    void htmlPathOfAcceptsHtmlAndHtm() {
        ObjectNode html = MAPPER.createObjectNode().put("path", "out/index.html");
        ObjectNode htm = MAPPER.createObjectNode().put("path", "Out\\Page.HTM");
        assertEquals("out/index.html", HtmlHarnessSmoke.htmlPathOf(html));
        assertEquals("Out\\Page.HTM", HtmlHarnessSmoke.htmlPathOf(htm));
        assertNull(HtmlHarnessSmoke.htmlPathOf(MAPPER.createObjectNode().put("path", "")));
        assertNull(HtmlHarnessSmoke.htmlPathOf(null));
    }

    @Test
    void smokeFileRereadsHtmlFromSandbox(@TempDir Path dir) throws Exception {
        String html = "<!DOCTYPE html><html><body><pre class='mermaid'>TOKEN</pre></body></html>";
        Files.writeString(dir.resolve("map.html"), html);
        AtomicReference<String> probed = new AtomicReference<>();
        AgentTool webview = receiptWebview(
            probed,
            null,
            "{\"ok\":true,\"resultPreview\":\"{\\\"svg\\\":0,\\\"canvas\\\":0,\\\"textLen\\\":4}\"}"
        );
        HtmlHarnessSmoke.Outcome outcome = HtmlHarnessSmoke.smokeFile(
            webview,
            new WorkspaceSandbox(dir),
            "map.html",
            new CancellationToken()
        );
        assertEquals("map.html", probed.get());
        assertTrue(outcome.failed());
        assertTrue(outcome.text().contains("没有画出图"));
        assertTrue(outcome.text().contains("map.html"));
    }

    @Test
    void smokeFileSkipsNonHarnessOrMissingPath(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("plain.htm"), "not a page");
        assertNull(HtmlHarnessSmoke.smokeFile(null, new WorkspaceSandbox(dir), "plain.htm", new CancellationToken()));
        assertNull(HtmlHarnessSmoke.smokeFile(null, new WorkspaceSandbox(dir), "gone.html", new CancellationToken()));
        assertNull(HtmlHarnessSmoke.smokeFile(null, new WorkspaceSandbox(dir), "", new CancellationToken()));
        assertNull(HtmlHarnessSmoke.smokeFile(null, new WorkspaceSandbox(dir), null, new CancellationToken()));
    }

    @Test
    void smokeFileNotesSkipWhenWebviewMissing(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("map.html"), "<!DOCTYPE html><html><body><pre class='mermaid'>m</pre></body></html>");
        HtmlHarnessSmoke.Outcome outcome = HtmlHarnessSmoke.smokeFile(
            null,
            new WorkspaceSandbox(dir),
            "map.html",
            new CancellationToken()
        );
        // 没有宿主 webview：不失败，但把跳过写进注记，避免静默丢失审计信息
        assertFalse(outcome.failed());
        assertTrue(outcome.text().contains("跳过"));
    }

    @Test
    void runtimeSmokesHtmlOnceAtRunEndNotPerEdit(@TempDir Path dir) throws Exception {
        AtomicReference<String> probedPath = new AtomicReference<>();
        AgentTool webview = receiptWebview(
            probedPath,
            null,
            "{\"ok\":true,\"resultPreview\":\"{\\\"svg\\\":1,\\\"canvas\\\":0,\\\"textLen\\\":20}\"}"
        );
        String args = MAPPER.createObjectNode()
            .put("path", "map.html")
            .put("content", "<!DOCTYPE html><html><body><pre class='mermaid'>mindmap</pre></html>")
            .toString();
        LlmClient client = new LlmClient() {
            private int turn;

            @Override
            public AssistantResponse streamChat(
                ChatRequest request,
                List<AgentTool> tools,
                LlmStreamListener streamListener,
                CancellationToken cancellationToken
            ) {
                turn += 1;
                if (turn == 1) {
                    return new AssistantResponse("", List.of(new ToolCall("w1", "write_file", args)));
                }
                return new AssistantResponse("完成", List.of());
            }
        };
        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder("test-model")
                .tools(List.of(new WriteFileTool(new WorkspaceSandbox(dir)), webview))
                .build(),
            client
        );
        runtime.setWorkspaceSandbox(new WorkspaceSandbox(dir));
        runtime.prompt("画图").join();
        runtime.waitForIdle();
        AgentStateSnapshot snapshot = runtime.getStateSnapshot();
        // 每笔 edit 的工具回执不再内嵌 ~10s 冒烟
        AgentMessage toolMessage = snapshot.getMessages().stream()
            .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
            .findFirst()
            .orElseThrow();
        assertTrue(toolMessage.getContent().contains("已写入"));
        assertFalse(toolMessage.getContent().contains("html_smoke"));
        assertFalse(toolMessage.isError());
        // 冒烟在 run 收尾（交付点）统一执行一次，结果以注记进入 transcript
        assertEquals("map.html", probedPath.get());
        AgentMessage smokeNote = snapshot.getMessages().stream()
            .filter(m -> m.getContent().contains("[html_smoke] map.html"))
            .reduce((first, second) -> second)
            .orElseThrow();
        assertTrue(smokeNote.getContent().contains("html_smoke: 通过"));
        assertTrue(Files.exists(dir.resolve("map.html")));
        runtime.close();
    }

    @Test
    void runtimeSmokeFailureFeedsBackForFix(@TempDir Path dir) throws Exception {
        AtomicInteger probes = new AtomicInteger();
        AgentTool webview = new AgentTool() {
            @Override
            public String name() {
                return "webview_exec";
            }

            @Override
            public String description() {
                return "webview";
            }

            @Override
            public JsonNode parametersSchema() {
                return MAPPER.createObjectNode().put("type", "object");
            }

            @Override
            public ToolExecutionResult execute(
                String toolCallId,
                JsonNode parameters,
                CancellationToken cancellationToken,
                ToolUpdateListener onUpdate
            ) {
                boolean first = probes.incrementAndGet() == 1;
                return ToolExecutionResult.text(
                    first
                        ? "{\"ok\":true,\"resultPreview\":\"{\\\"svg\\\":0,\\\"canvas\\\":0,\\\"textLen\\\":4}\"}"
                        : "{\"ok\":true,\"resultPreview\":\"{\\\"svg\\\":1,\\\"canvas\\\":0,\\\"textLen\\\":20}\"}"
                );
            }
        };
        String brokenArgs = MAPPER.createObjectNode()
            .put("path", "map.html")
            .put("content", "<!DOCTYPE html><html><body><pre class='mermaid'>mindmap</pre></body></html>")
            .toString();
        LlmClient client = new LlmClient() {
            private int turn;

            @Override
            public AssistantResponse streamChat(
                ChatRequest request,
                List<AgentTool> tools,
                LlmStreamListener streamListener,
                CancellationToken cancellationToken
            ) {
                turn += 1;
                if (turn == 1) {
                    return new AssistantResponse("", List.of(new ToolCall("w1", "write_file", brokenArgs)));
                }
                if (turn == 3) {
                    // 收到冒烟失败反馈后实际修复文件
                    String fixedArgs = MAPPER.createObjectNode()
                        .put("path", "map.html")
                        .put("content", "<!DOCTYPE html><html><body><pre class='mermaid'>mindmap</pre><svg></svg></body></html>")
                        .toString();
                    return new AssistantResponse("", List.of(new ToolCall("w2", "write_file", fixedArgs)));
                }
                return new AssistantResponse("完成", List.of());
            }
        };
        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder("test-model")
                .tools(List.of(new WriteFileTool(new WorkspaceSandbox(dir)), webview))
                .build(),
            client
        );
        runtime.setWorkspaceSandbox(new WorkspaceSandbox(dir));
        runtime.prompt("画图").join();
        runtime.waitForIdle();
        AgentStateSnapshot snapshot = runtime.getStateSnapshot();
        // 修复反馈进入 transcript，AI 修复后第二次冒烟通过
        assertEquals(2, probes.get());
        AgentMessage feedback = snapshot.getMessages().stream()
            .filter(m -> m.getContent().contains("交付前 html_smoke 检查未通过"))
            .findFirst()
            .orElseThrow();
        assertTrue(feedback.getContent().contains("没有画出图"));
        AgentMessage smokeNote = snapshot.getMessages().stream()
            .filter(m -> m.getContent().contains("[html_smoke] map.html"))
            .reduce((first, second) -> second)
            .orElseThrow();
        assertTrue(smokeNote.getContent().contains("html_smoke: 通过"));
        runtime.close();
    }
}