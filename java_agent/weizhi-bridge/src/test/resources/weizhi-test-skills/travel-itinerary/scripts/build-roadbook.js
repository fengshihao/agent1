/**
 * 路书流水线入口（模板）。执行前将本文件与 roadbook.js 一并放到 workspace/jobs/。
 *
 * import { appRouteUri, queryDrivingLegs, renderSchematicMap } from "./roadbook.js";
 */
import { appRouteUri, queryDrivingLegs, renderSchematicMap } from "./roadbook.js";

var fs = typeof require === "function" ? require("fs") : null;

function readJson(rel) {
  if (!fs) {
    throw new Error("需要 fs 读取 trip.json");
  }
  return JSON.parse(fs.readFileSync(rel).toString());
}

/**
 * @param {{ tripSlug: string }} options
 */
export default async function buildRoadbook(options) {
  var tripSlug = String((options && options.tripSlug) || "").trim();
  if (!tripSlug) {
    throw new Error("buildRoadbook: tripSlug 必填");
  }
  var tripPath = tripSlug + "/trip.json";
  var trip = readJson(tripPath);
  var days = trip.days || [];
  var navUris = [];

  for (var d = 0; d < days.length; d++) {
    var day = days[d] || {};
    var wps = day.waypoints || [];
    if (wps.length >= 2) {
      navUris.push({
        day: d + 1,
        uri: appRouteUri({ waypoints: wps, mode: "drive" })
      });
    }
  }

  // 按天 legs：相邻点成段；有 MCP 时写回 trip
  var legs = [];
  for (var i = 0; i < days.length; i++) {
    var w = (days[i] && days[i].waypoints) || [];
    for (var j = 0; j < w.length - 1; j++) {
      var a = w[j];
      var b = w[j + 1];
      legs.push({
        name: "第" + (i + 1) + "天 " + (a.name || "") + "→" + (b.name || ""),
        origin: a.lng + "," + a.lat,
        destination: b.lng + "," + b.lat
      });
    }
  }

  var distances = [];
  try {
    distances = await queryDrivingLegs(legs);
  } catch (e) {
    distances = { error: String(e && e.message ? e.message : e) };
  }

  for (var k = 0; k < days.length; k++) {
    var wps2 = (days[k] && days[k].waypoints) || [];
    if (wps2.length >= 2) {
      await renderSchematicMap({
        waypoints: wps2,
        outputPath: tripSlug + "/route-day" + (k + 1) + ".png",
        title: (days[k].title || "第" + (k + 1) + "天") + " 路线"
      });
    }
  }

  return { tripSlug: tripSlug, navUris: navUris, distances: distances };
}
