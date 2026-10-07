package com.agent1.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.agent1.javaagent.catalog.OfficeCatalogScripts;
import com.weizhi.WeizhiEngine;
import com.weizhi.caps.AndroidCaps;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 真机：Agent1 {@code assets/office} 上的 docx / pptx 脚本。
 * Weizhi 只提供引擎；这些用例替代原先 weizhi {@code CapsInstrumentedTest} 里的 Office 段。
 */
@RunWith(AndroidJUnit4.class)
public final class OfficeCatalogInstrumentedTest {

    @Test
    public void docxJsMarkdownToDocx() throws Exception {
        File scriptDir = scriptDir("docx");
        copyOfficeScripts(scriptDir, "docx.js");
        File workspace = workspace("office");
        write(new File(workspace, "in.md"), "# Title\n\nBody line\n");
        try (WeizhiEngine engine = new WeizhiEngine()) {
            AndroidCaps.install(engine, new AndroidCaps.Session(context(), workspace));
            engine.setScriptFolder(scriptDir.getAbsolutePath());
            String out = engine.runJs(
                    "import { markdownToDocx } from 'docx.js';\n"
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
        File scriptDir = scriptDir("docx-grep");
        copyOfficeScripts(scriptDir, "docx.js", "docx-raw.js");
        File workspace = workspace("office-grep");
        write(new File(workspace, "in.md"), "# T\n\nFind **needle** here.\n");
        try (WeizhiEngine engine = new WeizhiEngine()) {
            AndroidCaps.install(engine, new AndroidCaps.Session(context(), workspace));
            engine.setScriptFolder(scriptDir.getAbsolutePath());
            String out = engine.runJs(
                    "import { markdownToDocx, readDocx } from 'docx.js';\n"
                            + "import { validateDocx } from 'docx-raw.js';\n"
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
        File scriptDir = scriptDir("pptx");
        copyOfficeScripts(scriptDir, "pptx.js", "pptx-build.js");
        File workspace = workspace("office-pptx");
        try (WeizhiEngine engine = new WeizhiEngine()) {
            AndroidCaps.install(engine, new AndroidCaps.Session(context(), workspace));
            engine.setScriptFolder(scriptDir.getAbsolutePath());
            String out = engine.runJs(
                    "import { renderPptx } from 'pptx.js';\n"
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

    private static void copyOfficeScripts(File scriptDir, String... names) throws Exception {
        for (String name : names) {
            boolean known = false;
            for (String officeName : OfficeCatalogScripts.OFFICE_SCRIPT_NAMES) {
                if (officeName.equals(name)) {
                    known = true;
                    break;
                }
            }
            assertTrue(name, known);
            try (InputStream in = context().getAssets().open("office/" + name);
                    FileOutputStream out = new FileOutputStream(new File(scriptDir, name))) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
        }
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static File scriptDir(String name) {
        File dir = new File(context().getCacheDir(), "office-scripts-" + name + "-" + System.nanoTime());
        assertTrue(dir.getAbsolutePath(), dir.mkdirs());
        return dir;
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
