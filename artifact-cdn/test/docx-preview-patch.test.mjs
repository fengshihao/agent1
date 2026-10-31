import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import assert from "node:assert/strict";

import { patchDocxPreviewBundle } from "../lib/patch-docx-preview.mjs";

const dist = path.join(path.dirname(fileURLToPath(import.meta.url)), "../node_modules/docx-preview/dist");

test("patched docx-preview keeps horizontal lrTb and a default border width", async () => {
  for (const name of ["docx-preview.js", "docx-preview.min.js"]) {
    const source = await readFile(path.join(dist, name), "utf8");
    const patched = patchDocxPreviewBundle(source);
    assert.equal(patched.includes("vertical-lr"), false, name);
    assert.match(patched, /horizontal-tb/);
    assert.match(patched, /0\.50pt/);
    assert.match(patched, /insideH/);
    assert.match(patched, /insideV/);
  }
});
