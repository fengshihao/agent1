#!/usr/bin/env node
/**
 * 墨览 Agent1 embed：从 molan studio 复制运行时 + 烘焙 shell URL → dist/host-web/molan-embed/1/
 * Android 仅通过 registry 的 md_preview.shell 拉取，不写 APK assets。
 */
import { copyFile, cp, mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const EMBED_VERSION = "1";
const SHELL_VERSION_QUERY = "20251027a";

function argValue(argv, name) {
  const i = argv.indexOf(name);
  return i >= 0 && i + 1 < argv.length ? argv[i + 1] : null;
}

function resolveMolanStudio(argv) {
  const fromEnv = process.env.MOLAN_REPO;
  if (fromEnv) {
    return path.join(fromEnv, "apps", "studio");
  }
  const fromArg = argValue(argv, "--molan-studio");
  if (fromArg) return path.resolve(fromArg);
  return path.resolve(here, "..", "..", "molan", "apps", "studio");
}

async function copyRuntime(studioDir, destDir) {
  const files = ["molan.css", "molan-i18n.js", "molan-editor.js", "molan-app.js"];
  for (const name of files) {
    await copyFile(path.join(studioDir, name), path.join(destDir, name));
  }
  await cp(path.join(studioDir, "vendor"), path.join(destDir, "vendor"), { recursive: true });
  await copyFile(
    path.join(here, "host-web", "molan-embed", "agent1-embed.css"),
    path.join(destDir, "agent1-embed.css"),
  );
  await copyFile(
    path.join(here, "host-web", "molan-embed", "agent1-bridge.js"),
    path.join(destDir, "agent1-bridge.js"),
  );
}

async function bakeShell(destDir, baseUrl) {
  const src = path.join(here, "host-web", "molan-embed", EMBED_VERSION, "shell.html");
  let html = await readFile(src, "utf8");
  html = html.replaceAll("__MOLAN_EMBED_BASE__", baseUrl);
  if (html.includes("__MOLAN_EMBED_BASE__")) {
    throw new Error("shell.html still contains bake placeholders");
  }
  const shellDest = path.join(destDir, "shell.html");
  await writeFile(shellDest, html);
  return html;
}

async function main() {
  const outRoot = argValue(process.argv, "--out") || path.join(here, "dist");
  const studio = resolveMolanStudio(process.argv);
  const destDir = path.join(outRoot, "host-web", "molan-embed", EMBED_VERSION);
  const manifestPath =
    argValue(process.argv, "--manifest") || path.join(here, "host-web", "manifest.source.json");
  const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
  const shellPublished = manifest?.md_preview?.shell;
  if (!shellPublished || typeof shellPublished !== "string") {
    throw new Error("manifest.source.json missing md_preview.shell");
  }
  const baseUrl = shellPublished.replace(/shell\.html.*$/, "");

  await mkdir(destDir, { recursive: true });
  await copyRuntime(studio, destDir);
  await bakeShell(destDir, baseUrl);

  process.stdout.write(
    `molan-embed → ${destDir} (studio=${studio}) shell?v=${SHELL_VERSION_QUERY}\n`,
  );
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
