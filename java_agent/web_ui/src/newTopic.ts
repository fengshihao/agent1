import type { ChatLine } from "./state";

/** 对齐 Android ChatNewTopicPrompt：最新一条为助手回复且未在跑时展示「新话题」。 */
export function shouldOfferNewTopic(
  lines: ChatLine[],
  isRunning: boolean,
  streamingText: string,
  streamingReasoning: string,
  pendingAsk: boolean,
): boolean {
  if (isRunning || pendingAsk) return false;
  if (streamingText.trim() || streamingReasoning.trim()) return false;
  const last = conversationTail(lines);
  return last?.role === "assistant";
}

function conversationTail(lines: ChatLine[]): ChatLine | undefined {
  return lines
    .filter((l) => l.role === "user" || l.role === "assistant")
    .at(-1);
}
