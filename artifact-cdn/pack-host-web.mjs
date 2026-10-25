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

async function bakeShell(outDir, manifest) {
  const jszip = manifest.docx_preview.jszip;
  const docx = manifest.docx_preview.docx_preview;
  const shellSrc = path.join(here, "host-web", "shell", "docx-preview", "1", "shell.html");
  let html = await readFile(shellSrc, "utf8");
  html = html.replaceAll("__JSZIP_URL__", jszip).replaceAll("__DOCX_PREVIEW_URL__", docx);
  const shellDest = path.join(outDir, "host-web", "shell", "docx-preview", "1", "shell.html");
  await mkdir(path.dirname(shellDest), { recursive: true });
  await writeFile(shellDest, html);
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
  const registryPath = path.join(outDir, "host-web", "registry", "stable.json");
  await mkdir(path.dirname(registryPath), { recursive: true });
  await writeFile(registryPath, JSON.stringify(manifest, null, 2) + "\n");
  process.stdout.write(`host-web/registry/stable.json + vendor + baked shell → ${outDir}/host-web/\n`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
