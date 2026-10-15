#!/usr/bin/env node
import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

import { assertIndexUrls } from "./lib/registry.mjs";

async function main() {
  const [file, baseUrl, allowFlag] = process.argv.slice(2);
  if (!file || !baseUrl) {
    throw new Error("usage: node check-urls.mjs <index.json> <baseUrl> [allowLoopback]");
  }
  const doc = JSON.parse(await readFile(file, "utf8"));
  assertIndexUrls(doc, baseUrl, allowFlag === "1");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
