import { copyFile, mkdir, readFile, stat } from "node:fs/promises";
import path from "node:path";

import { assertBaseUrl, joinUrl } from "./registry.mjs";

const LOCAL_PREFIX = "路径 shared/catalog/templates/";
const LEGACY_TEMPLATE_PREFIX = "模板 shared/catalog/templates/";

/** @typedef {{ id: string, kind: string, title?: string, summary?: string, tags?: string[], platforms?: string[], weight?: number, lib_url?: string, template?: string, entry?: string }} WebLibCard */

/**
 * 从旧版 entry（内嵌 HTML）或新版 lib_url+template 归一化。
 */
export function normalizeWebLibCard(card) {
  if (card.lib_url && card.template) {
    return {
      id: card.id,
      kind: card.kind,
      title: card.title ?? "",
      summary: card.summary ?? "",
      tags: card.tags ?? [],
      platforms: card.platforms ?? ["any"],
      weight: card.weight ?? 1.2,
      lib_url: card.lib_url.trim(),
      template: card.template.trim(),
    };
  }
  const entry = typeof card.entry === "string" ? card.entry : "";
  const lines = entry.split("\n");
  const urlLine = lines.find((line) => line.startsWith("URL "));
  if (!urlLine) {
    throw new Error(`${card.id}: entry missing URL line`);
  }
  const libUrl = urlLine.slice(4).trim();
  const legacyTemplateLine = lines.find((line) => line.startsWith(LEGACY_TEMPLATE_PREFIX));
  if (!legacyTemplateLine) {
    throw new Error(`${card.id}: entry missing template path line`);
  }
  const template = legacyTemplateLine.slice(LEGACY_TEMPLATE_PREFIX.length).trim();
  if (!template.endsWith(".html")) {
    throw new Error(`${card.id}: template must be .html`);
  }
  return {
    id: card.id,
    kind: card.kind,
    title: card.title ?? "",
    summary: card.summary ?? "",
    tags: card.tags ?? [],
    platforms: card.platforms ?? ["any"],
    weight: card.weight ?? 1.2,
    lib_url: libUrl,
    template,
  };
}

export function buildWebLibEntry(libUrl, templateFile, baseUrl) {
  assertBaseUrl(baseUrl);
  const shellUrl = joinUrl(baseUrl, `web-libs/templates/${templateFile}`);
  return `URL ${libUrl}\n壳 ${shellUrl}\n${LOCAL_PREFIX}${templateFile}`;
}

export async function assertTemplateExists(templatesDir, templateFile) {
  const file = path.join(templatesDir, templateFile);
  try {
    const info = await stat(file);
    if (!info.isFile()) {
      throw new Error(`not a file: ${templateFile}`);
    }
  } catch {
    throw new Error(`missing template file: ${file}`);
  }
  return file;
}

export async function publishWebLibTemplates(normalizedCards, templatesDir, outDir) {
  const destDir = path.join(outDir, "web-libs", "templates");
  await mkdir(destDir, { recursive: true });
  for (const card of normalizedCards) {
    const src = await assertTemplateExists(templatesDir, card.template);
    const dest = path.join(destDir, card.template);
    await copyFile(src, dest);
  }
}

export function registryCardsFromNormalized(normalizedCards, baseUrl) {
  return normalizedCards.map((card) => {
    const entry = buildWebLibEntry(card.lib_url, card.template, baseUrl);
    return {
      id: card.id,
      kind: card.kind,
      title: card.title,
      summary: card.summary,
      tags: card.tags,
      platforms: card.platforms,
      entry,
      weight: card.weight,
    };
  });
}

export async function slimCardsJsonl(cardsPath, templatesDir) {
  const raw = loadCardsJsonl(await readFile(cardsPath, "utf8"));
  const slim = [];
  for (const card of raw) {
    if (card.kind !== "web_lib") {
      continue;
    }
    const normalized = normalizeWebLibCard(card);
    await assertTemplateExists(templatesDir, normalized.template);
    slim.push({
      id: normalized.id,
      kind: normalized.kind,
      title: normalized.title,
      summary: normalized.summary,
      tags: normalized.tags,
      platforms: normalized.platforms,
      lib_url: normalized.lib_url,
      template: normalized.template,
      weight: normalized.weight,
    });
  }
  return slim;
}

function loadCardsJsonl(jsonl) {
  const cards = [];
  for (const line of jsonl.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }
    cards.push(JSON.parse(trimmed));
  }
  return cards;
}
