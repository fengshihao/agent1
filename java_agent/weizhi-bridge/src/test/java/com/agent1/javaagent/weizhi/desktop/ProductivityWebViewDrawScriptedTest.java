package com.agent1.javaagent.weizhi.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.llm.scripted.ScriptedLlmClient;
import com.agent1.javaagent.llm.scripted.ScriptedResponses;
import com.agent1.javaagent.run.FileRunStore;
import com.agent1.javaagent.run.RunState;
import com.agent1.javaagent.session.FileSessionStore;
import com.agent1.javaagent.session.ProductivityAgentHost;
import com.agent1.javaagent.weizhi.WeizhiWorkspaceTools;
import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Mock LLM 预定 webview_exec → 真实 CDP 执行（无 DashScope）。 */
class ProductivityWebViewDrawScriptedTest {

    private static final String CANVAS_CODE = """
        const canvas = document.createElement('canvas');
        canvas.width = 128;
        canvas.height = 128;
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = '#ff6600';
        ctx.fillRect(0, 0, 128, 128);
        ctx.fillStyle = '#0066ff';
        ctx.beginPath();
        ctx.arc(64, 64, 40, 0, Math.PI * 2);
        ctx.fill();
        return canvas.toDataURL('image/png').split(',')[1];
        """;

    @Test
    void mockLlmTriggersWebviewExecAndWritesPng(@TempDir Path agentRoot) throws Exception {
        Assumptions.assumeTrue(CdpWebViewRuntime.isAvailable(), "需要 Chromium（CDP）");

        JsonObject args = new JsonObject();
        args.addProperty("code", CANVAS_CODE);
        args.addProperty("output_path", "mock_llm_draw.png");
        args.addProperty("timeout_ms", "90000");

        ScriptedLlmClient llm = ScriptedLlmClient.builder()
            .whenUserMessageContains(
                "webview-canvas-draw",
                ScriptedResponses.toolCall("webview_exec", args.toString()),
                ScriptedResponses.text("已通过 WebView 绘制并保存 mock_llm_draw.png")
            )
            .build();

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("mock-key").build();
        Path projectRoot = agentRoot.getParent() == null ? agentRoot : agentRoot.getParent();
        try (ProductivityAgentHost host = new ProductivityAgentHost(
            agentRoot,
            config,
            llm,
            null,
            0L,
            "",
            null,
            WeizhiWorkspaceTools.provider(agentRoot, projectRoot)
        )) {
            host.createSession();
            String runId = host.runUserMessage("任务:webview-canvas-draw 请用 webview_exec 画 PNG");
            assertEquals(
                RunState.SUCCEEDED,
                new FileRunStore(new FileSessionStore(agentRoot))
                    .read(host.getActiveSessionId(), runId)
                    .getState()
            );

            Path workspace = new FileSessionStore(agentRoot).workspaceDir(host.getActiveSessionId());
            Path png = workspace.resolve("mock_llm_draw.png");
            assertTrue(Files.isRegularFile(png));
            byte[] raw = Base64.getDecoder().decode(Files.readString(png).trim());
            assertTrue(raw.length > 32 && raw[0] == (byte) 0x89);
        }
    }
}
