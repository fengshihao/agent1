#!/usr/bin/env node
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { SCHEMA_VERSION, loadCards, writeJson } from "./lib/registry.mjs";
import { resolveCdnBaseUrl } from "./lib/cdn-base-url.mjs";
import {
  normalizeWebLibCard,
  publishWebLibTemplates,
  registryCardsFromNormalized,
} from "./lib/web-lib-cards.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_TEMPLATES = path.join(here, "web-libs", "templates");

export async function packWebLibs({ cardsPath, outDir, templatesDir, baseUrl }) {
  const raw = loadCards(await readFile(cardsPath, "utf8")).filter((card) => card.kind === "web_lib");
  if (raw.length === 0) {
    throw new Error("no web_lib cards");
  }
  const ids = new Set();
  const normalized = [];
  for (const card of raw) {
    if (typeof card.id !== "string" || !card.id.startsWith("web_lib.")) {
      throw new Error("web_lib card id must start with web_lib.");
    }
    if (ids.has(card.id)) {
      throw new Error(`duplicate web_lib id: ${card.id}`);
    }
    ids.add(card.id);
    const item = normalizeWebLibCard(card);
    if (!item.lib_url.startsWith("https://")) {
      throw new Error(`${card.id}: lib_url must be https`);
    }
    normalized.push(item);
  }

  const resolvedTemplates = templatesDir || DEFAULT_TEMPLATES;
  const resolvedBase = resolveCdnBaseUrl(baseUrl);
  await publishWebLibTemplates(normalized, resolvedTemplates, outDir);
  const cards = registryCardsFromNormalized(normalized, resolvedBase);

  for (const card of cards) {
    if (!card.entry.includes("https://")) {
      throw new Error(`${card.id}: packed entry must contain https URL`);
    }
    if (card.entry.includes("<!DOCTYPE") || card.entry.includes("<html")) {
      throw new Error(`${card.id}: entry must not embed HTML; use template + pack`);
    }
  }

  const indexPath = path.join(outDir, "web-libs", "registry", "stable.json");
  await writeJson(indexPath, {
    schema_version: SCHEMA_VERSION,
    channel: "stable",
    cards,
  });
  return { indexPath, count: cards.length, templatesDir: path.join(outDir, "web-libs", "templates") };
}

function argValue(argv, name) {
  const index = argv.indexOf(name);
  if (index < 0 || index + 1 >= argv.length) {
    return "";
  }
  return argv[index + 1];
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const cardsPath = argValue(process.argv, "--cards") || path.join(here, "web-libs", "cards.jsonl");
  const outDir = argValue(process.argv, "--out") || path.join(here, "dist");
  const templatesDir = argValue(process.argv, "--templates") || DEFAULT_TEMPLATES;
  const baseUrl = argValue(process.argv, "--base-url") || "";
  const result = await packWebLibs({ cardsPath, outDir, templatesDir, baseUrl });
  process.stdout.write(`${result.indexPath} cards=${result.count} templates=${result.templatesDir}\n`);
}
