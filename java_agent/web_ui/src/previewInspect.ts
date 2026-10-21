/** 与 {@code HtmlInspectScript.POST_MESSAGE_TYPE} 对齐 */
export const INSPECT_MESSAGE_TYPE = "agent1-inspect";

export const INSPECT_CMD_MESSAGE_TYPE = "agent1-inspect-cmd";

export type InspectCmd = "install" | "enable" | "disable" | "clear";

export function postInspectCmd(iframe: HTMLIFrameElement | null, cmd: InspectCmd): void {
  if (!iframe?.contentWindow) return;
  iframe.contentWindow.postMessage({ type: INSPECT_CMD_MESSAGE_TYPE, cmd }, window.location.origin);
}

export function parseInspectPayload(raw: unknown): string | null {
  if (typeof raw !== "string" || !raw.trim()) return null;
  return raw;
}
