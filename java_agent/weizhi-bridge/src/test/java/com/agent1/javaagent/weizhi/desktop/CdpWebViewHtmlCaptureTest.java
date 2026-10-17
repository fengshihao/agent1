package com.agent1.javaagent.weizhi.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * html2pptx 截图车道 spike：iframe srcdoc 渲染 slide HTML → CDN 加载 html-to-image/echarts →
 * 逐页导出 PNG base64。验证链路可行后沉淀进 weizhi html2pptx 包。
 */
class CdpWebViewHtmlCaptureTest {

    private static final int SLIDE_W = 640;
    private static final int SLIDE_H = 360;
    private static final int PIXEL_RATIO = 2;

    @Test
    void capturesIframeSlidesViaCdnLibraries(@TempDir Path workspace) throws Exception {
        Assumptions.assumeTrue(CdpWebViewRuntime.isAvailable(), "需要本机 Chromium（CDP）");

        String repoEnv = System.getenv("AGENT1_WEIZHI_REPO");
        Path weizhiRepo = repoEnv != null && !repoEnv.isBlank()
            ? Path.of(repoEnv.trim())
            : Path.of(".").toAbsolutePath().normalize().resolve("weizhi");
        Assumptions.assumeTrue(Files.isDirectory(weizhiRepo.resolve("android")), "需要 weizhi 仓库");

        com.agent1.javaagent.workspace.WorkspaceSandbox sandbox =
            new com.agent1.javaagent.workspace.WorkspaceSandbox(workspace);
        CdpWebViewRuntime runtime = CdpWebViewRuntime.getInstance(weizhiRepo);

        String code = """
            return (async () => {
              var html = '<!DOCTYPE html><html><head><meta charset="utf-8"><style>'
                + 'body{margin:0;font-family:Helvetica,Arial,"PingFang SC","Microsoft YaHei",sans-serif}'
                + '.slide{width: %dpx;height: %dpx;box-sizing:border-box;overflow:hidden}'
                + '#s1{background:linear-gradient(135deg,#1a1423,#2a2140);color:#f6f1ea;padding:48px;display:flex;flex-direction:column;justify-content:center}'
                + '#s1 h1{font-size:44px;margin:0 0 12px}#s1 p{font-size:18px;color:#c4b8a8;margin:0}'
                + '#s2{background:#ffffff;padding:32px}#s2 h2{font-size:24px;color:#122033;margin:0 0 16px}'
                + '#chart{width:576px;height:240px}'
                + '</style></head><body>'
                + '<div class="slide" id="s1"><h1>Html2Pptx Spike</h1><p>渐变背景 + 大标题，由 html-to-image 导出</p></div>'
                + '<div class="slide" id="s2"><h2>图表页</h2><div id="chart"></div></div>'
                + '<script src="https://cdn.jsdelivr.net/npm/html-to-image@1.11.11/dist/html-to-image.min.js"></script>'
                + '<script src="https://cdn.jsdelivr.net/npm/echarts@5.5.1/dist/echarts.min.js"></script>'
                + '</body></html>';
              var iframe = document.createElement('iframe');
              iframe.style.cssText = 'position:fixed;left:-99999px;top:0;border:0;width:%dpx;height:%dpx;';
              iframe.srcdoc = html;
              document.body.appendChild(iframe);
              await new Promise(function (resolve, reject) {
                iframe.onload = function () { resolve(); };
                setTimeout(function () { reject(new Error('iframe load timeout')); }, 30000);
              });
              var win = iframe.contentWindow;
              var doc = win.document;
              if (!win.htmlToImage || !win.echarts) {
                throw new Error('CDN 库未就绪: htmlToImage=' + !!win.htmlToImage + ' echarts=' + !!win.echarts);
              }
              var chart = win.echarts.init(doc.getElementById('chart'), null, { renderer: 'canvas' });
              chart.setOption({
                animation: false,
                grid: { left: 40, right: 16, top: 16, bottom: 24 },
                xAxis: { type: 'category', data: ['Q1', 'Q2', 'Q3', 'Q4'] },
                yAxis: { type: 'value' },
                series: [{ type: 'bar', data: [120, 200, 150, 80], itemStyle: { color: '#4c6ef5' } }]
              });
              await new Promise(function (r) { setTimeout(r, 300); });
              var out = [];
              var slides = doc.querySelectorAll('.slide');
              for (var i = 0; i < slides.length; i++) {
                var dataUrl = await win.htmlToImage.toPng(slides[i], {
                  width: %d, height: %d, pixelRatio: %d
                });
                out.push(dataUrl.split(',')[1]);
              }
              chart.dispose();
              return JSON.stringify(out);
            })();
            """.formatted(SLIDE_W, SLIDE_H, SLIDE_W, SLIDE_H, SLIDE_W, SLIDE_H, PIXEL_RATIO);

        String receipt = WebViewExecSupport.execute(
            runtime,
            sandbox,
            code,
            null,
            null,
            "html_capture.json",
            "120000"
        );
        assertTrue(receipt.contains("\"ok\":true"), receipt);

        Path out = workspace.resolve("html_capture.json");
        assertTrue(Files.isRegularFile(out), "base64 JSON 应落盘");
        Type listType = new TypeToken<List<String>>() { }.getType();
        List<String> pngs = new Gson().fromJson(Files.readString(out).trim(), listType);
        assertEquals(2, pngs.size(), "应捕获两页");

        for (int i = 0; i < pngs.size(); i++) {
            byte[] png = Base64.getDecoder().decode(pngs.get(i));
            assertTrue(png.length > 32, "第 " + i + " 页 PNG 过小");
            assertEquals((byte) 0x89, png[0], "PNG 魔数");
            assertEquals('P', png[1]);
            int w = ((png[16] & 0xff) << 24) | ((png[17] & 0xff) << 16) | ((png[18] & 0xff) << 8) | (png[19] & 0xff);
            int h = ((png[20] & 0xff) << 24) | ((png[21] & 0xff) << 16) | ((png[22] & 0xff) << 8) | (png[23] & 0xff);
            assertEquals(SLIDE_W * PIXEL_RATIO, w, "第 " + i + " 页宽度应为 pixelRatio 缩放后");
            assertEquals(SLIDE_H * PIXEL_RATIO, h, "第 " + i + " 页高度应为 pixelRatio 缩放后");
        }
    }
}
