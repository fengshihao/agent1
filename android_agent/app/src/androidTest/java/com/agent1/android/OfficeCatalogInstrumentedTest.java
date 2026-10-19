package com.agent1.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.weizhi.WeizhiPackageSync;
import com.weizhi.WeizhiEngine;
import com.weizhi.caps.AndroidCaps;
import com.weizhi.caps.WeizhiPackages;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 真机：docx / pptx 官方 Package 从**云端 registry 动态安装**（APK 不再预装
 * weizhi-packages，20-A 收口）。需要设备联网；{@code beforeEval} 解析源里
 * import 的 {@code weizhi:<name>} 并 ensure——从 registry 下载安装到
 * {@code filesDir/weizhi-home}，恰好覆盖「动态更新」链路的验收。
 */
@RunWith(AndroidJUnit4.class)
public final class OfficeCatalogInstrumentedTest {

    @Test
    public void docxJsMarkdownToDocx() throws Exception {
        String src = "import { markdownToDocx } from 'weizhi:docx';\n"
                + "export default markdownToDocx({inputPath:'in.md', outputPath:'out/doc.docx'});\n";
        Setup setup = prepare("office", src);
        write(new File(setup.workspace, "in.md"), "# Title\n\nBody line\n");
        try (WeizhiEngine engine = engine(setup)) {
            String out = engine.runJs(src, 60000);
            assertTrue(out, out.contains("\"ok\":true"));
            assertTrue(out, out.contains("\"path\":\"out/doc.docx\""));
        }
        byte[] head = java.nio.file.Files.readAllBytes(new File(setup.workspace, "out/doc.docx").toPath());
        assertTrue(head.length >= 2);
        assertEquals('P', (char) head[0]);
        assertEquals('K', (char) head[1]);
    }

    @Test
    public void docxJsGrepValidate() throws Exception {
        String src = "import { markdownToDocx, readDocx } from 'weizhi:docx';\n"
                + "import { validateDocx } from 'weizhi:docx-raw';\n"
                + "markdownToDocx({ inputPath: 'in.md', outputPath: 'out/x.docx' });\n"
                + "var doc = readDocx('out/x.docx');\n"
                + "var g = doc.grep('needle');\n"
                + "doc.replaceAll('needle', 'found');\n"
                + "doc.save('out/y.docx');\n"
                + "var v = validateDocx({ path: 'out/y.docx' });\n"
                + "export default { matches: g.matches.length, valid: v.ok, text: doc.plainText() };\n";
        Setup setup = prepare("office-grep", src);
        write(new File(setup.workspace, "in.md"), "# T\n\nFind **needle** here.\n");
        try (WeizhiEngine engine = engine(setup)) {
            String out = engine.runJs(src, 60000);
            assertTrue(out, out.contains("\"matches\":1"));
            assertTrue(out, out.contains("\"valid\":true"));
            assertTrue(out, out.contains("found"));
        }
    }

    @Test
    public void pptxJsRender() throws Exception {
        String src = "import { renderPptx } from 'weizhi:pptx';\n"
                + "export default renderPptx({ theme:'briefing', slides:["
                + "{ layout:'title', title:'封面' },"
                + "{ layout:'bullets', title:'结论', items:['一点'] },"
                + "{ layout:'shapes', shapes:[{ preset:'ellipse', col:0, row:0, colSpan:4, rowSpan:3, fill:'accent', text:'A' }] }"
                + "]}, 'out/deck.pptx');\n";
        Setup setup = prepare("office-pptx", src);
        try (WeizhiEngine engine = engine(setup)) {
            String out = engine.runJs(src, 60000);
            assertTrue(out, out.contains("\"ok\":true"));
            assertTrue(out, out.contains("\"slides\":3"));
        }
        byte[] head = java.nio.file.Files.readAllBytes(new File(setup.workspace, "out/deck.pptx").toPath());
        assertTrue(head.length >= 2);
        assertEquals('P', (char) head[0]);
        assertEquals('K', (char) head[1]);
    }

    /** 独立 agentRoot；beforeEval 对源里 import 的包做云端 ensure（下载安装）。 */
    private static Setup prepare(String name, String src) throws java.io.IOException {
        Path agentRoot = new File(context().getFilesDir(), "agent-" + name + "-" + System.nanoTime()).toPath();
        AgentHomeBootstrap.ensure(agentRoot);
        String packageRoot = WeizhiPackages.syncBundled(context());
        WeizhiPackageSync.beforeEval(src, packageRoot, agentRoot);
        File dir = new File(context().getCacheDir(), "office-ws-" + name + "-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        return new Setup(packageRoot, dir);
    }

    private static WeizhiEngine engine(Setup setup) throws Exception {
        WeizhiEngine engine = new WeizhiEngine();
        AndroidCaps.install(engine, new AndroidCaps.Session(context(), setup.workspace));
        engine.setPackageRoot(setup.packageRoot);
        engine.setFsRoot(setup.workspace.getAbsolutePath());
        return engine;
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static void write(File file, String text) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
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
