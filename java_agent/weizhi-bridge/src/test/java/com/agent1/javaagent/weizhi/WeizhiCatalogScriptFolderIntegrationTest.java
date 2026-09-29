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

/** 7.2：catalog scripts 目录作为 Weizhi scriptFolder，workspace 脚本用 import "./leaf.js"。 */
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
    void loadScriptFromCatalogScriptsDir(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        Path scripts = AgentCatalogPaths.catalogScriptsDir(agentRoot);
        Files.createDirectories(scripts);
        Files.writeString(
            scripts.resolve("math-lib.js"),
            "export function inc(x) { return x + 1; }\n"
        );

        Path workspace = agentRoot.resolve("workspace");
        Files.createDirectories(workspace);

        WeizhiScriptEngineFactory factory = new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions().installDesktopCaps(true),
            agentRoot
        );
        try (ScriptEngine engine = factory.open(workspace)) {
            String out = engine.eval(
                "import { inc } from \"./math-lib.js\";\nexport default inc(41);\n",
                5_000,
                null
            );
            assertEquals("42", out.replace("\"", "").trim());
        }
    }
}
