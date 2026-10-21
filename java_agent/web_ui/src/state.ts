import type { AskRequest } from "./askUser";
import type {
  AboutDto,
  AttachmentDto,
  ArtifactDto,
  ConfigDto,
  FailedToolDto,
  McpServerDto,
  MessageDto,
  CapabilityHitDto,
  ModelOptionDto,
  RegisteredToolDto,
  SessionMeta,
  UsageDto,
} from "./api";
import type { RunTokenSummary } from "./runTokenSummary";

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
  usage: UsageDto | null;
  usagePanelOpen: boolean;
  toast: string | null;
  attachments: AttachmentDto[];
  artifacts: ArtifactDto[];
  showArtifacts: boolean;
  preview: { sessionId: string; path: string } | null;
  showCapabilities: boolean;
  capQuery: string;
  capSelectedKinds: string[];
  capLimit: number;
  capBusy: boolean;
  capError: string | null;
  capSearched: boolean;
  capModelText: string;
  capHostPlatform: string;
  capHiddenHits: CapabilityHitDto[];
  capDisabledMcp: Array<{ name: string; url: string; description: string }>;
  modelOptions: ModelOptionDto[];
  settingsDraft: SettingsDraft | null;
  modelsLoading: boolean;
  modelsStatus: string | null;
  showTools: boolean;
  toolsList: Array<{ name: string; description: string }>;
  lastRunTokenSummary: RunTokenSummary | null;
  showMcp: boolean;
  mcpServers: McpServerDto[];
  mcpStatus: string | null;
  showFailedLogs: boolean;
  failedLogs: FailedToolDto[];
  failedLogsScopeSession: boolean;
  moreMenuOpen: boolean;
  showAbout: boolean;
  aboutLoading: boolean;
  aboutInfo: AboutDto | null;
  showSystemPrompt: boolean;
  systemPromptBusy: boolean;
  systemPromptError: string | null;
  systemPromptText: string;
  systemPromptTools: RegisteredToolDto[];
  mcpWebSearchBaseUrl: string;
  mcpWebSearchApiKey: string;
};

export type SettingsDraft = {
  providerId: string;
  baseUrl: string;
  model: string;
  maxContextTurns: number;
  maxContextMessages: number;
  maxTurnsPerRun: number;
  maxToolCallsPerRun: number;
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
    usage: null,
    usagePanelOpen: false,
    toast: null,
    attachments: [],
    artifacts: [],
    showArtifacts: false,
    preview: null,
    showCapabilities: false,
    capQuery: "",
    capSelectedKinds: [],
    capLimit: 8,
    capBusy: false,
    capError: null,
    capSearched: false,
    capModelText: "",
    capHostPlatform: "",
    capHiddenHits: [],
    capDisabledMcp: [],
    modelOptions: [],
    settingsDraft: null,
    modelsLoading: false,
    modelsStatus: null,
    showTools: false,
    toolsList: [],
    lastRunTokenSummary: null,
    showMcp: false,
    mcpServers: [],
    mcpStatus: null,
    showFailedLogs: false,
    failedLogs: [],
    failedLogsScopeSession: true,
    moreMenuOpen: false,
    showAbout: false,
    aboutLoading: false,
    aboutInfo: null,
    showSystemPrompt: false,
    systemPromptBusy: false,
    systemPromptError: null,
    systemPromptText: "",
    systemPromptTools: [],
    mcpWebSearchBaseUrl: "https://api.tavily.com",
    mcpWebSearchApiKey: "",
  };
}

export function isPreviewablePath(relativePath: string): boolean {
  const lower = relativePath.toLowerCase();
  return lower.endsWith(".html") || lower.endsWith(".htm");
}

/** 当前会话尚无可见对话（对齐 Android isFreshSession）。 */
export function isFreshSession(state: {
  lines: ChatLine[];
  isRunning: boolean;
  streamingText: string;
  streamingReasoning: string;
  tools: unknown[];
  pendingAsk: AskRequest | null;
}): boolean {
  if (state.isRunning) return false;
  if (state.streamingText.trim() || state.streamingReasoning.trim()) return false;
  if (state.tools.length > 0 || state.pendingAsk) return false;
  return !state.lines.some((l) => l.role === "user" || l.role === "assistant");
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
