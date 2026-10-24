/**
 * 官方 Skill 制品：manifest.json 仅 name+version；展示字段从 SKILL.md frontmatter 读取。
 */

import { readFile } from "node:fs/promises";
import path from "node:path";

const PACKAGE_NAME = /^[a-z0-9][a-z0-9._-]*$/;
const CATEGORY_ID = /^[a-z][a-z0-9._-]*$/;

/**
 * @param {string} dirName
 * @param {unknown} manifest
 * @param {string} skillMdText
 * @returns {{ name: string, version: string, category: string, title: string, summary: string, catalog_line: string, depends_weizhi: string[] }}
 */
export function readSkillIdentity(dirName, manifest, skillMdText) {
  if (!manifest || typeof manifest !== "object" || Array.isArray(manifest)) {
    throw new Error(`${dirName}: manifest.json must be an object`);
  }
  const name = manifest.name;
  const version = manifest.version;
  if (typeof name !== "string" || !PACKAGE_NAME.test(name)) {
    throw new Error(`${dirName}: manifest.name must be a skill id (lowercase, hyphens)`);
  }
  if (name !== dirName) {
    throw new Error(`${dirName}: directory name must match manifest.name`);
  }
  if (typeof version !== "string" || !version.trim()) {
    throw new Error(`${dirName}: manifest.version required`);
  }

  const fm = parseFrontmatter(skillMdText ?? "");
  const category = fm.category;
  if (typeof category !== "string" || !CATEGORY_ID.test(category)) {
    throw new Error(
      `${dirName}: SKILL.md frontmatter category required (e.g. office.presentation, travel.roadbook)`,
    );
  }
  const title = typeof fm.name === "string" && fm.name.trim() ? fm.name.trim() : name;
  const summary =
    typeof fm.description === "string" && fm.description.trim()
      ? fm.description.trim()
      : (() => {
          throw new Error(`${dirName}: SKILL.md frontmatter description required`);
        })();
  const catalogLine =
    typeof fm.catalog_line === "string" && fm.catalog_line.trim()
      ? fm.catalog_line.trim()
      : summary;
  const depends = parseDepends(fm.depends_weizhi);
  return {
    name,
    version,
    category,
    title,
    summary,
    catalog_line: catalogLine,
    depends_weizhi: depends,
  };
}

export async function readSkillIdentityFromDir(dir) {
  const dirName = path.basename(dir);
  const manifest = JSON.parse(await readFile(path.join(dir, "manifest.json"), "utf8"));
  const skillMd = await readFile(path.join(dir, "SKILL.md"), "utf8");
  return readSkillIdentity(dirName, manifest, skillMd);
}

function parseDepends(raw) {
  if (typeof raw !== "string" || !raw.trim()) {
    return [];
  }
  return raw
    .split(/[,;\s]+/)
    .map((x) => x.trim())
    .filter((x) => PACKAGE_NAME.test(x));
}

function parseFrontmatter(raw) {
  const normalized = raw.replace(/\r\n/g, "\n");
  if (!normalized.startsWith("---\n")) {
    return {};
  }
  const end = normalized.indexOf("\n---\n", 4);
  if (end < 0) {
    return {};
  }
  const block = normalized.substring(4, end);
  const values = {};
  for (const line of block.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }
    const idx = trimmed.indexOf(":");
    if (idx <= 0) {
      continue;
    }
    const key = trimmed.substring(0, idx).trim().toLowerCase();
    let value = trimmed.substring(idx + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    values[key] = value;
  }
  return values;
}

/** 写入统一 registry 的 skill 条目（不含 weizhi_package 的 cards）。 */
export function skillArtifactRecord(packed, identity) {
  const record = {
    kind: "skill",
    name: packed.name,
    version: packed.version,
    category: identity.category,
    title: identity.title,
    summary: identity.summary,
    catalog_line: identity.catalog_line,
    artifact: packed.artifact,
  };
  if (identity.depends_weizhi.length > 0) {
    record.depends_weizhi = identity.depends_weizhi;
  }
  if (packed.description) {
    record.description = packed.description;
  }
  if (packed.min_engine) {
    record.min_engine = packed.min_engine;
  }
  if (packed.platforms) {
    record.platforms = packed.platforms;
  }
  return record;
}

/**
 * 系统提示词用的 Skill 目录（大类可随 manifest 增减；条目可逐步瘦身为 name + 一句）。
 * @param {Array<ReturnType<typeof skillArtifactRecord>>} skillArtifacts
 */
export function buildSkillCatalog(skillArtifacts, channel) {
  const byCategory = new Map();
  for (const item of skillArtifacts) {
    const cat = item.category;
    if (!byCategory.has(cat)) {
      byCategory.set(cat, []);
    }
    byCategory.get(cat).push({
      name: item.name,
      line: item.catalog_line || item.summary || item.title,
    });
  }
  const categories = [...byCategory.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([id, skills]) => ({
      id,
      skills: skills.sort((a, b) => a.name.localeCompare(b.name)),
    }));
  return {
    schema_version: 1,
    channel: channel ?? "stable",
    generated_at: new Date().toISOString().slice(0, 10),
    categories,
  };
}
