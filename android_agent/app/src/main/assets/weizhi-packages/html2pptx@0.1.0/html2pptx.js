/**
 * html2pptx.js — 把 HTML 幻灯片渲染成 pptx（v0.1 截图模式）。
 *
 * 约定：HTML 里每个 class="slide" 的元素是一页，CSS 尺寸默认 1280×720（16:9）。
 * 每页经 webview_exec（Chromium / 系统 WebView）里的 html-to-image（CDN）截图成
 * PNG/JPG，再以全幅图片页打包为 pptx——图片即版面，版式自由度由 HTML/CSS 决定。
 *
 * import { renderHtmlDeck } from "weizhi:html2pptx";
 *
 * export default await renderHtmlDeck({
 *   htmlPath: "deck.html",        // 或直接传 html: "<html>…</html>"
 *   output: "out/deck.pptx",
 *   width: 1280, height: 720,     // 页 CSS 尺寸（可选）
 *   scale: 2,                     // 截图像素倍数（可选，默认 2）
 *   format: "png"                 // png | jpg（可选，默认 png）
 * });
 *
 * 需要网络（CDN）。路径相对当前会话 workspace，不要加 workspace/ 前缀。
 */
var fs = require("fs");
var zip = require("zip");

var MAX_SLIDES = 40;
var MIN_PX = 320;
var MAX_PX = 4096;
var MIN_SCALE = 1;
var MAX_SCALE = 3;
var SHOT_TIMEOUT_MS = "300000";

var LIB_URL = "https://cdn.jsdelivr.net/npm/html-to-image@1.11.11/dist/html-to-image.min.js";
var LIB_TOKEN = "html-to-image";

var A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";
var P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main";
var R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
var REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
var CT_NS = "http://schemas.openxmlformats.org/package/2006/content-types";
var EMU_PER_PX = 9525;

export async function renderHtmlDeck(options) {
  var opts = options || {};
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

/** 在 webview_exec 里跑的截图代码：iframe 渲染 deck HTML，逐页导出 base64。 */
function captureCode(width, height, scale, format, quality) {
  return [
    "return (async () => {",
    "  var html = new TextDecoder('utf-8').decode(input).replace(/^\\uFEFF/, '').trim();",
    "  if (!/<html[\\s>]/i.test(html)) {",
    "    throw new Error('input is not an html document');",
    "  }",
    "  var inject = '<script src=\"" + LIB_URL + "\"></script>';",
    "  if (html.indexOf('" + LIB_TOKEN + "') < 0) {",
    "    html = /<\\/body>/i.test(html)",
    "      ? html.replace(/<\\/body>/i, inject + '</body>')",
    "      : html + inject;",
    "  }",
    "  var iframe = document.createElement('iframe');",
    "  iframe.style.cssText = 'position:fixed;left:-99999px;top:0;border:0;width:" + width + "px;height:" + height + "px;';",
    "  iframe.srcdoc = html;",
    "  document.body.appendChild(iframe);",
    "  await new Promise(function (resolve, reject) {",
    "    iframe.onload = function () { resolve(); };",
    "    setTimeout(function () { reject(new Error('iframe load timeout')); }, 30000);",
    "  });",
    "  var win = iframe.contentWindow;",
    "  var doc = win.document;",
    "  for (var wait = 0; wait < 200 && !(win.htmlToImage && win.htmlToImage.toPng); wait++) {",
    "    await new Promise(function (r) { setTimeout(r, 100); });",
    "  }",
    "  if (!(win.htmlToImage && win.htmlToImage.toPng)) {",
    "    throw new Error('html-to-image 未加载（CDN 不可达？）');",
    "  }",
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
    "  }",
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
  ].join("\n");
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

  writeUtf8(buildDir + "/[Content_Types].xml", contentTypesXml(slides.length, imageExts(slides)));
  writeUtf8(
    buildDir + "/_rels/.rels",
    relsXml([
      { id: "rId1", type: OFFICE_DOC_REL + "/officeDocument", target: "ppt/presentation.xml" },
      { id: "rId2", type: PACKAGE_REL + "/metadata/core-properties", target: "docProps/core.xml" },
      { id: "rId3", type: OFFICE_DOC_REL + "/extended-properties", target: "docProps/app.xml" }
    ])
  );
  writeUtf8(buildDir + "/ppt/presentation.xml", presentationXml(slides.length, cx, cy));
  writeUtf8(
    buildDir + "/ppt/_rels/presentation.xml.rels",
    relsXml(presentationRels(slides.length))
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
      slides.length + "</Slides></Properties>"
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

function contentTypesXml(slideCount, exts) {
  var xml =
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="' + CT_NS + '">' +
    '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' +
    '<Default Extension="xml" ContentType="application/xml"/>';
  for (var j = 0; j < exts.length; j++) {
    var mime = exts[j] === "jpg" ? "image/jpeg" : "image/png";
    xml += '<Default Extension="' + exts[j] + '" ContentType="' + mime + '"/>';
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

export default { renderHtmlDeck: renderHtmlDeck };
