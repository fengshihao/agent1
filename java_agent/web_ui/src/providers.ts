/** 与 core {@code ModelProviderIds} / Android 设置页默认一致。 */
export type ProviderId = "dashscope" | "deepseek" | "zhipu_coding";

export type ProviderPreset = {
  id: ProviderId;
  label: string;
  baseUrl: string;
  defaultModel: string;
};

export const PROVIDER_PRESETS: ProviderPreset[] = [
  {
    id: "dashscope",
    label: "通义",
    baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    defaultModel: "qwen3.7-flash",
  },
  {
    id: "deepseek",
    label: "DeepSeek",
    baseUrl: "https://api.deepseek.com",
    defaultModel: "deepseek-flash",
  },
  {
    id: "zhipu_coding",
    label: "智谱 Coding",
    baseUrl: "https://open.bigmodel.cn/api/coding/paas/v4",
    defaultModel: "glm-5.3",
  },
];

export function inferProviderId(baseUrl: string): ProviderId {
  const u = baseUrl.toLowerCase();
  if (u.includes("deepseek.com")) return "deepseek";
  if (u.includes("bigmodel.cn")) return "zhipu_coding";
  return "dashscope";
}

export function presetFor(id: string): ProviderPreset {
  return PROVIDER_PRESETS.find((p) => p.id === id) ?? PROVIDER_PRESETS[0];
}
