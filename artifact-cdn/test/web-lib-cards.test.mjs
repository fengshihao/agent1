import { test } from "node:test";
import assert from "node:assert/strict";

import { buildWebLibEntry, normalizeWebLibCard } from "../lib/web-lib-cards.mjs";

test("normalize legacy fat entry and build slim registry entry", () => {
  const normalized = normalizeWebLibCard({
    id: "web_lib.mermaid",
    kind: "web_lib",
    entry:
      "URL https://cdn.jsdelivr.net/npm/mermaid@11.4.0/dist/mermaid.min.js\n"
      + "模板 shared/catalog/templates/mermaid.html\n"
      + "<!DOCTYPE html><html></html>",
  });
  assert.equal(normalized.lib_url, "https://cdn.jsdelivr.net/npm/mermaid@11.4.0/dist/mermaid.min.js");
  assert.equal(normalized.template, "mermaid.html");

  const entry = buildWebLibEntry(
    normalized.lib_url,
    normalized.template,
    "https://agentone.guoyoutech.cn/",
  );
  assert.match(entry, /^URL https:\/\/cdn\.jsdelivr\.net\//);
  assert.match(entry, /\n壳 https:\/\/agentone\.guoyoutech\.cn\/web-libs\/templates\/mermaid\.html$/);
  assert.doesNotMatch(entry, /路径 shared\/catalog\/templates\//);
  assert.doesNotMatch(entry, /<!DOCTYPE/);
});
