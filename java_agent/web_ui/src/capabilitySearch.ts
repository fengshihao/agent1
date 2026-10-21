/** 与 Android {@code CapabilitySearchViewModel.kindChoices} 一致。 */
export const CAP_KIND_CHOICES: Array<{ id: string; label: string }> = [
  { id: "caps", label: "Caps" },
  { id: "builtin", label: "内置" },
  { id: "weizhi_package", label: "官方包" },
  { id: "catalog_script", label: "catalog 脚本" },
  { id: "catalog_lib", label: "catalog 库" },
  { id: "native", label: "native" },
  { id: "skill", label: "Skill" },
  { id: "mcp", label: "MCP" },
  { id: "bridge_tool", label: "桥接" },
  { id: "agent_tool", label: "外层工具" },
];

export const CAP_DEFAULT_LIMIT = 8;
export const CAP_MAX_LIMIT = 20;

export function availabilityLabel(availability: string): string {
  if (availability === "listed_but_unusable") return "已索引，当前不可用";
  if (availability === "platform_hidden") return "当前平台不可用";
  return "可用";
}
