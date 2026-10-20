const IMAGE_EXT = new Set(["png", "jpg", "jpeg", "webp", "gif"]);

export function isImagePath(path: string): boolean {
  const ext = path.split(".").pop()?.toLowerCase() ?? "";
  return IMAGE_EXT.has(ext);
}

/** 对齐 Android extractMarkdownImagePaths。 */
export function extractMarkdownImagePaths(content: string): string[] {
  const regex = /!\[[^\]]*]\(([^)]+)\)/g;
  const out: string[] = [];
  let match: RegExpExecArray | null;
  while ((match = regex.exec(content)) !== null) {
    const p = match[1].trim();
    if (p && !p.startsWith("http://") && !p.startsWith("https://")) {
      out.push(normalizeWorkspacePath(p));
    }
  }
  return out;
}

function normalizeWorkspacePath(path: string): string {
  return path.replace(/^\.\//, "").replace(/^workspace\//, "");
}

export function imagePathsForBubble(content: string): string[] {
  const fromMd = extractMarkdownImagePaths(content).filter(isImagePath);
  const seen = new Set<string>();
  const out: string[] = [];
  for (const p of fromMd) {
    if (!seen.has(p)) {
      seen.add(p);
      out.push(p);
    }
  }
  const plain = /(?:imports|out|tmp|staging)[/][\w./-]+\.(?:png|jpe?g|webp|gif)/gi;
  let m: RegExpExecArray | null;
  while ((m = plain.exec(content)) !== null) {
    const p = normalizeWorkspacePath(m[0]);
    if (isImagePath(p) && !seen.has(p)) {
      seen.add(p);
      out.push(p);
    }
  }
  return out;
}
