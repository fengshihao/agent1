import type { AskRequest } from "./askUser";
import type { ConfigDto, MessageDto, SessionMeta } from "./api";

export type ChatLine = {
  id: string;
  role: "user" | "assistant" | "system" | "tool";
  content: string;
  toolName?: string;
  error?: boolean;
};

export type ToolPart = {
  id: string;
  toolCallId: string;
  toolName: string;
  argsPreview: string;
  resultSummary?: string;
  error?: boolean;
  running: boolean;
};

export type AppState = {
  sessions: SessionMeta[];
  activeSessionId: string | null;
  config: ConfigDto | null;
  lines: ChatLine[];
  streamingText: string;
  streamingReasoning: string;
  tools: ToolPart[];
  isRunning: boolean;
  pendingAsk: AskRequest | null;
  askAnswers: Record<string, string>;
  error: string | null;
  showSettings: boolean;
  drawerOpen: boolean;
  composer: string;
};

export function createInitialState(): AppState {
  return {
    sessions: [],
    activeSessionId: null,
    config: null,
    lines: [],
    streamingText: "",
    streamingReasoning: "",
    tools: [],
    isRunning: false,
    pendingAsk: null,
    askAnswers: {},
    error: null,
    showSettings: false,
    drawerOpen: true,
    composer: "",
  };
}

export function messagesToLines(messages: MessageDto[]): ChatLine[] {
  const lines: ChatLine[] = [];
  messages.forEach((msg, index) => {
    if (msg.role === "user") {
      lines.push({ id: `u-${index}`, role: "user", content: msg.content });
      return;
    }
    if (msg.role === "assistant") {
      if (msg.content?.trim()) {
        lines.push({ id: `a-${index}`, role: "assistant", content: msg.content });
      }
      for (const call of msg.toolCalls || []) {
        lines.push({
          id: `t-${call.id}`,
          role: "tool",
          content: call.argsPreview || call.argumentsJson || "",
          toolName: call.name,
        });
      }
      return;
    }
    if (msg.role === "toolResult") {
      lines.push({
        id: `tr-${index}`,
        role: "tool",
        content: msg.content,
        toolName: "result",
        error: !!msg.error,
      });
      return;
    }
    if (msg.role === "system") {
      // 系统提示过长，UI 不展示全文
      return;
    }
  });
  return lines;
}
