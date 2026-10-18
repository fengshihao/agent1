/**
 * html2pptx.js — 把 HTML 幻灯片渲染成 pptx（截图模式）+ DOM 几何提取。
 *
 * 约定：HTML 里每个 class="slide" 的元素是一页，CSS 尺寸默认 1280×720（16:9）。
 *
 * renderHtmlDeck：每页经 webview_exec（Chromium / 系统 WebView）里的
 * html-to-image（CDN）截图成 PNG/JPG，再以全幅图片页打包为 pptx——
 * 图片即版面，版式自由度由 HTML/CSS 决定。
 *
 * extractDeckGeometry：同一 iframe 渲染后遍历 .slide 内可见元素，输出几何
 * JSON（矩形/字体/背景/圆角/文本 run/图片引用），落盘 *-geometry.json，
 * 供原生 OOXML 生成（不改变 renderHtmlDeck 行为）。
 *
 * geometryToPptx：把几何 JSON 转成**原生可编辑** pptx（纯 QuickJS，无 webview）——
 * 文本块→文本框、卡片→roundRect、img/canvas→图片页元素、线性渐变→gradFill；
 * 阴影等复杂效果以 outerShdw 近似并记入 needsScreenshot，由后续区域截图补真。
 *
 * import { renderHtmlDeck, extractDeckGeometry, geometryToPptx } from "weizhi:html2pptx";
 *
 * renderHtmlDeck：截图模式（默认）每页整页 PNG/JPG 打包全幅图片页 pptx；
 * mode:"native" 时一次 webview_exec 同时做几何提取 + 需截图区域（阴影/远程图/
 * 超限图片）导出，QuickJS 侧组原生可编辑 pptx 并用区域截图补真，失败自动回退
 * 截图模式（返回值 fallback:true 带原因）。
 *
 * export default await renderHtmlDeck({
 *   htmlPath: "deck.html",        // 或直接传 html: "<html>…</html>"
 *   output: "out/deck.pptx",
 *   mode: "native",               // screenshot（默认）| native
 *   width: 1280, height: 720,     // 页 CSS 尺寸（可选）
 *   scale: 2,                     // 截图像素倍数（可选，默认 2）
 *   format: "png"                 // png | jpg（可选，默认 png，仅截图模式）
 * });
 *
 * export default await extractDeckGeometry({
 *   htmlPath: "deck.html",        // 或 html 内联（此时 output 必填）
 *   output: "deck-geometry.json"  // 可选，默认 <htmlPath 词干>-geometry.json
 * });
 *
 * export default await geometryToPptx({
 *   geometryPath: "deck-geometry.json",  // 或直接传 geometry 对象（此时 output 必填）
 *   output: "out/deck-native.pptx"       // 可选，默认 <词干去掉 -geometry>.pptx
 * });
 *
 * 截图模式需要网络（CDN）。路径相对当前会话 workspace，不要加 workspace/ 前缀。
 */
var fs = require("fs");
var zip = require("zip");

var MAX_SLIDES = 40;
var MIN_PX = 320;
var MAX_PX = 4096;
var MIN_SCALE = 1;
var MAX_SCALE = 3;
var SHOT_TIMEOUT_MS = "300000";
var MAX_ELEMENTS_PER_SLIDE = 800;
var IMG_SRC_MAX_CHARS = 1024 * 1024;
var PACKAGE_TAG = "html2pptx@0.2.0";

var LIB_URL = "https://cdn.jsdelivr.net/npm/html-to-image@1.11.11/dist/html-to-image.min.js";
var LIB_TOKEN = "html-to-image";

var A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";
var P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main";
var R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
var REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
var CT_NS = "http://schemas.openxmlformats.org/package/2006/content-types";
var EMU_PER_PX = 9525;

// svg 图片在 PowerPoint 里要走 svgBlip 扩展：现代版本渲 SVG、老版本/WPS 渲 PNG 兜底。
var ASVG_NS = "http://schemas.microsoft.com/office/drawing/2016/SVG/main";
var SVG_BLIP_EXT = "{96DAC541-7B7A-43D3-8B79-37D633B846F1}";
var FALLBACK_PNG_B64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";
var NATIVE_MIME_EXT = { "image/png": "png", "image/jpeg": "jpg", "image/gif": "gif", "image/svg+xml": "svg" };

export async function renderHtmlDeck(options) {
  var opts = options || {};
  var mode = opts.mode == null ? "screenshot" : String(opts.mode);
  if (mode !== "screenshot" && mode !== "native") {
    throw new Error('renderHtmlDeck: mode 只支持 "screenshot"（默认）或 "native"');
  }
  if (mode === "screenshot") {
    return renderScreenshotDeck(opts);
  }
  try {
    var native = await renderNativeDeck(opts);
    native.mode = "native";
    native.fallback = false;
    return native;
  } catch (e) {
    // native 任一步失败（提取/截图/组包）自动回退整页截图模式，同一 output 产物可用
    var fallback = await renderScreenshotDeck(opts);
    fallback.mode = "native";
    fallback.fallback = true;
    fallback.fallbackReason = String((e && e.message) || e);
    return fallback;
  }
}

/**
 * 截图模式（renderHtmlDeck 默认，原行为）：每页经 webview_exec 截成整页
 * PNG/JPG，打包全幅图片页 pptx。
 */
async function renderScreenshotDeck(opts) {
  var output = normalizeRel(opts.output, "output");
  var width = intIn(opts.width == null ? 1280 : opts.width, "width", MIN_PX, MAX_PX);
  var height = intIn(opts.height == null ? 720 : opts.height, "height", MIN_PX, MAX_PX);
  var scale = intIn(opts.scale == null ? 2 : opts.scale, "scale", MIN_SCALE, MAX_SCALE);
  var format = normalizeFormat(opts.format);
  var quality = jpegQuality(opts.quality);

  var html = readHtml(opts);
  var title = deckTitle(opts, html);
  // srcdoc 无 base URL，相对路径图片加载不到：先把 workspace 图片内联为 data URI。
  var inlined = inlineWorkspaceImages(html);

  if (typeof globalThis.$tools === "undefined" || typeof globalThis.$tools.webview_exec !== "function") {
    throw new Error("renderHtmlDeck 需要 $tools.webview_exec（桌面需 Chromium，Android 需系统 WebView）");
  }

  var stamp = Date.now();
  var htmlPathRel = typeof opts.htmlPath === "string" && opts.htmlPath.trim() !== ""
    ? normalizeRel(opts.htmlPath, "htmlPath")
    : null;
  // 原文未改动且来自文件时直接作为 webview 输入；内联 html 或改写过的才落临时文件。
  var useOriginalFile = htmlPathRel != null && inlined === html;
  var inputRel = useOriginalFile
    ? htmlPathRel
    : "tmp/html2pptx-input-" + stamp + ".html";
  var shotsRel = "tmp/html2pptx-shots-" + stamp + ".json";
  if (!useOriginalFile) {
    fs.writeFileSync(inputRel, inlined);
  }

  try {
    var receiptText = await globalThis.$tools.webview_exec({
      code: captureCode(width, height, scale, format, quality),
      input_path: inputRel,
      output_path: shotsRel,
      timeout_ms: SHOT_TIMEOUT_MS
    });
    var receipt = asObject(receiptText);
    if (!receipt || receipt.ok === false) {
      var err = receipt && receipt.error ? receipt.error : "webview_exec 失败";
      throw new Error(String(err));
    }
    var shotsPath = receipt.outputPath ? String(receipt.outputPath) : shotsRel;
    var payload;
    try {
      payload = JSON.parse(fs.readFileSync(shotsPath).toString().trim());
    } catch (e) {
      throw new Error("截图回执不是 JSON（页面可能未找到 .slide 元素）");
    }
    var slides = payload && payload.slides;
    if (!slides || !slides.length) {
      throw new Error('HTML 里没有 class="slide" 的页元素：每页应是 <section class="slide">…</section>');
    }
    if (slides.length > MAX_SLIDES) {
      throw new Error("renderHtmlDeck: too many slides（上限 " + MAX_SLIDES + "）");
    }
    var decoded = [];
    for (var i = 0; i < slides.length; i++) {
      var shot = slides[i];
      if (!shot || typeof shot.b64 !== "string" || !shot.b64) {
        throw new Error("第 " + (i + 1) + " 页截图数据缺失");
      }
      var bytes = decodeBase64(shot.b64);
      if (!isImageMagic(bytes, format)) {
        throw new Error("第 " + (i + 1) + " 页不是有效的 " + format.toUpperCase() + "（页面资源可能加载失败）");
      }
      decoded.push({ bytes: bytes, ext: format === "jpg" ? "jpg" : "png" });
    }
    var buildDir = "tmp/html2pptx-render-" + stamp;
    writePptxTree(buildDir, decoded, title, width, height);
    packDir(buildDir, output);
    var total = fs.readFileSync(output).length;
    return { ok: true, path: output, slides: decoded.length, width: width, height: height, bytes: total };
  } finally {
    if (!useOriginalFile) {
      unlinkQuiet(inputRel);
    }
    unlinkQuiet(shotsRel);
  }
}

/**
 * webview_exec 里 deck 装载的公共段：decode → 校验 → iframe srcdoc 渲染 →
 * （截图模式）注入并等待 html-to-image → 字体就绪 → 找 .slide → 等图片、
 * 收集加载失败清单。返回行数组，由截图/提取各自的代码生成器续写。
 */
function deckLoadCode(width, height, needLib) {
  var lines = [
    "return (async () => {",
    "  var html = new TextDecoder('utf-8').decode(input).replace(/^\\uFEFF/, '').trim();",
    "  if (!/<html[\\s>]/i.test(html)) {",
    "    throw new Error('input is not an html document');",
    "  }"
  ];
  if (needLib) {
    lines.push(
      "  var inject = '<script src=\"" + LIB_URL + "\"></script>';",
      "  if (html.indexOf('" + LIB_TOKEN + "') < 0) {",
      "    html = /<\\/body>/i.test(html)",
      "      ? html.replace(/<\\/body>/i, inject + '</body>')",
      "      : html + inject;",
      "  }"
    );
  }
  lines.push(
    "  var iframe = document.createElement('iframe');",
    "  iframe.style.cssText = 'position:fixed;left:-99999px;top:0;border:0;width:" + width + "px;height:" + height + "px;';",
    "  iframe.srcdoc = html;",
    "  document.body.appendChild(iframe);",
    "  await new Promise(function (resolve, reject) {",
    "    iframe.onload = function () { resolve(); };",
    "    setTimeout(function () { reject(new Error('iframe load timeout')); }, 30000);",
    "  });",
    "  var win = iframe.contentWindow;",
    "  var doc = win.document;"
  );
  if (needLib) {
    lines.push(
      "  for (var wait = 0; wait < 200 && !(win.htmlToImage && win.htmlToImage.toPng); wait++) {",
      "    await new Promise(function (r) { setTimeout(r, 100); });",
      "  }",
      "  if (!(win.htmlToImage && win.htmlToImage.toPng)) {",
      "    throw new Error('html-to-image 未加载（CDN 不可达？）');",
      "  }"
    );
  }
  lines.push(
    "  if (doc.fonts && doc.fonts.ready) {",
    "    try { await doc.fonts.ready; } catch (e) {}",
    "  }",
    "  var nodes = doc.querySelectorAll('.slide');",
    "  if (!nodes.length) {",
    "    throw new Error('no .slide elements: 每页应是 <section class=\"slide\">');",
    "  }",
    "  var broken = [];",
    "  for (var im = 0; im < doc.images.length; im++) {",
    "    var img = doc.images[im];",
    "    if (!img.complete) {",
    "      await new Promise(function (resolve) {",
    "        img.onload = function () { resolve(); };",
    "        img.onerror = function () { resolve(); };",
    "        setTimeout(resolve, 10000);",
    "      });",
    "    }",
    "    if (!img.complete || img.naturalWidth === 0) {",
    "      broken.push(img.getAttribute('src') || '(unnamed)');",
    "    }",
    "  }"
  );
  return lines;
}

/** 在 webview_exec 里跑的截图代码：iframe 渲染 deck HTML，逐页导出 base64。 */
function captureCode(width, height, scale, format, quality) {
  return deckLoadCode(width, height, true).concat([
    "  if (broken.length) {",
    "    throw new Error('图片加载失败: ' + broken.join(', ') + '（相对路径需指向 workspace 内已有图片）');",
    "  }",
    "  var slides = [];",
    "  for (var i = 0; i < nodes.length; i++) {",
    "    var w = Math.round(nodes[i].offsetWidth) || " + width + ";",
    "    var h = Math.round(nodes[i].offsetHeight) || " + height + ";",
    "    var opts = { width: w, height: h, pixelRatio: " + scale + " };",
    "    var dataUrl;",
    "    try {",
    "      dataUrl = " + (format === "jpg"
      ? "await win.htmlToImage.toJpeg(nodes[i], merge(opts, { quality: " + quality + " }));"
      : "await win.htmlToImage.toPng(nodes[i], opts);"),
    "    } catch (e) {",
    "      throw new Error('第 ' + (i + 1) + ' 页截图失败: ' + ((e && e.message) || String(e)));",
    "    }",
    "    slides.push({ w: w, h: h, b64: String(dataUrl).split(',')[1] });",
    "  }",
    "  function merge(a, b) { var o = {}; for (var k in a) { o[k] = a[k]; } for (var k2 in b) { o[k2] = b[k2]; } return o; }",
    "  return JSON.stringify({ slides: slides });",
    "})();"
  ]).join("\n");
}

/**
 * 提取 DOM 几何：渲染 deck HTML 后遍历每个 .slide 的可见元素，输出
 * {version, generator, slides:[{slideW, slideH, bg?, bgImage?, elements:[…]}]}。
 * 元素：{tag, cls?, rect{x,y,w,h}(相对 slide 左上), font{size,weight,color,align,
 * lineHeight,family}, bg?, bgImage?, border?, radius?, shadow?, textRuns[], imgSrc?}。
 * 纯结构容器（无背景/边框/圆角/阴影/直接文本/图片）跳过；textRuns 合并相邻同款 run；
 * img/canvas/svg 记 data/https 引用，超 IMG_SRC_MAX_CHARS 省略并标 imgOmitted。
 */
export async function extractDeckGeometry(options) {
  var opts = options || {};
  var width = intIn(opts.width == null ? 1280 : opts.width, "width", MIN_PX, MAX_PX);
  var height = intIn(opts.height == null ? 720 : opts.height, "height", MIN_PX, MAX_PX);
  var output = geometryOutput(opts);

  var html = readHtml(opts);
  var inlined = inlineWorkspaceImages(html);

  if (typeof globalThis.$tools === "undefined" || typeof globalThis.$tools.webview_exec !== "function") {
    throw new Error("extractDeckGeometry 需要 $tools.webview_exec（桌面需 Chromium，Android 需系统 WebView）");
  }

  var stamp = Date.now();
  var htmlPathRel = typeof opts.htmlPath === "string" && opts.htmlPath.trim() !== ""
    ? normalizeRel(opts.htmlPath, "htmlPath")
    : null;
  var useOriginalFile = htmlPathRel != null && inlined === html;
  var inputRel = useOriginalFile
    ? htmlPathRel
    : "tmp/html2pptx-geo-input-" + stamp + ".html";
  var geoRel = "tmp/html2pptx-geo-" + stamp + ".json";
  if (!useOriginalFile) {
    fs.writeFileSync(inputRel, inlined);
  }

  try {
    var receiptText = await globalThis.$tools.webview_exec({
      code: extractCode(width, height),
      input_path: inputRel,
      output_path: geoRel,
      timeout_ms: SHOT_TIMEOUT_MS
    });
    var receipt = asObject(receiptText);
    if (!receipt || receipt.ok === false) {
      var err = receipt && receipt.error ? receipt.error : "webview_exec 失败";
      throw new Error(String(err));
    }
    var geoPath = receipt.outputPath ? String(receipt.outputPath) : geoRel;
    var payload;
    try {
      payload = JSON.parse(fs.readFileSync(geoPath).toString().trim());
    } catch (e) {
      throw new Error("几何提取回执不是 JSON（页面可能未找到 .slide 元素）");
    }
    var slides = payload && payload.slides;
    if (!slides || !slides.length) {
      throw new Error('HTML 里没有 class="slide" 的页元素：每页应是 <section class="slide">…</section>');
    }
    if (slides.length > MAX_SLIDES) {
      throw new Error("extractDeckGeometry: too many slides（上限 " + MAX_SLIDES + "）");
    }
    var elementCount = 0;
    for (var i = 0; i < slides.length; i++) {
      var s = slides[i];
      if (!s || typeof s.slideW !== "number" || typeof s.slideH !== "number" || !Array.isArray(s.elements)) {
        throw new Error("第 " + (i + 1) + " 页几何数据结构不完整");
      }
      elementCount += s.elements.length;
    }
    fs.writeFileSync(output, JSON.stringify(payload, null, 2));
    return { ok: true, path: output, slides: slides.length, elements: elementCount };
  } finally {
    if (!useOriginalFile) {
      unlinkQuiet(inputRel);
    }
    unlinkQuiet(geoRel);
  }
}

/** 几何 JSON 落盘路径：默认 <htmlPath 词干>-geometry.json；内联 html 必须显式传 output。 */
function geometryOutput(opts) {
  if (opts.output != null && String(opts.output).trim() !== "") {
    return normalizeRel(opts.output, "output");
  }
  if (typeof opts.htmlPath === "string" && opts.htmlPath.trim() !== "") {
    var path = normalizeRel(opts.htmlPath, "htmlPath");
    return path.replace(/\.x?html?$/i, "") + "-geometry.json";
  }
  throw new Error("内联 html 必须显式传 output（workspace 相对的 *-geometry.json 路径）");
}

/** 在 webview_exec 里跑的几何提取代码：iframe 渲染 deck HTML，逐页遍历元素。 */
function extractCode(width, height) {
  return deckLoadCode(width, height, false)
    .concat(geometryHelpers())
    .concat(geometryRecord())
    .concat([
      "  return JSON.stringify({ version: 1, generator: '" + PACKAGE_TAG + " extractDeckGeometry', slides: slides });",
      "})();"
    ]).join("\n");
}

/** 提取段公共函数（px/颜色/图片源/textRuns/边框/圆角），依赖 deckLoadCode 的 win。 */
function geometryHelpers() {
  return [
    "  function px(v) { var n = parseFloat(v); return isFinite(n) ? n : 0; }",
    "  function r2(n) { return Math.round(n * 100) / 100; }",
    "  function normColor(c) {",
    "    var m = /^rgba?\\(([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+)(?:,\\s*([\\d.]+))?\\)$/.exec(String(c).trim());",
    "    if (!m) { return c === 'transparent' ? null : String(c); }",
    "    if (m[4] != null && parseFloat(m[4]) === 0) { return null; }",
    "    function hx(s) { var b = Math.max(0, Math.min(255, Math.round(parseFloat(s)))); return (b < 16 ? '0' : '') + b.toString(16); }",
    "    return '#' + hx(m[1]) + hx(m[2]) + hx(m[3]);",
    "  }",
    "  function imgSource(el, tag) {",
    "    var src = null;",
    "    if (tag === 'img') { src = el.currentSrc || el.getAttribute('src') || ''; }",
    "    else if (tag === 'canvas') { try { src = el.toDataURL('image/png'); } catch (e) { src = null; } }",
    "    else if (tag === 'svg') { try { src = 'data:image/svg+xml;utf8,' + encodeURIComponent(el.outerHTML); } catch (e) { src = null; } }",
    "    if (src === '' || src == null) { return { src: null, omitted: false }; }",
    "    if (src.length > " + IMG_SRC_MAX_CHARS + ") { return { src: null, omitted: true }; }",
    "    return { src: src, omitted: false };",
    "  }",
    "  function runsOf(el) {",
    "    var runs = [];",
    "    function walk(node, cs) {",
    "      for (var i = 0; i < node.childNodes.length; i++) {",
    "        var ch = node.childNodes[i];",
    "        if (ch.nodeType === 3) {",
    "          var t = ch.nodeValue.replace(/\\s+/g, ' ').trim();",
    "          if (!t) { continue; }",
    "          var dec = cs.textDecorationLine || cs.textDecoration || 'none';",
    "          var sameAsLast = false;",
    "          if (runs.length) {",
    "            var last = runs[runs.length - 1];",
    "            sameAsLast = last.size === px(cs.fontSize) && last.weight === String(cs.fontWeight)",
    "              && last.color === (normColor(cs.color) || '#000000')",
    "              && last.italic === (cs.fontStyle === 'italic') && last.underline === (dec !== 'none');",
    "          }",
    "          if (sameAsLast) { last.text += ' ' + t; }",
    "          else {",
    "            runs.push({ text: t, size: px(cs.fontSize), weight: String(cs.fontWeight), color: normColor(cs.color) || '#000000', italic: cs.fontStyle === 'italic', underline: dec !== 'none' });",
    "          }",
    "        } else if (ch.nodeType === 1) {",
    "          var d = win.getComputedStyle(ch).display;",
    "          if (d === 'inline' || d === 'inline-block') { walk(ch, win.getComputedStyle(ch)); }",
    "        }",
    "      }",
    "    }",
    "    walk(el, win.getComputedStyle(el));",
    "    return runs;",
    "  }",
    "  function borderOf(cs) {",
    "    var sides = [['top', cs.borderTopWidth, cs.borderTopColor], ['right', cs.borderRightWidth, cs.borderRightColor], ['bottom', cs.borderBottomWidth, cs.borderBottomColor], ['left', cs.borderLeftWidth, cs.borderLeftColor]];",
    "    var out = null;",
    "    for (var s = 0; s < sides.length; s++) {",
    "      var w = px(sides[s][1]);",
    "      if (w <= 0) { continue; }",
    "      out = out || {};",
    "      out[sides[s][0]] = { w: r2(w), color: normColor(sides[s][2]) };",
    "    }",
    "    return out;",
    "  }",
    "  function radiusOf(cs, w, h) {",
    "    function one(v) {",
    "      var str = String(v);",
    "      var n = parseFloat(str);",
    "      if (!isFinite(n) || n <= 0) { return 0; }",
    "      if (str.indexOf('%') >= 0) { return r2(n / 100 * Math.min(w, h) / 2); }",
    "      return r2(n);",
    "    }",
    "    var tl = one(cs.borderTopLeftRadius), tr = one(cs.borderTopRightRadius), br = one(cs.borderBottomRightRadius), bl = one(cs.borderBottomLeftRadius);",
    "    if (tl === 0 && tr === 0 && br === 0 && bl === 0) { return null; }",
    "    if (tl === tr && tr === br && br === bl) { return tl; }",
    "    return { tl: tl, tr: tr, br: br, bl: bl };",
    "  }"
  ];
}

/** recordSlide 定义 + slides 收集（不含 return 行），native 模式在此基础上续写 clip 段。 */
function geometryRecord() {
  return [
    "  function recordSlide(node) {",
    "    var cs = win.getComputedStyle(node);",
    "    var sr = node.getBoundingClientRect();",
    "    var slide = { slideW: Math.round(sr.width), slideH: Math.round(sr.height) };",
    "    var sbg = normColor(cs.backgroundColor);",
    "    if (sbg != null) { slide.bg = sbg; }",
    "    if (cs.backgroundImage && cs.backgroundImage !== 'none') { slide.bgImage = cs.backgroundImage; }",
    "    slide.elements = [];",
    "    var all = node.querySelectorAll('*');",
    "    for (var i = 0; i < all.length && slide.elements.length < " + MAX_ELEMENTS_PER_SLIDE + "; i++) {",
    "      var el = all[i];",
    "      var ecs = win.getComputedStyle(el);",
    "      if (ecs.display === 'none' || ecs.visibility === 'hidden') { continue; }",
    "      var r = el.getBoundingClientRect();",
    "      if (r.width < 0.5 || r.height < 0.5) { continue; }",
    "      var tag = el.tagName.toLowerCase();",
    "      var runs = runsOf(el);",
    "      var img = tag === 'img' || tag === 'canvas' || tag === 'svg' || tag === 'video' ? imgSource(el, tag) : { src: null, omitted: false };",
    "      var bg = normColor(ecs.backgroundColor);",
    "      var bgImage = ecs.backgroundImage && ecs.backgroundImage !== 'none' ? ecs.backgroundImage : null;",
    "      var border = borderOf(ecs);",
    "      var radius = radiusOf(ecs, r.width, r.height);",
    "      var shadow = ecs.boxShadow && ecs.boxShadow !== 'none' ? ecs.boxShadow : null;",
    "      var visual = bg != null || bgImage != null || border != null || radius != null || shadow != null || runs.length > 0 || (img && (img.src != null || img.omitted));",
    "      if (!visual) { continue; }",
    "      var item = { tag: tag, rect: { x: r2(r.left - sr.left), y: r2(r.top - sr.top), w: r2(r.width), h: r2(r.height) } };",
    "      if (typeof el.className === 'string' && el.className.trim()) { item.cls = el.className.trim(); }",
    "      item.font = { size: r2(px(ecs.fontSize)), weight: String(ecs.fontWeight), color: normColor(ecs.color) || '#000000', align: ecs.textAlign, lineHeight: r2(px(ecs.lineHeight)), family: ecs.fontFamily };",
    "      if (bg != null) { item.bg = bg; }",
    "      if (bgImage != null) { item.bgImage = bgImage; }",
    "      if (border != null) { item.border = border; }",
    "      if (radius != null) { item.radius = radius; }",
    "      if (shadow != null) { item.shadow = shadow; }",
    "      if (runs.length) { item.textRuns = runs; }",
    "      if (img && img.src != null) { item.imgSrc = img.src; }",
    "      else if (img && img.omitted) { item.imgOmitted = true; }",
    "      slide.elements.push(item);",
    "    }",
    "    return slide;",
    "  }",
    "  var slides = [];",
    "  for (var si = 0; si < nodes.length; si++) { slides.push(recordSlide(nodes[si])); }"
  ];
}

/**
 * native 模式的 webview 代码：几何提取 + 区域截图一次完成。对带阴影（矩形外扩
 * offset/blur/spread）、远程图片、超限省略图片的元素，整页 toPng 一次后按区域
 * canvas 裁剪出 clip PNG，挂到对应页 clips 数组（QuickJS 侧补真用）。
 */
function nativeCaptureCode(width, height, scale) {
  return deckLoadCode(width, height, true)
    .concat(geometryHelpers())
    .concat(geometryRecord())
    .concat([
      "  function expanded(rect, shadow) {",
      "    var m = String(shadow).match(/-?[\\d.]+px/g) || [];",
      "    var ox = m.length > 0 ? Math.abs(parseFloat(m[0])) : 0;",
      "    var oy = m.length > 1 ? Math.abs(parseFloat(m[1])) : 0;",
      "    var blur = m.length > 2 ? parseFloat(m[2]) : 0;",
      "    var spread = m.length > 3 ? Math.abs(parseFloat(m[3])) : 0;",
      "    var pad = Math.ceil(Math.max(ox, oy) + blur + spread);",
      "    return { x: rect.x - pad, y: rect.y - pad, w: rect.w + pad * 2, h: rect.h + pad * 2 };",
      "  }",
      "  function clampRect(rect, w, h) {",
      "    var x = Math.max(0, rect.x);",
      "    var y = Math.max(0, rect.y);",
      "    var x2 = Math.min(w, rect.x + rect.w);",
      "    var y2 = Math.min(h, rect.y + rect.h);",
      "    return { x: x, y: y, w: Math.max(0, x2 - x), h: Math.max(0, y2 - y) };",
      "  }",
      "  function needsRects(slide) {",
      "    var out = [];",
      "    for (var i = 0; i < slide.elements.length; i++) {",
      "      var el = slide.elements[i];",
      "      var isImg = el.tag === 'img' || el.tag === 'canvas' || el.tag === 'svg' || el.tag === 'video';",
      "      var gradText = el.textRuns && el.textRuns.length && el.bgImage",
      "        && String(el.bgImage).indexOf('gradient') >= 0 && el.font && el.font.color === '#000000';",
      "      if (el.shadow) { out.push(expanded(el.rect, el.shadow)); }",
      "      else if (isImg || el.imgOmitted || gradText) { out.push(el.rect); }",
      "    }",
      "    return out;",
      "  }",
      "  for (var ni = 0; ni < slides.length; ni++) {",
      "    var nslide = slides[ni];",
      "    nslide.clips = [];",
      "    var rects = needsRects(nslide);",
      "    if (!rects.length) { continue; }",
      "    var node = nodes[ni];",
      "    var pw = Math.round(node.offsetWidth) || " + width + ";",
      "    var ph = Math.round(node.offsetHeight) || " + height + ";",
      "    var dataUrl;",
      "    try {",
      "      dataUrl = await win.htmlToImage.toPng(node, { width: pw, height: ph, pixelRatio: " + scale + " });",
      "    } catch (e) {",
      "      throw new Error('第 ' + (ni + 1) + ' 页区域截图失败: ' + ((e && e.message) || String(e)));",
      "    }",
      "    var shot = new Image();",
      "    await new Promise(function (resolve, reject) {",
      "      shot.onload = function () { resolve(); };",
      "      shot.onerror = function () { reject(new Error('区域截图解码失败')); };",
      "      shot.src = dataUrl;",
      "    });",
      "    for (var ri = 0; ri < rects.length; ri++) {",
      "      var rr = clampRect(rects[ri], pw, ph);",
      "      if (rr.w < 2 || rr.h < 2) { continue; }",
      "      var cv = document.createElement('canvas');",
      "      cv.width = Math.round(rr.w * " + scale + ");",
      "      cv.height = Math.round(rr.h * " + scale + ");",
      "      var ctx = cv.getContext('2d');",
      "      ctx.drawImage(shot, rr.x * " + scale + ", rr.y * " + scale + ", rr.w * " + scale + ", rr.h * " + scale + ", 0, 0, cv.width, cv.height);",
      "      nslide.clips.push({ x: r2(rr.x), y: r2(rr.y), w: r2(rr.w), h: r2(rr.h), b64: cv.toDataURL('image/png').split(',')[1] });",
      "    }",
      "  }",
      // 图片一律走 clip 补真：回执不再携带 imgSrc/imgOmitted（data URI 动辄百 KB–MB，
      // 是 QuickJS 回执与内存的主要负担，也把转换职责收窄为文本+盒子+背景+截图）
      "  for (var sk = 0; sk < slides.length; sk++) {",
      "    var sels = slides[sk].elements;",
      "    for (var ek = 0; ek < sels.length; ek++) { delete sels[ek].imgSrc; delete sels[ek].imgOmitted; }",
      "  }",
      "  return JSON.stringify({ version: 1, generator: '" + PACKAGE_TAG + " native capture', slides: slides });",
      "})();"
    ]).join("\n");
}

/**
 * native 模式（renderHtmlDeck mode:"native"）：一次 webview_exec 完成几何提取
 * + 需截图区域导出，QuickJS 侧 applyClips 用区域截图补真（替代阴影/远程图
 * 近似），再走 geometryToPptx 组原生可编辑 pptx。broken 图片不硬报错（交给
 * clip 真实渲染）；任一步失败由 renderHtmlDeck 回退整页截图模式。
 */
async function renderNativeDeck(opts) {
  var output = normalizeRel(opts.output, "output");
  var width = intIn(opts.width == null ? 1280 : opts.width, "width", MIN_PX, MAX_PX);
  var height = intIn(opts.height == null ? 720 : opts.height, "height", MIN_PX, MAX_PX);
  var scale = intIn(opts.scale == null ? 2 : opts.scale, "scale", MIN_SCALE, MAX_SCALE);

  var html = readHtml(opts);
  var title = deckTitle(opts, html);
  var inlined = inlineWorkspaceImages(html);

  if (typeof globalThis.$tools === "undefined" || typeof globalThis.$tools.webview_exec !== "function") {
    throw new Error("renderHtmlDeck 需要 $tools.webview_exec（桌面需 Chromium，Android 需系统 WebView）");
  }

  var stamp = Date.now();
  var htmlPathRel = typeof opts.htmlPath === "string" && opts.htmlPath.trim() !== ""
    ? normalizeRel(opts.htmlPath, "htmlPath")
    : null;
  var useOriginalFile = htmlPathRel != null && inlined === html;
  var inputRel = useOriginalFile
    ? htmlPathRel
    : "tmp/html2pptx-native-input-" + stamp + ".html";
  var payloadRel = "tmp/html2pptx-native-" + stamp + ".json";
  if (!useOriginalFile) {
    fs.writeFileSync(inputRel, inlined);
  }

  try {
    var receiptText = await globalThis.$tools.webview_exec({
      code: nativeCaptureCode(width, height, scale),
      input_path: inputRel,
      output_path: payloadRel,
      timeout_ms: SHOT_TIMEOUT_MS
    });
    var receipt = asObject(receiptText);
    if (!receipt || receipt.ok === false) {
      var err = receipt && receipt.error ? receipt.error : "webview_exec 失败";
      throw new Error(String(err));
    }
    var payloadPath = receipt.outputPath ? String(receipt.outputPath) : payloadRel;
    var payload;
    try {
      payload = JSON.parse(fs.readFileSync(payloadPath).toString().trim());
    } catch (e) {
      throw new Error("native 回执不是 JSON（页面可能未找到 .slide 元素）");
    }
    var slides = payload && payload.slides;
    if (!slides || !slides.length) {
      throw new Error('HTML 里没有 class="slide" 的页元素：每页应是 <section class="slide">…</section>');
    }
    if (slides.length > MAX_SLIDES) {
      throw new Error("renderHtmlDeck: too many slides（上限 " + MAX_SLIDES + "）");
    }
    validateNativePayload(slides);
    applyClips(slides);
    var geometry = { version: 1, generator: PACKAGE_TAG + " renderHtmlDeck native", slides: slides };
    return geometryToPptx({ geometry: geometry, output: output, title: title });
  } finally {
    if (!useOriginalFile) {
      unlinkQuiet(inputRel);
    }
    unlinkQuiet(payloadRel);
  }
}

/** 校验 native 回执：每页结构完整；clip 平铺矩形 + b64 必须是 PNG。 */
function validateNativePayload(slides) {
  for (var i = 0; i < slides.length; i++) {
    var s = slides[i];
    if (!s || typeof s.slideW !== "number" || typeof s.slideH !== "number" || !Array.isArray(s.elements)) {
      throw new Error("第 " + (i + 1) + " 页几何数据结构不完整");
    }
    if (s.clips == null) {
      continue;
    }
    if (!Array.isArray(s.clips)) {
      throw new Error("第 " + (i + 1) + " 页 clips 结构不完整");
    }
    for (var c = 0; c < s.clips.length; c++) {
      var cl = s.clips[c];
      if (!cl || typeof cl.b64 !== "string" || !cl.b64
        || typeof cl.x !== "number" || typeof cl.y !== "number"
        || typeof cl.w !== "number" || typeof cl.h !== "number") {
        throw new Error("第 " + (i + 1) + " 页 clip 数据缺失");
      }
      if (!isPng(decodeBase64(cl.b64))) {
        throw new Error("第 " + (i + 1) + " 页 clip 不是有效的 PNG");
      }
    }
  }
}

/**
 * 区域截图补真：clip 矩形完全包含的元素删除（阴影近似/远程图占位不再需要，
 * 也避免与截图重复绘制），clip 作为顶层图片元素追加到页尾（DOM 序即 z 序，
 * 截图盖在其覆盖区域的近似之上）。跨出 clip 边界的文本保留，与 clip 边缘
 * 轻微重叠可接受。
 */
function applyClips(slides) {
  for (var i = 0; i < slides.length; i++) {
    var slide = slides[i];
    var clips = slide.clips;
    if (!clips || !clips.length) {
      continue;
    }
    var kept = [];
    var els = slide.elements;
    for (var e = 0; e < els.length; e++) {
      var el = els[e];
      var covered = false;
      for (var c = 0; c < clips.length; c++) {
        if (containsRect(clips[c], el.rect, 2)) {
          covered = true;
          break;
        }
      }
      if (!covered) {
        kept.push(el);
      }
    }
    for (var c2 = 0; c2 < clips.length; c2++) {
      var k = clips[c2];
      kept.push({
        tag: "img",
        cls: "html2pptx-clip",
        rect: { x: k.x, y: k.y, w: k.w, h: k.h },
        imgSrc: "data:image/png;base64," + k.b64,
        font: { size: 16, weight: "400", color: "#000000", align: "left", lineHeight: 0 }
      });
    }
    slide.elements = kept;
  }
}

/**
 * geometry JSON → 原生 OOXML pptx（20-A T3）。纯 QuickJS（fs + zip），不需要
 * webview/网络：文本块→p:sp 文本框（EMU = px×9525、字号 px×0.75pt），带背景/
 * 圆角/边框的块→roundRect（fill/line/adj），线性渐变→gradFill，radial 渐变退化
 * 为首色 alpha 近似；img/canvas/svg 的 data URI→p:pic（svg 走 svgBlip 扩展 +
 * 1×1 透明 PNG 兜底）。阴影用 outerShdw 近似；阴影/超限图片/远程图片记入
 * needsScreenshot（含页码与矩形），由 T4 的区域截图补真。行内元素（em/span/
 * svg text）文本已包含在祖先文本块里、且 rect 被包含时跳过，避免重复绘制。
 */
export function geometryToPptx(options) {
  var opts = options || {};
  var geo = readGeometry(opts);
  var output = nativePptxOutput(opts);

  var slides = geo.slides;
  if (!slides || !slides.length) {
    throw new Error("几何 JSON 里没有 slides（应来自 extractDeckGeometry 的输出）");
  }
  if (slides.length > MAX_SLIDES) {
    throw new Error("geometryToPptx: too many slides（上限 " + MAX_SLIDES + "）");
  }
  for (var i = 0; i < slides.length; i++) {
    var s = slides[i];
    if (!s || typeof s.slideW !== "number" || typeof s.slideH !== "number" || !Array.isArray(s.elements)) {
      throw new Error("第 " + (i + 1) + " 页几何数据结构不完整");
    }
  }

  var deck = buildNativeDeck(slides);
  var title = opts.title != null && String(opts.title).trim() !== "" ? String(opts.title).trim() : "Presentation";
  var stamp = Date.now();
  var buildDir = "tmp/html2pptx-native-" + stamp;
  writeNativePptxTree(buildDir, deck, title);
  packDir(buildDir, output);
  var total = fs.readFileSync(output).length;
  return {
    ok: true,
    path: output,
    slides: slides.length,
    shapes: deck.shapeCount,
    pictures: deck.picCount,
    needsScreenshot: deck.needsScreenshot,
    bytes: total
  };
}

/** 读取几何：内联对象直接用；geometryPath 走 workspace .json。 */
function readGeometry(opts) {
  if (opts.geometry != null && typeof opts.geometry === "object") {
    return opts.geometry;
  }
  if (typeof opts.geometryPath === "string" && opts.geometryPath.trim() !== "") {
    var path = normalizeRel(opts.geometryPath, "geometryPath");
    if (!/\.json$/i.test(path)) {
      throw new Error("geometryPath 必须是 .json 文件");
    }
    try {
      return JSON.parse(fs.readFileSync(path).toString().trim());
    } catch (e) {
      throw new Error("geometryPath 不是合法 JSON: " + path);
    }
  }
  throw new Error("需要 geometry（extractDeckGeometry 的输出对象）或 geometryPath（workspace .json 路径）");
}

/** pptx 落盘路径：默认 <geometryPath 词干去掉 -geometry>.pptx；内联必须显式传 output。 */
function nativePptxOutput(opts) {
  if (opts.output != null && String(opts.output).trim() !== "") {
    return normalizeRel(opts.output, "output");
  }
  if (typeof opts.geometryPath === "string" && opts.geometryPath.trim() !== "") {
    var path = normalizeRel(opts.geometryPath, "geometryPath");
    if (!/\.json$/i.test(path)) {
      throw new Error("geometryPath 必须是 .json 文件");
    }
    return path.replace(/\.json$/i, "").replace(/-geometry$/i, "") + ".pptx";
  }
  throw new Error("内联 geometry 必须显式传 output（workspace 相对的 .pptx 路径）");
}

/** 组 deck：每页 slide XML + rels，媒体注册表，计数与 needsScreenshot 清单。 */
function buildNativeDeck(slides) {
  var media = nativeMedia();
  var deck = {
    slideW: slides[0].slideW,
    slideH: slides[0].slideH,
    slides: [],
    media: media,
    exts: [],
    shapeCount: 0,
    picCount: 0,
    needsScreenshot: []
  };
  for (var i = 0; i < slides.length; i++) {
    var built = nativeSlide(slides[i], media, i + 1);
    deck.slides.push(built);
    deck.shapeCount += built.shapeCount;
    deck.picCount += built.picCount;
    for (var n = 0; n < built.needs.length; n++) {
      deck.needsScreenshot.push(built.needs[n]);
    }
  }
  var seen = {};
  for (var j = 0; j < media.files.length; j++) {
    var ext = media.files[j].name.split(".").pop();
    if (!seen[ext]) {
      seen[ext] = true;
      deck.exts.push(ext);
    }
  }
  return deck;
}

/** 媒体注册表：data URI 去重复用；svg 附共享 1×1 透明 PNG 兜底件。 */
function nativeMedia() {
  var files = [];
  var byUri = {};
  var fallbackName = null;
  return {
    files: files,
    register: function (uri) {
      if (Object.prototype.hasOwnProperty.call(byUri, uri)) {
        return byUri[uri];
      }
      var entry = null;
      var parsed = parseDataUri(uri);
      if (parsed && parsed.mime === "image/svg+xml") {
        var text = null;
        if (parsed.base64) {
          // base64 svg 少见；按字节转字符串（多字节字符可能失真，常规路径是 utf8 百分号编码）
          var bytes = decodeBase64(parsed.payload);
          var out = "";
          for (var i = 0; i < bytes.length; i++) {
            out += String.fromCharCode(bytes[i]);
          }
          text = out;
        } else {
          try {
            text = decodeURIComponent(parsed.payload);
          } catch (e) {
            text = null;
          }
        }
        if (text != null) {
          if (!fallbackName) {
            fallbackName = "svg-fallback.png";
            files.push({ name: fallbackName, data: decodeBase64(FALLBACK_PNG_B64), text: null });
          }
          var svgName = "image" + (files.length + 1) + ".svg";
          files.push({ name: svgName, data: "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + text, text: text });
          entry = { kind: "svg", svg: svgName, png: fallbackName };
        }
      } else if (parsed && parsed.base64 && NATIVE_MIME_EXT[parsed.mime]) {
        var name = "image" + (files.length + 1) + "." + NATIVE_MIME_EXT[parsed.mime];
        files.push({ name: name, data: decodeBase64(parsed.payload), text: null });
        entry = { kind: "raster", file: name };
      }
      byUri[uri] = entry;
      return entry;
    }
  };
}

/** data:image/png;base64,xx / data:image/svg+xml;utf8,%3Csvg… 解析。 */
function parseDataUri(uri) {
  var m = /^data:([^;,]*)((?:;[^,]*)*),([\s\S]*)$/.exec(String(uri));
  if (!m) {
    return null;
  }
  var params = m[2] ? m[2].split(";") : [];
  var base64 = false;
  for (var i = 0; i < params.length; i++) {
    if (params[i].trim() === "base64") {
      base64 = true;
    }
  }
  return { mime: m[1].toLowerCase(), base64: base64, payload: m[3] };
}

/** 单页转换：去重行内文本 → 逐元素出 XML 节点（保持 DOM 序即 z 序）。 */
function nativeSlide(slide, media, slideNo) {
  var els = slide.elements;
  var skip = textDupes(els);
  var rels = [{ id: "rId1", type: LAYOUT_REL, target: "../slideLayouts/slideLayout1.xml" }];
  var body = "";
  var id = 2;
  var shapeCount = 0;
  var picCount = 0;
  var needs = [];

  function addRel(target) {
    var relId = "rId" + (rels.length + 1);
    rels.push({ id: relId, type: IMAGE_REL, target: target });
    return relId;
  }
  function node(xml) {
    body += xml;
  }

  // 渐变页背景 → 整页最底层矩形（WPS 对 p:bg 的 gradFill 不可靠，T5b 实测）
  var gradBg = slide.bgImage ? parseGradient(slide.bgImage) : null;
  if (gradBg) {
    body += pageBackgroundSpXml(slide, gradBg, id);
    id++;
    shapeCount++;
  }

  for (var i = 0; i < els.length; i++) {
    var el = els[i];
    if (!el || !el.rect || skip[i]) {
      continue;
    }
    if (el.imgOmitted || (el.imgSrc && !/^data:/i.test(el.imgSrc))) {
      needs.push(needEntry(slideNo, el.rect, el.imgOmitted ? "imgOmitted" : "remote image"));
      continue;
    }
    if (el.imgSrc) {
      var entry = media.register(el.imgSrc);
      if (!entry) {
        needs.push(needEntry(slideNo, el.rect, "unsupported image"));
        continue;
      }
      node(picXml(el, entry, addRel, id, el.tag + " " + id));
      id++;
      picCount++;
      continue;
    }
    var hasBox = el.bg != null || el.bgImage != null || el.border != null || el.radius != null;
    var hasText = !!(el.textRuns && el.textRuns.length);
    if (!hasBox && !hasText) {
      continue;
    }
    var name = (el.cls ? el.tag + "." + el.cls.split(/\s+/)[0] : el.tag) + " " + id;
    if (hasText) {
      node(textSpXml(el, id, name, hasBox));
    } else {
      node(boxSpXml(el, id, name));
    }
    id++;
    shapeCount++;
    // 非均匀边框逐边色条，每条独立形状 id
    node(borderStripsXml(el, function () {
      shapeCount++;
      return { id: ++id, name: "border " + id };
    }));
    if (el.shadow) {
      needs.push(needEntry(slideNo, el.rect, "shadow"));
    }
    if (isGradientText(el)) {
      // 渐变字独立走生成器时以渐变首色近似；native 集成模式由区域截图补真
      needs.push(needEntry(slideNo, el.rect, "gradient text"));
    }
  }
  return {
    xml: nativeSlideXml(slideBackgroundXml(slide, gradBg), body),
    rels: rels,
    shapeCount: shapeCount,
    picCount: picCount,
    needs: needs
  };
}

function needEntry(slideNo, rect, reason) {
  return { slide: slideNo, x: rect.x, y: rect.y, w: rect.w, h: rect.h, reason: reason };
}

/**
 * 标记应跳过的行内文本元素：其 run 文本（去空白）是另一更大文本块文本的子串、
 * 且 rect 被该块包含（em/span 在父块里已画过）；或 rect 落在图片元素内
 * （svg 内 text 已在导出的 svg 光栅里）。
 */
function textDupes(els) {
  var skip = [];
  var texts = [];
  for (var i = 0; i < els.length; i++) {
    var t = runsText(els[i]);
    if (t) {
      texts.push({ i: i, text: t });
    }
  }
  for (var a = 0; a < texts.length; a++) {
    var ea = els[texts[a].i];
    for (var b = 0; b < texts.length; b++) {
      if (a === b) {
        continue;
      }
      var eb = els[texts[b].i];
      if (texts[a].text !== texts[b].text && texts[b].text.indexOf(texts[a].text) >= 0
        && rectArea(eb.rect) > rectArea(ea.rect) && containsRect(eb.rect, ea.rect, 2)) {
        skip[texts[a].i] = true;
        break;
      }
    }
    if (skip[texts[a].i]) {
      continue;
    }
    for (var p = 0; p < els.length; p++) {
      var ep = els[p];
      if (ep !== ea && (ep.imgSrc || ep.imgOmitted) && rectArea(ep.rect) > rectArea(ea.rect)
        && containsRect(ep.rect, ea.rect, 2)) {
        skip[texts[a].i] = true;
        break;
      }
    }
  }
  return skip;
}

function runsText(el) {
  if (!el || !el.textRuns || !el.textRuns.length) {
    return "";
  }
  var out = "";
  for (var i = 0; i < el.textRuns.length; i++) {
    out += String(el.textRuns[i].text || "");
  }
  return out.replace(/\s+/g, "");
}

function rectArea(r) {
  return r.w * r.h;
}

function containsRect(outer, inner, tol) {
  return inner.x >= outer.x - tol && inner.y >= outer.y - tol
    && inner.x + inner.w <= outer.x + outer.w + tol
    && inner.y + inner.h <= outer.y + outer.h + tol;
}

/** 页背景：纯色 solidFill；线性渐变 gradFill（CSS 角度→OOXML ang = (θ-90°)×60000）；radial 退化首色。 */
/**
 * 页背景：渐变一律落整页最底层矩形（nativeSlide 里插入）——WPS 对 p:bg 的
 * gradFill 渲染不可靠（20-A T5b 用户 WPS 实测封面背景不对）；p:bg 只留纯色
 * （优先 slide.bg，否则渐变首色兜底，防渲染器不画矩形时白底）。
 */
function slideBackgroundXml(slide, gradBg) {
  var fill = null;
  if (slide.bg != null && isHexColor(slide.bg)) {
    fill = solidFillCss(slide.bg, 1);
  } else if (gradBg && gradBg.stops.length) {
    fill = solidFillCss(gradBg.stops[0].color, 1);
  }
  if (!fill) {
    return "";
  }
  return "<p:bg><p:bgPr>" + fill + "<a:effectLst/></p:bgPr></p:bg>";
}

/** 整页背景矩形（z 序最底，nativeSlide 的 body 首个节点）。 */
function pageBackgroundSpXml(slide, grad, id) {
  var fill = grad.type === "linear" ? gradFillXml(grad) : radialGradFillXml(grad.stops, grad.center);
  return (
    "<p:sp><p:nvSpPr><p:cNvPr id=\"" + id + "\" name=\"background\"/>" +
    "<p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm>" +
    '<a:off x="0" y="0"/><a:ext cx="' + emu(slide.slideW) + '" cy="' + emu(slide.slideH) + '"/></a:xfrm>' +
    '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom>' + fill + "<a:ln><a:noFill/></a:ln></p:spPr></p:sp>"
  );
}

/** p:pic：svg 走 svgBlip 扩展（PNG 兜底在前、SVG 扩展在后），普通位图直接 blip。 */
function picXml(el, entry, addRel, id, name) {
  var r = el.rect;
  var off = emuOff(r);
  var ext = emuExt(r);
  var geom = geomXml(el);
  if (entry.kind === "svg") {
    var pngRid = addRel("../media/" + entry.png);
    var svgRid = addRel("../media/" + entry.svg);
    return (
      "<p:pic><p:nvPicPr><p:cNvPr id=\"" + id + "\" name=\"" + xmlEscape(name) + "\"/>" +
      "<p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>" +
      "<p:blipFill><a:blip r:embed=\"" + pngRid + "\"><a:extLst><a:ext uri=\"" + SVG_BLIP_EXT + "\">" +
      "<asvg:svgBlip xmlns:asvg=\"" + ASVG_NS + "\" r:embed=\"" + svgRid + "\"/>" +
      "</a:ext></a:extLst></a:blip><a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
      "<p:spPr><a:xfrm>" + off + ext + "</a:xfrm>" + geom + "</p:spPr></p:pic>"
    );
  }
  var rid = addRel("../media/" + entry.file);
  return (
    "<p:pic><p:nvPicPr><p:cNvPr id=\"" + id + "\" name=\"" + xmlEscape(name) + "\"/>" +
    "<p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>" +
    "<p:blipFill><a:blip r:embed=\"" + rid + "\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
    "<p:spPr><a:xfrm>" + off + ext + "</a:xfrm>" + geom + "</p:spPr></p:pic>"
  );
}

/** 文本元素 → p:sp：无盒子特征时 txBox=1 + noFill；有则按盒子出形状再叠文字。 */
function textSpXml(el, id, name, hasBox) {
  var geom = hasBox ? geomXml(el) : '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom>';
  var fill = hasBox ? elementFillXml(el) : "<a:noFill/>";
  var ln = uniformBorderLn(el);
  var effect = shadowXml(el);
  return (
    "<p:sp><p:nvSpPr><p:cNvPr id=\"" + id + "\" name=\"" + xmlEscape(name) + "\"/>" +
    (hasBox ? "<p:cNvSpPr/>" : "<p:cNvSpPr txBox=\"1\"/>") +
    "<p:nvPr/></p:nvSpPr><p:spPr><a:xfrm>" + emuOff(el.rect) + emuExt(el.rect) + "</a:xfrm>" +
    geom + fill + (ln || "<a:ln><a:noFill/></a:ln>") + effect + "</p:spPr>" +
    txBodyXml(el) +
    "</p:sp>"
  );
}

/** 装饰块（无文本）→ p:sp：填充 + 均匀边框 + 阴影。 */
function boxSpXml(el, id, name) {
  return (
    "<p:sp><p:nvSpPr><p:cNvPr id=\"" + id + "\" name=\"" + xmlEscape(name) + "\"/>" +
    "<p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm>" + emuOff(el.rect) + emuExt(el.rect) + "</a:xfrm>" +
    geomXml(el) + elementFillXml(el) + uniformBorderLn(el, true) + shadowXml(el) + "</p:spPr></p:sp>"
  );
}

/**
 * 非均匀边框（如卡片彩色上边框）→ 逐边细色条（四边全等时走 a:ln，不进这里）。
 * 色条按圆角向内缩进，避免盖住圆角。alloc 由调用方提供（id 递增）。
 */
function borderStripsXml(el, alloc) {
  var border = el.border;
  if (!border || uniformBorderLn(el)) {
    return "";
  }
  var c = radiusCorners(el);
  var r = el.rect;
  var out = "";
  // 每边：色条位置按边与相邻角圆角缩进；宽/高由边宽决定
  var sides = [
    { key: "top", x: r.x + c.tl, y: r.y, len: r.w - c.tl - c.tr },
    { key: "right", x: r.x + r.w, y: r.y + c.tr, len: r.h - c.tr - c.br },
    { key: "bottom", x: r.x + c.bl, y: r.y + r.h, len: r.w - c.bl - c.br },
    { key: "left", x: r.x, y: r.y + c.tl, len: r.h - c.tl - c.bl }
  ];
  for (var i = 0; i < sides.length; i++) {
    var side = sides[i];
    var b = border[side.key];
    if (!b || !isHexColor(b.color) || side.len <= 0) {
      continue;
    }
    var bw = Math.max(b.w, 1);
    var x = side.key === "right" ? side.x - bw : side.x;
    var y = side.key === "bottom" ? side.y - bw : side.y;
    var w = side.key === "left" || side.key === "right" ? bw : side.len;
    var h = side.key === "top" || side.key === "bottom" ? bw : side.len;
    var info = alloc();
    out += (
      "<p:sp><p:nvSpPr><p:cNvPr id=\"" + info.id + "\" name=\"" + info.name + "\"/>" +
      "<p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm>" +
      '<a:off x="' + emu(x) + '" y="' + emu(y) + '"/><a:ext cx="' + emu(w) + '" cy="' + emu(h) + '"/>' +
      "</a:xfrm>" + '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom>' +
      solidFillCss(b.color, 1) + "<a:ln><a:noFill/></a:ln></p:spPr></p:sp>"
    );
  }
  return out;
}

/** 四边同宽同色 → a:ln；否则 null（由色条路径处理）。forceNoLine 时无均匀边框返回 noFill 线。 */
function uniformBorderLn(el, forceNoLine) {
  var b = el.border;
  if (!b) {
    return forceNoLine ? "<a:ln><a:noFill/></a:ln>" : null;
  }
  var sides = ["top", "right", "bottom", "left"];
  var first = null;
  for (var i = 0; i < sides.length; i++) {
    var s = b[sides[i]];
    if (!s || !isHexColor(s.color)) {
      return forceNoLine ? "<a:ln><a:noFill/></a:ln>" : null;
    }
    if (!first) {
      first = s;
    } else if (Math.abs(s.w - first.w) > 0.5 || s.color !== first.color) {
      return forceNoLine ? "<a:ln><a:noFill/></a:ln>" : null;
    }
  }
  return (
    "<a:ln w=\"" + emu(Math.max(first.w, 0.75)) + "\"><a:solidFill><a:srgbClr val=\"" + hexOf(first.color) + "\"/></a:solidFill></a:ln>"
  );
}

/** 元素填充：纯色 > 线性渐变 gradFill > radial 首色近似 > noFill。 */
function elementFillXml(el) {
  if (el.bgImage) {
    var grad = parseGradient(el.bgImage);
    if (grad) {
      return grad.type === "linear" ? gradFillXml(grad) : radialGradFillXml(grad.stops, grad.center);
    }
  }
  if (el.bg != null && isHexColor(el.bg)) {
    return solidFillCss(el.bg, 1);
  }
  return "<a:noFill/>";
}

/**
 * radial → OOXML 射线渐变（path circle，中心 = fillToRect 全 50%），带各 stop
 * alpha（如封面 deco 光点 rgba(125,211,192,.25)→transparent 65%）。此前退化为
 * 首色纯色近似，WPS 实测是一块生硬半透明圆片（20-A T5b）。
 */
function radialGradFillXml(stops, center) {
  if (!stops || !stops.length) {
    return "<a:noFill/>";
  }
  if (stops.length < 2) {
    return solidFillCss(stops[0].color, stops[0].alpha);
  }
  var gs = "";
  for (var i = 0; i < stops.length; i++) {
    gs += '<a:gs pos="' + Math.round(stops[i].pos * 100000) + '">'
      + gsColorXml(stops[i].color, stops[i].alpha) + "</a:gs>";
  }
  // fillToRect 表示渐变终点矩形占比，交点即中心；CSS `at x% y%` 偏移中心（默认 50/50）
  var cx = center && isFinite(center.x) ? Math.max(0, Math.min(1, center.x)) : 0.5;
  var cy = center && isFinite(center.y) ? Math.max(0, Math.min(1, center.y)) : 0.5;
  var l = Math.round(cx * 100000);
  var t = Math.round(cy * 100000);
  return '<a:gradFill rotWithShape="1"><a:gsLst>' + gs
    + '</a:gsLst><a:path path="circle"><a:fillToRect l="' + l + '" t="' + t
    + '" r="' + (100000 - l) + '" b="' + (100000 - t) + '"/></a:path></a:gradFill>';
}

function gradFillXml(grad) {
  var stops = "";
  for (var i = 0; i < grad.stops.length; i++) {
    var st = grad.stops[i];
    stops += '<a:gs pos="' + Math.round(st.pos * 100000) + '">' + gsColorXml(st.color, st.alpha) + "</a:gs>";
  }
  return '<a:gradFill rotWithShape="1"><a:gsLst>' + stops + '</a:gsLst><a:lin ang="' + grad.ang + '" scaled="0"/></a:gradFill>';
}

/**
 * 渐变 stop 颜色：gs 直接包颜色元素，**不套 a:solidFill**（PowerPoint 生成的
 * 渐变就是这个结构；套了 solidFill 属非规范嵌套——Keynote 宽容能渲染，WPS
 * 严格解析会忽略整个渐变，形状回退默认黑填充，20-A T5b WPS 实测）。
 */
function gsColorXml(color, alpha) {
  var hex = isHexColor(color) ? hexOf(color) : "000000";
  var a = alpha == null ? 1 : alpha;
  return '<a:srgbClr val="' + hex + '">'
    + (a < 1 ? '<a:alpha val="' + Math.round(a * 100000) + '"/>' : "")
    + "</a:srgbClr>";
}

/** radial 渐变的退化：取第一个 stop 的颜色与透明度做纯色近似。 */
function solidFillCss(color, alpha) {
  if (!isHexColor(color)) {
    return "<a:noFill/>";
  }
  var a = alpha != null && alpha < 1 ? '<a:alpha val="' + Math.round(alpha * 100000) + '"/>' : "";
  return '<a:solidFill><a:srgbClr val="' + hexOf(color) + '">' + a + "</a:srgbClr></a:solidFill>";
}

/** box-shadow 解析 → outerShdw 近似（颜色/偏移/模糊）；解析失败返回空。 */
function shadowXml(el) {
  var sh = el.shadow;
  if (!sh) {
    return "";
  }
  var parts = splitTopLevel(sh, " ");
  if (!parts.length) {
    return "";
  }
  var color = parseCssColor(parts[0].trim());
  var nums = [];
  for (var i = 1; i < parts.length; i++) {
    var m = /^(-?[\d.]+)px$/i.exec(parts[i].trim());
    if (m) {
      nums.push(parseFloat(m[1]));
    }
  }
  if (!color || nums.length < 2) {
    return "";
  }
  var offX = nums[0];
  var offY = nums[1];
  var blur = nums.length > 2 ? nums[2] : 0;
  var dist = Math.sqrt(offX * offX + offY * offY);
  var dir = Math.atan2(offY, offX) * 180 / Math.PI;
  if (dir < 0) {
    dir += 360;
  }
  return (
    '<a:effectLst><a:outerShdw blurRad="' + emu(Math.max(blur, 0)) + '" dist="' + emu(Math.max(dist, 0)) +
    '" dir="' + Math.round(dir * 60000) + '" rotWithShape="0">' +
    '<a:srgbClr val="' + color.hex + '"><a:alpha val="' + Math.round(color.alpha * 100000) + '"/></a:srgbClr>' +
    "</a:outerShdw></a:effectLst>"
  );
}

/** 几何：radius → roundRect（adj = 最大圆角/min(w,h)×100000，不等圆角取最大近似）；否则 rect。 */
function geomXml(el) {
  var corners = radiusCorners(el);
  var maxR = Math.max(corners.tl, corners.tr, corners.br, corners.bl);
  if (maxR <= 0) {
    return '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom>';
  }
  var adj = Math.round(Math.min(Math.max(maxR / Math.min(el.rect.w, el.rect.h), 0), 0.5) * 100000);
  return '<a:prstGeom prst="roundRect"><a:avLst><a:gd name="adj" fmla="val ' + adj + '"/></a:avLst></a:prstGeom>';
}

function radiusCorners(el) {
  var r = el.radius;
  if (r == null) {
    return { tl: 0, tr: 0, br: 0, bl: 0 };
  }
  if (typeof r === "number") {
    return { tl: r, tr: r, br: r, bl: r };
  }
  function n(v) {
    return typeof v === "number" && isFinite(v) && v > 0 ? v : 0;
  }
  return { tl: n(r.tl), tr: n(r.tr), br: n(r.br), bl: n(r.bl) };
}

/** 文本体：bodyPr 零内边距 + 顶对齐；段落带对齐与行距；run 带字号/粗斜下划线/颜色/字体。 */
function txBodyXml(el) {
  var font = el.font || {};
  var align = mapAlign(font.align);
  var pct = 0;
  if (font.lineHeight > 0 && font.size > 0) {
    var ratio = font.lineHeight / font.size;
    if (ratio >= 0.5 && ratio <= 3) {
      pct = Math.round(ratio * 100000);
    }
  }
  var pPr = '<a:pPr algn="' + align + '" marL="0" indent="0">' +
    (pct ? "<a:lnSpc><a:spcPct val=\"" + pct + "\"/></a:lnSpc>" : "") +
    "</a:pPr>";
  // 单行文本（rect 高 ≈ 一行行高）不折行：Office 字体度量与浏览器不同，
  // 固定宽度下大字（如 ending 大字）会被迫折成两行（WPS 实测 20-A T5b）
  var singleLine = el.rect && font.size > 0
    && el.rect.h <= Math.max(font.size * 1.8, (font.lineHeight || 0) * 1.25);
  var runs = "";
  var faces = fontFaces(font.family);
  var gradText = isGradientText(el);
  for (var i = 0; i < el.textRuns.length; i++) {
    var run = el.textRuns[i];
    var color = run.color || font.color || "#000000";
    if (gradText && color === "#000000") {
      // background-clip:text 渐变字：transparent 提取 fallback 成黑——改用渐变首色近似
      color = gradientFirstColor(el);
    }
    runs += "<a:r><a:rPr lang=\"zh-CN\" sz=\"" + fontSz(run.size || font.size) +
      "\" b=\"" + (isBold(run.weight) ? "1" : "0") +
      "\" i=\"" + (run.italic ? "1" : "0") + "\"" +
      (run.underline ? ' u="sng"' : "") + ">" +
      solidFillCss(color, 1) +
      (faces.latin ? '<a:latin typeface="' + xmlEscape(faces.latin) + '"/>' : "") +
      (faces.ea ? '<a:ea typeface="' + xmlEscape(faces.ea) + '"/>' : "") +
      "</a:rPr><a:t>" + xmlEscape(run.text || "") + "</a:t></a:r>";
  }
  return (
    '<p:txBody><a:bodyPr wrap="' + (singleLine ? "none" : "square")
    + '" lIns="0" tIns="0" rIns="0" bIns="0" anchor="t"/><a:lstStyle/>' +
    "<a:p>" + pPr + runs + "</a:p></p:txBody>"
  );
}

/** background-clip:text 渐变字：有文本 + 渐变背景图 + color transparent（提取 fallback #000000）。 */
function isGradientText(el) {
  return !!(el && el.textRuns && el.textRuns.length && el.bgImage
    && String(el.bgImage).indexOf("gradient") >= 0
    && el.font && el.font.color === "#000000");
}

/** 渐变字近似色：取首个色 stop。 */
function gradientFirstColor(el) {
  var grad = parseGradient(el.bgImage);
  return grad && grad.stops.length ? grad.stops[0].color : "#000000";
}

function mapAlign(align) {
  if (align === "center") {
    return "ctr";
  }
  if (align === "right" || align === "end") {
    return "r";
  }
  if (align === "justify") {
    return "just";
  }
  return "l";
}

/** 字号 px → OOXML 百分之一磅（px×0.75×100），保底 100（1pt）。 */
function fontSz(px) {
  var n = Number(px);
  if (!isFinite(n) || n <= 0) {
    n = 16;
  }
  return Math.max(Math.round(n * 75), 100);
}

function isBold(weight) {
  var w = String(weight);
  return w === "bold" || (parseInt(w, 10) >= 600 && isFinite(parseInt(w, 10)));
}

/** 字体族拆 latin/ea：首个族做 latin，CJK 常见族名做 ea。 */
function fontFaces(family) {
  var tokens = String(family || "").split(",");
  var latin = "";
  var ea = "";
  for (var i = 0; i < tokens.length; i++) {
    var t = tokens[i].trim().replace(/^["']|["']$/g, "");
    if (!t) {
      continue;
    }
    if (!latin) {
      latin = t;
    }
    if (!ea && /pingfang|yahei|heiti|songti|simsun|simhei|kaiti|fangsong|noto sans cjk|source han/i.test(t)) {
      ea = t;
    }
  }
  return { latin: latin, ea: ea };
}

/**
 * CSS 渐变解析（extractor 的 backgroundImage 序列化）：linear/radial，角度
 * deg 或 to-方向，stops 取色 + 位置（缺省按首尾 0/1）。CSS 角度顺时针自
 * 「向上」，OOXML ang 自「向右」：ang = (θ-90°)×60000。
 */
function parseGradient(bgImage) {
  var m = /^\s*(linear|radial)-gradient\s*\(([\s\S]*)\)\s*$/.exec(String(bgImage));
  if (!m) {
    return null;
  }
  var type = m[1];
  var parts = splitTopLevel(m[2], ",");
  var idx = 0;
  var cssDeg = 180;
  if (type === "linear") {
    var head = parts[0] != null ? parts[0].trim() : "";
    var degM = /^([\d.]+)deg$/i.exec(head);
    var toM = /^to\s+(.+)$/i.exec(head);
    if (degM) {
      cssDeg = parseFloat(degM[1]);
      idx = 1;
    } else if (toM) {
      cssDeg = toKeywordDeg(toM[1]);
      idx = 1;
    }
  } else if (/^(circle|ellipse|closest|farthest)/i.test(parts[0] != null ? parts[0].trim() : "")) {
    idx = 1;
  }
  var stops = [];
  for (var i = idx; i < parts.length; i++) {
    var stop = parseColorStop(parts[i]);
    if (stop) {
      stops.push(stop);
    }
  }
  if (stops.length < 2) {
    if (stops.length === 1) {
      stops.push({ pos: 1, color: stops[0].color, alpha: stops[0].alpha });
    } else {
      return null;
    }
  }
  for (var j = 0; j < stops.length; j++) {
    if (stops[j].pos == null) {
      stops[j].pos = stops.length === 1 ? 1 : j / (stops.length - 1);
    }
  }
  var out = { type: type, stops: stops };
  if (type === "linear") {
    out.ang = Math.round(((cssDeg - 90) % 360 + 360) % 360 * 60000);
  } else {
    out.center = { x: 0.5, y: 0.5 };
    var atM = /\bat\s+([\d.]+)%\s+([\d.]+)%/i.exec(parts[0] != null ? parts[0] : "");
    if (atM) {
      out.center = { x: parseFloat(atM[1]) / 100, y: parseFloat(atM[2]) / 100 };
    }
  }
  return out;
}

function toKeywordDeg(keyword) {
  var map = {
    "top": 0, "right": 90, "bottom": 180, "left": 270,
    "top left": 315, "left top": 315, "top right": 45, "right top": 45,
    "bottom left": 225, "left bottom": 225, "bottom right": 135, "right bottom": 135
  };
  return map[keyword] != null ? map[keyword] : 180;
}

function parseColorStop(seg) {
  var m = /^\s*(rgba?\([^)]*\)|#[0-9a-fA-F]{3,8})\s*(.*)$/.exec(seg);
  if (!m) {
    return null;
  }
  var color = parseCssColor(m[1]);
  if (!color) {
    return null;
  }
  var pos = null;
  var posM = /^([\d.]+)%$/.exec(m[2].trim());
  if (posM) {
    pos = parseFloat(posM[1]) / 100;
  }
  return { pos: pos, color: color.hex, alpha: color.alpha };
}

/** rgb()/rgba()/#hex → {hex:"rrggbb", alpha:0..1}；解析不了返回 null。 */
function parseCssColor(value) {
  var s = String(value).trim();
  var rgbM = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/i.exec(s);
  if (rgbM) {
    function hx(v) {
      var b = Math.max(0, Math.min(255, Math.round(parseFloat(v))));
      return (b < 16 ? "0" : "") + b.toString(16);
    }
    return {
      hex: hx(rgbM[1]) + hx(rgbM[2]) + hx(rgbM[3]),
      alpha: rgbM[4] != null ? Math.max(0, Math.min(1, parseFloat(rgbM[4]))) : 1
    };
  }
  var hexM = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.exec(s);
  if (hexM) {
    var h = hexM[1];
    if (h.length === 3) {
      h = h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
    }
    return { hex: h.toLowerCase(), alpha: 1 };
  }
  return null;
}

/** 顶层切分（跳过括号内的分隔符），sep="," 用于渐变参数、空白用于 box-shadow。 */
function splitTopLevel(s, sep) {
  var out = [];
  var depth = 0;
  var cur = "";
  for (var i = 0; i < s.length; i++) {
    var c = s.charAt(i);
    if (c === "(") {
      depth++;
    } else if (c === ")") {
      depth--;
    }
    var hit = depth === 0 && (sep === "," ? c === "," : /\s/.test(c));
    if (hit) {
      if (cur !== "") {
        out.push(cur);
        cur = "";
      }
    } else {
      cur += c;
    }
  }
  if (cur.trim() !== "") {
    out.push(cur);
  }
  return out;
}

function isHexColor(c) {
  return typeof c === "string" && /^#?[0-9a-fA-F]{6}$/.test(c);
}

function hexOf(c) {
  return String(c).replace("#", "").toLowerCase();
}

function emu(px) {
  return Math.round(px * EMU_PER_PX);
}

function emuOff(r) {
  return '<a:off x="' + emu(r.x) + '" y="' + emu(r.y) + '"/>';
}

function emuExt(r) {
  return '<a:ext cx="' + emu(r.w) + '" cy="' + emu(r.h) + '"/>';
}

function nativeSlideXml(bgXml, bodyXml) {
  return (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<p:sld xmlns:a="' + A_NS + '" xmlns:r="' + R_NS + '" xmlns:p="' + P_NS + '"><p:cSld>' +
    bgXml +
    "<p:spTree>" +
    '<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>' +
    '<p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>' +
    bodyXml +
    "</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"
  );
}

function readHtml(opts) {
  var inline = typeof opts.html === "string" ? opts.html.trim() : "";
  if (inline) {
    return inline;
  }
  if (opts.htmlPath != null && String(opts.htmlPath).trim() !== "") {
    var htmlPath = normalizeRel(opts.htmlPath, "htmlPath");
    if (!/\.x?html?$/i.test(htmlPath)) {
      throw new Error("htmlPath 必须是工作区内的 .html 文件");
    }
    return fs.readFileSync(htmlPath).toString();
  }
  throw new Error("需要 html（内联字符串）或 htmlPath（workspace .html 路径）");
}

var INLINE_IMG_MAX_BYTES = 8 * 1024 * 1024;
var INLINE_IMG_MIME = {
  png: "image/png",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  gif: "image/gif",
  svg: "image/svg+xml",
  webp: "image/webp"
};

/**
 * srcdoc 没有 base URL，相对路径图片加载不到；把 src=/href=/url() 指向的
 * workspace 已有图片内联为 data URI。https/data 引用原样保留。
 */
function inlineWorkspaceImages(html) {
  function dataUriFor(ref) {
    if (!ref || /^(?:[a-z][a-z0-9+.-]*:|\/\/|#)/i.test(ref)) {
      return null;
    }
    var pathOnly = ref.split("#")[0].split("?")[0];
    var m = /\.([a-z0-9]+)$/i.exec(pathOnly);
    if (!m) {
      return null;
    }
    var mime = INLINE_IMG_MIME[m[1].toLowerCase()];
    if (!mime) {
      return null;
    }
    var rel;
    try {
      rel = normalizeRel(pathOnly, "img");
    } catch (e) {
      return null;
    }
    var bytes;
    try {
      if (typeof fs.existsSync === "function" && !fs.existsSync(rel)) {
        return null;
      }
      bytes = fs.readFileSync(rel);
    } catch (e) {
      return null;
    }
    if (!bytes || !bytes.length || bytes.length > INLINE_IMG_MAX_BYTES) {
      return null;
    }
    return "data:" + mime + ";base64," + base64Encode(bytes);
  }

  function replaceAttr(all, name, eq, ref, quote) {
    var uri = dataUriFor(ref);
    return uri == null ? all : name + eq + quote + uri + quote;
  }

  var out = html.replace(/\b(src|href)(\s*=\s*)"([^"]*)"/gi, function (all, name, eq, ref) {
    return replaceAttr(all, name, eq, ref, '"');
  });
  out = out.replace(/\b(src|href)(\s*=\s*)'([^']*)'/gi, function (all, name, eq, ref) {
    return replaceAttr(all, name, eq, ref, "'");
  });
  out = out.replace(/url\(\s*(['"]?)([^'")]+)\1\s*\)/gi, function (all, quote, ref) {
    var uri = dataUriFor(ref);
    return uri == null ? all : "url(" + (quote || '"') + uri + (quote || '"') + ")";
  });
  return out;
}

function base64Encode(bytes) {
  if (typeof Buffer !== "undefined" && Buffer.from) {
    try {
      return Buffer.from(bytes).toString("base64");
    } catch (e) {
      // 落到手工编码。
    }
  }
  var table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  var out = "";
  for (var i = 0; i < bytes.length; i += 3) {
    var b0 = bytes[i] & 0xff;
    var b1 = i + 1 < bytes.length ? bytes[i + 1] & 0xff : 0;
    var b2 = i + 2 < bytes.length ? bytes[i + 2] & 0xff : 0;
    out += table.charAt(b0 >> 2);
    out += table.charAt(((b0 & 3) << 4) | (b1 >> 4));
    out += i + 1 < bytes.length ? table.charAt(((b1 & 15) << 2) | (b2 >> 6)) : "=";
    out += i + 2 < bytes.length ? table.charAt(b2 & 63) : "=";
  }
  return out;
}

function deckTitle(opts, html) {
  if (opts.title != null && String(opts.title).trim() !== "") {
    return String(opts.title).trim();
  }
  var m = /<title[^>]*>([^<]*)<\/title>/i.exec(html);
  return m && m[1].trim() ? m[1].trim() : "Presentation";
}

function writePptxTree(buildDir, slides, title, widthPx, heightPx) {
  var cx = widthPx * EMU_PER_PX;
  var cy = heightPx * EMU_PER_PX;

  for (var i = 0; i < slides.length; i++) {
    var name = "image" + (i + 1) + "." + slides[i].ext;
    writeBytes(buildDir + "/ppt/media/" + name, slides[i].bytes);
    writeUtf8(
      buildDir + "/ppt/slides/slide" + (i + 1) + ".xml",
      pictureSlideXml(cx, cy, i + 1)
    );
    writeUtf8(
      buildDir + "/ppt/slides/_rels/slide" + (i + 1) + ".xml.rels",
      relsXml([
        { id: "rId1", type: LAYOUT_REL, target: "../slideLayouts/slideLayout1.xml" },
        { id: "rId2", type: IMAGE_REL, target: "../media/" + name }
      ])
    );
  }
  writePackageCommon(buildDir, slides.length, title, cx, cy, imageExts(slides));
}

/** 原生模式组包：媒体件 + 每页 slide XML/rels（图片 rels 由生成器给出）+ 公共骨架。 */
function writeNativePptxTree(buildDir, deck, title) {
  var cx = deck.slideW * EMU_PER_PX;
  var cy = deck.slideH * EMU_PER_PX;

  for (var m = 0; m < deck.media.files.length; m++) {
    var f = deck.media.files[m];
    if (f.text != null) {
      writeUtf8(buildDir + "/ppt/media/" + f.name, f.data);
    } else {
      writeBytes(buildDir + "/ppt/media/" + f.name, f.data);
    }
  }
  for (var i = 0; i < deck.slides.length; i++) {
    writeUtf8(buildDir + "/ppt/slides/slide" + (i + 1) + ".xml", deck.slides[i].xml);
    writeUtf8(buildDir + "/ppt/slides/_rels/slide" + (i + 1) + ".xml.rels", relsXml(deck.slides[i].rels));
  }
  writePackageCommon(buildDir, deck.slides.length, title, cx, cy, deck.exts.length ? deck.exts : ["png"]);
}

/** pptx 包公共骨架：content types / 根 rels / presentation / master / layout / theme / docProps。 */
function writePackageCommon(buildDir, slideCount, title, cx, cy, exts) {
  writeUtf8(buildDir + "/[Content_Types].xml", contentTypesXml(slideCount, exts));
  writeUtf8(
    buildDir + "/_rels/.rels",
    relsXml([
      { id: "rId1", type: OFFICE_DOC_REL + "/officeDocument", target: "ppt/presentation.xml" },
      { id: "rId2", type: PACKAGE_REL + "/metadata/core-properties", target: "docProps/core.xml" },
      { id: "rId3", type: OFFICE_DOC_REL + "/extended-properties", target: "docProps/app.xml" }
    ])
  );
  writeUtf8(buildDir + "/ppt/presentation.xml", presentationXml(slideCount, cx, cy));
  writeUtf8(
    buildDir + "/ppt/_rels/presentation.xml.rels",
    relsXml(presentationRels(slideCount))
  );
  writeUtf8(buildDir + "/ppt/slideMasters/slideMaster1.xml", masterXml());
  writeUtf8(
    buildDir + "/ppt/slideMasters/_rels/slideMaster1.xml.rels",
    relsXml([
      { id: "rId1", type: LAYOUT_REL, target: "../slideLayouts/slideLayout1.xml" },
      { id: "rId2", type: THEME_REL, target: "../theme/theme1.xml" }
    ])
  );
  writeUtf8(buildDir + "/ppt/slideLayouts/slideLayout1.xml", layoutXml());
  writeUtf8(
    buildDir + "/ppt/slideLayouts/_rels/slideLayout1.xml.rels",
    relsXml([
      { id: "rId1", type: MASTER_REL, target: "../slideMasters/slideMaster1.xml" }
    ])
  );
  writeUtf8(buildDir + "/ppt/theme/theme1.xml", themeXml());
  writeUtf8(buildDir + "/ppt/presProps.xml", '<p:presentationPr xmlns:p="' + P_NS + '" xmlns:a="' + A_NS + '"/>');
  writeUtf8(buildDir + "/ppt/viewProps.xml", '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:viewProps xmlns:p="' + P_NS + '" xmlns:a="' + A_NS + '"/>');
  writeUtf8(
    buildDir + "/ppt/tableStyles.xml",
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><a:tblStyleLst xmlns:a="' + A_NS + '" def="{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}"/>'
  );
  writeUtf8(
    buildDir + "/docProps/core.xml",
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/">' +
      "<dc:title>" + xmlEscape(title) + "</dc:title><dc:creator>Weizhi</dc:creator></cp:coreProperties>"
  );
  writeUtf8(
    buildDir + "/docProps/app.xml",
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>Weizhi</Application><Slides>' +
      slideCount + "</Slides></Properties>"
  );
}

var OFFICE_DOC_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
var PACKAGE_REL = "http://schemas.openxmlformats.org/package/2006";
var LAYOUT_REL = OFFICE_DOC_REL + "/slideLayout";
var THEME_REL = OFFICE_DOC_REL + "/theme";
var MASTER_REL = OFFICE_DOC_REL + "/slideMaster";
var IMAGE_REL = OFFICE_DOC_REL + "/image";

function pictureSlideXml(cx, cy, index) {
  return (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<p:sld xmlns:a="' + A_NS + '" xmlns:r="' + R_NS + '" xmlns:p="' + P_NS + '"><p:cSld><p:spTree>' +
    '<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>' +
    '<p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>' +
    '<p:pic><p:nvPicPr><p:cNvPr id="2" name="slide ' + index + ' image"/><p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>' +
    '<p:blipFill><a:blip r:embed="rId2"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>' +
    '<p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="' + cx + '" cy="' + cy + '"/></a:xfrm>' +
    '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>' +
    '</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>'
  );
}

function presentationXml(slideCount, cx, cy) {
  var ids = "";
  for (var i = 0; i < slideCount; i++) {
    ids += '<p:sldId id="' + (256 + i) + '" r:id="rId' + (5 + i) + '"/>';
  }
  var ratio = cx / cy;
  var type = Math.abs(ratio - 16 / 9) < 0.02 ? "screen16x9" : "custom";
  return (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<p:presentation xmlns:a="' + A_NS + '" xmlns:r="' + R_NS + '" xmlns:p="' + P_NS + '" saveSubsetFonts="1">' +
    '<p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>' +
    '<p:sldIdLst>' + ids + '</p:sldIdLst>' +
    '<p:sldSz cx="' + cx + '" cy="' + cy + '" type="' + type + '"/><p:notesSz cx="6858000" cy="9144000"/></p:presentation>'
  );
}

function presentationRels(slideCount) {
  var rels = [
    { id: "rId1", type: MASTER_REL, target: "slideMasters/slideMaster1.xml" },
    { id: "rId2", type: OFFICE_DOC_REL + "/presProps", target: "presProps.xml" },
    { id: "rId3", type: OFFICE_DOC_REL + "/viewProps", target: "viewProps.xml" },
    { id: "rId4", type: OFFICE_DOC_REL + "/tableStyles", target: "tableStyles.xml" }
  ];
  for (var i = 0; i < slideCount; i++) {
    rels.push({ id: "rId" + (5 + i), type: OFFICE_DOC_REL + "/slide", target: "slides/slide" + (i + 1) + ".xml" });
  }
  return rels;
}

var IMAGE_MIME_BY_EXT = { png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg", gif: "image/gif", svg: "image/svg+xml" };

function contentTypesXml(slideCount, exts) {
  var xml =
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="' + CT_NS + '">' +
    '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' +
    '<Default Extension="xml" ContentType="application/xml"/>';
  for (var j = 0; j < exts.length; j++) {
    xml += '<Default Extension="' + exts[j] + '" ContentType="' + (IMAGE_MIME_BY_EXT[exts[j]] || "image/png") + '"/>';
  }
  xml +=
    '<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>' +
    '<Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>' +
    '<Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>' +
    '<Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>' +
    '<Override PartName="/ppt/presProps.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presProps+xml"/>' +
    '<Override PartName="/ppt/viewProps.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml"/>' +
    '<Override PartName="/ppt/tableStyles.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml"/>' +
    '<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>' +
    '<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>';
  for (var i = 1; i <= slideCount; i++) {
    xml +=
      '<Override PartName="/ppt/slides/slide' + i + '.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>';
  }
  return xml + "</Types>";
}

function masterXml() {
  return (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<p:sldMaster xmlns:a="' + A_NS + '" xmlns:r="' + R_NS + '" xmlns:p="' + P_NS + '"><p:cSld><p:bg><p:bgRef idx="1001"><a:schemeClr val="bg1"/></p:bgRef></p:bg><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld><p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/><p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst><p:txStyles><p:titleStyle><a:lvl1pPr algn="l"><a:defRPr sz="4000"/></a:lvl1pPr></p:titleStyle><p:bodyStyle><a:lvl1pPr marL="0" indent="0"><a:defRPr sz="1800"/></a:lvl1pPr></p:bodyStyle><p:otherStyle><a:lvl1pPr><a:defRPr sz="1400"/></a:lvl1pPr></p:otherStyle></p:txStyles></p:sldMaster>'
  );
}

function layoutXml() {
  return (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<p:sldLayout xmlns:a="' + A_NS + '" xmlns:r="' + R_NS + '" xmlns:p="' + P_NS + '" type="blank" preserve="1"><p:cSld name="Blank"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>'
  );
}

function themeXml() {
  function clr(tag, val) {
    return "<a:" + tag + '><a:srgbClr val="' + val + '"/></a:' + tag + ">";
  }
  return (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<a:theme xmlns:a="' + A_NS + '" name="Weizhi"><a:themeElements><a:clrScheme name="Weizhi">' +
    clr("dk1", "122033") + clr("lt1", "FFFFFF") + clr("dk2", "122033") + clr("lt2", "FFFFFF") +
    clr("accent1", "2B6CB0") + clr("accent2", "2C7A7B") + clr("accent3", "5B6B7C") +
    clr("accent4", "EDF2F7") + clr("accent5", "1A1423") + clr("accent6", "E8B86D") +
    clr("hlink", "2B6CB0") + clr("folHlink", "2C7A7B") +
    '</a:clrScheme><a:fontScheme name="Weizhi"><a:majorFont><a:latin typeface="Calibri"/><a:ea typeface="Microsoft YaHei"/><a:cs typeface=""/></a:majorFont><a:minorFont><a:latin typeface="Calibri"/><a:ea typeface="Microsoft YaHei"/><a:cs typeface=""/></a:minorFont></a:fontScheme><a:fmtScheme name="Weizhi"><a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w="6350"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="19050"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements><a:objectDefaults/><a:extraClrSchemeLst/></a:theme>'
  );
}

function relsXml(rels) {
  var xml = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="' + REL_NS + '">';
  for (var i = 0; i < rels.length; i++) {
    xml +=
      '<Relationship Id="' + rels[i].id + '" Type="' + rels[i].type + '" Target="' + xmlEscape(rels[i].target) + '"/>';
  }
  return xml + "</Relationships>";
}

function imageExts(slides) {
  var seen = {};
  var out = [];
  for (var i = 0; i < slides.length; i++) {
    if (!seen[slides[i].ext]) {
      seen[slides[i].ext] = true;
      out.push(slides[i].ext);
    }
  }
  if (!out.length) {
    out.push("png");
  }
  return out;
}

function xmlEscape(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

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

function packDir(sourceDir, outPath) {
  var files = platformFiles();
  if (files && files.zipCreate) {
    files.zipCreate(sourceDir, outPath);
    return;
  }
  zip.createSync(sourceDir, outPath);
}

function mkdirp(relDir) {
  var files = platformFiles();
  if (!files || !files.mkdir) {
    throw new Error("renderHtmlDeck: platform files.mkdir required (install caps)");
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

function writeUtf8(relPath, text) {
  var parent = relPath.replace(/\/[^/]+$/, "");
  if (parent && parent !== relPath) {
    mkdirp(parent);
  }
  fs.writeFileSync(relPath, text);
}

function writeBytes(relPath, bytes) {
  var parent = relPath.replace(/\/[^/]+$/, "");
  if (parent && parent !== relPath) {
    mkdirp(parent);
  }
  fs.writeFileSync(relPath, bytes);
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

function intIn(value, name, min, max) {
  var n = Number(value);
  if (!isFinite(n)) {
    throw new Error(name + " 必须是 " + min + " 到 " + max + " 之间的整数");
  }
  var v = Math.round(n);
  if (v < min || v > max) {
    throw new Error(name + " 必须在 " + min + " 到 " + max + " 之间");
  }
  return v;
}

function normalizeFormat(value) {
  var fmt = value == null || String(value).trim() === "" ? "png" : String(value).trim().toLowerCase();
  if (fmt === "jpeg") {
    fmt = "jpg";
  }
  if (fmt !== "png" && fmt !== "jpg") {
    throw new Error("format 只能是 png 或 jpg");
  }
  return fmt;
}

function jpegQuality(value) {
  if (value == null || value === "") {
    return 0.92;
  }
  var n = Number(value);
  if (!isFinite(n) || n <= 0 || n > 1) {
    throw new Error("quality 必须是 0 到 1 之间的小数");
  }
  return Math.round(n * 100) / 100;
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

function decodeBase64(b64) {
  var clean = String(b64).replace(/\s+/g, "");
  if (typeof Buffer !== "undefined" && Buffer.from) {
    try {
      var viaBuffer = Buffer.from(clean, "base64");
      if (isPng(viaBuffer) || isJpeg(viaBuffer)) {
        return viaBuffer;
      }
    } catch (e) {
      // 落到手工解码。
    }
  }
  return manualDecode(clean);
}

function manualDecode(clean) {
  var table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  var lookup = {};
  for (var i = 0; i < table.length; i++) {
    lookup[table.charAt(i)] = i;
  }
  var pad = 0;
  if (clean.length >= 2 && clean.charAt(clean.length - 1) === "=") {
    pad = clean.charAt(clean.length - 2) === "=" ? 2 : 1;
  }
  var len = Math.floor(clean.length / 4) * 3 - pad;
  if (len < 0) {
    throw new Error("图片 Base64 无效");
  }
  var out = new Uint8Array(len);
  var o = 0;
  for (var p = 0; p < clean.length; p += 4) {
    var c0 = lookup[clean.charAt(p)];
    var c1 = lookup[clean.charAt(p + 1)];
    var c2 = clean.charAt(p + 2) === "=" ? 0 : lookup[clean.charAt(p + 2)];
    var c3 = clean.charAt(p + 3) === "=" ? 0 : lookup[clean.charAt(p + 3)];
    if (c0 == null || c1 == null || c2 == null || c3 == null) {
      throw new Error("图片 Base64 无效");
    }
    var n = (c0 << 18) | (c1 << 12) | (c2 << 6) | c3;
    if (o < len) {
      out[o++] = (n >> 16) & 255;
    }
    if (o < len) {
      out[o++] = (n >> 8) & 255;
    }
    if (o < len) {
      out[o++] = n & 255;
    }
  }
  return out;
}

function isImageMagic(bytes, format) {
  return format === "jpg" ? isJpeg(bytes) : isPng(bytes);
}

function isPng(bytes) {
  return byteLength(bytes) >= 8
    && byteAt(bytes, 0) === 0x89
    && byteAt(bytes, 1) === 0x50
    && byteAt(bytes, 2) === 0x4e
    && byteAt(bytes, 3) === 0x47;
}

function isJpeg(bytes) {
  return byteLength(bytes) >= 3
    && byteAt(bytes, 0) === 0xff
    && byteAt(bytes, 1) === 0xd8
    && byteAt(bytes, 2) === 0xff;
}

function byteAt(bytes, index) {
  var v = bytes[index];
  if (typeof v !== "number") {
    return -1;
  }
  return v < 0 ? v + 256 : v;
}

function byteLength(bytes) {
  if (!bytes) {
    return 0;
  }
  if (typeof bytes.length === "number") {
    return bytes.length;
  }
  return 0;
}

function unlinkQuiet(relPath) {
  try {
    if (fs.unlinkSync) {
      fs.unlinkSync(relPath);
    }
  } catch (e) {
    // 临时文件删不掉不影响产物。
  }
}

export default { renderHtmlDeck: renderHtmlDeck, extractDeckGeometry: extractDeckGeometry, geometryToPptx: geometryToPptx };
