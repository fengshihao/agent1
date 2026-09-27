package com.agent1.javaagent.weizhi.desktop;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CdpWebViewCanvasDrawTest {

    @Test
    void webviewExecDrawsPngViaCdp(@TempDir Path workspace) throws Exception {
        Assumptions.assumeTrue(CdpWebViewRuntime.isAvailable(), "需要本机 Chromium（CDP）");

        String repoEnv = System.getenv("AGENT1_WEIZHI_REPO");
        Path weizhiRepo = repoEnv != null && !repoEnv.isBlank()
            ? Path.of(repoEnv.trim())
            : Path.of(".").toAbsolutePath().normalize().resolve("weizhi");
        Assumptions.assumeTrue(Files.isDirectory(weizhiRepo.resolve("android")), "需要 weizhi 仓库");

        com.weizhi.agent.sandbox.WorkspaceSandbox sandbox =
            new com.weizhi.agent.sandbox.WorkspaceSandbox(workspace);
        CdpWebViewRuntime runtime = CdpWebViewRuntime.getInstance(weizhiRepo);

        String code = """
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

        String receipt = WebViewExecSupport.execute(
            runtime,
            sandbox,
            code,
            null,
            null,
            "cdp_draw.png",
            "90000"
        );
        assertTrue(receipt.contains("\"ok\":true"), receipt);

        Path out = workspace.resolve("cdp_draw.png");
        assertTrue(Files.isRegularFile(out));
        byte[] png = Base64.getDecoder().decode(Files.readString(out).trim());
        assertTrue(png.length > 32);
        assertTrue(png[0] == (byte) 0x89 && png[1] == 'P');
    }
}
