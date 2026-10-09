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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlHarnessSmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void plainTextWriteIsNotProbed() {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("path", "notes.md");
        params.put("content", "<html>no</html>");
        assertNull(HtmlHarnessSmoke.augment(null, null, params, "已写入", new CancellationToken()));
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
    void largeHtmlWithoutSourceGetsCoachEvenWhenWebViewMissing(@TempDir Path dir) throws Exception {
        String html = "<!DOCTYPE html><html><body>" + "甲".repeat(HtmlHarnessSmoke.LARGE_HTML_CHARS) + "</body></html>";
        ObjectNode params = MAPPER.createObjectNode();
        params.put("path", "page.html");
        params.put("content", html);
        WorkspaceSandbox sandbox = new WorkspaceSandbox(dir);
        HtmlHarnessSmoke.Outcome outcome = HtmlHarnessSmoke.augment(
            null,
            sandbox,
            params,
            "已写入: page.html",
            new CancellationToken()
        );
        assertFalse(outcome.failed());
        assertTrue(outcome.text().contains("[coach] html.missing_source"));
        assertTrue(outcome.text().contains("html_smoke: 跳过"));
    }

    @Test
    void siblingMarkdownSuppressesMissingSourceCoach(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("outline.md"), "# 主题\n");
        String html = "<!DOCTYPE html><html><style>" + "x".repeat(HtmlHarnessSmoke.FAT_STYLE_CHARS) + "</style></html>";
        ObjectNode params = MAPPER.createObjectNode();
        params.put("path", "page.html");
        params.put("content", html);
        assertNull(HtmlHarnessSmoke.missingSourceAdvice(new WorkspaceSandbox(dir), "page.html", html));
    }

    @Test
    void runtimeAppendsSmokeToWriteFileResult(@TempDir Path dir) throws Exception {
        AtomicReference<String> probedPath = new AtomicReference<>();
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
                probedPath.set(parameters.path("input_path").asText());
                return ToolExecutionResult.text(
                    "{\"ok\":true,\"resultPreview\":\"{\\\"svg\\\":1,\\\"canvas\\\":0,\\\"textLen\\\":20}\"}"
                );
            }
        };
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
        AgentMessage toolMessage = snapshot.getMessages().stream()
            .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
            .findFirst()
            .orElseThrow();
        assertEquals("map.html", probedPath.get());
        assertFalse(toolMessage.isError());
        assertTrue(toolMessage.getContent().contains("已写入"));
        assertTrue(toolMessage.getContent().contains("html_smoke: 通过"));
        assertTrue(Files.exists(dir.resolve("map.html")));
        runtime.close();
    }

    @Test
    void editFileRereadsHtmlAndSmokes(@TempDir Path dir) throws Exception {
        String html = "<!DOCTYPE html><html><body><pre class='mermaid'>TOKEN</pre></body></html>";
        Files.writeString(dir.resolve("map.html"), html);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("path", "map.html");
        params.put("old_string", "TOKEN");
        params.put("new_string", "DONE");
        AtomicReference<String> probed = new AtomicReference<>();
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
                probed.set(parameters.path("input_path").asText());
                return ToolExecutionResult.text(
                    "{\"ok\":true,\"resultPreview\":\"{\\\"svg\\\":0,\\\"canvas\\\":0,\\\"textLen\\\":4}\"}"
                );
            }
        };
        HtmlHarnessSmoke.Outcome outcome = HtmlHarnessSmoke.augment(
            webview,
            new WorkspaceSandbox(dir),
            params,
            "已编辑: map.html",
            new CancellationToken()
        );
        assertEquals("map.html", probed.get());
        assertTrue(outcome.failed());
        assertTrue(outcome.text().contains("没有画出图"));
    }
}
