#!/usr/bin/env node
/**
 * 宿主 WebView：vendor + shell（script URL 发布时 baked-in）+ registry/stable.json
 */
import { copyFile, mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));

function argValue(argv, name) {
  const i = argv.indexOf(name);
  return i >= 0 && i + 1 < argv.length ? argv[i + 1] : null;
}

async function copyVendor(outDir) {
  const jszipSrc = path.join(here, "node_modules", "jszip", "dist", "jszip.min.js");
  const docxSrc = path.join(here, "node_modules", "docx-preview", "dist", "docx-preview.min.js");
  const jszipDest = path.join(outDir, "host-web", "vendor", "jszip", "3.10.1", "jszip.min.js");
  const docxDest = path.join(
    outDir,
    "host-web",
    "vendor",
    "docx-preview",
    "0.4.1",
    "docx-preview.min.js",
  );
  await mkdir(path.dirname(jszipDest), { recursive: true });
  await mkdir(path.dirname(docxDest), { recursive: true });
  await copyFile(jszipSrc, jszipDest);
  await copyFile(docxSrc, docxDest);
}

const SANDBOX_PLACEHOLDER = "/*__BRIDGE_JS__*/";

async function bakeWeizhiSandbox(outDir, manifest) {
  const shellPublished = manifest?.weizhi_sandbox?.shell;
  if (!shellPublished || typeof shellPublished !== "string") {
    throw new Error("manifest.source.json missing weizhi_sandbox.shell");
  }
  const sourceDir = path.join(here, "host-web", "weizhi-sandbox", "source");
  const bridge = await readFile(path.join(sourceDir, "bridge.js"), "utf8");
  let template = await readFile(path.join(sourceDir, "bootstrap.html"), "utf8");
  if (!template.includes(SANDBOX_PLACEHOLDER)) {
    throw new Error("weizhi-sandbox bootstrap.html missing bridge placeholder");
  }
  const document = template.replace(SANDBOX_PLACEHOLDER, bridge);
  const dest = path.join(outDir, "host-web", "weizhi-sandbox", "1", "document.html");
  await mkdir(path.dirname(dest), { recursive: true });
  await writeFile(dest, document);
}

async function bakeShell(outDir, manifest) {
  const jszip = manifest.docx_preview.jszip;
  const docx = manifest.docx_preview.docx_preview;
  const shellSrc = path.join(here, "host-web", "shell", "docx-preview", "1", "shell.html");
  let html = await readFile(shellSrc, "utf8");
  html = html.replaceAll("__JSZIP_URL__", jszip).replaceAll("__DOCX_PREVIEW_URL__", docx);
  if (html.includes("__JSZIP_URL__") || html.includes("__DOCX_PREVIEW_URL__")) {
    throw new Error("shell.html still contains bake placeholders");
  }
  const shellDest = path.join(outDir, "host-web", "shell", "docx-preview", "1", "shell.html");
  await mkdir(path.dirname(shellDest), { recursive: true });
  await writeFile(shellDest, html);
}

function publishedManifest(source) {
  const shell = source?.docx_preview?.shell;
  if (!shell || typeof shell !== "string") {
    throw new Error("manifest.source.json missing docx_preview.shell");
  }
  const mdShell = source?.md_preview?.shell;
  if (!mdShell || typeof mdShell !== "string") {
    throw new Error("manifest.source.json missing md_preview.shell");
  }
  const weizhiShell = source?.weizhi_sandbox?.shell;
  if (!weizhiShell || typeof weizhiShell !== "string") {
    throw new Error("manifest.source.json missing weizhi_sandbox.shell");
  }
  return {
    schema_version: 1,
    docx_preview: { shell },
    md_preview: { shell: mdShell },
    weizhi_sandbox: { shell: weizhiShell },
  };
}

async function main() {
  const outDir = argValue(process.argv, "--out") || path.join(here, "dist");
  const sourcePath =
    argValue(process.argv, "--manifest") || path.join(here, "host-web", "manifest.source.json");
  const manifest = JSON.parse(await readFile(sourcePath, "utf8"));
  if (manifest.schema_version !== 1) {
    throw new Error("schema_version must be 1");
  }
  await copyVendor(outDir);
  await bakeShell(outDir, manifest);
  await bakeWeizhiSandbox(outDir, manifest);
  const registryPath = path.join(outDir, "host-web", "registry", "stable.json");
  await mkdir(path.dirname(registryPath), { recursive: true });
  await writeFile(registryPath, JSON.stringify(publishedManifest(manifest), null, 2) + "\n");
  process.stdout.write(`host-web/registry/stable.json + vendor + baked shell → ${outDir}/host-web/\n`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
