/**
 * svg-raster.js — 把工作区 SVG 栅格化成 PNG 或 JPG。
 *
 * import { svgToImage } from "./svg-raster.js";
 *
 * export default await svgToImage({
 *   svgPath: "logo.svg",
 *   outputPath: "out/logo.png",
 *   width: 800,
 *   height: 400,
 *   format: "png"
 * });
 *
 * 画布在 webview_exec（Chromium / 系统 WebView）里完成。本模块只编排参数、解码 Base64，
 * 并写出二进制图片。路径相对当前会话 workspace，不要加 workspace/ 前缀。
 */
var fs = require("fs");

var MIN_PX = 1;
var MAX_PX = 8192;

export async function svgToImage(options) {
  var opts = options || {};
  var svgPath = normalizeRel(opts.svgPath, "svgPath");
  if (!/\.svg$/i.test(svgPath)) {
    throw new Error("svgPath 必须是工作区内的 .svg 文件");
  }
  var format = normalizeFormat(opts.format);
  var size = positiveInt(opts.size, "size", true);
  var width = positiveInt(opts.width, "width", true);
  var height = positiveInt(opts.height != null ? opts.height : opts.length, "height", true);
  if (size != null) {
    if (width == null) {
      width = size;
    }
    if (height == null) {
      height = size;
    }
  }
  if (width == null || height == null) {
    throw new Error("需要导出尺寸：width 与 height（高度也可写 length），或用 size 指定正方形边长");
  }
  var quality = jpegQuality(opts.quality);
  var outputPath = opts.outputPath == null || String(opts.outputPath).trim() === ""
    ? replaceExt(svgPath, format === "jpg" ? ".jpg" : ".png")
    : normalizeRel(opts.outputPath, "outputPath");
  assertFormatExt(outputPath, format);

  if (typeof globalThis.$tools === "undefined" || typeof globalThis.$tools.webview_exec !== "function") {
    throw new Error("svgToImage 需要 $tools.webview_exec（桌面需 Chromium，Android 需系统 WebView）");
  }

  var receiptText = await globalThis.$tools.webview_exec({
    code: rasterCode(width, height, format, quality),
    input_path: svgPath,
    timeout_ms: "120000"
  });
  var receipt = asObject(receiptText);
  if (!receipt || receipt.ok === false) {
    var err = receipt && receipt.error ? receipt.error : "webview_exec 失败";
    throw new Error(String(err));
  }
  var b64Path = receipt.outputPath ? String(receipt.outputPath) : "";
  if (!b64Path) {
    throw new Error("webview_exec 没有 outputPath，无法读取图片 Base64");
  }
  var b64 = fs.readFileSync(b64Path).toString().trim();
  var bytes = decodeBase64(b64);
  if (!isImageMagic(bytes, format)) {
    throw new Error("栅格化结果不是有效的 " + format + "（请检查 SVG 是否能被 WebView 解码）");
  }
  writeBytes(outputPath, bytes);
  try {
    if (fs.unlinkSync && b64Path !== outputPath) {
      fs.unlinkSync(b64Path);
    }
  } catch (e) {
    // 临时 Base64 删不掉不影响已经写好的图片。
  }
  return {
    ok: true,
    outputPath: outputPath,
    width: width,
    height: height,
    format: format,
    bytes: byteLength(bytes)
  };
}

function rasterCode(width, height, format, quality) {
  var mime = format === "jpg" ? "image/jpeg" : "image/png";
  var lines = [
    "return (async () => {",
    "  if (!input || !input.length) throw new Error('svg input empty');",
    "  var text = new TextDecoder('utf-8').decode(input).replace(/^\\uFEFF/, '').trim();",
    "  if (!/<svg\\b/i.test(text)) throw new Error('not an svg');",
    "  if (!/xmlns\\s*=/i.test(text)) {",
    "    text = text.replace(/<svg\\b/i, '<svg xmlns=\"http://www.w3.org/2000/svg\"');",
    "  }",
    "  text = text.replace(/<svg\\b([^>]*)>/i, function (_, attrs) {",
    "    var next = attrs",
    "      .replace(/\\swidth\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)/i, '')",
    "      .replace(/\\sheight\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)/i, '');",
    "    return '<svg' + next + ' width=\"" + width + "\" height=\"" + height + "\">';",
    "  });",
    "  var url = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(text);",
    "  var img = new Image();",
    "  await new Promise(function (resolve, reject) {",
    "    img.onload = function () { resolve(); };",
    "    img.onerror = function () { reject(new Error('svg decode failed')); };",
    "    img.src = url;",
    "  });",
    "  var canvas = document.createElement('canvas');",
    "  canvas.width = " + width + ";",
    "  canvas.height = " + height + ";",
    "  var ctx = canvas.getContext('2d');",
    format === "jpg"
      ? "  ctx.fillStyle = '#ffffff'; ctx.fillRect(0, 0, canvas.width, canvas.height);"
      : "",
    "  ctx.drawImage(img, 0, 0, canvas.width, canvas.height);",
    format === "jpg"
      ? "  return canvas.toDataURL('" + mime + "', " + quality + ").split(',')[1];"
      : "  return canvas.toDataURL('" + mime + "').split(',')[1];",
    "})();"
  ];
  var out = [];
  for (var i = 0; i < lines.length; i++) {
    if (lines[i]) {
      out.push(lines[i]);
    }
  }
  return out.join("\n");
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

function positiveInt(value, name, optional) {
  if (value == null || value === "") {
    if (optional) {
      return null;
    }
    throw new Error(name + " 必须是正整数像素");
  }
  var n = Number(value);
  if (!isFinite(n)) {
    throw new Error(name + " 必须是正整数像素");
  }
  var px = Math.round(n);
  if (px < MIN_PX || px > MAX_PX) {
    throw new Error(name + " 必须在 " + MIN_PX + " 到 " + MAX_PX + " 像素之间");
  }
  return px;
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

function replaceExt(path, ext) {
  var slash = path.lastIndexOf("/");
  var name = slash >= 0 ? path.slice(slash + 1) : path;
  var dir = slash >= 0 ? path.slice(0, slash + 1) : "";
  var dot = name.lastIndexOf(".");
  var stem = dot > 0 ? name.slice(0, dot) : name;
  return dir + stem + ext;
}

function assertFormatExt(path, format) {
  var lower = path.toLowerCase();
  if (format === "png" && !lower.endsWith(".png")) {
    throw new Error("PNG 的 outputPath 必须以 .png 结尾");
  }
  if (format === "jpg" && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) {
    throw new Error("JPG 的 outputPath 必须以 .jpg 或 .jpeg 结尾");
  }
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

function writeBytes(relPath, bytes) {
  var payload = bytes;
  if (typeof Buffer !== "undefined" && Buffer.from && !(typeof Buffer.isBuffer === "function" && Buffer.isBuffer(bytes))) {
    payload = Buffer.from(bytes);
  }
  // Weizhi ≥ weizhi#27：writeFileSync 自动创建 workspace 内父目录。
  fs.writeFileSync(relPath, payload);
}
