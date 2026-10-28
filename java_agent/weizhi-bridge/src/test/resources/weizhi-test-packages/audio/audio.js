/**
 * weizhi:audio — 工作区纯音频文件元数据（剪辑/转码后续扩展）。
 */
async function tools(name, input) {
  if (typeof globalThis.$tools === "undefined") {
    throw new Error("weizhi:audio 需要 run_js 与宿主 $tools 桥");
  }
  return await globalThis.$tools[name](input || {});
}

export async function probe(path) {
  if (!path || String(path).trim() === "") {
    throw new Error("probe: path 必填");
  }
  const meta = await tools("media_probe", { path: String(path).trim() });
  if (meta && meta.kind && meta.kind !== "audio") {
    throw new Error("probe: 不是音频（kind=" + meta.kind + "）");
  }
  return meta;
}
