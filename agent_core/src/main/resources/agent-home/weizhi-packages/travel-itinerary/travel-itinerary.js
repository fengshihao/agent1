/**
 * travel-itinerary.js — 路书：驾车里程、高德个人地图 URI、示意图 PNG。
 *
 * import { buildLineList, queryDrivingLegs, personalMapUri, renderSchematicMap } from "weizhi:travel-itinerary";
 *
 * 依赖：高德 MCP（$mcp.gaode.maps_direction_driving / maps_schema_personal_map）、
 *       $tools.webview_exec（示意图 PNG）。
 */
var fs = typeof require === "function" ? require("fs") : null;

export function formatCoord(lng, lat) {
  var lo = Number(lng);
  var la = Number(lat);
  if (!isFinite(lo) || !isFinite(la)) {
    throw new Error("formatCoord: 需要有效经纬度");
  }
  return lo + "," + la;
}

/**
 * @param {Array<{name:string,lng:number,lat:number}>} waypoints 按行程顺序
 * @returns {Array<{start:string,end:string}>} 高德 personal map lineList
 */
export function buildLineList(waypoints) {
  if (!waypoints || waypoints.length < 2) {
    throw new Error("buildLineList: 至少需要 2 个途经点");
  }
  var out = [];
  for (var i = 0; i < waypoints.length - 1; i++) {
    var a = waypoints[i];
    var b = waypoints[i + 1];
    var an = String(a.name || "点" + (i + 1)).trim();
    var bn = String(b.name || "点" + (i + 2)).trim();
    out.push({
      start: an + "," + formatCoord(a.lng, a.lat),
      end: bn + "," + formatCoord(b.lng, b.lat)
    });
  }
  return out;
}

function gaodeDriving() {
  var g = globalThis.$mcp && globalThis.$mcp.gaode;
  if (!g || typeof g.maps_direction_driving !== "function") {
    throw new Error("需要高德 MCP：maps_direction_driving");
  }
  return g.maps_direction_driving.bind(g);
}

function pickNumber(obj, keys) {
  if (!obj) {
    return null;
  }
  for (var i = 0; i < keys.length; i++) {
    var k = keys[i];
    if (obj[k] != null && isFinite(Number(obj[k]))) {
      return Number(obj[k]);
    }
  }
  return null;
}

/** 从驾车规划返回里尽量取出米、秒（字段因 MCP 封装可能不同）。 */
export function parseDrivingMetrics(raw) {
  if (!raw || typeof raw !== "object") {
    return { distanceM: null, durationSec: null };
  }
  var route = raw.route || raw.data || raw;
  var paths = route.paths || route.path || route.routes;
  var first = Array.isArray(paths) && paths.length ? paths[0] : route;
  var distanceM = pickNumber(first, ["distance", "distanceM", "distance_m"]);
  var durationSec = pickNumber(first, ["duration", "durationSec", "duration_s", "time"]);
  if (distanceM == null) {
    distanceM = pickNumber(route, ["distance", "distanceM"]);
  }
  if (durationSec == null) {
    durationSec = pickNumber(route, ["duration", "durationSec", "time"]);
  }
  return { distanceM: distanceM, durationSec: durationSec };
}

/**
 * @param {Array<{name?:string,origin:string,destination:string,strategy?:number}>} legs
 */
export async function queryDrivingLegs(legs) {
  if (!legs || !legs.length) {
    throw new Error("queryDrivingLegs: legs 不能为空");
  }
  var driving = gaodeDriving();
  var results = [];
  for (var i = 0; i < legs.length; i++) {
    var leg = legs[i] || {};
    var origin = String(leg.origin || "").trim();
    var destination = String(leg.destination || "").trim();
    if (!origin || !destination) {
      throw new Error("queryDrivingLegs: 每段需要 origin 与 destination（经度,纬度）");
    }
    var raw = await driving({
      origin: origin,
      destination: destination,
      strategy: leg.strategy != null ? leg.strategy : 0
    });
    var metrics = parseDrivingMetrics(raw);
    results.push({
      name: leg.name || "leg-" + (i + 1),
      origin: origin,
      destination: destination,
      distanceM: metrics.distanceM,
      durationSec: metrics.durationSec,
      distanceKm: metrics.distanceM != null ? Math.round(metrics.distanceM / 100) / 10 : null,
      durationMin: metrics.durationSec != null ? Math.round(metrics.durationSec / 60) : null,
      raw: raw
    });
  }
  return results;
}

export async function personalMapUri(options) {
  var opts = options || {};
  var g = globalThis.$mcp && globalThis.$mcp.gaode;
  if (!g || typeof g.maps_schema_personal_map !== "function") {
    throw new Error("需要高德 MCP：maps_schema_personal_map");
  }
  var lineList = opts.lineList;
  if (!lineList || !lineList.length) {
    throw new Error("personalMapUri: lineList 不能为空");
  }
  var payload = {
    orgName: opts.orgName || opts.title || "行程地图",
    lineList: lineList
  };
  if (opts.title) {
    payload.title = opts.title;
  }
  return await g.maps_schema_personal_map(payload);
}

/**
 * 简易北向示意图（不拉地图瓦片）。waypoints: { name, lng, lat, day? }
 */
export async function renderSchematicMap(options) {
  var opts = options || {};
  var waypoints = opts.waypoints;
  if (!waypoints || waypoints.length < 2) {
    throw new Error("renderSchematicMap: 至少 2 个 waypoints");
  }
  if (typeof globalThis.$tools === "undefined" || typeof globalThis.$tools.webview_exec !== "function") {
    throw new Error("renderSchematicMap 需要 $tools.webview_exec");
  }
  var outputPath = normalizeRel(opts.outputPath || "route_map.png", "outputPath");
  var width = positiveInt(opts.width, 1600) || 1600;
  var height = positiveInt(opts.height, 1200) || 1200;
  var title = String(opts.title || "路线图").slice(0, 80);
  var wpJson = JSON.stringify(waypoints);

  var receiptText = await globalThis.$tools.webview_exec({
    code: schematicMapCode(width, height, title, wpJson),
    timeout_ms: "120000"
  });
  var receipt = asObject(receiptText);
  if (!receipt || receipt.ok === false) {
    var err = receipt && receipt.error ? receipt.error : "webview_exec 失败";
    throw new Error(String(err));
  }
  var b64Path = receipt.outputPath ? String(receipt.outputPath) : "";
  if (!b64Path) {
    throw new Error("renderSchematicMap: webview 未返回 outputPath");
  }
  if (!fs) {
    throw new Error("renderSchematicMap: 需要 fs 写出 PNG");
  }
  var b64 = fs.readFileSync(b64Path).toString().trim();
  var bytes = decodeBase64(b64);
  writeBytes(outputPath, bytes);
  return {
    ok: true,
    outputPath: outputPath,
    width: width,
    height: height,
    bytes: bytes.length,
    waypointCount: waypoints.length
  };
}

function schematicMapCode(w, h, title, wpJson) {
  return (
    "return (async () => {\n" +
    "  const wps = " + wpJson + ";\n" +
    "  const W = " + w + ", H = " + h + ";\n" +
    "  const c = document.createElement('canvas');\n" +
    "  c.width = W; c.height = H;\n" +
    "  const g = c.getContext('2d');\n" +
    "  g.fillStyle = '#f7f4ef';\n" +
    "  g.fillRect(0, 0, W, H);\n" +
    "  if (!wps.length) throw new Error('no waypoints');\n" +
    "  let minLon = wps[0].lng, maxLon = wps[0].lng, minLat = wps[0].lat, maxLat = wps[0].lat;\n" +
    "  for (const p of wps) {\n" +
    "    minLon = Math.min(minLon, p.lng); maxLon = Math.max(maxLon, p.lng);\n" +
    "    minLat = Math.min(minLat, p.lat); maxLat = Math.max(maxLat, p.lat);\n" +
    "  }\n" +
    "  const pad = 80;\n" +
    "  const lonSpan = Math.max(0.01, maxLon - minLon);\n" +
    "  const latSpan = Math.max(0.01, maxLat - minLat);\n" +
    "  const sx = (W - pad * 2) / lonSpan;\n" +
    "  const sy = (H - pad * 2 - 40) / latSpan;\n" +
    "  const s = Math.min(sx, sy);\n" +
    "  const cx = (minLon + maxLon) / 2;\n" +
    "  const cy = (minLat + maxLat) / 2;\n" +
    "  function xy(lon, lat) {\n" +
    "    return { x: W / 2 + (lon - cx) * s, y: H / 2 - (lat - cy) * s };\n" +
    "  }\n" +
    "  g.strokeStyle = '#c45c3e';\n" +
    "  g.lineWidth = 4;\n" +
    "  g.lineJoin = 'round';\n" +
    "  g.beginPath();\n" +
    "  const p0 = xy(wps[0].lng, wps[0].lat);\n" +
    "  g.moveTo(p0.x, p0.y);\n" +
    "  for (let i = 1; i < wps.length; i++) {\n" +
    "    const p = xy(wps[i].lng, wps[i].lat);\n" +
    "    g.lineTo(p.x, p.y);\n" +
    "  }\n" +
    "  g.stroke();\n" +
    "  g.font = 'bold 22px system-ui,sans-serif';\n" +
    "  g.fillStyle = '#2a2118';\n" +
    "  g.fillText(" + JSON.stringify(title) + ", pad, 36);\n" +
    "  for (let i = 0; i < wps.length; i++) {\n" +
    "    const p = wps[i];\n" +
    "    const pt = xy(p.lng, p.lat);\n" +
    "    g.fillStyle = '#1a5f7a';\n" +
    "    g.beginPath();\n" +
    "    g.arc(pt.x, pt.y, 10, 0, Math.PI * 2);\n" +
    "    g.fill();\n" +
    "    g.fillStyle = '#2a2118';\n" +
    "    g.font = '16px system-ui,sans-serif';\n" +
    "    const label = (p.day ? p.day + ' ' : '') + (p.name || ('点' + (i+1)));\n" +
    "    g.fillText(label, pt.x + 14, pt.y + 5);\n" +
    "  }\n" +
    "  return c.toDataURL('image/png').split(',')[1];\n" +
    "})();\n"
  );
}

function normalizeRel(path, label) {
  var p = String(path || "").trim().replace(/\\\\/g, "/");
  if (!p || p.startsWith("/") || p.includes("..")) {
    throw new Error(label + ": 需要 workspace 内相对路径");
  }
  return p;
}

function positiveInt(v, fallback) {
  var n = Number(v);
  if (!isFinite(n) || n < 1) {
    return fallback;
  }
  return Math.floor(n);
}

function asObject(v) {
  if (v == null) {
    return null;
  }
  if (typeof v === "object") {
    return v;
  }
  try {
    return JSON.parse(String(v));
  } catch (e) {
    return null;
  }
}

function decodeBase64(b64) {
  if (typeof globalThis.atob === "function") {
    var bin = globalThis.atob(b64);
    var out = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) {
      out[i] = bin.charCodeAt(i);
    }
    return out;
  }
  if (fs && typeof fs.base64Decode === "function") {
    return fs.base64Decode(b64);
  }
  throw new Error("无法解码 Base64 PNG");
}

function writeBytes(path, bytes) {
  if (fs.writeFileSync) {
    fs.writeFileSync(path, bytes);
    return;
  }
  throw new Error("需要 fs.writeFileSync");
}
