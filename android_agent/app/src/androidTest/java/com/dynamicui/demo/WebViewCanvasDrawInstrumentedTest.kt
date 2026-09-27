package com.dynamicui.demo

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dynamicui.demo.BuildConfig
import com.weizhi.agent.sandbox.WorkspaceSandbox
import com.weizhi.agent.web.HandlerUiExecutor
import com.weizhi.agent.web.WebViewExecTool
import com.weizhi.agent.web.WebViewRuntime
import java.nio.file.Files
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机/模拟器：用 **webview_exec + canvas** 绘制 PNG（不走 LLM 生图）。
 * 对应生产力场景「让 Agent 用 WebView 画图并落盘」的底层能力验收。
 */
@RunWith(AndroidJUnit4::class)
class WebViewCanvasDrawInstrumentedTest {

    @Test
    fun webviewExecDrawsPngViaCanvas() {
        assumeTrue(
            BuildConfig.WEIZHI_INTEGRATED,
            "需要 Weizhi 联编（WEIZHI_INTEGRATED=true）",
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspaceDir = context.cacheDir.resolve("webview-canvas-test").apply { mkdirs() }
        Files.list(workspaceDir.toPath()).use { stream ->
            stream.forEach { Files.deleteIfExists(it) }
        }

        val sandbox = WorkspaceSandbox(workspaceDir.toPath())
        val runtime = WebViewRuntime.getInstance(context, HandlerUiExecutor())
        val tool = WebViewExecTool(runtime, sandbox)

        val code = """
            const canvas = document.createElement('canvas');
            canvas.width = 256;
            canvas.height = 256;
            const ctx = canvas.getContext('2d');
            ctx.fillStyle = '#ff6600';
            ctx.fillRect(0, 0, 256, 256);
            ctx.fillStyle = '#0066ff';
            ctx.beginPath();
            ctx.arc(128, 128, 72, 0, Math.PI * 2);
            ctx.fill();
            return canvas.toDataURL('image/png').split(',')[1];
        """.trimIndent()

        val receipt = tool.webviewExec(code, null, null, "webview_draw.png", "180000")
        assertTrue(receipt.contains("\"ok\":true"), "webview_exec 失败: $receipt")

        val outPath = workspaceDir.toPath().resolve("webview_draw.png")
        assertTrue(Files.isRegularFile(outPath), "未生成 webview_draw.png")

        val pngBytes = Base64.decode(outPath.readText().trim(), Base64.DEFAULT)
        assertTrue(pngBytes.size > 64, "PNG 过小")
        assertTrue(pngBytes[0] == 0x89.toByte() && pngBytes[1] == 'P'.code.toByte(), "非 PNG 魔数")
        assertTrue(pngBytes[2] == 'N'.code.toByte() && pngBytes[3] == 'G'.code.toByte())
    }
}
