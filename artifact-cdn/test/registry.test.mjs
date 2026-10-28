import { existsSync } from "node:fs";
import { execFile } from "node:child_process";
import { mkdir, mkdtemp, readFile, readdir, rm, writeFile } from "node:fs/promises";
import http from "node:http";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { test } from "node:test";
import assert from "node:assert/strict";

import { cardsForPackage, projectWeizhi, readPackageIdentity, sha256Hex } from "../lib/registry.mjs";

const execFileAsync = promisify(execFile);
const here = path.dirname(fileURLToPath(import.meta.url));
const artifactCdn = path.resolve(here, "..");
const packagesRoot = (() => {
  // packages-src 候选：env > CI 内嵌（agent1/agent1-skills）> 同级仓（../agent1-skills）。
  const candidates = [
    process.env.AGENT1_SKILLS_ROOT
      ? path.resolve(process.env.AGENT1_SKILLS_ROOT, "packages-src")
      : null,
    path.resolve(artifactCdn, "../agent1-skills/packages-src"),
    path.resolve(artifactCdn, "../../agent1-skills/packages-src"),
  ].filter(Boolean);
  for (const candidate of candidates) {
    if (existsSync(candidate)) {
      return candidate;
    }
  }
  return path.resolve(
    artifactCdn,
    "../agent_core/src/main/resources/agent-home/weizhi-packages",
  );
})();

function run(script, args, env = {}) {
  return new Promise((resolvePromise, reject) => {
    execFile(
      process.execPath,
      [path.join(artifactCdn, script), ...args],
      { cwd: artifactCdn, env: { ...process.env, ...env } },
      (error, stdout, stderr) => {
        if (error) {
          reject(Object.assign(error, { stdout, stderr }));
          return;
        }
        resolvePromise({ stdout, stderr });
      },
    );
  });
}

function runAllowFail(command, args, env = {}) {
  return new Promise((resolvePromise) => {
    execFile(
      command,
      args,
      { cwd: artifactCdn, env: { ...process.env, ...env } },
      (error, stdout, stderr) => {
        resolvePromise({
          code: error ? error.code ?? 1 : 0,
          stdout: String(stdout),
          stderr: String(stderr),
        });
      },
    );
  });
}

function startServer(root) {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url ?? "/", "http://127.0.0.1");
    const rel = decodeURIComponent(url.pathname);
    const file = path.resolve(root, `.${rel}`);
    const base = path.resolve(root);
    if (file !== base && !file.startsWith(`${base}${path.sep}`)) {
      res.writeHead(403).end();
      return;
    }
    readFile(file).then(
      (data) => {
        res.writeHead(200).end(data);
      },
      () => {
        res.writeHead(404).end();
      },
    );
  });
  return new Promise((resolvePromise) => {
    server.listen(0, "127.0.0.1", () => resolvePromise(server));
  });
}

test("cards attach only to the exact package name", () => {
  const cards = [
    { id: "docx.markdownToDocx", entry: 'import { markdownToDocx } from "weizhi:docx";' },
    { id: "docx.create", entry: 'import { buildDocx } from "weizhi:docx-build";' },
  ];
  assert.deepEqual(cardsForPackage("docx", cards).map((card) => card.id), ["docx.markdownToDocx"]);
  assert.deepEqual(cardsForPackage("docx-build", cards).map((card) => card.id), ["docx.create"]);
});

test("readPackageIdentity prefers versionless dir + manifest.version", () => {
  assert.deepEqual(
    readPackageIdentity("html2pptx", { name: "html2pptx", version: "0.2.5" }),
    { name: "html2pptx", version: "0.2.5" },
  );
  assert.deepEqual(
    readPackageIdentity("demo@1.0.0", { name: "demo", version: "1.0.0" }),
    { name: "demo", version: "1.0.0" },
  );
  assert.throws(
    () => readPackageIdentity("html2pptx", { name: "other", version: "0.2.5" }),
    /manifest.name must match/,
  );
});

test("weizhi projection drops kind and non-package entries", () => {
  const projected = projectWeizhi({
    schema_version: 1,
    channel: "stable",
    artifacts: [
      {
        kind: "skill",
        name: "demo-skill",
        version: "0.1.0",
        artifact: { url: "https://cdn.example/skills/demo-skill/0.1.0/demo-skill-0.1.0.zip", sha256: "ab", size: 1 },
      },
      {
        kind: "weizhi_package",
        name: "docx",
        version: "1.0.0",
        description: "Word",
        artifact: { url: "https://cdn.example/packages/docx/1.0.0/docx-1.0.0.zip", sha256: "cd", size: 2 },
      },
    ],
  });
  assert.equal(JSON.stringify(projected).includes('"kind"'), false);
  assert.deepEqual(projected.packages.map((item) => item.name), ["docx"]);
  assert.equal(projected.packages[0].description, "Word");
});

test("pack weizhi packages, fetch the projection, reject a bad digest", async () => {
  const dist = await mkdtemp(path.join(tmpdir(), "artifact-cdn-dist-"));
  const fetched = await mkdtemp(path.join(tmpdir(), "artifact-cdn-out-"));
  const rejected = await mkdtemp(path.join(tmpdir(), "artifact-cdn-bad-"));
  const server = await startServer(dist);
  try {
    const { port } = server.address();
    const base = `http://127.0.0.1:${port}/`;
    await run("pack.mjs", [
      "--root",
      packagesRoot,
      "--kind",
      "weizhi_package",
      "--base-url",
      base,
      "--out",
      dist,
    ]);

    const index = JSON.parse(await readFile(path.join(dist, "registry", "stable.json"), "utf8"));
    const projection = JSON.parse(
      await readFile(path.join(dist, "packages", "registry", "stable.json"), "utf8"),
    );
    assert.equal(index.schema_version, 1);
    assert.equal(index.channel, "stable");
    assert.deepEqual(
      index.artifacts.map((item) => item.name).sort(),
      [
        "audio",
        "docx",
        "docx-build",
        "docx-raw",
        "html2pptx",
        "image",
        "pptx",
        "pptx-build",
        "svg-raster",
        "xlsx",
        "xlsx-build",
        "xlsx-raw",
        "chart",
      ].sort(),
    );
    assert.ok(index.artifacts.every((item) => item.kind === "weizhi_package"));
    assert.equal(JSON.stringify(projection).includes('"kind"'), false);
    assert.equal(projection.packages.length, index.artifacts.length);

    for (const item of index.artifacts) {
      const rel = item.artifact.url.slice(base.length);
      const zipPath = path.join(dist, ...rel.split("/"));
      const bytes = await readFile(zipPath);
      assert.equal(item.artifact.sha256, sha256Hex(bytes));
      assert.equal(item.artifact.size, bytes.length);
      assert.match(item.artifact.sha256, /^[0-9a-f]{64}$/);
      const listed = projection.packages.find((pkg) => pkg.name === item.name);
      assert.equal(listed.artifact.sha256, item.artifact.sha256);
      assert.equal(listed.artifact.url, item.artifact.url);
      const { stdout } = await execFileAsync("unzip", ["-l", zipPath]);
      assert.match(stdout, /manifest\.json/);
    }

    const saved = (
      await run("fetch.mjs", [
        "--index",
        `${base}packages/registry/stable.json`,
        "--name",
        "docx",
        "--kind",
        "weizhi_package",
        "--out",
        fetched,
      ])
    ).stdout.trim();
    const packed = await readFile(
      path.join(dist, "packages", "docx", "1.0.0", "docx-1.0.0.zip"),
    );
    assert.deepEqual(await readFile(saved), packed);

    const fromGeneric = await mkdtemp(path.join(tmpdir(), "artifact-cdn-generic-"));
    const genericSaved = (
      await run("fetch.mjs", [
        "--index",
        `${base}registry/stable.json`,
        "--name",
        "docx",
        "--kind",
        "weizhi_package",
        "--out",
        fromGeneric,
      ])
    ).stdout.trim();
    assert.deepEqual(await readFile(genericSaved), packed);
    await rm(fromGeneric, { recursive: true, force: true });

    const bad = structuredClone(projection);
    bad.packages.find((item) => item.name === "docx").artifact.sha256 = "0".repeat(64);
    await writeFile(
      path.join(dist, "packages", "registry", "bad-sha.json"),
      JSON.stringify(bad),
    );
    const shaFail = await runAllowFail(process.execPath, [
      path.join(artifactCdn, "fetch.mjs"),
      "--index",
      `${base}packages/registry/bad-sha.json`,
      "--name",
      "docx",
      "--out",
      rejected,
    ]);
    assert.notEqual(shaFail.code, 0);
    assert.match(shaFail.stderr, /sha256 mismatch/);
    assert.deepEqual(await readdir(rejected), []);

    bad.packages.find((item) => item.name === "docx").artifact.sha256 =
      projection.packages.find((item) => item.name === "docx").artifact.sha256;
    bad.packages.find((item) => item.name === "docx").artifact.size = 1;
    await writeFile(
      path.join(dist, "packages", "registry", "bad-size.json"),
      JSON.stringify(bad),
    );
    const sizeFail = await runAllowFail(process.execPath, [
      path.join(artifactCdn, "fetch.mjs"),
      "--index",
      `${base}packages/registry/bad-size.json`,
      "--name",
      "docx",
      "--out",
      rejected,
    ]);
    assert.notEqual(sizeFail.code, 0);
    assert.match(sizeFail.stderr, /size mismatch/);
    assert.deepEqual(await readdir(rejected), []);
  } finally {
    await new Promise((resolvePromise) => server.close(resolvePromise));
    await rm(dist, { recursive: true, force: true });
    await rm(fetched, { recursive: true, force: true });
    await rm(rejected, { recursive: true, force: true });
  }
});

test("skill uses the same pack and fetch and stays out of the weizhi projection", async () => {
  const src = await mkdtemp(path.join(tmpdir(), "artifact-cdn-skill-src-"));
  const dist = await mkdtemp(path.join(tmpdir(), "artifact-cdn-skill-dist-"));
  const out = await mkdtemp(path.join(tmpdir(), "artifact-cdn-skill-out-"));
  const server = await startServer(dist);
  try {
    const pkg = path.join(src, "demo-skill@0.1.0");
    await mkdir(pkg);
    await writeFile(
      path.join(pkg, "manifest.json"),
      JSON.stringify({ name: "demo-skill", version: "0.1.0" }),
    );
    await writeFile(path.join(pkg, "SKILL.md"), "# demo\n");
    const { port } = server.address();
    const base = `http://127.0.0.1:${port}/`;
    await run("pack.mjs", ["--root", src, "--kind", "skill", "--base-url", base, "--out", dist]);
    const index = JSON.parse(await readFile(path.join(dist, "registry", "stable.json"), "utf8"));
    const projection = JSON.parse(
      await readFile(path.join(dist, "packages", "registry", "stable.json"), "utf8"),
    );
    assert.equal(index.artifacts.length, 1);
    assert.equal(index.artifacts[0].kind, "skill");
    assert.deepEqual(projection.packages, []);
    const saved = (
      await run("fetch.mjs", [
        "--index",
        `${base}registry/stable.json`,
        "--name",
        "demo-skill",
        "--kind",
        "skill",
        "--out",
        out,
      ])
    ).stdout.trim();
    const bytes = await readFile(saved);
    assert.equal(sha256Hex(bytes), index.artifacts[0].artifact.sha256);
  } finally {
    await new Promise((resolvePromise) => server.close(resolvePromise));
    await rm(src, { recursive: true, force: true });
    await rm(dist, { recursive: true, force: true });
    await rm(out, { recursive: true, force: true });
  }
});

test("upload prints zips before indexes and refuses loopback urls", async () => {
  const dist = await mkdtemp(path.join(tmpdir(), "artifact-cdn-upload-"));
  const local = await mkdtemp(path.join(tmpdir(), "artifact-cdn-loop-"));
  try {
    await run("pack.mjs", [
      "--root",
      packagesRoot,
      "--kind",
      "weizhi_package",
      "--base-url",
      "https://agentone.guoyoutech.cn/",
      "--out",
      dist,
    ]);
    const dry = await runAllowFail("bash", [path.join(artifactCdn, "upload.sh")], {
      ARTIFACT_CDN_SKIP_LOCAL_ENV: "1",
      ARTIFACT_CDN_DIST: dist,
      ARTIFACT_CDN_BASE_URL: "https://agentone.guoyoutech.cn/",
      UPLOAD_DRY_RUN: "1",
    });
    assert.equal(dry.code, 0, dry.stderr);
    const lines = dry.stdout.trim().split("\n").filter((line) => line.startsWith("scp "));
    const firstIndex = lines.findIndex((line) => line.includes("stable.json"));
    assert.ok(firstIndex > 0);
    assert.ok(lines.slice(0, firstIndex).every((line) => line.includes(".zip")));
    assert.match(lines[firstIndex], /registry\/stable\.json/);
    assert.match(lines[lines.length - 1], /packages\/registry\/stable\.json/);
    assert.match(lines[0], /root@49\.232\.7\.38:\/var\/www\/agentone\/packages\//);

    await run("pack.mjs", [
      "--root",
      packagesRoot,
      "--kind",
      "weizhi_package",
      "--base-url",
      "http://127.0.0.1:9/",
      "--out",
      local,
    ]);
    const refused = await runAllowFail("bash", [path.join(artifactCdn, "upload.sh")], {
      ARTIFACT_CDN_SKIP_LOCAL_ENV: "1",
      ARTIFACT_CDN_DIST: local,
      ARTIFACT_CDN_BASE_URL: "http://127.0.0.1:9/",
      UPLOAD_DRY_RUN: "1",
    });
    assert.notEqual(refused.code, 0);
    assert.match(refused.stderr, /refuse loopback url/);
    assert.doesNotMatch(refused.stdout, /scp /);
  } finally {
    await rm(dist, { recursive: true, force: true });
    await rm(local, { recursive: true, force: true });
  }
});

test("web lib cards pack into their own registry and upload ships it last", async () => {
  const dist = await mkdtemp(path.join(tmpdir(), "artifact-cdn-web-libs-"));
  const badCards = await mkdtemp(path.join(tmpdir(), "artifact-cdn-web-libs-bad-"));
  try {
    await run("pack.mjs", [
      "--root",
      packagesRoot,
      "--kind",
      "weizhi_package",
      "--base-url",
      "https://agentone.guoyoutech.cn/",
      "--out",
      dist,
    ]);
    const { stdout } = await run("pack-web-libs.mjs", ["--out", dist]);
    assert.match(stdout, /web-libs\/registry\/stable\.json cards=\d+/);

    const registry = JSON.parse(
      await readFile(path.join(dist, "web-libs", "registry", "stable.json"), "utf8"),
    );
    assert.equal(registry.schema_version, 1);
    assert.equal(registry.channel, "stable");
    assert.ok(registry.cards.length > 0);
    for (const card of registry.cards) {
      assert.equal(card.kind, "web_lib");
      assert.match(card.id, /^web_lib\./);
      assert.match(card.entry, /https:\/\//);
      assert.match(card.entry, /^URL https:\/\//m);
      assert.match(card.entry, /\n壳 https:\/\/agentone\.guoyoutech\.cn\/web-libs\/templates\//);
      assert.doesNotMatch(card.entry, /<!DOCTYPE|<html/i);
    }
    const templateCount = (
      await readdir(path.join(dist, "web-libs", "templates"), { withFileTypes: true })
    ).filter((e) => e.isFile() && e.name.endsWith(".html")).length;
    assert.equal(templateCount, registry.cards.length);

    const dry = await runAllowFail("bash", [path.join(artifactCdn, "upload.sh")], {
      ARTIFACT_CDN_SKIP_LOCAL_ENV: "1",
      ARTIFACT_CDN_DIST: dist,
      ARTIFACT_CDN_BASE_URL: "https://agentone.guoyoutech.cn/",
      UPLOAD_DRY_RUN: "1",
    });
    assert.equal(dry.code, 0, dry.stderr);
    const lines = dry.stdout.trim().split("\n").filter((line) => line.startsWith("scp "));
    assert.ok(lines.some((line) => /web-libs\/registry\/stable\.json/.test(line)));
    assert.ok(lines.some((line) => /web-libs\/templates\/.*\.html/.test(line)));

    const bad = path.join(badCards, "cards.jsonl");
    await writeFile(
      bad,
      `${JSON.stringify({ id: "docx.markdownToDocx", kind: "weizhi_package", entry: "import" })}\n`,
    );
    const kindFail = await runAllowFail(
      process.execPath,
      [path.join(artifactCdn, "pack-web-libs.mjs"), "--cards", bad, "--out", badCards],
    );
    assert.notEqual(kindFail.code, 0);
    assert.match(kindFail.stderr, /no web_lib cards/);

    await writeFile(
      bad,
      `${JSON.stringify({
        id: "web_lib.mermaid",
        kind: "web_lib",
        lib_url: "http://cdn.example/mermaid.js",
        template: "mermaid.html",
      })}\n`,
    );
    const httpFail = await runAllowFail(
      process.execPath,
      [path.join(artifactCdn, "pack-web-libs.mjs"), "--cards", bad, "--out", badCards],
    );
    assert.notEqual(httpFail.code, 0);
    assert.match(httpFail.stderr, /lib_url must be https/);
  } finally {
    await rm(dist, { recursive: true, force: true });
    await rm(badCards, { recursive: true, force: true });
  }
});
