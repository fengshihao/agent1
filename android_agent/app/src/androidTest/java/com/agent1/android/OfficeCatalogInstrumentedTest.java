package com.agent1.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.weizhi.WeizhiEngine;
import com.weizhi.caps.AndroidCaps;
import com.weizhi.caps.WeizhiPackages;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 真机：APK {@code assets/weizhi-packages} 上的 docx / pptx 官方 Package。
 */
@RunWith(AndroidJUnit4.class)
public final class OfficeCatalogInstrumentedTest {

    @Test
    public void docxJsMarkdownToDocx() throws Exception {
        File workspace = workspace("office");
        write(new File(workspace, "in.md"), "# Title\n\nBody line\n");
        try (WeizhiEngine engine = new WeizhiEngine()) {
            AndroidCaps.install(engine, new AndroidCaps.Session(context(), workspace));
            engine.setPackageRoot(WeizhiPackages.syncBundled(context()));
            engine.setFsRoot(workspace.getAbsolutePath());
            String out = engine.runJs(
                    "import { markdownToDocx } from 'weizhi:docx';\n"
                            + "export default markdownToDocx({inputPath:'in.md', outputPath:'out/doc.docx'});\n",
                    15000);
            assertTrue(out, out.contains("\"ok\":true"));
            assertTrue(out, out.contains("\"path\":\"out/doc.docx\""));
        }
        byte[] head = java.nio.file.Files.readAllBytes(new File(workspace, "out/doc.docx").toPath());
        assertTrue(head.length >= 2);
        assertEquals('P', (char) head[0]);
        assertEquals('K', (char) head[1]);
    }

    @Test
    public void docxJsGrepValidate() throws Exception {
        File workspace = workspace("office-grep");
        write(new File(workspace, "in.md"), "# T\n\nFind **needle** here.\n");
        try (WeizhiEngine engine = new WeizhiEngine()) {
            AndroidCaps.install(engine, new AndroidCaps.Session(context(), workspace));
            engine.setPackageRoot(WeizhiPackages.syncBundled(context()));
            engine.setFsRoot(workspace.getAbsolutePath());
            String out = engine.runJs(
                    "import { markdownToDocx, readDocx } from 'weizhi:docx';\n"
                            + "import { validateDocx } from 'weizhi:docx-raw';\n"
                            + "markdownToDocx({ inputPath: 'in.md', outputPath: 'out/x.docx' });\n"
                            + "var doc = readDocx('out/x.docx');\n"
                            + "var g = doc.grep('needle');\n"
                            + "doc.replaceAll('needle', 'found');\n"
                            + "doc.save('out/y.docx');\n"
                            + "var v = validateDocx({ path: 'out/y.docx' });\n"
                            + "export default { matches: g.matches.length, valid: v.ok, text: doc.plainText() };\n",
                    20000);
            assertTrue(out, out.contains("\"matches\":1"));
            assertTrue(out, out.contains("\"valid\":true"));
            assertTrue(out, out.contains("found"));
        }
    }

    @Test
    public void pptxJsRender() throws Exception {
        File workspace = workspace("office-pptx");
        try (WeizhiEngine engine = new WeizhiEngine()) {
            AndroidCaps.install(engine, new AndroidCaps.Session(context(), workspace));
            engine.setPackageRoot(WeizhiPackages.syncBundled(context()));
            engine.setFsRoot(workspace.getAbsolutePath());
            String out = engine.runJs(
                    "import { renderPptx } from 'weizhi:pptx';\n"
                            + "export default renderPptx({ theme:'briefing', slides:["
                            + "{ layout:'title', title:'封面' },"
                            + "{ layout:'bullets', title:'结论', items:['一点'] },"
                            + "{ layout:'shapes', shapes:[{ preset:'ellipse', col:0, row:0, colSpan:4, rowSpan:3, fill:'accent', text:'A' }] }"
                            + "]}, 'out/deck.pptx');\n",
                    20000);
            assertTrue(out, out.contains("\"ok\":true"));
            assertTrue(out, out.contains("\"slides\":3"));
        }
        byte[] head = java.nio.file.Files.readAllBytes(new File(workspace, "out/deck.pptx").toPath());
        assertTrue(head.length >= 2);
        assertEquals('P', (char) head[0]);
        assertEquals('K', (char) head[1]);
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static File workspace(String name) {
        File dir = new File(context().getCacheDir(), "office-ws-" + name + "-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        return dir;
    }

    private static void write(File file, String text) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
