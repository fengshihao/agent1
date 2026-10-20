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

/** 将正文转为安全 HTML：转义 + Markdown 图片/工作区文件链接。 */
export function formatBubbleContentHtml(content: string, sessionId: string | null): string {
  if (!content) return "";
  const pattern = /!?\[([^\]]*)]\(([^)]+)\)/g;
  let html = "";
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(content)) !== null) {
    html += escapeHtml(content.slice(last, match.index));
    const alt = match[1];
    const dest = match[2].trim();
    const isImage = match[0].startsWith("!");
    if (sessionId && !dest.startsWith("http://") && !dest.startsWith("https://")) {
      const path = normalizeWorkspacePath(dest);
      if (isImage && isImagePath(path)) {
        html += `<figure class="bubble-image"><img src="${fileUrl(sessionId, path)}" alt="${escapeAttr(path)}" loading="lazy"/></figure>`;
      } else if (!isImage) {
        html += `<button type="button" class="file-link" data-file-path="${escapeAttr(path)}">${escapeHtml(alt || path)}</button>`;
      } else {
        html += escapeHtml(match[0]);
      }
    } else {
      html += escapeHtml(match[0]);
    }
    last = match.index + match[0].length;
  }
  html += escapeHtml(content.slice(last));
  return html;
}

function fileUrl(sessionId: string, path: string): string {
  const encoded = path
    .split("/")
    .map((s) => encodeURIComponent(s))
    .join("/");
  return `/api/sessions/${encodeURIComponent(sessionId)}/files/${encoded}`;
}

function escapeHtml(text: string): string {
  return text
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function escapeAttr(text: string): string {
  return escapeHtml(text).replaceAll("'", "&#39;");
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

/** 非 Markdown 语法的裸图片路径，补在正文后。 */
export function extraPlainImageHtml(content: string, sessionId: string): string {
  const mdPaths = new Set(extractMarkdownImagePaths(content));
  return imagePathsForBubble(content)
    .filter((p) => !mdPaths.has(p))
    .map(
      (p) =>
        `<figure class="bubble-image"><img src="${fileUrl(sessionId, p)}" alt="${escapeAttr(p)}" loading="lazy"/></figure>`,
    )
    .join("");
}
