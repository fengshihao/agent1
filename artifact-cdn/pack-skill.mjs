#!/usr/bin/env node
/**
 * 打包官方 Skill（与 weizhi_package 分离）。
 *
 * 源布局（每个子目录一个 skill，目录名 = manifest.name）：
 *
 *   <name>/
 *     manifest.json   # 见 doc/SKILL-PACK-FIELDS.md
 *     SKILL.md        # 必填
 *     scripts/        # 可选业务 JS，run_js 内可 import weizhi:*（与主脚本相同 beforeEval）
 *     references/     # 可选
 *
 * 产出：
 *   dist/skills/<name>/<version>/<name>-<version>.zip
 *   dist/skills/registry/stable.json   # 仅 Skill 目录（进系统提示词）
 *   dist/registry/stable.json          # 合并 artifacts（kind=skill），不覆盖已有 weizhi_package
 *
 * 用法：
 *   node pack-skill.mjs --root ../skills-src --base-url https://agentone.guoyoutech.cn/
 */
import { spawn } from "node:child_process";
import { mkdir, readFile, readdir, rm } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

import {
  SCHEMA_VERSION,
  artifactRelPath,
  assertBaseUrl,
  joinUrl,
  optionalMetadata,
  readJson,
  sha256Hex,
  writeJson,
} from "./lib/registry.mjs";
import {
  buildSkillCatalog,
  readSkillIdentity,
  skillArtifactRecord,
} from "./lib/skill-manifest.mjs";
const KIND = "skill";

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const baseUrl = args["base-url"] || process.env.ARTIFACT_CDN_BASE_URL;
  if (!baseUrl) {
    throw new Error("set --base-url or ARTIFACT_CDN_BASE_URL");
  }
  assertBaseUrl(baseUrl);
  const root = path.resolve(args.root);
  const outDir = path.resolve(
    args.out || path.join(path.dirname(fileURLToPath(import.meta.url)), "dist"),
  );
  const channel = args.channel || "stable";

  const dirs = await skillDirs(root);
  const built = [];
  for (const dir of dirs) {
    built.push(await packSkillDir(dir, { baseUrl, outDir }));
  }
  built.sort((a, b) => a.name.localeCompare(b.name) || a.version.localeCompare(b.version));

  const catalog = buildSkillCatalog(built, channel);
  const catalogPath = path.join(outDir, "skills", "registry", "stable.json");
  await writeJson(catalogPath, catalog);

  const merged = await mergeSkillArtifacts(outDir, built, channel);
  const indexPath = path.join(outDir, "registry", "stable.json");
  await writeJson(indexPath, merged);

  process.stdout.write(`${catalogPath}\n${indexPath}\n`);
}

async function skillDirs(root) {
  const entries = await readdir(root, { withFileTypes: true });
  const dirs = entries.filter((e) => e.isDirectory()).map((e) => path.join(root, e.name));
  if (dirs.length === 0) {
    throw new Error(`no skill directories in ${root}`);
  }
  return dirs.sort((a, b) => a.localeCompare(b));
}

async function packSkillDir(dir, { baseUrl, outDir }) {
  const dirName = path.basename(dir);
  const manifestPath = path.join(dir, "manifest.json");
  const skillMd = path.join(dir, "SKILL.md");
  const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
  let skillMdText;
  try {
    skillMdText = await readFile(skillMd, "utf8");
  } catch {
    throw new Error(`${dirName}: SKILL.md required`);
  }
  const identity = readSkillIdentity(dirName, manifest, skillMdText);
  if (manifest.skill || manifest.entry) {
    throw new Error(`${dirName}: skill package must not use package-only fields (skill/entry)`);
  }

  const rel = artifactRelPath(KIND, identity.name, identity.version);
  const zipPath = path.join(outDir, ...rel.split("/"));
  await mkdir(path.dirname(zipPath), { recursive: true });
  await rm(zipPath, { force: true });
  await zipDirectory(dir, zipPath);
  const bytes = await readFile(zipPath);

  const extra = optionalMetadata(manifest);
  const platforms = normalizePlatforms(manifest.platforms);

  const packed = {
    name: identity.name,
    version: identity.version,
    rel,
    ...extra,
    ...(platforms ? { platforms } : {}),
    artifact: {
      url: joinUrl(baseUrl, rel),
      sha256: sha256Hex(bytes),
      size: bytes.length,
    },
  };
  return skillArtifactRecord(packed, identity);
}

function normalizePlatforms(platforms) {
  if (!Array.isArray(platforms)) {
    return undefined;
  }
  const list = platforms.filter((p) => p === "any" || p === "android" || p === "desktop");
  return list.length > 0 ? list : undefined;
}

async function mergeSkillArtifacts(outDir, built, channel) {
  let existing;
  try {
    existing = await readJson(path.join(outDir, "registry", "stable.json"));
  } catch (error) {
    if (error && error.code === "ENOENT") {
      existing = { schema_version: SCHEMA_VERSION, channel, artifacts: [] };
    } else {
      throw error;
    }
  }
  const others = (existing.artifacts ?? []).filter((item) => item.kind !== KIND);
  const artifacts = [...others, ...built];
  artifacts.sort(
    (a, b) =>
      (a.kind ?? "").localeCompare(b.kind ?? "") ||
      a.name.localeCompare(b.name) ||
      a.version.localeCompare(b.version),
  );
  return {
    schema_version: SCHEMA_VERSION,
    channel: existing.channel ?? channel,
    artifacts,
  };
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

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
