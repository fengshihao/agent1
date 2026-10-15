#!/usr/bin/env node
import { mkdir, rename, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

import {
  MAX_ARTIFACT_BYTES,
  MAX_INDEX_BYTES,
  assertDigest,
  assertFetchUrl,
  assertSize,
  selectEntry,
  sha256Hex,
} from "./lib/registry.mjs";

export async function fetchArtifact({ indexUrl, name, kind, outDir, maxBytes = MAX_ARTIFACT_BYTES }) {
  const index = assertFetchUrl(indexUrl);
  const doc = JSON.parse((await download(indexUrl, MAX_INDEX_BYTES, index.host)).toString("utf8"));
  const entry = selectEntry(doc, { name, kind });
  const artifactUrl = entry?.artifact?.url;
  if (typeof artifactUrl !== "string") {
    throw new Error(`not in registry: ${name}`);
  }
  const bytes = await download(artifactUrl, maxBytes, index.host);
  assertSize(entry.artifact.size, bytes.length);
  assertDigest(String(entry.artifact.sha256 || "").toLowerCase(), sha256Hex(bytes));

  await mkdir(outDir, { recursive: true });
  const finalPath = path.join(outDir, `${entry.name}-${entry.version}.zip`);
  const partial = path.join(outDir, `.${entry.name}-${entry.version}.zip.partial`);
  let committed = false;
  try {
    await writeFile(partial, bytes);
    await rename(partial, finalPath);
    committed = true;
  } finally {
    if (!committed) {
      await rm(partial, { force: true });
    }
  }
  return finalPath;
}

async function download(rawUrl, maxBytes, expectedHost) {
  let current = rawUrl;
  for (let hop = 0; hop < 5; hop += 1) {
    assertFetchUrl(current, expectedHost);
    const response = await fetch(current, { redirect: "manual" });
    if (response.status >= 300 && response.status < 400) {
      const location = response.headers.get("location");
      if (!location) {
        throw new Error("redirect without location");
      }
      current = new URL(location, current).href;
      continue;
    }
    if (!response.ok) {
      throw new Error(`HTTP ${response.status} for ${current}`);
    }
    return readLimited(response, maxBytes);
  }
  throw new Error("too many redirects");
}

async function readLimited(response, maxBytes) {
  const chunks = [];
  let total = 0;
  for await (const chunk of response.body) {
    const buf = Buffer.from(chunk);
    total += buf.length;
    if (total > maxBytes) {
      throw new Error("response too large");
    }
    chunks.push(buf);
  }
  return Buffer.concat(chunks, total);
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
  if (!args.index || !args.name || !args.out) {
    throw new Error(
      "usage: node fetch.mjs --index <清单 URL> --name <name> --out <目录> [--kind <kind>] [--max-bytes N]",
    );
  }
  const maxBytes = args["max-bytes"] ? Number(args["max-bytes"]) : MAX_ARTIFACT_BYTES;
  if (!Number.isInteger(maxBytes) || maxBytes <= 0) {
    throw new Error("invalid --max-bytes");
  }
  const saved = await fetchArtifact({
    indexUrl: args.index,
    name: args.name,
    kind: args.kind,
    outDir: path.resolve(args.out),
    maxBytes,
  });
  process.stdout.write(`${saved}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
