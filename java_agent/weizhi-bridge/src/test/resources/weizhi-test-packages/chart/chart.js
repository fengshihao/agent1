/**
 * chart.js — 图表 → 工作区 PNG / SVG（doc20 A1）。
 *
 * import { renderChart } from "weizhi:chart";
 * export default await renderChart({
 *   lib: "echarts",            // "echarts" | "mermaid"
 *   spec: { xAxis: {...}, ... },  // echarts：option 对象
 *   text: "graph TD; A-->B",     // mermaid：图定义文本
 *   width: 800, height: 400,
 *   scale: 2,                  // PNG 栅格倍率（1-3，默认 2）
 *   format: "png",             // "png" | "svg"
 *   output: "out/chart.png"
 * });
 *
 * 渲染在 webview_exec（桌面 Chromium / Android 系统 WebView）里完成：
 * 页面内动态加载 CDN echarts@5 / mermaid@11 → SSR SVG →（png）canvas 栅格。
 * 需要网络加载 CDN；库版本与 web-libs 卡片一致。
 */
var fs = require("fs");

var ECHARTS_URL = "https://cdn.jsdelivr.net/npm/echarts@5.5.1/dist/echarts.min.js";
var MERMAID_URL = "https://cdn.jsdelivr.net/npm/mermaid@11.4.0/dist/mermaid.min.js";

var MIN_PX = 100;
var MAX_PX = 4096;

function platformFiles() {
  if (typeof linux !== "undefined" && linux.platform === "linux" && linux.files) {
    return linux.files;
  }
  if (typeof mac !== "undefined" && mac.platform === "mac" && mac.files) {
    return mac.files;
  }
  if (typeof android !== "undefined" && android.platform === "android" && android.files) {
    return android.files;
  }
  return null;
}

function mkdirp(relDir) {
  var files = platformFiles();
  if (!files || !files.mkdir) {
    throw new Error("chart: platform files.mkdir required (install caps)");
  }
  var parts = relDir.split("/");
  var acc = "";
  for (var i = 0; i < parts.length; i++) {
    if (!parts[i]) {
      continue;
    }
    acc = acc ? acc + "/" + parts[i] : parts[i];
    files.mkdir(acc);
  }
}

function writeBytes(relPath, bytes) {
  var parent = relPath.replace(/\/[^/]+$/, "");
  if (parent && parent !== relPath) {
    mkdirp(parent);
  }
  fs.writeFileSync(relPath, bytes);
}

function writeUtf8(relPath, text) {
  writeBytes(relPath, text);
}

function normalizeRel(value, name) {
  if (value == null || String(value).trim() === "") {
    throw new Error(name + " 不能为空");
  }
  var path = String(value).trim().replace(/\\/g, "/");
  if (path.charAt(0) === "/" || /^[A-Za-z]:/.test(path)) {
    throw new Error(name + " 必须是 workspace 相对路径");
  }
  var parts = path.split("/");
  var kept = [];
  for (var i = 0; i < parts.length; i++) {
    if (!parts[i] || parts[i] === ".") {
      continue;
    }
    if (parts[i] === "..") {
      throw new Error(name + " 不能包含 ..");
    }
    kept.push(parts[i]);
  }
  if (!kept.length) {
    throw new Error(name + " 不能为空");
  }
  return kept.join("/");
}

function positiveInt(value, name, fallback) {
  if (value == null || value === "") {
    return fallback;
  }
  var n = Number(value);
  if (!isFinite(n)) {
    throw new Error(name + " 必须是正整数");
  }
  var px = Math.round(n);
  if (px < MIN_PX || px > MAX_PX) {
    throw new Error(name + " 必须在 " + MIN_PX + " 到 " + MAX_PX + " 之间");
  }
  return px;
}

function decodeBase64(b64) {
  var clean = String(b64).replace(/\s+/g, "");
  var bytes = Buffer.from(clean, "base64");
  return bytes;
}

function isPng(bytes) {
  return (
    bytes.length > 8 &&
    bytes[0] === 0x89 &&
    bytes[1] === 0x50 &&
    bytes[2] === 0x4e &&
    bytes[3] === 0x47
  );
}

function asObject(value) {
  if (value && typeof value === "object") {
    return value;
  }
  if (typeof value === "string") {
    try {
      return JSON.parse(value);
    } catch (e) {
      throw new Error("webview_exec 回执不是 JSON");
    }
  }
  throw new Error("webview_exec 无回执");
}

/** WebView 里执行的渲染代码：读 payload → CDN 加载 → SSR SVG →（png）canvas 栅格。 */
function chartCode() {
  var lines = [
    "return (async () => {",
    "  if (!input || !input.length) throw new Error('payload empty');",
    "  var payload = JSON.parse(new TextDecoder('utf-8').decode(input));",
    "  function loadScript(url) {",
    "    return new Promise(function (resolve, reject) {",
    "      var s = document.createElement('script');",
    "      s.src = url;",
    "      s.onload = function () { resolve(); };",
    "      s.onerror = function () { reject(new Error('CDN 加载失败（需要网络）: ' + url)); };",
    "      document.head.appendChild(s);",
    "    });",
    "  }",
    "  var svg = '';",
    "  if (payload.lib === 'mermaid') {",
    "    await loadScript(payload.mermaidUrl);",
    "    mermaid.initialize({ startOnLoad: false, theme: payload.theme || 'default' });",
    "    var out = await mermaid.render('chart' + Date.now(), payload.text);",
    "    svg = out.svg;",
    "  } else {",
    "    await loadScript(payload.echartsUrl);",
    "    var chart = echarts.init(null, null, { renderer: 'svg', ssr: true, width: payload.width, height: payload.height });",
    "    chart.setOption(payload.spec);",
    "    svg = chart.renderToSVGString();",
    "  }",
    "  if (!/<svg[\\s>]/i.test(svg)) throw new Error('渲染结果不是 SVG');",
    "  if (!/xmlns\\s*=/.test(svg)) {",
    "    svg = svg.replace(/<svg\\b/i, '<svg xmlns=\"http://www.w3.org/2000/svg\"');",
    "  }",
    "  if (payload.format === 'svg') {",
    "    return svg;",
    "  }",
    "  svg = svg.replace(/<svg\\b([^>]*)>/i, function (_, attrs) {",
    "    var next = attrs",
    "      .replace(/\\swidth\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)/i, '')",
    "      .replace(/\\sheight\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)/i, '');",
    "    return '<svg' + next + ' width=\"' + (payload.width * payload.scale) + '\" height=\"' + (payload.height * payload.scale) + '\">';",
    "  });",
    "  var url = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);",
    "  var img = new Image();",
    "  await new Promise(function (resolve, reject) {",
    "    img.onload = function () { resolve(); };",
    "    img.onerror = function () { reject(new Error('svg decode failed')); };",
    "    img.src = url;",
    "  });",
    "  var canvas = document.createElement('canvas');",
    "  canvas.width = payload.width * payload.scale;",
    "  canvas.height = payload.height * payload.scale;",
    "  canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);",
    "  return canvas.toDataURL('image/png').split(',')[1];",
    "})();"
  ];
  return lines.join("\n");
}

/**
 * 渲染图表并写入工作区。
 *
 * - lib "echarts"：spec 为 echarts option 对象（柱/线/饼/散点…统计图）
 * - lib "mermaid"：text 为图定义（流程/时序/思维导图/四象限…）
 * - format "png"：返回像素尺寸为 width*scale × height*scale
 */
export async function renderChart(options) {
  var opts = options || {};
  var lib = opts.lib == null || opts.lib === "" ? "echarts" : String(opts.lib).trim().toLowerCase();
  if (lib !== "echarts" && lib !== "mermaid") {
    throw new Error("lib 只能是 echarts 或 mermaid");
  }
  var format = opts.format == null || opts.format === "" ? "png" : String(opts.format).trim().toLowerCase();
  if (format !== "png" && format !== "svg") {
    throw new Error("format 只能是 png 或 svg");
  }
  var output = normalizeRel(opts.output == null ? opts.outputPath : opts.output, "output");
  var lower = output.toLowerCase();
  if (format === "png" && !lower.endsWith(".png")) {
    throw new Error("PNG 的 output 必须以 .png 结尾");
  }
  if (format === "svg" && !lower.endsWith(".svg")) {
    throw new Error("SVG 的 output 必须以 .svg 结尾");
  }
  var width = positiveInt(opts.width, "width", 800);
  var height = positiveInt(opts.height, "height", 400);
  var scale = format === "png" ? scaleOf(opts.scale) : 1;

  var payload = {
    lib: lib,
    width: width,
    height: height,
    scale: scale,
    format: format,
    theme: opts.theme != null ? String(opts.theme) : "default",
    echartsUrl: ECHARTS_URL,
    mermaidUrl: MERMAID_URL,
  };
  if (lib === "echarts") {
    if (opts.spec == null || typeof opts.spec !== "object") {
      throw new Error("echarts 需要 spec（option 对象）");
    }
    payload.spec = opts.spec;
  } else {
    if (opts.text == null || String(opts.text).trim() === "") {
      throw new Error("mermaid 需要 text（图定义）");
    }
    payload.text = String(opts.text);
  }

  if (typeof globalThis.$tools === "undefined" || typeof globalThis.$tools.webview_exec !== "function") {
    throw new Error("renderChart 需要 $tools.webview_exec（桌面需 Chromium，Android 需系统 WebView）");
  }

  var payloadPath = "tmp/chart-input-" + Date.now() + ".json";
  writeUtf8(payloadPath, JSON.stringify(payload));

  var receiptText = await globalThis.$tools.webview_exec({
    code: chartCode(),
    input_path: payloadPath,
    output_path: "tmp/webview_exec/chart-" + Date.now() + ".b64",
    timeout_ms: "180000",
  });
  var receipt = asObject(receiptText);
  if (!receipt || receipt.ok === false) {
    var err = receipt && receipt.error ? receipt.error : "webview_exec 失败";
    throw new Error(String(err));
  }
  var outPath = receipt.outputPath ? String(receipt.outputPath) : "";
  if (!outPath) {
    throw new Error("webview_exec 没有 outputPath，无法读取渲染结果");
  }

  if (format === "svg") {
    var svg = fs.readFileSync(outPath).toString().trim();
    if (svg.indexOf("<svg") < 0) {
      throw new Error("渲染结果不是 SVG");
    }
    writeUtf8(output, svg);
    return {
      ok: true,
      outputPath: output,
      width: width,
      height: height,
      format: "svg",
      bytes: svg.length,
    };
  }

  var b64 = fs.readFileSync(outPath).toString().trim();
  var bytes = decodeBase64(b64);
  if (!isPng(bytes)) {
    throw new Error("渲染结果不是有效的 PNG（请检查 spec / 网络加载是否失败）");
  }
  writeBytes(output, bytes);
  try {
    if (fs.unlinkSync && outPath !== output && outPath.indexOf("tmp/webview_exec/") === 0) {
      fs.unlinkSync(outPath);
    }
  } catch (e) {
    /* 临时文件删不掉不影响结果。 */
  }
  return {
    ok: true,
    outputPath: output,
    width: width * scale,
    height: height * scale,
    format: "png",
    bytes: bytes.length,
  };
}

function scaleOf(value) {
  if (value == null || value === "") {
    return 2;
  }
  var n = Number(value);
  if (!isFinite(n) || n < 1 || n > 3) {
    throw new Error("scale 必须在 1 到 3 之间");
  }
  return Math.round(n);
}

export { ECHARTS_URL, MERMAID_URL };

export default {
  renderChart: renderChart,
};
