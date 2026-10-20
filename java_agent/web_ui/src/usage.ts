import type { UsageDto } from "./api";

export function formatCompactTokenCount(value: number): string {
  if (value < 1_000) return String(value);
  const scaled = Math.round(value / 100) / 10;
  const whole = Math.trunc(scaled);
  return scaled === whole ? `${whole}k` : `${scaled.toFixed(1)}k`;
}

export function usageFooterLine(usage: UsageDto | null): { headline: string; detail: string } {
  if (!usage || !usage.hasUsage) {
    return { headline: "累计用量", detail: "暂无 usage 记录" };
  }
  const headline = `约 ${formatCompactTokenCount(usage.totalTokens)} tokens`;
  const hit = usage.cacheHitPercent;
  const detail =
    hit != null
      ? `输入 ${formatCompactTokenCount(usage.inputTokens)} · 缓存 ${hit}%`
      : `输入 ${formatCompactTokenCount(usage.inputTokens)} · 输出 ${formatCompactTokenCount(usage.outputTokens)}`;
  return { headline, detail };
}

function esc(text: string): string {
  return text
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

export function usageDetailHtml(usage: UsageDto): string {
  const rows = [
    `输入 ${usage.inputTokens.toLocaleString()}`,
    `输出 ${usage.outputTokens.toLocaleString()}`,
    `缓存 ${usage.cachedTokens.toLocaleString()}`,
    `usage 事件 ${usage.usageEvents}`,
  ];
  let models = "";
  if (usage.byModel.length) {
    models =
      "<ul>" +
      usage.byModel
        .map((m) => {
          const name = esc(m.modelId || "（未知模型）");
          return `<li><strong>${name}</strong>：入 ${m.inputTokens.toLocaleString()} / 出 ${m.outputTokens.toLocaleString()}</li>`;
        })
        .join("") +
      "</ul>";
  }
  return `<p>${rows.join(" · ")}</p>${models}`;
}
