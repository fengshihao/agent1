package com.agent1.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.weizhi.WeizhiPackageSync;
import com.weizhi.WeizhiEngine;
import com.weizhi.caps.WeizhiPackages;
import com.weizhi.video.WeizhiVideo;
import java.io.File;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 视频剪辑冒烟（issue #98）：{@code weizhi:video} JS 包随 :weizhi-video AAR assets
 * 交付（syncBundled 自动安装，**无需联网**——区别于 office 包的云端安装路径），
 * 插件 SO（video/transcode）由 {@link WeizhiVideo#install} 注册。
 *
 * 链路覆盖：APK assets 装包 → beforeEval 对 registry 无 video 条目不挡本地解析
 * （issue 边界项）→ import weizhi:video → 包层 probe（video@0.3.1 起，weizhi#39）
 * → exportVideo remux 掐头去尾 → 输出再 probe。
 */
@RunWith(AndroidJUnit4.class)
public final class VideoExportInstrumentedTest {

    @Test
    public void exportVideoTrimsViaRemux() throws Exception {
        // weizhi#39 落地（video@0.3.1）：包层 probe 导出，元数据探测不再降级插件 API。
        String src = "const { exportVideo, probe } = await import('weizhi:video');\n"
                + "const inMeta = await probe('in.mp4');\n"
                + "const r = await exportVideo({"
                + "  clips: [{ path: 'in.mp4', startMs: 200, endMs: 800 }],"
                + "  output: 'out.mp4'});\n"
                + "const meta = await probe('out.mp4');\n"
                + "({ ok: r.ok, rDur: r.durationMs, outDur: meta.durationMs,"
                + "  tracks: meta.tracks.length, w: meta.tracks[0].width, inDur: inMeta.durationMs })\n";
        Setup setup = prepare(src);
        File in = AvcFixtures.encode(setup.workspace, "in.mp4", AvcFixtures.DEFAULT_MS, 40);
        assertTrue(in.length() > 0);

        try (WeizhiEngine engine = engine(setup)) {
            String out = engine.runJs(src, 60_000);
            assertTrue(out, out.contains("\"ok\":true"));
            // 600ms 掐头去尾（200–800），codec/muxer slop 余量。
            int rDur = jsonInt(out, "rDur");
            assertTrue("rDur=" + rDur, rDur > 400 && rDur < 900);
            int outDur = jsonInt(out, "outDur");
            assertTrue("outDur=" + outDur, outDur > 400 && outDur < 900);
            assertEquals(1, jsonInt(out, "tracks"));
            assertEquals(AvcFixtures.WIDTH, jsonInt(out, "w"));
            // 输入探测：1000ms fixture（探测面与插件层一致）。
            int inDur = jsonInt(out, "inDur");
            assertTrue("inDur=" + inDur, inDur > 800 && inDur < 1300);
        }
        byte[] head = java.nio.file.Files.readAllBytes(new File(setup.workspace, "out.mp4").toPath());
        assertTrue(head.length > 12);
    }

    /**
     * 独立 agentRoot（对齐 OfficeCatalogInstrumentedTest），但包不走云端：
     * video@0.3.0 在 APK assets 里，syncBundled 直接装。beforeEval 仍要跑一次——
     * 验证 registry 清单无 video 条目时不会挡本地已装包的解析（issue 边界项）。
     */
    private static Setup prepare(String src) throws java.io.IOException {
        Path agentRoot = new File(context().getFilesDir(), "agent-video-" + System.nanoTime()).toPath();
        AgentHomeBootstrap.ensure(agentRoot);
        String packageRoot = WeizhiPackages.syncBundled(context());
        WeizhiPackageSync.beforeEval(src, packageRoot, agentRoot);
        assertTrue("syncBundled 应从 APK assets 安装 video@0.3.1",
            new File(packageRoot, "packages/video@0.3.1/index.js").isFile());
        File dir = new File(context().getCacheDir(), "video-ws-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        return new Setup(packageRoot, dir);
    }

    private static WeizhiEngine engine(Setup setup) {
        WeizhiEngine engine = new WeizhiEngine();
        WeizhiVideo.install(engine);
        engine.setPackageRoot(setup.packageRoot);
        engine.setFsRoot(setup.workspace.getAbsolutePath());
        return engine;
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static int jsonInt(String json, String key) {
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("\"" + key + "\":(-?\\d+)").matcher(json);
        assertTrue("missing " + key + " in " + json, m.find());
        return Integer.parseInt(m.group(1));
    }

    private static final class Setup {
        final String packageRoot;
        final File workspace;

        Setup(String packageRoot, File workspace) {
            this.packageRoot = packageRoot;
            this.workspace = workspace;
        }
    }
}
