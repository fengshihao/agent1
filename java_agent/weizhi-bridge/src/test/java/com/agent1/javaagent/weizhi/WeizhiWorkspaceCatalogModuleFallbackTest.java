package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.catalog.AgentCatalogPaths;
import com.agent1.javaagent.script.ScriptEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * weizhi#9 / #10：workspace 入口 {@code jobs/run.js} 可 {@code import './docx.js'}（catalog 回退），
 * {@code import './helper.js'} 仍解析 workspace。
 */
class WeizhiWorkspaceCatalogModuleFallbackTest {

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
    void workspaceOrchestratorImportsCatalogDocx(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path scripts = AgentCatalogPaths.catalogScriptsDir(agentRoot);
        Files.writeString(scripts.resolve("docx.js"), "export const marker = 'catalog-docx';\n");

        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(
            workspace.resolve("jobs/run.js"),
            """
                import { marker } from './docx.js';
                export default marker;
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
            assertEquals("catalog-docx", out.replace("\"", "").trim());
        }
    }

    @Test
    void workspaceOrchestratorImportsLocalHelper(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path scripts = AgentCatalogPaths.catalogScriptsDir(agentRoot);
        Files.writeString(scripts.resolve("docx.js"), "export const marker = 'unused';\n");

        Path workspace = agentRoot.resolve("ws");
        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(workspace.resolve("jobs/helper.js"), "export function answer(){ return 7; }\n");
        Files.writeString(
            workspace.resolve("jobs/run.js"),
            """
                import { answer } from './helper.js';
                export default answer();
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
            assertEquals("7", out.replace("\"", "").trim());
        }
    }
}
