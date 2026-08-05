package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.ScriptEngine;
import com.agent1.javaagent.script.ScriptEngineFactory;
import com.agent1.javaagent.tool.script.ExecuteScriptTool;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weizhi.WeizhiEngine;
import com.weizhi.WeizhiLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 对齐 Weizhi {@code SmokeTest} 与 {@code docs/INTEGRATION_FOR_AI.md} §9 验收项。
 */
class WeizhiScriptEngineIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static ScriptEngineFactory factory;

    @BeforeAll
    static void requireNative() {
        Path repo = Path.of(System.getenv().getOrDefault(WeizhiHostSupport.ENV_WEIZHI_REPO, "")).normalize();
        if (repo.getNameCount() == 0 || !Files.isDirectory(repo)) {
            repo = WeizhiHostSupport.defaultWeizhiRepo();
        }
        assumeTrue(WeizhiJniBootstrap.tryLoad(repo), "libweizhijni not built; run weizhi ./scripts/build.sh");
        factory = new WeizhiScriptEngineFactory(new WeizhiRuntimeOptions().installDesktopCaps(true));
    }

    @Test
    void runJsArithmetic() throws Exception {
        try (ScriptEngine engine = factory.open(Files.createTempDirectory("wz-arith"))) {
            assertEquals("3", engine.eval("1+2", 5_000, new CancellationToken()));
        }
    }

    @Test
    void fsRoundTrip(@TempDir Path workspace) throws Exception {
        try (ScriptEngine engine = factory.open(workspace)) {
            String out = engine.eval(
                "fs.writeFileSync('a.txt','hello'); fs.readFileSync('a.txt').toString()",
                5_000,
                new CancellationToken()
            );
            assertEquals("\"hello\"", out);
        }
    }

    @Test
    void fsPromises(@TempDir Path workspace) throws Exception {
        try (ScriptEngine engine = factory.open(workspace)) {
            String out = engine.eval(
                "await fs.promises.writeFile('b.txt','world');"
                    + "(await fs.promises.readFile('b.txt')).toString()",
                10_000,
                new CancellationToken()
            );
            assertEquals("\"world\"", out);
        }
    }

    @Test
    void zlib(@TempDir Path workspace) throws Exception {
        try (ScriptEngine engine = factory.open(workspace)) {
            String out = engine.eval(
                "const z = require('zlib');"
                    + "z.gunzipSync(z.gzipSync(Buffer.from('hello zlib'))).toString()",
                10_000,
                new CancellationToken()
            );
            assertEquals("\"hello zlib\"", out);
        }
    }

    @Test
    void desktopCapsZip(@TempDir Path workspace) throws Exception {
        String platform = com.weizhi.desktop.DesktopCaps.platformObjectName();
        assumeTrue(platform != null);
        try (ScriptEngine engine = factory.open(workspace)) {
            engine.eval(platform + ".files.mkdir('pack'); " + platform + ".files.write('pack/a.txt','hello zip')",
                10_000, new CancellationToken());
            String zipMsg = engine.eval(platform + ".files.zipCreate('pack', 'out.zip')", 10_000, new CancellationToken());
            assertTrue(zipMsg.contains("1 files"));
            String extractMsg = engine.eval(platform + ".files.zipExtract('out.zip', 'unpacked')", 10_000,
                new CancellationToken());
            assertTrue(extractMsg.contains("entries"));
            assertEquals("\"hello zip\"", engine.eval(platform + ".files.read('unpacked/a.txt')", 10_000,
                new CancellationToken()));
        }
    }

    @Test
    void fetchRequiresEnable(@TempDir Path workspace) throws Exception {
        WeizhiRuntimeOptions opts = new WeizhiRuntimeOptions().installDesktopCaps(false).enableFetch(false);
        ScriptEngineFactory noFetch = new WeizhiScriptEngineFactory(opts);
        try (ScriptEngine engine = noFetch.open(workspace)) {
            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                engine.eval("fetch('https://example.com')", 5_000, new CancellationToken())
            );
            assertTrue(ex.getMessage().contains("enableFetch") || ex.getMessage().contains("fetch"));
        }
    }

    @Test
    void cancelLongScript(@TempDir Path workspace) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<ScriptEngine> ref = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            ScriptEngine engine = factory.open(workspace);
            ref.set(engine);
            started.countDown();
            try {
                engine.eval("while(true){}", 600_000, new CancellationToken());
            } catch (RuntimeException ignored) {
            } finally {
                engine.close();
            }
        });
        runner.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        ref.get().cancel();
        runner.join(10_000);
    }

    @Test
    void fsIoLimit(@TempDir Path workspace) throws Exception {
        WeizhiLimits limits = new WeizhiLimits();
        limits.fsIoBytes = 16;
        WeizhiScriptEngineFactory limited = new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions().limits(limits).installDesktopCaps(false)
        );
        try (ScriptEngine engine = limited.open(workspace)) {
            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                engine.eval("fs.writeFileSync('big.txt','abcdefghijklmnopqrstuvwxyz')", 5_000, new CancellationToken())
            );
            assertTrue(ex.getMessage().contains("too large"));
        }
    }

    @Test
    void executeScriptToolRoundTrip(@TempDir Path workspace) throws Exception {
        ExecuteScriptTool tool = new ExecuteScriptTool(
            new WorkspaceSandbox(workspace),
            factory,
            10_000
        );
        ObjectNode params = MAPPER.createObjectNode();
        params.put("code", "fs.writeFileSync('out.txt','via-tool'); fs.readFileSync('out.txt').toString()");
        var result = tool.execute("t1", params, new CancellationToken(), u -> {});
        assertEquals("\"via-tool\"", result.getText());
    }

    @Test
    void nativeMockEchoMath(@TempDir Path workspace) throws Exception {
        WeizhiScriptEngineFactory mockFactory = new WeizhiScriptEngineFactory(
            new WeizhiRuntimeOptions().enableNativeMock(true).installDesktopCaps(false)
        );
        try (ScriptEngine engine = mockFactory.open(workspace)) {
            String out = engine.eval(
                "const p = await host.ensureNative('echo_math'); p.add([20,22])",
                15_000,
                new CancellationToken()
            );
            assertEquals("42", out);
        }
    }
}
