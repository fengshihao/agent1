import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

export const SCHEMA_VERSION = 1;
export const MAX_INDEX_BYTES = 1024 * 1024;
export const MAX_ARTIFACT_BYTES = 64 * 1024 * 1024;

const KIND_ROOT = {
  weizhi_package: "packages",
  skill: "skills",
};

const NAME_VERSION = /^([a-z0-9][a-z0-9._-]*)@([0-9A-Za-z][0-9A-Za-z._+-]*)$/;

export function kindRoot(kind) {
  const root = KIND_ROOT[kind];
  if (!root) {
    throw new Error(`unknown kind: ${kind}`);
  }
  return root;
}

export function artifactRelPath(kind, name, version) {
  return `${kindRoot(kind)}/${name}/${version}/${name}-${version}.zip`;
}

export function joinUrl(base, rel) {
  const normalized = base.endsWith("/") ? base : `${base}/`;
  return normalized + rel.replace(/^\/+/, "");
}

export function assertBaseUrl(base) {
  let url;
  try {
    url = new URL(base);
  } catch {
    throw new Error("base url is invalid");
  }
  const loopback = url.hostname === "127.0.0.1" || url.hostname === "localhost";
  if (url.protocol === "http:" && !loopback) {
    throw new Error("http base url only allowed for loopback");
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("base url must be http or https");
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new Error("base url must not contain credentials, query, or hash");
  }
  return url;
}

export function assertFetchUrl(raw, expectedHost) {
  let url;
  try {
    url = new URL(raw);
  } catch {
    throw new Error("invalid url");
  }
  const loopback = url.hostname === "127.0.0.1" || url.hostname === "localhost";
  if (url.protocol === "http:" && !loopback) {
    throw new Error("http only allowed for loopback");
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("unsupported protocol");
  }
  if (expectedHost && url.host !== expectedHost) {
    throw new Error(`host not allowed: ${url.host}`);
  }
  return url;
}

export function sha256Hex(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

export function parseNameVersionDir(dirName) {
  const match = NAME_VERSION.exec(dirName);
  if (!match) {
    throw new Error(`package directory must be name@version: ${dirName}`);
  }
  return { name: match[1], version: match[2] };
}

export function readPackageIdentity(dirName, manifest) {
  const parsed = parseNameVersionDir(dirName);
  if (!manifest || typeof manifest !== "object" || Array.isArray(manifest)) {
    throw new Error(`${dirName}: manifest.json must be an object`);
  }
  if (manifest.name !== parsed.name || manifest.version !== parsed.version) {
    throw new Error(
      `${dirName}: manifest name/version must match the directory name`,
    );
  }
  return parsed;
}

export function loadCards(jsonl) {
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

export function cardsForPackage(name, cards) {
  const boundary = new RegExp(`weizhi:${name}(?![A-Za-z0-9._-])`);
  return (cards ?? []).filter(
    (card) => typeof card.entry === "string" && boundary.test(card.entry),
  );
}

export function optionalMetadata(manifest) {
  const extra = {};
  if (typeof manifest.description === "string" && manifest.description.trim()) {
    extra.description = manifest.description;
  }
  if (typeof manifest.min_engine === "string" && manifest.min_engine.trim()) {
    extra.min_engine = manifest.min_engine;
  }
  const capability = manifest.capability;
  if (capability && typeof capability === "object" && !Array.isArray(capability)) {
    const copied = {};
    for (const key of ["title", "summary", "entry"]) {
      if (typeof capability[key] === "string" && capability[key].trim()) {
        copied[key] = capability[key];
      }
    }
    if (Object.keys(copied).length > 0) {
      extra.capability = copied;
    }
  }
  return extra;
}

export function projectWeizhi(index) {
  const packages = (index.artifacts ?? [])
    .filter((item) => item.kind === "weizhi_package")
    .map((item) => {
      const projected = {
        name: item.name,
        version: item.version,
        artifact: {
          url: item.artifact.url,
          sha256: item.artifact.sha256,
          size: item.artifact.size,
        },
      };
      if (item.description) {
        projected.description = item.description;
      }
      if (item.min_engine) {
        projected.min_engine = item.min_engine;
      }
      if (item.capability) {
        projected.capability = item.capability;
      }
      if (item.cards) {
        projected.cards = item.cards;
      }
      return projected;
    });
  return {
    schema_version: SCHEMA_VERSION,
    channel: index.channel ?? "stable",
    packages,
  };
}

export function selectEntry(doc, { name, kind }) {
  if (!doc || doc.schema_version !== SCHEMA_VERSION) {
    throw new Error("schema_version must be 1");
  }
  if (Array.isArray(doc.artifacts)) {
    const hits = doc.artifacts.filter((item) => {
      if (item.name !== name) {
        return false;
      }
      return kind ? item.kind === kind : true;
    });
    return oneHit(hits, name);
  }
  if (Array.isArray(doc.packages)) {
    if (kind && kind !== "weizhi_package") {
      throw new Error(`weizhi projection has no kind ${kind}`);
    }
    const hits = doc.packages.filter((item) => item.name === name);
    const hit = oneHit(hits, name);
    return { ...hit, kind: "weizhi_package" };
  }
  throw new Error("registry missing artifacts or packages");
}

function oneHit(hits, name) {
  if (hits.length === 0) {
    throw new Error(`not in registry: ${name}`);
  }
  if (hits.length > 1) {
    throw new Error(`ambiguous registry entry: ${name}`);
  }
  return hits[0];
}

export function assertDigest(expected, actualHex) {
  if (typeof expected !== "string" || !/^[0-9a-f]{64}$/.test(expected)) {
    throw new Error("invalid sha256");
  }
  if (expected !== actualHex) {
    throw new Error("sha256 mismatch");
  }
}

export function assertSize(expected, actual) {
  if (expected === undefined || expected === null) {
    return;
  }
  if (typeof expected !== "number" || !Number.isInteger(expected) || expected < 0) {
    throw new Error("invalid size");
  }
  if (expected !== actual) {
    throw new Error("size mismatch");
  }
}

export async function writeJson(file, value) {
  await mkdir(path.dirname(file), { recursive: true });
  await writeFile(file, `${JSON.stringify(value, null, 2)}\n`);
}

export async function readJson(file) {
  return JSON.parse(await readFile(file, "utf8"));
}

export function assertIndexUrls(doc, baseUrl, allowLoopback) {
  assertBaseUrl(baseUrl);
  const prefix = baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`;
  const list = Array.isArray(doc.artifacts) ? doc.artifacts : doc.packages;
  if (!Array.isArray(list)) {
    throw new Error("registry missing artifacts or packages");
  }
  for (const item of list) {
    const artifactUrl = item?.artifact?.url;
    if (typeof artifactUrl !== "string" || !artifactUrl.startsWith(prefix)) {
      throw new Error(`url base mismatch: ${artifactUrl}`);
    }
    const host = new URL(artifactUrl).hostname;
    if (!allowLoopback && (host === "127.0.0.1" || host === "localhost")) {
      throw new Error(`refuse loopback url: ${artifactUrl}`);
    }
  }
}
