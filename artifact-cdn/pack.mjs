#!/usr/bin/env node
import { spawn } from "node:child_process";
import { mkdir, readFile, readdir, rm } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

import {
  SCHEMA_VERSION,
  artifactRelPath,
  assertBaseUrl,
  joinUrl,
  cardsForPackage,
  kindRoot,
  loadCards,
  optionalMetadata,
  projectWeizhi,
  readJson,
  readPackageIdentity,
  sha256Hex,
  writeJson,
} from "./lib/registry.mjs";

export async function packRoot({ root, kind, baseUrl, outDir, channel = "stable", cards = [] }) {
  assertBaseUrl(baseUrl);
  kindRoot(kind);
  const dirs = await packageDirs(root);
  const built = [];
  for (const dir of dirs) {
    built.push(await packOne(dir, { kind, baseUrl, outDir, cards }));
  }
  built.sort((a, b) => a.name.localeCompare(b.name) || a.version.localeCompare(b.version));

  const existing = await readExistingIndex(outDir);
  const artifacts = (existing.artifacts ?? []).filter((item) => item.kind !== kind);
  artifacts.push(...built.map((item) => publicRecord(item)));
  artifacts.sort(compareArtifacts);
  const index = {
    schema_version: SCHEMA_VERSION,
    channel,
    artifacts,
  };
  await deleteStaleZips(outDir, kind, new Set(built.map((item) => item.rel)));
  const indexPath = path.join(outDir, "registry", "stable.json");
  const projectionPath = path.join(outDir, "packages", "registry", "stable.json");
  await writeJson(indexPath, index);
  await writeJson(projectionPath, projectWeizhi(index));
  return { indexPath, projectionPath, index };
}

async function packageDirs(root) {
  const entries = await readdir(root, { withFileTypes: true });
  const dirs = entries.filter((entry) => entry.isDirectory()).map((entry) => entry.name);
  if (dirs.length === 0) {
    throw new Error(`no package directories in ${root}`);
  }
  return dirs.sort().map((name) => path.join(root, name));
}

async function packOne(dir, { kind, baseUrl, outDir, cards }) {
  const manifestPath = path.join(dir, "manifest.json");
  const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
  const { name, version } = readPackageIdentity(path.basename(dir), manifest);
  const matched = cardsForPackage(name, cards);
  const rel = artifactRelPath(kind, name, version);
  const zipPath = path.join(outDir, ...rel.split("/"));
  await mkdir(path.dirname(zipPath), { recursive: true });
  await rm(zipPath, { force: true });
  await zipDirectory(dir, zipPath);
  const bytes = await readFile(zipPath);
  return {
    kind,
    name,
    version,
    rel,
    ...optionalMetadata(manifest),
    ...(matched.length > 0 ? { cards: matched } : {}),
    artifact: {
      url: joinUrl(baseUrl, rel),
      sha256: sha256Hex(bytes),
      size: bytes.length,
    },
  };
}

function publicRecord(item) {
  const record = {
    kind: item.kind,
    name: item.name,
    version: item.version,
  };
  if (item.description) {
    record.description = item.description;
  }
  if (item.min_engine) {
    record.min_engine = item.min_engine;
  }
  if (item.capability) {
    record.capability = item.capability;
  } else if (item.cards && item.cards.length > 0) {
    const first = item.cards[0];
    record.capability = {
      title: first.title ?? "",
      summary: first.summary ?? "",
      entry: first.entry ?? "",
    };
  }
  if (item.cards && item.cards.length > 0) {
    record.cards = item.cards;
  }
  record.artifact = item.artifact;
  return record;
}

function compareArtifacts(a, b) {
  return (
    a.kind.localeCompare(b.kind) ||
    a.name.localeCompare(b.name) ||
    a.version.localeCompare(b.version)
  );
}

async function readExistingIndex(outDir) {
  try {
    return await readJson(path.join(outDir, "registry", "stable.json"));
  } catch (error) {
    if (error && error.code === "ENOENT") {
      return { schema_version: SCHEMA_VERSION, channel: "stable", artifacts: [] };
    }
    throw error;
  }
}

async function deleteStaleZips(outDir, kind, keep) {
  const root = path.join(outDir, kindRoot(kind));
  for (const file of await walkFiles(root)) {
    if (!file.endsWith(".zip")) {
      continue;
    }
    const rel = path.relative(outDir, file).split(path.sep).join("/");
    if (!keep.has(rel)) {
      await rm(file, { force: true });
    }
  }
}

async function walkFiles(dir) {
  let entries;
  try {
    entries = await readdir(dir, { withFileTypes: true });
  } catch (error) {
    if (error && error.code === "ENOENT") {
      return [];
    }
    throw error;
  }
  const files = [];
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await walkFiles(full)));
    } else if (entry.isFile()) {
      files.push(full);
    }
  }
  return files;
}

function zipDirectory(srcDir, destZip) {
  return new Promise((resolve, reject) => {
    const child = spawn(
      "zip",
      ["-r", "-X", "-q", destZip, ".", "-x", "*.DS_Store", "-x", "__MACOSX/*"],
      { cwd: srcDir },
    );
    let stderr = "";
    child.stderr.on("data", (chunk) => {
      stderr += chunk;
    });
    child.on("error", (error) => {
      reject(error.code === "ENOENT" ? new Error("zip command not found") : error);
    });
    child.on("close", (code) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(stderr.trim() || `zip exit ${code}`));
      }
    });
  });
}

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (!arg.startsWith("--")) {
      throw new Error(`unknown arg ${arg}`);
    }
    const key = arg.slice(2);
    const value = argv[i + 1];
    if (value === undefined || value.startsWith("--")) {
      throw new Error(`missing value for --${key}`);
    }
    i += 1;
    out[key] = value;
  }
  return out;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.root || !args.kind) {
    throw new Error(
      "usage: node pack.mjs --root <packages 父目录，子目录为 name> --kind <weizhi_package|skill> --base-url <公网根> [--out dist] [--channel stable] [--cards <jsonl>]",
    );
  }
  const baseUrl = args["base-url"] || process.env.ARTIFACT_CDN_BASE_URL;
  if (!baseUrl) {
    throw new Error("set --base-url or ARTIFACT_CDN_BASE_URL");
  }
  const outDir = path.resolve(
    args.out || path.join(path.dirname(fileURLToPath(import.meta.url)), "dist"),
  );
  const cards = args.cards ? loadCards(await readFile(args.cards, "utf8")) : [];
  const { indexPath } = await packRoot({
    root: path.resolve(args.root),
    kind: args.kind,
    baseUrl,
    outDir,
    channel: args.channel || "stable",
    cards,
  });
  process.stdout.write(`${indexPath}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
