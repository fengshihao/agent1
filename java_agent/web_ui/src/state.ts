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

export type TodoItem = {
  id: string;
  content: string;
  status: string;
};

export type AppState = {
  sessions: SessionMeta[];
  activeSessionId: string | null;
  config: ConfigDto | null;
  lines: ChatLine[];
  streamingText: string;
  streamingReasoning: string;
  tools: ToolPart[];
  sessionTodos: TodoItem[];
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
  previewReloadKey: number;
  previewIsDeck: boolean;
  previewInspectEnabled: boolean;
  previewSelectedElementJson: string | null;
  previewOpinion: string;
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
  showToolTracePanel: boolean;
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
  pendingDeleteSessionId: string | null;
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
    sessionTodos: [],
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
    previewReloadKey: 0,
    previewIsDeck: false,
    previewInspectEnabled: false,
    previewSelectedElementJson: null,
    previewOpinion: "",
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
    showToolTracePanel: false,
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
    pendingDeleteSessionId: null,
  };
}

function fileExt(relativePath: string): string {
  const name = relativePath.split("/").pop() ?? relativePath;
  const dot = name.lastIndexOf(".");
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : "";
}

/** HTML 走审查预览壳；图片/SVG 用文件直出即可在浏览器里显示。 */
export function isHtmlPreviewPath(relativePath: string): boolean {
  const ext = fileExt(relativePath);
  return ext === "html" || ext === "htm";
}

const PREVIEWABLE_EXT = new Set(["html", "htm", "svg", "png", "jpg", "jpeg", "gif", "webp"]);

export function isPreviewablePath(relativePath: string): boolean {
  return PREVIEWABLE_EXT.has(fileExt(relativePath));
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

const PROGRESS_TURN_MARK = "往返轮次已达上限";

function isContinuableStop(content: string): boolean {
  return (
    content.includes("单轮模型与工具往返超过上限") ||
    content.includes("单轮工具调用次数超过上限") ||
    content.includes("工具调用次数超过上限") ||
    content.includes("insufficient tool messages")
  );
}

/** Run 失败记录（AgentRuntime 本地合成，非模型输出）：以「错误: 」开头。 */
function isRunFailureRecord(content: string): boolean {
  return content.trim().startsWith("错误:");
}

/**
 * 轮次或工具次数用尽、上一轮异常收场（没有最终回答）时，输入框上方显示「继续」。
 * 与 Android 端 ContinueAfterBudget 语义对齐。
 */
export function shouldOfferContinue(
  lines: ChatLine[],
  isRunning: boolean,
  pendingAsk: boolean,
): boolean {
  if (isRunning || pendingAsk) return false;
  let marker = -1;
  lines.forEach((line, index) => {
    const progressPrompt = line.role === "user" && line.content.includes(PROGRESS_TURN_MARK);
    const failureRecord = line.role === "assistant" && isRunFailureRecord(line.content);
    if (progressPrompt || failureRecord || isContinuableStop(line.content)) marker = index;
  });
  if (marker < 0) return false;
  let sawUserAfter = false;
  for (let index = marker + 1; index < lines.length; index += 1) {
    const line = lines[index];
    if (line.role === "user" && !line.content.includes(PROGRESS_TURN_MARK)) sawUserAfter = true;
    if (sawUserAfter && line.role === "assistant" && line.content.trim() && !isContinuableStop(line.content)) {
      return false;
    }
  }
  return true;
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
