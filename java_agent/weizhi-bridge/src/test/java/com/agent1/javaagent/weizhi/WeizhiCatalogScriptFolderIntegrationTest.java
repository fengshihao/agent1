package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.script.ScriptEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** workspace 内相对 {@code import './lib.js'} 解析。 */
class WeizhiCatalogScriptFolderIntegrationTest {

    private static Path weizhiRepo;

    @BeforeAll
    static void requireWeizhi() {
        weizhiRepo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (weizhiRepo.getNameCount() == 0 || !Files.isDirectory(weizhiRepo)) {
            weizhiRepo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        assumeTrue(WeizhiJniBootstrap.tryLoad(weizhiRepo), "libweizhijni not built");
    }

    @Test
    void importWorkspaceRelativeModule(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path workspace = agentRoot.resolve("workspace");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(
            workspace.resolve("jobs/math-lib.js"),
            "export function inc(x) { return x + 1; }\n"
        );
        Files.writeString(
            workspace.resolve("jobs/run.js"),
            """
            import { inc } from './math-lib.js';
            export default inc(41);
            """.stripIndent()
        );

        WeizhiScriptEngineFactory factory = new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions().installDesktopCaps(true),
            agentRoot
        );
        try (ScriptEngine engine = factory.open(workspace)) {
            String out = engine.evalForAgent(
                Files.readString(workspace.resolve("jobs/run.js")),
                null,
                5_000,
                null,
                "jobs/run.js"
            );
            assertEquals("42", out.replace("\"", "").trim());
        }
    }
}
