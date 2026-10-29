/** 清单源里的 CDN 占位，打包时替换为 --base-url / AGENT1_CDN_BASE_URL。 */
export const CDN_PLACEHOLDER = "{CDN}";

/** 单测用的假主机，不指向生产。 */
export const TEST_CDN_BASE_URL = "https://cdn.example.test/";

export function normalizeBaseUrl(raw) {
  const text = String(raw ?? "").trim();
  if (!text) {
    throw new Error("CDN base url is empty");
  }
  return text.endsWith("/") ? text : `${text}/`;
}

export function resolveCdnBaseUrl(explicit) {
  const raw =
    explicit ||
    process.env.ARTIFACT_CDN_BASE_URL ||
    process.env.AGENT1_CDN_BASE_URL ||
    "";
  if (!String(raw).trim()) {
    throw new Error("set --base-url or AGENT1_CDN_BASE_URL / ARTIFACT_CDN_BASE_URL");
  }
  return normalizeBaseUrl(raw);
}

export function applyCdnPlaceholder(text, baseUrl) {
  const origin = normalizeBaseUrl(baseUrl).replace(/\/$/, "");
  return String(text).replaceAll(CDN_PLACEHOLDER, origin);
}
