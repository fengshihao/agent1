import type { MessageDto, ToolCallDto } from "./api";

export type AskQuestion = {
  id: string;
  prompt: string;
  options?: string[];
  allowFreeText?: boolean;
};

export type AskRequest = {
  questions: AskQuestion[];
};

const TOOL_NAME = "ask_user";
const PAUSE_MARKERS = ["等待用户", "waiting", "ask_user"];

export function parseAskRequest(call: ToolCallDto): AskRequest | null {
  try {
    const raw = JSON.parse(call.argumentsJson || "{}") as {
      questions?: Array<{
        id?: string;
        prompt?: string;
        question?: string;
        options?: string[];
        allowFreeText?: boolean;
        allow_free_text?: boolean;
      }>;
    };
    const questions = (raw.questions || [])
      .map((q, i) => ({
        id: q.id || `q${i}`,
        prompt: q.prompt || q.question || "",
        options: q.options,
        allowFreeText: q.allowFreeText ?? q.allow_free_text ?? true,
      }))
      .filter((q) => q.prompt);
    if (questions.length === 0) return null;
    return { questions };
  } catch {
    return null;
  }
}

export function detectPendingAsk(messages: MessageDto[]): AskRequest | null {
  if (!messages.length) return null;
  for (let i = messages.length - 1; i >= 0; i--) {
    const msg = messages[i];
    if (msg.role === "user") return null;
    if (msg.role === "assistant") {
      for (const call of msg.toolCalls || []) {
        if (call.name !== TOOL_NAME) continue;
        return parseAskRequest(call);
      }
    }
    if (msg.role === "toolResult") {
      const text = msg.content || "";
      const isPause = PAUSE_MARKERS.some((m) => text.toLowerCase().includes(m.toLowerCase()));
      if (!isPause) return null;
    }
  }
  return null;
}

export function formatAskReply(
  request: AskRequest,
  answers: Record<string, string>,
): string {
  const lines = request.questions.map((q) => {
    const ans = (answers[q.id] || "").trim();
    return `${q.prompt}\n→ ${ans || "(未答)"}`;
  });
  return lines.join("\n\n");
}
