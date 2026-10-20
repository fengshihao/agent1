export type RunTokenSummary = {
  inputTokens: number;
  outputTokens: number;
  cachedTokens: number;
  failed: boolean;
};

let runInput = 0;
let runOutput = 0;
let runCached = 0;

export function resetRunUsageAccumulators(): void {
  runInput = 0;
  runOutput = 0;
  runCached = 0;
}

export function accumulateRunUsage(payload: Record<string, unknown>): void {
  runInput += Number(payload.inputTokens) || 0;
  runOutput += Number(payload.outputTokens) || 0;
  const cached = payload.cachedTokens;
  if (cached != null) runCached += Number(cached) || 0;
}

export function buildRunTokenSummary(failed: boolean): RunTokenSummary | null {
  const hasUsage = runInput > 0 || runOutput > 0;
  if (!failed && !hasUsage) return null;
  return {
    inputTokens: runInput,
    outputTokens: runOutput,
    cachedTokens: runCached,
    failed,
  };
}

export function runTokenSummaryLine(summary: RunTokenSummary): string {
  const parts = [
    `输入 ${formatCompactTokenCount(summary.inputTokens)}`,
    `输出 ${formatCompactTokenCount(summary.outputTokens)}`,
  ];
  if (summary.inputTokens > 0) {
    const hit = Math.min(100, Math.round((summary.cachedTokens * 100) / summary.inputTokens));
    parts.push(`缓存命中 ${hit}%`);
  }
  if (summary.failed) parts.push("未完成");
  return parts.join(" · ");
}

export function formatCompactTokenCount(value: number): string {
  if (value < 1_000) return String(value);
  const scaled = Math.round(value / 100) / 10;
  const whole = Math.trunc(scaled);
  return scaled === whole ? `${whole}k` : `${scaled.toFixed(1)}k`;
}
