import { execFile } from "node:child_process";
import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import test from "node:test";
import assert from "node:assert/strict";

const execFileAsync = promisify(execFile);
const here = path.dirname(fileURLToPath(import.meta.url));
const artifactCdn = path.resolve(here, "..");

function run(env) {
  return execFileAsync("bash", [path.join(artifactCdn, "upload-host-web.sh")], {
    cwd: artifactCdn,
    env: { ...process.env, ARTIFACT_CDN_SKIP_LOCAL_ENV: "1", ...env },
  }).then(
    (result) => ({ code: 0, ...result }),
    (error) => ({ code: error.code ?? 1, stdout: error.stdout ?? "", stderr: error.stderr ?? "" }),
  );
}

test("upload-host-web dry-run copies only host-web and refuses loopback", async () => {
  const dist = await mkdtemp(path.join(tmpdir(), "host-web-upload-"));
  try {
    const registryDir = path.join(dist, "host-web", "registry");
    await mkdir(registryDir, { recursive: true });
    await writeFile(
      path.join(registryDir, "stable.json"),
      `${JSON.stringify({
        schema_version: 1,
        docx_preview: { shell: "https://cdn.example.test/host-web/shell/docx-preview/1/shell.html?v=1" },
      })}\n`,
    );
    await writeFile(path.join(dist, "host-web", "shell.txt"), "ok");

    const dry = await run({
      ARTIFACT_CDN_DIST: dist,
      ARTIFACT_CDN_BASE_URL: "https://cdn.example.test/",
      ARTIFACT_CDN_SSH: "user@cdn.example.test",
      ARTIFACT_CDN_REMOTE_DIR: "/var/www/agentone",
      UPLOAD_DRY_RUN: "1",
    });
    assert.equal(dry.code, 0, dry.stderr);
    const lines = dry.stdout.trim().split("\n").filter((line) => line.startsWith("scp "));
    assert.equal(lines.length, 2);
    assert.ok(lines.every((line) => line.includes("user@cdn.example.test:/var/www/agentone/host-web/")));
    assert.ok(lines.some((line) => line.endsWith("/host-web/registry/stable.json")));

    await writeFile(
      path.join(registryDir, "stable.json"),
      `${JSON.stringify({
        docx_preview: { shell: "http://127.0.0.1:9/host-web/shell.html" },
      })}\n`,
    );
    const refused = await run({
      ARTIFACT_CDN_DIST: dist,
      ARTIFACT_CDN_BASE_URL: "http://127.0.0.1:9/",
      ARTIFACT_CDN_SSH: "user@cdn.example.test",
      ARTIFACT_CDN_REMOTE_DIR: "/var/www/agentone",
      UPLOAD_DRY_RUN: "1",
    });
    assert.notEqual(refused.code, 0);
    assert.match(refused.stderr, /refuse loopback url/);
    assert.doesNotMatch(refused.stdout, /scp /);
  } finally {
    await rm(dist, { recursive: true, force: true });
  }
});
