export type SessionMeta = {
  sessionId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
};

export type ToolCallDto = {
  id: string;
  name: string;
  argumentsJson: string;
  argsPreview?: string;
};

export type MessageDto = {
  role: string;
  content: string;
  reasoningContent?: string;
  timestampMs: number;
  toolCallId?: string;
  error?: boolean;
  toolCalls?: ToolCallDto[];
};

export type UsageDto = {
  inputTokens: number;
  outputTokens: number;
  cachedTokens: number;
  usageEvents: number;
  totalTokens: number;
  hasUsage: boolean;
  cacheHitPercent?: number;
  byModel: Array<{
    modelId: string;
    inputTokens: number;
    outputTokens: number;
    cachedTokens: number;
  }>;
};

export type ConfigDto = {
  model: string;
  baseUrl: string;
  hasApiKey: boolean;
  apiKeyHint?: string;
  configurationError?: string;
};

export type AgentEventDto = {
  type: string;
  timestampMs: number;
  sessionId?: string;
  runId?: string;
  payload: Record<string, unknown>;
};

async function parseJson<T>(res: Response): Promise<T> {
  const data = (await res.json()) as T & { error?: string };
  if (!res.ok) {
    throw new Error((data as { error?: string }).error || res.statusText);
  }
  return data;
}

export async function fetchHealth(): Promise<{ ok: boolean; runInProgress: boolean }> {
  return parseJson(await fetch("/api/health"));
}

export async function fetchConfig(): Promise<ConfigDto> {
  return parseJson(await fetch("/api/config"));
}

export async function saveConfig(body: {
  apiKey?: string;
  baseUrl?: string;
  model?: string;
}): Promise<ConfigDto> {
  return parseJson(
    await fetch("/api/config", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    }),
  );
}

export async function fetchUsage(): Promise<UsageDto> {
  return parseJson(await fetch("/api/usage"));
}

export async function fetchSessions(): Promise<{
  sessions: SessionMeta[];
  activeSessionId: string | null;
  runInProgress: boolean;
}> {
  return parseJson(await fetch("/api/sessions"));
}

export async function createSession(): Promise<SessionMeta> {
  return parseJson(
    await fetch("/api/sessions", { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" }),
  );
}

export async function useSession(sessionId: string): Promise<void> {
  await parseJson(
    await fetch(`/api/sessions/${encodeURIComponent(sessionId)}/use`, { method: "POST", body: "{}" }),
  );
}

export async function deleteSession(sessionId: string): Promise<void> {
  await parseJson(
    await fetch(`/api/sessions/${encodeURIComponent(sessionId)}`, { method: "DELETE" }),
  );
}

export async function fetchTranscript(sessionId: string): Promise<MessageDto[]> {
  const data = await parseJson<{ messages: MessageDto[] }>(
    await fetch(`/api/sessions/${encodeURIComponent(sessionId)}/transcript`),
  );
  return data.messages;
}

export async function sendMessage(sessionId: string, text: string): Promise<void> {
  await parseJson(
    await fetch(`/api/sessions/${encodeURIComponent(sessionId)}/messages`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text }),
    }),
  );
}

export async function abortRun(): Promise<void> {
  await parseJson(await fetch("/api/run/abort", { method: "POST", body: "{}" }));
}

export function connectEvents(onEvent: (event: AgentEventDto) => void): () => void {
  const source = new EventSource("/api/events");
  source.onmessage = (ev) => {
    try {
      onEvent(JSON.parse(ev.data) as AgentEventDto);
    } catch {
      // ignore malformed
    }
  };
  return () => source.close();
}
