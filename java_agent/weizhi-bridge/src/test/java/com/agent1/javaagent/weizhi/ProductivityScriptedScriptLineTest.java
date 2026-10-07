package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.llm.scripted.ScriptedLlmClient;
import com.agent1.javaagent.llm.scripted.ScriptedResponses;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.session.ProductivityAgentHost;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** UC-04：file 模式语法错 → tool 回执含 userLine（Scripted + Weizhi native）。 */
class ProductivityScriptedScriptLineTest {

    private static ScriptEngineFactoryHolder factory;

    @BeforeAll
    static void requireWeizhi() {
        Path repo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (repo.getNameCount() == 0 || !Files.isDirectory(repo)) {
            repo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        assumeTrue(WeizhiJniBootstrap.tryLoad(repo), "libweizhijni not built");
        factory = new ScriptEngineFactoryHolder(
            new WeizhiScriptEngineFactory(new WeizhiRuntimeOptions().installDesktopCaps(true))
        );
    }

    @Test
    void uc04SyntaxErrorReportsUserLineFive(@TempDir Path agentRoot) throws Exception {
        ScriptedLlmClient llm = ScriptedLlmClient.builder()
            .whenUserMessageContains(
                "scripted-uc04-line",
                ScriptedResponses.toolCall(
                    "write_file",
                    "{\"path\":\"bug.js\",\"content\":\"line1\\nline2\\nline3\\nline4\\n}\\n\"}"
                ),
                ScriptedResponses.toolCall("run_js", "{\"file\":\"bug.js\"}"),
                ScriptedResponses.text("已看到行号")
            )
            .build();

        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("mock-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(
            agentRoot,
            config,
            llm,
            factory.factory(),
            WeizhiHostSupport.scriptTimeoutMs(),
            ""
        )) {
            host.createSession();
            host.runUserMessage("任务:scripted-uc04-line 脚本第5行错误");

            assertTrue(host.runtime().getStateSnapshot().getMessages().stream()
                .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()))
                .anyMatch(m -> m.getContent().contains("\"userLine\":5")
                    || m.getContent().contains("\"userLine\": 5")));
        }
    }

    /** 避免 @BeforeAll 与泛型 factory 字段类型冲突。 */
    private record ScriptEngineFactoryHolder(com.agent1.javaagent.script.ScriptEngineFactory factory) {
    }
}
