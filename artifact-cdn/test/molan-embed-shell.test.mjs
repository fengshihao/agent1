import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import assert from "node:assert/strict";

const here = path.dirname(fileURLToPath(import.meta.url));
const cdn = path.resolve(here, "..");
const repo = path.resolve(cdn, "..");

const SHELL_V = "20261027c";

test("embed shell keeps theme, type, and mode controls", async () => {
  const html = await readFile(path.join(cdn, "host-web/molan-embed/1/shell.html"), "utf8");
  assert.match(html, /id="modeBtn"/);
  assert.match(html, /id="typeBtn"/);
  assert.match(html, /id="headerPrefsBtn"/);
  assert.match(html, /id="themeSwitch"/);
  assert.match(html, /class="app agent1-embed"/);
});

test("embed css pins a two-row header on a phone width", async () => {
  const css = await readFile(path.join(cdn, "host-web/molan-embed/agent1-embed.css"), "utf8");
  assert.match(css, /\.app\.agent1-embed\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)/);
  assert.doesNotMatch(css, /\.agent1-embed \.app\s*\{/);
  assert.match(css, /@media \(max-width: 720px\)/);
  assert.match(css, /\.agent1-embed \.reader-header\s*\{[^}]*position:\s*sticky/);
  assert.match(css, /\.agent1-embed \.reader-actions\s*\{[^}]*flex-wrap:\s*wrap/);
  assert.match(css, /\.agent1-embed \.reader-actions\s*\{[^}]*flex:\s*1 0 100%/);
});

test("md preview shell cache buster matches the registry fallback", async () => {
  const pack = await readFile(path.join(cdn, "pack-molan-embed.mjs"), "utf8");
  const manifest = await readFile(path.join(cdn, "host-web/manifest.source.json"), "utf8");
  const java = await readFile(
    path.join(repo, "agent_core/src/main/java/com/agent1/javaagent/config/OfficialCdn.java"),
    "utf8",
  );
  assert.match(pack, new RegExp(`SHELL_VERSION_QUERY = "${SHELL_V}"`));
  assert.match(manifest, new RegExp(`shell\\.html\\?v=${SHELL_V}`));
  assert.match(java, new RegExp(`shell\\.html\\?v=${SHELL_V}`));
});
