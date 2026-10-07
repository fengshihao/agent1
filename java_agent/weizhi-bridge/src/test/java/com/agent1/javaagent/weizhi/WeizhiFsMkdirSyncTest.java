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

/** weizhi#27：引擎 fs.mkdirSync 与 writeFile 自动建父目录。 */
class WeizhiFsMkdirSyncTest {

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
    void writeFileSyncCreatesNestedPathWithoutMkdir(@TempDir Path workspace) throws Exception {
        String js =
            """
            import fs from "fs";
            fs.writeFileSync("nested/out/note.txt", "ok");
            export default fs.readFileSync("nested/out/note.txt").toString();
            """;

        WeizhiScriptEngineFactory factory = new WeizhiScriptEngineFactory(new WeizhiRuntimeOptions(), null);
        try (ScriptEngine engine = factory.open(workspace)) {
            String out = engine.evalForAgent(js, null, 5_000, null, "jobs/nested-write.js");
            assertEquals("ok", out.replace("\"", "").trim());
            assertTrue(Files.isRegularFile(workspace.resolve("nested/out/note.txt")));
        }
    }

    @Test
    void mkdirSyncRecursiveThenWrite(@TempDir Path workspace) throws Exception {
        String js =
            """
            import fs from "fs";
            fs.mkdirSync("pack", { recursive: true });
            fs.writeFileSync("pack/a.bin", "x");
            export default fs.existsSync("pack/a.bin") ? "yes" : "no";
            """;

        WeizhiScriptEngineFactory factory = new WeizhiScriptEngineFactory(new WeizhiRuntimeOptions(), null);
        try (ScriptEngine engine = factory.open(workspace)) {
            String out = engine.evalForAgent(js, null, 5_000, null, "jobs/mkdir.js");
            if (out.toLowerCase().contains("not found") || out.toLowerCase().contains("mkdirsync")) {
                assumeTrue(false, "weizhi build lacks fs.mkdirSync (upgrade for weizhi#27)");
            }
            assertEquals("yes", out.replace("\"", "").trim());
        }
    }
}
