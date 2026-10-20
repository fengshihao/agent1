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
  providerId?: string;
  maxContextTurns?: number;
  maxContextMessages?: number;
  maxTurnsPerRun?: number;
  maxToolCallsPerRun?: number;
};

export type ModelOptionDto = {
  modelId: string;
  title: string;
  subtitle: string;
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
  providerId?: string;
  maxContextTurns?: number;
  maxContextMessages?: number;
  maxTurnsPerRun?: number;
  maxToolCallsPerRun?: number;
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

export async function fetchModels(
  provider: string,
  refresh = false,
): Promise<{ models: ModelOptionDto[]; source: string }> {
  const q = new URLSearchParams({ provider });
  if (refresh) q.set("refresh", "1");
  return parseJson(await fetch(`/api/models?${q}`));
}

export async function rolloverSession(summarize = true): Promise<{
  session: SessionMeta;
  previousSessionId?: string;
  summaryPath?: string;
}> {
  return parseJson(
    await fetch("/api/sessions/rollover", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ summarize }),
    }),
  );
}

export type AttachmentDto = {
  workspaceRelativePath: string;
  fileName: string;
  sizeBytes: number;
};

export type ArtifactDto = {
  sessionId: string;
  workspaceRelativePath: string;
  fileName: string;
  sizeBytes: number;
  lastModifiedMillis: number;
};

export async function fetchArtifacts(): Promise<ArtifactDto[]> {
  const data = await parseJson<{ artifacts: ArtifactDto[] }>(await fetch("/api/artifacts"));
  return data.artifacts;
}

export async function importArtifactToSession(
  sessionId: string,
  sourceSessionId: string,
  workspaceRelativePath: string,
): Promise<AttachmentDto> {
  return parseJson(
    await fetch(`/api/sessions/${encodeURIComponent(sessionId)}/artifacts/import`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sourceSessionId, workspaceRelativePath }),
    }),
  );
}

export async function fetchAttachments(sessionId: string): Promise<AttachmentDto[]> {
  const data = await parseJson<{ files: AttachmentDto[] }>(
    await fetch(`/api/sessions/${encodeURIComponent(sessionId)}/attachments`),
  );
  return data.files;
}

export async function uploadAttachment(sessionId: string, file: File): Promise<AttachmentDto> {
  const fd = new FormData();
  fd.append("file", file);
  return parseJson(
    await fetch(`/api/sessions/${encodeURIComponent(sessionId)}/attachments`, {
      method: "POST",
      body: fd,
    }),
  );
}

export async function deleteAttachment(sessionId: string, workspaceRelativePath: string): Promise<void> {
  await parseJson(
    await fetch(
      `/api/sessions/${encodeURIComponent(sessionId)}/attachments/${encodeURIComponent(workspaceRelativePath)}`,
      { method: "DELETE" },
    ),
  );
}

export function workspaceFileUrl(sessionId: string, workspaceRelativePath: string): string {
  const path = workspaceRelativePath
    .split("/")
    .map((p) => encodeURIComponent(p))
    .join("/");
  return `/api/sessions/${encodeURIComponent(sessionId)}/files/${path}`;
}

export async function searchCapabilities(query: string): Promise<string> {
  const data = await parseJson<{ text: string }>(
    await fetch(`/api/capabilities?q=${encodeURIComponent(query)}`),
  );
  return data.text;
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
