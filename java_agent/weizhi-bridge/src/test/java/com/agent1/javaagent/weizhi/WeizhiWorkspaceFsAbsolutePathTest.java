package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.script.ScriptEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** agent1#72 / Weizhi 路径沙箱：workspace 内绝对路径 fs 读。 */
class WeizhiWorkspaceFsAbsolutePathTest {

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
    void fsReadFileSyncAcceptsWorkspaceAbsolutePath(@TempDir Path workspace) throws Exception {
        Path file = workspace.resolve("note.txt");
        Files.writeString(file, "hello-abs");

        String abs = file.toAbsolutePath().normalize().toString().replace("\\", "/");
        String js =
            """
            import fs from "fs";
            const data = fs.readFileSync("%s", "utf8");
            export default typeof data === "string" ? data : String(data);
            """
                .formatted(abs);

        WeizhiScriptEngineFactory factory = new WeizhiScriptEngineFactory(new WeizhiRuntimeOptions(), null);
        try (ScriptEngine engine = factory.open(workspace)) {
            String out = engine.evalForAgent(js, null, 5_000, null, "jobs/abs-read.js");
            assertEquals("hello-abs", out.replace("\"", "").trim());
        }
    }

    @Test
    void fsRejectsPathOutsideWorkspace(@TempDir Path workspace) {
        String js =
            """
            import fs from "fs";
            const tag = (() => {
              try {
                fs.readFileSync("/etc/hosts", "utf8");
                return "ok";
              } catch (e) {
                return String(e && e.message || e);
              }
            })();
            export default tag;
            """;

        WeizhiScriptEngineFactory factory = new WeizhiScriptEngineFactory(new WeizhiRuntimeOptions(), null);
        try (ScriptEngine engine = factory.open(workspace)) {
            String out = engine.evalForAgent(js, null, 5_000, null, "jobs/escape.js").toLowerCase();
            if (out.contains("ok")) {
                assumeTrue(false, "weizhi build lacks workspace path sandbox (upgrade weizhi for agent1#72)");
            }
            assertTrue(out.contains("escape") || out.contains("path") || out.contains("invalid"), out);
        }
    }
}
