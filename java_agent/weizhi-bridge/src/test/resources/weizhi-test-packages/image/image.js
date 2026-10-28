/**
 * weizhi:image — 工作区静态图：探测元数据、按长边缩放。
 * 路径相对 workspace，不要加 workspace/ 前缀。
 */
async function tools(name, input) {
  if (typeof globalThis.$tools === "undefined") {
    throw new Error("weizhi:image 需要 run_js 与宿主 $tools 桥");
  }
  return await globalThis.$tools[name](input || {});
}

export async function probe(path) {
  if (!path || String(path).trim() === "") {
    throw new Error("probe: path 必填");
  }
  const meta = await tools("media_probe", { path: String(path).trim() });
  if (meta && meta.kind && meta.kind !== "image") {
    throw new Error("probe: 不是图片（kind=" + meta.kind + "），视频/音频请用 weizhi:video 或 weizhi:audio");
  }
  return meta;
}

export async function resize(options) {
  const opts = options || {};
  const path = opts.path;
  const output = opts.output;
  const maxEdge = opts.maxEdge != null ? opts.maxEdge : opts.max_edge;
  if (!path || !output) {
    throw new Error("resize: path 与 output 必填");
  }
  if (maxEdge == null) {
    throw new Error("resize: maxEdge 必填");
  }
  return await tools("media_resize", {
    path: String(path).trim(),
    output: String(output).trim(),
    maxEdge: Number(maxEdge),
  });
}
