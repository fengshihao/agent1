import "./styles.css";
import * as api from "./api";
import { detectPendingAsk, formatAskReply } from "./askUser";
import { shouldOfferNewTopic } from "./newTopic";
import {
  AppState,
  ChatLine,
  createInitialState,
  isFreshSession,
  isHtmlPreviewPath,
  isPreviewablePath,
  messagesToLines,
  ToolPart,
} from "./state";
import { extraPlainImageHtml, formatBubbleContentHtml } from "./messageFormat";
import {
  accumulateRunUsage,
  buildRunTokenSummary,
  resetRunUsageAccumulators,
  runTokenSummaryLine,
} from "./runTokenSummary";
import { inferProviderId, presetFor, PROVIDER_PRESETS } from "./providers";
import type { SettingsDraft } from "./state";
import { usageDetailHtml, usageFooterLine } from "./usage";
import { availabilityLabel, CAP_KIND_CHOICES, CAP_MAX_LIMIT } from "./capabilitySearch";
import { INSPECT_MESSAGE_TYPE, postInspectCmd } from "./previewInspect";

const root = document.getElementById("app");
if (!root) {
  throw new Error("#app missing");
}

let state: AppState = createInitialState();
let disconnectEvents: (() => void) | null = null;
let mcpAutoSaveTimer: ReturnType<typeof setTimeout> | undefined;
let webSearchAutoSaveTimer: ReturnType<typeof setTimeout> | undefined;
let previewReloadTimer: ReturnType<typeof setTimeout> | undefined;
const overlayWasOpen: Record<string, boolean> = {};

function inspectElementTag(elementJson: string | null): string {
  if (!elementJson) return "";
  try {
    const parsed = JSON.parse(elementJson) as { tag?: string };
    return parsed.tag || "element";
  } catch {
    return "element";
  }
}

function openHtmlPreview(sessionId: string, path: string, extra?: Partial<AppState>) {
  setState({
    preview: { sessionId, path },
    previewReloadKey: 0,
    previewIsDeck: false,
    previewInspectEnabled: false,
    previewSelectedElementJson: null,
    previewOpinion: "",
    showArtifacts: false,
    ...extra,
  });
  if (!isHtmlPreviewPath(path)) return;
  void api.fetchPreviewMeta(sessionId, path).then(
    (meta) => {
      if (state.preview?.sessionId === sessionId && state.preview?.path === path) {
        setState({ previewIsDeck: meta.isDeck });
      }
    },
    () => {
      /* ignore */
    },
  );
}

function closeHtmlPreview() {
  const iframe = document.getElementById("preview-frame") as HTMLIFrameElement | null;
  if (state.previewInspectEnabled) {
    postInspectCmd(iframe, "disable");
  }
  setState({
    preview: null,
    previewInspectEnabled: false,
    previewSelectedElementJson: null,
    previewOpinion: "",
    previewIsDeck: false,
  });
}

function schedulePreviewReload() {
  if (!state.preview) return;
  if (previewReloadTimer) clearTimeout(previewReloadTimer);
  previewReloadTimer = setTimeout(() => {
    previewReloadTimer = undefined;
    if (!state.preview) return;
    const iframe = document.getElementById("preview-frame") as HTMLIFrameElement | null;
    const reEnable = state.previewInspectEnabled;
    setState({
      previewReloadKey: state.previewReloadKey + 1,
      previewSelectedElementJson: null,
      previewOpinion: "",
    });
    window.setTimeout(() => {
      postInspectCmd(iframe, "install");
      if (reEnable) postInspectCmd(iframe, "enable");
    }, 50);
  }, 300);
}

function wirePreviewIframe() {
  const iframe = document.getElementById("preview-frame") as HTMLIFrameElement | null;
  if (!iframe) return;
  iframe.addEventListener("load", () => {
    postInspectCmd(iframe, "install");
    if (state.previewInspectEnabled) {
      postInspectCmd(iframe, "enable");
    }
  });
}

/** 侧栏已打开时再次 render 不加 fade/slide，避免整页重绘时闪一下 */
function overlayEnterClass(key: string, open: boolean): string {
  const wasOpen = overlayWasOpen[key] ?? false;
  overlayWasOpen[key] = open;
  if (!open) return "";
  return wasOpen ? " overlay-no-enter" : "";
}

function anySideOverlayOpen(): boolean {
  return (
    state.showCapabilities ||
    state.showSettings ||
    state.showMcp ||
    state.showArtifacts ||
    state.showSystemPrompt ||
    state.showAbout ||
    state.showTools ||
    state.usagePanelOpen ||
    state.showFailedLogs
  );
}

function setCapabilitySearchBusy(busy: boolean) {
  const btn = document.getElementById("cap-search-btn") as HTMLButtonElement | null;
  if (btn) {
    btn.disabled = busy;
    btn.textContent = busy ? "…" : "搜索";
  }
  const down = document.getElementById("cap-limit-down") as HTMLButtonElement | null;
  const up = document.getElementById("cap-limit-up") as HTMLButtonElement | null;
  if (down) down.disabled = busy || state.capLimit <= 1;
  if (up) up.disabled = busy || state.capLimit >= CAP_MAX_LIMIT;
  document.querySelectorAll<HTMLButtonElement>(".cap-kind-chip").forEach((chip) => {
    chip.disabled = busy;
  });
}

function setState(patch: Partial<AppState>) {
  state = { ...state, ...patch };
  render();
}

function escapeHtml(text: string): string {
  return text
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function refreshSessions() {
  const data = await api.fetchSessions();
  setState({
    sessions: data.sessions.slice().sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)),
    activeSessionId: data.activeSessionId ?? state.activeSessionId,
    isRunning: data.runInProgress,
  });
}

async function refreshUsage() {
  try {
    const usage = await api.fetchUsage();
    setState({ usage });
  } catch {
    // 非阻塞
  }
}

async function refreshFailedLogs() {
  const sessionId =
    state.failedLogsScopeSession && state.activeSessionId ? state.activeSessionId : null;
  const items = await api.fetchFailedToolCalls(sessionId);
  setState({ failedLogs: items });
}

async function openFailedLogsPanel() {
  setState({ showFailedLogs: true });
  try {
    await refreshFailedLogs();
  } catch (e) {
    setState({ error: e instanceof Error ? e.message : String(e) });
  }
}

function showToast(message: string) {
  setState({ toast: message });
  window.setTimeout(() => {
    if (state.toast === message) setState({ toast: null });
  }, 2200);
}

async function startNewChat() {
  if (state.pendingDeleteSessionId) setState({ pendingDeleteSessionId: null });
  if (state.isRunning) {
    setState({ error: "请先停止当前 Run" });
    return;
  }
  if (state.activeSessionId && isFreshSession(state)) {
    showToast("已是新会话");
    return;
  }
  const created = await api.createSession();
  await refreshSessions();
  await loadActiveTranscript(created.sessionId);
  await refreshUsage();
  setState({ drawerOpen: false, pendingDeleteSessionId: null });
}

async function confirmPendingDelete() {
  const id = state.pendingDeleteSessionId;
  if (!id) return;
  const wasActive = state.activeSessionId === id;
  setState({ pendingDeleteSessionId: null });
  try {
    await api.deleteSession(id);
    await refreshSessions();
    if (!wasActive) return;
    if (state.sessions[0]) {
      await api.useSession(state.sessions[0].sessionId);
      await loadActiveTranscript(state.sessions[0].sessionId);
    } else {
      const created = await api.createSession();
      await refreshSessions();
      await loadActiveTranscript(created.sessionId);
    }
  } catch (e) {
    setState({ error: e instanceof Error ? e.message : String(e) });
  }
}

function settingsDraftFromConfig(): SettingsDraft {
  const cfg = state.config;
  const baseUrl = cfg?.baseUrl || "";
  const providerId = cfg?.providerId || inferProviderId(baseUrl);
  return {
    providerId,
    baseUrl,
    model: cfg?.model || presetFor(providerId).defaultModel,
    maxContextTurns: cfg?.maxContextTurns ?? 6,
    maxContextMessages: cfg?.maxContextMessages ?? 0,
    maxTurnsPerRun: cfg?.maxTurnsPerRun ?? 24,
    maxToolCallsPerRun: cfg?.maxToolCallsPerRun ?? 48,
  };
}

async function fetchModelsForDraft(draft: SettingsDraft, refresh: boolean) {
  setState({ modelsLoading: true });
  try {
    const { models, source } = await api.fetchModels(draft.providerId, refresh);
    let model = draft.model;
    if (!models.some((m) => m.modelId === model)) {
      const preset = presetFor(draft.providerId);
      model = models.find((m) => m.modelId === preset.defaultModel)?.modelId
        ?? models[0]?.modelId
        ?? model;
    }
    setState({
      modelOptions: models,
      modelsLoading: false,
      settingsDraft: { ...draft, model },
      modelsStatus: refresh ? `已从网络更新 · ${models.length} 个模型` : `共 ${models.length} 个模型`,
    });
  } catch (e) {
    setState({
      modelsLoading: false,
      error: e instanceof Error ? e.message : "模型列表加载失败",
    });
  }
}

function onSettingsProviderChange(providerId: string) {
  const preset = presetFor(providerId);
  const draft: SettingsDraft = {
    ...(state.settingsDraft ?? settingsDraftFromConfig()),
    providerId,
    baseUrl: preset.baseUrl,
    model: preset.defaultModel,
  };
  setState({ settingsDraft: draft, error: null });
  void fetchModelsForDraft(draft, false);
}

async function openSettings() {
  const draft = settingsDraftFromConfig();
  setState({
    showSettings: true,
    settingsDraft: draft,
    modelOptions: [],
    error: null,
    moreMenuOpen: false,
    showMcp: false,
    showSystemPrompt: false,
  });
  await fetchModelsForDraft(draft, false);
}

async function openMcpPanel() {
  try {
    const [{ servers }, config] = await Promise.all([api.fetchMcpServers(), api.fetchConfig()]);
    setState({
      config,
      showMcp: true,
      mcpServers: servers,
      mcpStatus: null,
      showSettings: false,
      showSystemPrompt: false,
      moreMenuOpen: false,
      mcpWebSearchBaseUrl: config.webSearchBaseUrl || "https://api.tavily.com",
      mcpWebSearchApiKey: "",
    });
  } catch (e) {
    setState({ error: e instanceof Error ? e.message : String(e) });
  }
}

async function openSystemPrompt() {
  const sessionId = state.activeSessionId;
  if (!sessionId) {
    setState({
      showSystemPrompt: true,
      showSettings: false,
      showMcp: false,
      systemPromptBusy: false,
      systemPromptError: "先打开或新建一个会话，再查看发给模型的系统提示词。",
      systemPromptText: "",
      systemPromptTools: [],
      moreMenuOpen: false,
    });
    return;
  }
  setState({
    showSystemPrompt: true,
    showSettings: false,
    showMcp: false,
    systemPromptBusy: true,
    systemPromptError: null,
    moreMenuOpen: false,
  });
  try {
    const snap = await api.fetchSystemPrompt(sessionId);
    setState({
      showSystemPrompt: true,
      systemPromptBusy: false,
      systemPromptError: null,
      systemPromptText: snap.prompt || "",
      systemPromptTools: snap.tools || [],
    });
  } catch (e) {
    setState({
      showSystemPrompt: true,
      systemPromptBusy: false,
      systemPromptError: e instanceof Error ? e.message : String(e),
    });
  }
}

function collectMcpServersFromDom(): api.McpServerDto[] {
  const cards = document.querySelectorAll<HTMLElement>(".mcp-card");
  const servers: api.McpServerDto[] = [];
  cards.forEach((card, i) => {
    const existing = state.mcpServers[i];
    const name = card.querySelector<HTMLInputElement>(".mcp-name")?.value.trim() || "";
    const url = card.querySelector<HTMLInputElement>(".mcp-url")?.value.trim() || "";
    const enabled = card.querySelector<HTMLInputElement>(".mcp-enabled")?.checked ?? true;
    let headers: Record<string, string> = {};
    try {
      headers = JSON.parse(card.querySelector<HTMLInputElement>(".mcp-headers")?.value || "{}");
    } catch {
      // keep {}
    }
    servers.push({
      name,
      url,
      enabled,
      headers,
      toolCount: existing?.toolCount,
      lastListedAt: existing?.lastListedAt,
    });
  });
  return servers;
}

function scheduleMcpAutoSave() {
  if (!state.showMcp) return;
  if (mcpAutoSaveTimer) clearTimeout(mcpAutoSaveTimer);
  mcpAutoSaveTimer = setTimeout(() => void saveMcpFromDom(), 700);
}

function scheduleWebSearchAutoSave() {
  if (!state.showMcp) return;
  if (webSearchAutoSaveTimer) clearTimeout(webSearchAutoSaveTimer);
  webSearchAutoSaveTimer = setTimeout(() => void saveWebSearchFromMcp(), 700);
}

async function saveMcpFromDom() {
  if (!state.showMcp) return;
  const servers = collectMcpServersFromDom();
  try {
    const result = await api.saveMcpServers(servers);
    const { servers: reloaded } = await api.fetchMcpServers();
    const warn = result.warnings.length ? ` · ${result.warnings.join("; ")}` : "";
    setState({
      mcpServers: reloaded,
      mcpStatus: `已自动保存 · 索引 ${result.toolCount} 个 MCP 工具${warn}`,
    });
  } catch (e) {
    setState({ error: e instanceof Error ? e.message : String(e) });
  }
}

async function saveWebSearchFromMcp() {
  const keyEl = document.getElementById("mcp-web-key") as HTMLInputElement | null;
  const baseEl = document.getElementById("mcp-web-base") as HTMLInputElement | null;
  const webSearchApiKey = keyEl?.value?.trim() || undefined;
  const webSearchBaseUrl = baseEl?.value?.trim() || undefined;
  try {
    const config = await api.saveConfig({ webSearchApiKey, webSearchBaseUrl });
    setState({
      config,
      mcpWebSearchApiKey: keyEl?.value ?? "",
      mcpWebSearchBaseUrl: config.webSearchBaseUrl || webSearchBaseUrl || "https://api.tavily.com",
      mcpStatus: config.hasWebSearch
        ? "已自动保存 · 联网搜索已配置"
        : "已自动保存 · 尚未配置 Key（web_search 不会注册）",
    });
  } catch (e) {
    setState({ error: e instanceof Error ? e.message : String(e) });
  }
}

function openCapabilitySearch() {
  setState({
    showCapabilities: true,
    showArtifacts: false,
    moreMenuOpen: false,
    capError: null,
  });
}

function renderCapabilityResultsHtml(): string {
  if (!state.capSearched) return "";
  const platformNote = state.capHostPlatform
    ? `（平台 ${escapeHtml(state.capHostPlatform)}）`
    : "";
  const tail = [
    state.capHiddenHits.length
      ? `<div class="cap-results-tail">
            <h3 class="cap-section-title">当前平台搜不到</h3>
            <p class="muted">索引里有这些条目，但宿主平台过滤后不会出现在上面的正文里。</p>
            ${state.capHiddenHits.map(renderCapabilityHit).join("")}
          </div>`
      : "",
    state.capDisabledMcp.length
      ? `<div class="cap-results-tail">
            <h3 class="cap-section-title">未启用的 MCP</h3>
            <p class="muted">未启用的服务器不会写入能力索引，模型的 find_caps 也找不到。</p>
            ${state.capDisabledMcp
              .map(
                (s) =>
                  `<div class="cap-hit-card"><strong>${escapeHtml(s.name)}</strong><div class="muted small">${escapeHtml(s.url)}</div><div>${escapeHtml(s.description)}</div></div>`,
              )
              .join("")}
          </div>`
      : "",
  ].join("");
  return `<div class="cap-results">
        ${state.capError ? `<p class="error-inline">${escapeHtml(state.capError)}</p>` : ""}
        ${
          !state.capError
            ? `<div class="cap-model-block">
            <h3 class="cap-section-title">模型看到的正文${platformNote}</h3>
            <pre class="cap-result">${escapeHtml(state.capModelText || "（空）")}</pre>
          </div>`
            : ""
        }
        ${tail}
      </div>`;
}

function syncCapabilityResultsDom() {
  const body = document.querySelector("#capabilities-overlay .cap-panel-body");
  if (!body) return;
  body.querySelector(".cap-results")?.remove();
  const html = renderCapabilityResultsHtml();
  if (!html) return;
  const toolbar = body.querySelector(".cap-panel-toolbar");
  if (toolbar) toolbar.insertAdjacentHTML("afterend", html);
  else body.insertAdjacentHTML("beforeend", html);
}

function applyCapabilitySearchResult(patch: Partial<AppState>) {
  state = { ...state, ...patch, capBusy: false };
  setCapabilitySearchBusy(false);
  const limitSpan = document.querySelector("#capabilities-overlay .cap-limit-row span");
  if (limitSpan) limitSpan.textContent = String(state.capLimit);
  syncCapabilityResultsDom();
}

async function runCapabilitySearch() {
  const input = document.getElementById("cap-query") as HTMLInputElement | null;
  const q = (input?.value ?? state.capQuery).trim();
  if (!q || state.capBusy) return;
  state = { ...state, capQuery: q, capBusy: true, capError: null };
  setCapabilitySearchBusy(true);
  try {
    const result = await api.searchCapabilities(q, state.capSelectedKinds, state.capLimit);
    applyCapabilitySearchResult({
      capSearched: true,
      capError: null,
      capModelText: result.modelText,
      capHostPlatform: result.hostPlatform,
      capLimit: result.limit,
      capHiddenHits: result.hiddenByPlatform,
      capDisabledMcp: result.disabledMcp,
    });
  } catch (e) {
    applyCapabilitySearchResult({
      capSearched: true,
      capError: e instanceof Error ? e.message : String(e),
      capModelText: "",
      capHostPlatform: "",
      capHiddenHits: [],
      capDisabledMcp: [],
    });
  }
}

async function copySessionBrief() {
  const sid = state.activeSessionId;
  if (!sid) return;
  const { text } = await api.fetchBrief(sid);
  await navigator.clipboard.writeText(text);
  showToast("简报已复制到剪贴板");
}

function closeMoreMenu() {
  if (!state.moreMenuOpen) return;
  setState({ moreMenuOpen: false });
}

async function refreshAttachments(sessionId: string) {
  try {
    const attachments = await api.fetchAttachments(sessionId);
    setState({ attachments });
  } catch {
    setState({ attachments: [] });
  }
}

async function loadActiveTranscript(sessionId: string) {
  const messages = await api.fetchTranscript(sessionId);
  const pendingAsk = detectPendingAsk(messages);
  setState({
    activeSessionId: sessionId,
    lines: messagesToLines(messages),
    pendingAsk,
    askAnswers: {},
    streamingText: "",
    streamingReasoning: "",
    tools: [],
    error: null,
  });
  await refreshAttachments(sessionId);
}

async function bootstrap() {
  const [config] = await Promise.all([api.fetchConfig(), refreshSessions()]);
  setState({ config });
  let sessionId = state.activeSessionId;
  if (!sessionId) {
    if (state.sessions.length === 0) {
      const created = await api.createSession();
      sessionId = created.sessionId;
      await refreshSessions();
    } else {
      sessionId = state.sessions[0].sessionId;
      await api.useSession(sessionId);
    }
  }
  await loadActiveTranscript(sessionId!);
  await refreshUsage();
  disconnectEvents = api.connectEvents(onAgentEvent);
}

function onAgentEvent(event: api.AgentEventDto) {
  if (event.sessionId && state.activeSessionId && event.sessionId !== state.activeSessionId) {
    const previewSid = state.preview?.sessionId;
    if (!previewSid || event.sessionId !== previewSid) {
      return;
    }
  }
  switch (event.type) {
    case "AGENT_START":
      resetRunUsageAccumulators();
      setState({
        isRunning: true,
        streamingText: "",
        streamingReasoning: "",
        tools: [],
        error: null,
        lastRunTokenSummary: null,
      });
      break;
    case "USAGE":
      accumulateRunUsage(event.payload);
      break;
    case "MESSAGE_UPDATE":
      setState({
        streamingText: state.streamingText + String(event.payload.delta || ""),
        isRunning: true,
      });
      break;
    case "REASONING_UPDATE":
      setState({
        streamingReasoning: state.streamingReasoning + String(event.payload.delta || ""),
        isRunning: true,
      });
      break;
    case "MESSAGE_RESET":
      setState({ streamingText: "", streamingReasoning: "" });
      break;
    case "TOOL_EXECUTION_START": {
      const call = event.payload.toolCall as {
        id?: string;
        name?: string;
        argsPreview?: string;
      };
      const part: ToolPart = {
        id: call.id || `tool-${Date.now()}`,
        toolCallId: call.id || "",
        toolName: call.name || "tool",
        argsPreview: call.argsPreview || "",
        running: true,
      };
      setState({ tools: [...state.tools, part], isRunning: true });
      break;
    }
    case "TOOL_EXECUTION_END": {
      const toolCallId = String(event.payload.toolCallId || "");
      const ended = state.tools.find((t) => t.toolCallId === toolCallId);
      const tools = state.tools.map((t) =>
        t.toolCallId === toolCallId
          ? {
              ...t,
              running: false,
              error: !!(event.payload.error || event.payload.toolError),
              resultSummary: String(event.payload.resultText || ""),
            }
          : t,
      );
      const waiting = !!event.payload.stopRunWaitingUser;
      setState({ tools, isRunning: !waiting && state.isRunning });
      if (
        state.preview &&
        ended &&
        (ended.toolName === "write_file" || ended.toolName === "edit_file")
      ) {
        schedulePreviewReload();
      }
      break;
    }
    case "AGENT_ERROR":
      setState({
        error: String(event.payload.message || "运行出错"),
        isRunning: false,
        lastRunTokenSummary: buildRunTokenSummary(true),
      });
      void reloadAfterRun();
      break;
    case "AGENT_END":
      setState({
        isRunning: false,
        lastRunTokenSummary: buildRunTokenSummary(false),
      });
      void reloadAfterRun();
      break;
    default:
      break;
  }
}

async function reloadAfterRun() {
  if (!state.activeSessionId) return;
  const tokenSummary = state.lastRunTokenSummary;
  try {
    const messages = await api.fetchTranscript(state.activeSessionId);
    setState({
      lines: messagesToLines(messages),
      pendingAsk: detectPendingAsk(messages),
      streamingText: "",
      streamingReasoning: "",
      tools: [],
      isRunning: false,
      lastRunTokenSummary: tokenSummary,
    });
    await refreshSessions();
    await refreshUsage();
  } catch (e) {
    setState({ error: e instanceof Error ? e.message : String(e) });
  }
}

async function onSend() {
  const text = state.composer.trim();
  if (!text || !state.activeSessionId || state.isRunning || state.pendingAsk) return;
  if (!state.config?.hasApiKey) {
    setState({ showSettings: true, error: "请先配置 API Key" });
    return;
  }
  const optimistic: ChatLine = {
    id: `local-${Date.now()}`,
    role: "user",
    content: text,
  };
  setState({
    lines: [...state.lines, optimistic],
    composer: "",
    isRunning: true,
    streamingText: "",
    tools: [],
    error: null,
    lastRunTokenSummary: null,
  });
  let payload = text;
  if (state.attachments.length > 0) {
    const lines = state.attachments.map((a) => `- ${a.workspaceRelativePath}`);
    payload += `\n\n[用户附件]\n${lines.join("\n")}`;
  }
  try {
    await api.sendMessage(state.activeSessionId, payload);
  } catch (e) {
    setState({
      isRunning: false,
      error: e instanceof Error ? e.message : String(e),
    });
  }
}

async function onSubmitAsk() {
  if (!state.pendingAsk || !state.activeSessionId) return;
  const text = formatAskReply(state.pendingAsk, state.askAnswers);
  setState({ pendingAsk: null, composer: text });
  await onSend();
}

function renderBubble(line: ChatLine, sessionId: string | null): string {
  if (line.role === "tool") {
    const body = sessionId
      ? formatBubbleContentHtml(line.content, sessionId) + extraPlainImageHtml(line.content, sessionId)
      : escapeHtml(line.content);
    return `<details class="tool-card"><summary>⚙ ${escapeHtml(line.toolName || "tool")}</summary>
      <div class="tool-row ${line.error ? "err" : ""}">${body}</div></details>`;
  }
  const body = sessionId
    ? formatBubbleContentHtml(line.content, sessionId) + extraPlainImageHtml(line.content, sessionId)
    : escapeHtml(line.content);
  return `<div class="bubble ${line.role}"><div class="bubble-text">${body}</div></div>`;
}

function renderLiveTools(): string {
  if (!state.tools.length && !state.streamingText && !state.streamingReasoning) return "";
  const rows = state.tools
    .map((t) => {
      const status = t.running ? "进行中…" : t.error ? "失败" : "完成";
      return `<div class="tool-row ${t.error ? "err" : ""}"><strong>${escapeHtml(t.toolName)}</strong> · ${status}
        ${t.argsPreview ? `<div>${escapeHtml(t.argsPreview)}</div>` : ""}
        ${t.resultSummary ? `<div>${escapeHtml(t.resultSummary)}</div>` : ""}
      </div>`;
    })
    .join("");
  const body = [
    state.streamingReasoning
      ? `<div class="tool-row">思考：${escapeHtml(state.streamingReasoning)}</div>`
      : "",
    rows,
  ].join("");
  return `<details class="tool-card" open><summary>思考与执行</summary>${body}</details>
    ${state.streamingText ? `<div class="bubble assistant">${escapeHtml(state.streamingText)}</div>` : ""}`;
}

function renderAskPanel(): string {
  if (!state.pendingAsk) return "";
  const qs = state.pendingAsk.questions
    .map((q) => {
      const options = (q.options || [])
        .map((opt) => {
          const selected = state.askAnswers[q.id] === opt ? "selected" : "";
          return `<button type="button" class="chip ${selected}" data-ask-id="${escapeHtml(q.id)}" data-ask-opt="${escapeHtml(opt)}">${escapeHtml(opt)}</button>`;
        })
        .join("");
      return `<div class="ask-q">
        <label>${escapeHtml(q.prompt)}</label>
        ${options ? `<div class="ask-options">${options}</div>` : ""}
        <input data-ask-input="${escapeHtml(q.id)}" value="${escapeHtml(state.askAnswers[q.id] || "")}" placeholder="你的回答" />
      </div>`;
    })
    .join("");
  return `<div class="ask-panel"><h3>请回答后继续</h3>${qs}
    <button type="button" class="primary-btn" id="ask-submit">提交回答</button></div>`;
}

function renderCapabilityHit(hit: api.CapabilityHitDto): string {
  const title = hit.title || hit.id;
  return `<div class="cap-hit-card">
    <div class="cap-hit-title">[${escapeHtml(hit.kind)}] ${escapeHtml(title)}</div>
    <div class="muted small">${escapeHtml(availabilityLabel(hit.availability))}</div>
    ${hit.summary ? `<div>${escapeHtml(hit.summary)}</div>` : ""}
  </div>`;
}

function renderCapabilitiesPanel(): string {
  if (!state.showCapabilities) {
    overlayEnterClass("capabilities", false);
    return "";
  }
  const overlayEnter = overlayEnterClass("capabilities", true);
  const kindChips = CAP_KIND_CHOICES
    .map(({ id, label }) => {
      const active = state.capSelectedKinds.includes(id) ? "active" : "";
      return `<button type="button" class="provider-chip cap-kind-chip ${active}" data-cap-kind="${id}">${escapeHtml(label)}</button>`;
    })
    .join("");
  const results = renderCapabilityResultsHtml();
  return `<div class="overlay settings-overlay${overlayEnter}" id="capabilities-overlay">
    <div class="overlay-panel settings-panel wide-panel">
      <header class="settings-header">
        <h2>能力检索</h2>
        <button type="button" class="icon-btn" id="capabilities-close" title="关闭">✕</button>
      </header>
      <div class="settings-body cap-panel-body">
        <div class="cap-panel-toolbar">
          <div class="cap-search">
            <input id="cap-query" class="settings-control" placeholder="搜索" value="${escapeHtml(state.capQuery)}" />
            <button type="button" class="primary-btn" id="cap-search-btn" ${state.capBusy ? "disabled" : ""}>${state.capBusy ? "…" : "搜索"}</button>
          </div>
          <div class="cap-kind-row">${kindChips}</div>
          <div class="cap-limit-row">
            <button type="button" class="icon-btn" id="cap-limit-down" ${state.capLimit <= 1 || state.capBusy ? "disabled" : ""}>−</button>
            <span>${state.capLimit}</span>
            <button type="button" class="icon-btn" id="cap-limit-up" ${state.capLimit >= CAP_MAX_LIMIT || state.capBusy ? "disabled" : ""}>+</button>
          </div>
        </div>
        ${results}
      </div>
    </div>
  </div>`;
}

function renderArtifactsPanel(): string {
  if (!state.showArtifacts) return "";
  const rows = state.artifacts
    .map((a) => {
      const canPreview = isPreviewablePath(a.workspaceRelativePath);
      const previewBtn = canPreview
        ? `<button type="button" class="link-btn" data-art-preview="1" data-art-session="${escapeHtml(a.sessionId)}" data-art-path="${escapeHtml(a.workspaceRelativePath)}">预览</button>`
        : "";
      const insertBtn = state.activeSessionId
        ? `<button type="button" class="link-btn" data-art-insert="1" data-art-session="${escapeHtml(a.sessionId)}" data-art-path="${escapeHtml(a.workspaceRelativePath)}">插入当前会话</button>`
        : "";
      return `<li><strong>${escapeHtml(a.fileName)}</strong>
        <span class="muted"> · ${escapeHtml(a.sessionId.slice(0, 8))} · ${Math.round(a.sizeBytes / 1024)}KB</span>
        <div class="artifact-actions">${previewBtn} ${insertBtn}</div></li>`;
    })
    .join("");
  return `<div class="overlay settings-overlay" id="artifacts-overlay">
    <div class="overlay-panel settings-panel wide-panel">
      <header class="settings-header">
        <h2>产物库</h2>
        <button type="button" class="icon-btn" id="artifacts-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <p class="muted">workspace 内 AI 产出（不含 imports/、tmp/）</p>
        <ul class="artifact-list">${rows || "<li class='muted'>暂无产物</li>"}</ul>
      </div>
    </div>
  </div>`;
}

function renderPreviewPanel(): string {
  if (!state.preview) return "";
  const htmlPreview = isHtmlPreviewPath(state.preview.path);
  const url = htmlPreview
    ? `${api.workspacePreviewUrl(state.preview.sessionId, state.preview.path)}?v=${state.previewReloadKey}`
    : `${api.workspaceFileUrl(state.preview.sessionId, state.preview.path)}?v=${state.previewReloadKey}`;
  const deckBadge = state.previewIsDeck ? `<span class="preview-deck-badge">Deck</span>` : "";
  const inspectLabel = state.previewInspectEnabled ? "关闭审查" : "审查模式";
  const selectedJson = state.previewSelectedElementJson;
  const bottom =
    htmlPreview && selectedJson
      ? `<div class="preview-inspect-bar">
        <p class="preview-inspect-summary">已选中 &lt;${escapeHtml(inspectElementTag(selectedJson))}&gt;</p>
        <textarea id="preview-opinion" class="preview-opinion" rows="2" placeholder="描述要修改的问题…">${escapeHtml(state.previewOpinion)}</textarea>
        <div class="preview-inspect-actions">
          <button type="button" class="link-btn" id="preview-inspect-cancel">取消</button>
          <button type="button" class="link-btn" id="preview-fill-composer">填入输入框</button>
          <button type="button" class="primary-btn" id="preview-send-ai">反馈给 AI</button>
        </div>
      </div>`
      : "";
  const inspectBtn = htmlPreview
    ? `<button type="button" class="link-btn" id="preview-inspect-toggle">${inspectLabel}</button>`
    : "";
  const body = htmlPreview
    ? `<iframe class="preview-frame" id="preview-frame" src="${url}" title="preview"></iframe>`
    : `<div class="preview-media"><img src="${url}" alt="${escapeHtml(state.preview.path)}" /></div>`;
  return `<div class="overlay preview-overlay" id="preview-overlay">
    <div class="preview-panel">
      <div class="preview-toolbar">
        <span class="preview-path">${deckBadge}${escapeHtml(state.preview.path)}</span>
        <div class="preview-toolbar-actions">
          ${inspectBtn}
          <button type="button" class="icon-btn" id="preview-close" title="关闭">✕</button>
        </div>
      </div>
      ${body}
      ${bottom}
    </div>
  </div>`;
}

function renderMcpPanel(): string {
  if (!state.showMcp) return "";
  const rows = state.mcpServers
    .map((s, i) => {
      const headers = JSON.stringify(s.headers || {});
      return `<div class="mcp-card" data-mcp-index="${i}">
        <div class="field-row">
          <div class="field"><label>名称</label><input class="settings-control mcp-name" value="${escapeHtml(s.name)}" /></div>
          <label class="mcp-enable"><input type="checkbox" class="mcp-enabled" ${s.enabled ? "checked" : ""}/> 启用</label>
        </div>
        <div class="field"><label>URL</label><input class="settings-control mcp-url" value="${escapeHtml(s.url)}" /></div>
        <div class="field"><label>Headers (JSON)</label><input class="settings-control mcp-headers" value="${escapeHtml(headers)}" /></div>
        <p class="settings-hint">${s.toolCount ? `${s.toolCount} 个工具` : "保存后尝试索引工具"}</p>
        <button type="button" class="link-btn mcp-remove" data-mcp-index="${i}">删除</button>
      </div>`;
    })
    .join("");
  const cfg = state.config;
  return `<div class="overlay settings-overlay" id="mcp-overlay">
    <div class="overlay-panel settings-panel">
      <header class="settings-header">
        <h2>MCP 与联网搜索</h2>
        <button type="button" class="icon-btn" id="mcp-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <p class="muted">添加服务器后，助手用 find_caps 查找接口，再在脚本里调用 $mcp.名称.工具。修改后将自动保存并刷新索引。</p>
        ${state.mcpStatus ? `<p class="settings-hint">${escapeHtml(state.mcpStatus)}</p>` : ""}
        <div id="mcp-list">${rows}</div>
        <button type="button" class="link-btn" id="mcp-add">添加服务器</button>
        <section class="settings-section">
          <h3>联网搜索（Tavily）</h3>
          <div class="field">
            <label for="mcp-web-key">Web Search API Key</label>
            <input id="mcp-web-key" class="settings-control" type="password" autocomplete="off"
              value="${escapeHtml(state.mcpWebSearchApiKey)}"
              placeholder="${cfg?.hasWebSearch ? "已配置，留空不修改" : "tvly-…"}" />
          </div>
          <div class="field">
            <label for="mcp-web-base">Tavily Base URL</label>
            <input id="mcp-web-base" class="settings-control" value="${escapeHtml(state.mcpWebSearchBaseUrl)}" />
          </div>
        </section>
      </div>
    </div>
  </div>`;
}

function renderSystemPromptPanel(): string {
  if (!state.showSystemPrompt) return "";
  const tools = state.systemPromptTools
    .map((t) => {
      const desc = t.description ? `<p class="muted">${escapeHtml(t.description)}</p>` : "";
      const schema = t.parametersSchema
        ? `<pre class="prompt-schema">${escapeHtml(t.parametersSchema)}</pre>`
        : "";
      return `<details class="prompt-tool">
        <summary>${escapeHtml(t.name)}</summary>
        ${desc}${schema}
      </details>`;
    })
    .join("");
  const body = state.systemPromptBusy
    ? `<p class="muted">正在读取系统提示词…</p>`
    : [
        state.systemPromptError
          ? `<p class="error-inline">${escapeHtml(state.systemPromptError)}</p>`
          : "",
        state.systemPromptText
          ? `<div class="prompt-body">${formatBubbleContentHtml(state.systemPromptText, null)}</div>`
          : state.systemPromptError
            ? ""
            : `<p class="muted">当前会话尚未装入系统提示词。</p>`,
        state.systemPromptTools.length
          ? `<h3 class="prompt-tools-title">工具 ${state.systemPromptTools.length}</h3>${tools}`
          : "",
      ].join("");
  return `<div class="overlay settings-overlay" id="system-prompt-overlay">
    <div class="overlay-panel settings-panel wide-panel">
      <header class="settings-header">
        <h2>系统提示词</h2>
        <div class="settings-header-actions">
          <button type="button" class="link-btn" id="system-prompt-copy" ${state.systemPromptText ? "" : "disabled"}>复制</button>
          <button type="button" class="icon-btn" id="system-prompt-close" title="关闭">✕</button>
        </div>
      </header>
      <div class="settings-body prompt-scroll">${body}</div>
    </div>
  </div>`;
}

function renderToolsPanel(): string {
  if (!state.showTools) return "";
  const rows = state.toolsList
    .map(
      (t) =>
        `<li><strong>${escapeHtml(t.name)}</strong><span class="muted">${escapeHtml(t.description)}</span></li>`,
    )
    .join("");
  return `<div class="overlay" id="tools-overlay">
    <div class="overlay-panel usage-panel">
      <h2>Agent 工具</h2>
      <ul class="tools-list">${rows || "<li class='muted'>无</li>"}</ul>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="tools-close">关闭</button>
      </div>
    </div>
  </div>`;
}

function renderAboutPanel(): string {
  if (!state.showAbout) return "";
  const info = state.aboutInfo;
  const body = state.aboutLoading || !info
    ? `<p class="muted">加载中…</p>`
    : (() => {
        const versionLine =
          info.versionCode > 0
            ? `版本 ${escapeHtml(info.versionName)}（${info.versionCode}）`
            : `版本 ${escapeHtml(info.versionName)}`;
        const deps = info.dependencies
          .map(
            (d) =>
              `<div class="about-row"><div class="about-label">${escapeHtml(d.name)}</div><div class="about-value">${escapeHtml(d.license)}</div></div>`,
          )
          .join("");
        return `<div class="about-brand">${escapeHtml(info.appName)}</div>
          <div class="about-version">${versionLine}</div>
          <div class="about-row"><div class="about-label">${escapeHtml(info.authorLabel)}</div><div class="about-value">${escapeHtml(info.authorValue)}</div></div>
          <div class="about-row"><div class="about-label">${escapeHtml(info.websiteLabel)}</div><a class="about-link" href="${escapeAttr(info.websiteUrl)}" target="_blank" rel="noopener noreferrer">${escapeHtml(info.websiteUrl)}</a></div>
          <div class="about-row"><div class="about-label">${escapeHtml(info.sourceLabel)}</div><a class="about-link" href="${escapeAttr(info.sourceUrl)}" target="_blank" rel="noopener noreferrer">${escapeHtml(info.sourceUrl)}</a></div>
          <div class="about-row"><div class="about-label">${escapeHtml(info.licenseLabel)}</div><div class="about-value">${escapeHtml(info.licenseValue)}</div></div>
          <h3 class="about-deps-title">${escapeHtml(info.depsTitle)}</h3>
          <p class="muted about-deps-notice">${escapeHtml(info.depsNotice)}</p>
          ${deps}`;
      })();
  return `<div class="overlay settings-overlay" id="about-overlay">
    <div class="overlay-panel settings-panel">
      <header class="settings-header">
        <h2>${escapeHtml(info?.title ?? "关于")}</h2>
        <button type="button" class="icon-btn" id="about-close" title="关闭">✕</button>
      </header>
      <div class="settings-body about-scroll">${body}</div>
    </div>
  </div>`;
}

function escapeAttr(text: string): string {
  return text
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

async function openAboutPanel() {
  setState({ showAbout: true, aboutLoading: true, aboutInfo: null, moreMenuOpen: false });
  try {
    const aboutInfo = await api.fetchAbout();
    setState({ aboutInfo, aboutLoading: false });
  } catch (e) {
    setState({
      showAbout: false,
      aboutLoading: false,
      error: e instanceof Error ? e.message : String(e),
    });
  }
}

function renderMoreMenu(): string {
  if (!state.moreMenuOpen) return "";
  return `<div class="more-menu" role="menu" id="more-menu">
    <button type="button" class="more-menu-item" data-more-action="settings">模型设置</button>
    <button type="button" class="more-menu-item" data-more-action="mcp">MCP 与联网搜索</button>
    <button type="button" class="more-menu-item" data-more-action="capabilities">能力检索</button>
    <button type="button" class="more-menu-item" data-more-action="system-prompt">系统提示词</button>
    <button type="button" class="more-menu-item" data-more-action="brief">会话简报</button>
    <button type="button" class="more-menu-item" data-more-action="diagnostics">诊断包</button>
    <button type="button" class="more-menu-item" data-more-action="failed">失败工具调用</button>
    <button type="button" class="more-menu-item" data-more-action="about">关于</button>
  </div>`;
}

function renderFailedLogsPanel(): string {
  if (!state.showFailedLogs) return "";
  const scopeLabel = state.failedLogsScopeSession ? "当前会话" : "全部会话";
  const rows = state.failedLogs
    .map(
      (item) =>
        `<li class="failed-log-item">
          <div><strong>${escapeHtml(item.toolName || "tool")}</strong>
            <span class="muted"> · ${escapeHtml(item.toolCallId)}</span></div>
          <div class="muted small">run ${escapeHtml(item.runId)} · session ${escapeHtml(item.sessionId)}</div>
          <div>${escapeHtml(item.errorMessage || "—")}</div>
        </li>`,
    )
    .join("");
  return `<div class="overlay" id="failed-logs-overlay">
    <div class="overlay-panel usage-panel wide-panel">
      <h2>失败工具调用</h2>
      <p class="muted">范围：${escapeHtml(scopeLabel)} · 对齐 <code>./agent1 logs failed</code></p>
      <ul class="failed-log-list">${rows || "<li class='muted'>（无失败记录）</li>"}</ul>
      <div class="overlay-actions">
        <label class="checkbox-row"><input type="checkbox" id="failed-scope-session" ${state.failedLogsScopeSession ? "checked" : ""}/> 仅当前会话</label>
        <button type="button" id="failed-refresh">刷新</button>
        <button type="button" class="primary-btn" id="failed-close">关闭</button>
      </div>
    </div>
  </div>`;
}

function renderUsagePanel(): string {
  if (!state.usagePanelOpen || !state.usage) return "";
  return `<div class="overlay" id="usage-overlay">
    <div class="overlay-panel usage-panel">
      <h2>累计用量</h2>
      <div class="usage-detail">${usageDetailHtml(state.usage)}</div>
      <p class="muted">来自 agentRoot/logs/events.jsonl</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="usage-close">关闭</button>
      </div>
    </div>
  </div>`;
}

function renderSettings(): string {
  if (!state.showSettings || !state.settingsDraft) return "";
  const draft = state.settingsDraft;
  const cfg = state.config;
  const providerChips = PROVIDER_PRESETS
    .map((p) => {
      const active = p.id === draft.providerId ? "active" : "";
      return `<button type="button" class="provider-chip ${active}" data-provider="${p.id}">${escapeHtml(p.label)}</button>`;
    })
    .join("");
  const modelOptions = state.modelOptions
    .map((m) => {
      const selected = m.modelId === draft.model ? "selected" : "";
      return `<option value="${escapeHtml(m.modelId)}" ${selected}>${escapeHtml(m.title)} · ${escapeHtml(m.subtitle)}</option>`;
    })
    .join("");
  const customModel =
    draft.model && !state.modelOptions.some((m) => m.modelId === draft.model)
      ? `<option value="${escapeHtml(draft.model)}" selected>${escapeHtml(draft.model)}（当前）</option>`
      : "";
  const modelsStatus = state.modelsLoading
    ? "正在加载模型列表…"
    : state.modelsStatus || (state.modelOptions.length ? `共 ${state.modelOptions.length} 个模型` : "");
  return `<div class="overlay settings-overlay" id="settings-overlay">
    <div class="overlay-panel settings-panel">
      <header class="settings-header">
        <h2>模型与限额</h2>
        <button type="button" class="icon-btn" id="cfg-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <section class="settings-section">
          <h3>服务商</h3>
          <div class="provider-row">${providerChips}</div>
        </section>
        <section class="settings-section">
          <h3>连接</h3>
          <div class="field">
            <label for="cfg-key">API Key</label>
            <input id="cfg-key" class="settings-control" type="password" autocomplete="off"
              placeholder="${escapeHtml(cfg?.hasApiKey ? `已配置 ${cfg.apiKeyHint || ""}` : "粘贴 Key，留空则不修改")}" />
          </div>
          <div class="field">
            <label for="cfg-base">Base URL</label>
            <input id="cfg-base" class="settings-control" value="${escapeHtml(draft.baseUrl)}" />
          </div>
          <div class="field">
            <div class="field-label-row">
              <label for="cfg-model">模型</label>
              <button type="button" class="link-btn" id="cfg-refresh-models" ${state.modelsLoading ? "disabled" : ""}>从网络刷新</button>
            </div>
            <select id="cfg-model" class="settings-control settings-select">${customModel}${modelOptions}</select>
            <p class="settings-hint">${escapeHtml(modelsStatus)}</p>
          </div>
        </section>
        <section class="settings-section">
          <h3>Run 限额</h3>
          <div class="field-row">
            <div class="field">
              <label for="cfg-ctx-turns">上下文轮次</label>
              <input id="cfg-ctx-turns" class="settings-control" type="number" min="0" value="${draft.maxContextTurns}" />
            </div>
            <div class="field">
              <label for="cfg-ctx-msgs">消息条数</label>
              <input id="cfg-ctx-msgs" class="settings-control" type="number" min="0" value="${draft.maxContextMessages}" />
            </div>
          </div>
          <div class="field-row">
            <div class="field">
              <label for="cfg-run-turns">单 Run 轮次</label>
              <input id="cfg-run-turns" class="settings-control" type="number" min="0" value="${draft.maxTurnsPerRun}" />
            </div>
            <div class="field">
              <label for="cfg-tool-calls">工具调用</label>
              <input id="cfg-tool-calls" class="settings-control" type="number" min="0" value="${draft.maxToolCallsPerRun}" />
            </div>
          </div>
          <p class="settings-hint">填 0 表示沿用环境变量或 core 默认；保存写入 ui-config.json。</p>
        </section>
      </div>
      <footer class="settings-footer">
        <button type="button" class="link-btn" id="cfg-open-mcp">MCP 与联网搜索</button>
        <button type="button" class="link-btn" id="cfg-show-tools">已注册工具</button>
        <button type="button" class="primary-btn settings-save" id="cfg-save">保存并生效</button>
      </footer>
    </div>
  </div>`;
}

function render() {
  const sessions = state.sessions
    .map((s) => {
      const active = s.sessionId === state.activeSessionId ? "active" : "";
      const title = s.title || s.sessionId.slice(0, 8);
      if (s.sessionId === state.pendingDeleteSessionId) {
        return `<div class="session-item ${active} session-item-confirm">
          <span class="session-confirm-text">删除此对话？</span>
          <button type="button" class="session-confirm-cancel" id="delete-session-cancel">取消</button>
          <button type="button" class="session-confirm-ok" id="delete-session-confirm">删除</button>
        </div>`;
      }
      return `<div class="session-item ${active}">
        <button type="button" class="session-open" data-use="${s.sessionId}">
          <span class="session-title">${escapeHtml(title)}</span>
        </button>
        <button type="button" class="session-delete" data-del="${s.sessionId}" title="删除" aria-label="删除">✕</button>
      </div>`;
    })
    .join("");

  const offerNewTopic = shouldOfferNewTopic(
    state.lines,
    state.isRunning,
    state.streamingText,
    state.streamingReasoning,
    !!state.pendingAsk,
  );
  const chatLinesHtml = state.lines.map((l) => renderBubble(l, state.activeSessionId)).join("");
  const newTopicBtn = offerNewTopic
    ? `<button type="button" class="new-topic-btn" id="btn-new-topic">开始新话题</button>`
    : "";
  const runTokenRow =
    state.lastRunTokenSummary && !state.isRunning
      ? `<div class="run-token-summary ${state.lastRunTokenSummary.failed ? "failed" : ""}">${escapeHtml(runTokenSummaryLine(state.lastRunTokenSummary))}</div>`
      : "";
  const chatBody =
    state.lines.length === 0 && !state.streamingText && !state.isRunning
      ? `<div class="landing"><h2>Agent1</h2><p>生产力助手 · 与 Android 端同款纸本风格。左侧管理会话，下方输入开始对话。</p></div>`
      : chatLinesHtml + runTokenRow + newTopicBtn + renderLiveTools() + renderAskPanel();

  const usageFoot = usageFooterLine(state.usage);

  const composerDisabled = state.isRunning || !!state.pendingAsk;
  const placeholder = state.pendingAsk
    ? "请在上方卡片回答"
    : state.isRunning
      ? "生成中…"
      : "输入消息，Enter 发送，Shift+Enter 换行";

  root!.innerHTML = `
  <div class="app">
    <aside class="drawer ${state.drawerOpen ? "" : "collapsed"}">
      <div class="drawer-header">
        <div class="brand">Agent1</div>
        <div class="drawer-actions">
          <button type="button" class="icon-btn" id="btn-new" title="新会话">＋</button>
        </div>
      </div>
      <div class="session-list">${sessions}</div>
      <button type="button" class="usage-footer" id="usage-footer">
        <span class="usage-headline">${escapeHtml(usageFoot.headline)}</span>
        <span class="usage-detail-line">${escapeHtml(usageFoot.detail)}</span>
      </button>
    </aside>
    <section class="main" id="app-main">
      <div class="topbar">
        <button type="button" class="icon-btn" id="btn-drawer" title="会话">☰</button>
        <h1>对话</h1>
        <div class="topbar-actions">
          <button type="button" class="icon-btn" id="btn-artifacts" title="产物库">📁</button>
          <div class="more-menu-anchor">
            <button type="button" class="icon-btn" id="btn-more" title="更多" aria-haspopup="menu" aria-expanded="${state.moreMenuOpen}">⋮</button>
            ${renderMoreMenu()}
          </div>
        </div>
      </div>
      ${state.toast ? `<div class="toast-banner">${escapeHtml(state.toast)}</div>` : ""}
      ${state.error ? `<div class="error-banner">${escapeHtml(state.error)}</div>` : ""}
      <div class="chat-scroll" id="chat-scroll">${chatBody}</div>
      <div class="composer-wrap">
        ${
          state.config && !state.config.hasApiKey
            ? `<div class="inline-setup">尚未配置 API Key。<button type="button" id="btn-setup">打开设置</button></div>`
            : ""
        }
        ${
          state.attachments.length
            ? `<div class="attachment-chips">${state.attachments
                .map(
                  (a) =>
                    `<span class="chip">${escapeHtml(a.fileName)} <button type="button" data-rm-attach="${escapeHtml(a.workspaceRelativePath)}">×</button></span>`,
                )
                .join("")}</div>`
            : ""
        }
        <div class="composer">
          <input type="file" id="file-input" class="hidden-file" multiple />
          <button type="button" class="icon-btn" id="btn-attach" title="添加附件" ${composerDisabled ? "disabled" : ""}>📎</button>
          <textarea id="composer" ${composerDisabled ? "disabled" : ""} placeholder="${placeholder}">${escapeHtml(state.composer)}</textarea>
          ${
            state.isRunning
              ? `<button type="button" class="danger-btn" id="btn-stop">停止</button>`
              : `<button type="button" class="primary-btn" id="btn-send" ${composerDisabled ? "disabled" : ""}>发送</button>`
          }
        </div>
        <div class="model-chip">模型 ${escapeHtml(state.config?.model || "—")} ·
          <button type="button" id="btn-model">更改</button></div>
      </div>
    </section>
    ${renderSettings()}
    ${renderMcpPanel()}
    ${renderSystemPromptPanel()}
    ${renderToolsPanel()}
    ${renderUsagePanel()}
    ${renderFailedLogsPanel()}
    ${renderAboutPanel()}
    ${renderCapabilitiesPanel()}
    ${renderArtifactsPanel()}
    ${renderPreviewPanel()}
  </div>`;

  bindEvents();
  const scroll = document.getElementById("chat-scroll");
  if (scroll && !anySideOverlayOpen()) scroll.scrollTop = scroll.scrollHeight;
}

function bindEvents() {
  document.getElementById("btn-new")?.addEventListener("click", () => void startNewChat());
  document.getElementById("btn-new-topic")?.addEventListener("click", () => void startNewChat());
  document.getElementById("btn-drawer")?.addEventListener("click", (ev) => {
    ev.stopPropagation();
    const next = !state.drawerOpen;
    setState({ drawerOpen: next });
    if (next) void refreshUsage();
  });
  document.getElementById("app-main")?.addEventListener("click", (ev) => {
    if (!state.drawerOpen) return;
    const t = ev.target as HTMLElement | null;
    if (t?.closest("button, a, input, textarea, select, label")) return;
    setState({ drawerOpen: false });
  });
  document.getElementById("usage-footer")?.addEventListener("click", () => {
    void refreshUsage().then(() => setState({ usagePanelOpen: true }));
  });
  document.getElementById("usage-close")?.addEventListener("click", () => setState({ usagePanelOpen: false }));
  document.getElementById("usage-overlay")?.addEventListener("click", (ev) => {
    if (ev.target === ev.currentTarget) setState({ usagePanelOpen: false });
  });
  document.getElementById("failed-close")?.addEventListener("click", () => setState({ showFailedLogs: false }));
  document.getElementById("failed-refresh")?.addEventListener("click", () => void refreshFailedLogs());
  document.getElementById("failed-scope-session")?.addEventListener("change", (ev) => {
    const checked = (ev.target as HTMLInputElement).checked;
    setState({ failedLogsScopeSession: checked });
    void refreshFailedLogs();
  });
  document.getElementById("failed-logs-overlay")?.addEventListener("click", (ev) => {
    if (ev.target === ev.currentTarget) setState({ showFailedLogs: false });
  });
  document.getElementById("btn-more")?.addEventListener("click", (ev) => {
    ev.stopPropagation();
    setState({ moreMenuOpen: !state.moreMenuOpen });
  });
  document.getElementById("more-menu")?.addEventListener("click", (ev) => ev.stopPropagation());
  document.querySelectorAll<HTMLButtonElement>("[data-more-action]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const action = btn.dataset.moreAction;
      closeMoreMenu();
      switch (action) {
        case "settings":
          void openSettings();
          break;
        case "mcp":
          void openMcpPanel();
          break;
        case "capabilities":
          void openCapabilitySearch();
          break;
        case "system-prompt":
          void openSystemPrompt();
          break;
        case "brief":
          void copySessionBrief().catch((e) =>
            setState({ error: e instanceof Error ? e.message : String(e) }),
          );
          break;
        case "diagnostics":
          void api.downloadDiagnostics()
            .then(() => showToast("诊断包已下载"))
            .catch((e) => setState({ error: e instanceof Error ? e.message : String(e) }));
          break;
        case "failed":
          void openFailedLogsPanel();
          break;
        case "about":
          void openAboutPanel();
          break;
        default:
          break;
      }
    });
  });
  if (state.moreMenuOpen) {
    window.setTimeout(() => {
      if (!state.moreMenuOpen) return;
      const onDocClick = () => {
        document.removeEventListener("click", onDocClick);
        closeMoreMenu();
      };
      document.addEventListener("click", onDocClick);
    }, 0);
  }
  document.getElementById("capabilities-overlay")?.addEventListener("click", (ev) => {
    const panel = (ev.currentTarget as HTMLElement).querySelector(".overlay-panel");
    if (panel && panel.contains(ev.target as Node)) ev.stopPropagation();
  });
  document.getElementById("about-close")?.addEventListener("click", () =>
    setState({ showAbout: false, aboutInfo: null, aboutLoading: false }),
  );
  document.getElementById("about-overlay")?.addEventListener("click", (ev) => {
    if (ev.target === ev.currentTarget) {
      setState({ showAbout: false, aboutInfo: null, aboutLoading: false });
    }
  });
  document.getElementById("btn-setup")?.addEventListener("click", () => void openSettings());
  document.getElementById("btn-model")?.addEventListener("click", () => void openSettings());
  document.getElementById("btn-artifacts")?.addEventListener("click", async () => {
    const artifacts = await api.fetchArtifacts();
    setState({ artifacts, showArtifacts: true });
  });
  document.getElementById("artifacts-close")?.addEventListener("click", () => setState({ showArtifacts: false }));
  document.getElementById("capabilities-close")?.addEventListener("click", () =>
    setState({ showCapabilities: false }),
  );
  document.getElementById("cap-search-btn")?.addEventListener("click", (ev) => {
    ev.preventDefault();
    ev.stopPropagation();
    void runCapabilitySearch();
  });
  document.getElementById("cap-query")?.addEventListener("keydown", (ev) => {
    if (ev.key === "Enter") {
      ev.preventDefault();
      void runCapabilitySearch();
    }
  });
  document.querySelectorAll<HTMLButtonElement>(".cap-kind-chip").forEach((btn) => {
    btn.addEventListener("click", () => {
      const kind = btn.dataset.capKind;
      if (!kind) return;
      const selected = new Set(state.capSelectedKinds);
      if (selected.has(kind)) selected.delete(kind);
      else selected.add(kind);
      setState({ capSelectedKinds: [...selected] });
    });
  });
  document.getElementById("cap-limit-down")?.addEventListener("click", () => {
    if (state.capLimit > 1) setState({ capLimit: state.capLimit - 1 });
  });
  document.getElementById("cap-limit-up")?.addEventListener("click", () => {
    if (state.capLimit < CAP_MAX_LIMIT) setState({ capLimit: state.capLimit + 1 });
  });
  document.querySelectorAll<HTMLButtonElement>("[data-art-preview]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const sessionId = btn.dataset.artSession;
      const path = btn.dataset.artPath;
      if (sessionId && path) openHtmlPreview(sessionId, path);
    });
  });
  document.querySelectorAll<HTMLButtonElement>("[data-art-insert]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const sourceSessionId = btn.dataset.artSession;
      const workspaceRelativePath = btn.dataset.artPath;
      const sid = state.activeSessionId;
      if (!sid || !sourceSessionId || !workspaceRelativePath) return;
      try {
        await api.importArtifactToSession(sid, sourceSessionId, workspaceRelativePath);
        await refreshAttachments(sid);
        showToast("已复制到 imports/");
      } catch (e) {
        setState({ error: e instanceof Error ? e.message : String(e) });
      }
    });
  });
  wirePreviewIframe();
  document.getElementById("preview-close")?.addEventListener("click", () => closeHtmlPreview());
  document.getElementById("preview-overlay")?.addEventListener("click", (ev) => {
    if (ev.target === ev.currentTarget) closeHtmlPreview();
  });
  document.getElementById("preview-inspect-toggle")?.addEventListener("click", () => {
    const iframe = document.getElementById("preview-frame") as HTMLIFrameElement | null;
    const enable = !state.previewInspectEnabled;
    setState({
      previewInspectEnabled: enable,
      previewSelectedElementJson: null,
      previewOpinion: "",
    });
    postInspectCmd(iframe, enable ? "enable" : "disable");
  });
  document.getElementById("preview-inspect-cancel")?.addEventListener("click", () => {
    const iframe = document.getElementById("preview-frame") as HTMLIFrameElement | null;
    postInspectCmd(iframe, "clear");
    setState({ previewSelectedElementJson: null, previewOpinion: "" });
  });
  document.getElementById("preview-fill-composer")?.addEventListener("click", async () => {
    const sel = state.preview;
    const elementJson = state.previewSelectedElementJson;
    if (!sel || !elementJson) return;
    const opinion = (document.getElementById("preview-opinion") as HTMLTextAreaElement | null)?.value
      ?? state.previewOpinion;
    try {
      const draft = await api.composeInspectFeedback(sel.path, elementJson, opinion);
      setState({ composer: draft });
      closeHtmlPreview();
      showToast("已填入输入框");
    } catch (e) {
      setState({ error: e instanceof Error ? e.message : String(e) });
    }
  });
  document.getElementById("preview-send-ai")?.addEventListener("click", async () => {
    const sel = state.preview;
    const elementJson = state.previewSelectedElementJson;
    const sid = sel.sessionId;
    if (!elementJson) return;
    if (state.isRunning) {
      setState({ error: "请先停止当前 Run" });
      return;
    }
    const opinion = (document.getElementById("preview-opinion") as HTMLTextAreaElement | null)?.value
      ?? state.previewOpinion;
    if (!opinion.trim()) {
      showToast("请先描述问题");
      return;
    }
    try {
      const text = await api.composeInspectFeedback(sel.path, elementJson, opinion);
      await api.sendMessage(sid, text);
      const iframe = document.getElementById("preview-frame") as HTMLIFrameElement | null;
      postInspectCmd(iframe, "clear");
      setState({
        previewSelectedElementJson: null,
        previewOpinion: "",
        isRunning: true,
        streamingText: "",
        tools: [],
      });
      showToast("已发送给 AI，修改后页面将自动刷新");
    } catch (e) {
      setState({ error: e instanceof Error ? e.message : String(e) });
    }
  });
  document.getElementById("preview-opinion")?.addEventListener("input", (ev) => {
    setState({ previewOpinion: (ev.target as HTMLTextAreaElement).value });
  });
  const fileInput = document.getElementById("file-input") as HTMLInputElement | null;
  document.getElementById("btn-attach")?.addEventListener("click", () => fileInput?.click());
  fileInput?.addEventListener("change", async () => {
    const sid = state.activeSessionId;
    if (!sid || !fileInput.files?.length) return;
    try {
      for (const file of Array.from(fileInput.files)) {
        await api.uploadAttachment(sid, file);
      }
      await refreshAttachments(sid);
      fileInput.value = "";
    } catch (e) {
      setState({ error: e instanceof Error ? e.message : String(e) });
    }
  });
  document.querySelectorAll<HTMLButtonElement>("[data-rm-attach]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const sid = state.activeSessionId;
      const path = btn.dataset.rmAttach;
      if (!sid || !path) return;
      await api.deleteAttachment(sid, path);
      await refreshAttachments(sid);
    });
  });

  document.querySelectorAll<HTMLButtonElement>("[data-use]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const id = btn.dataset.use!;
      if (state.pendingDeleteSessionId) setState({ pendingDeleteSessionId: null });
      if (id === state.activeSessionId) return;
      if (state.isRunning) {
        setState({ error: "请先停止当前 Run 再切换会话" });
        return;
      }
      await api.useSession(id);
      await loadActiveTranscript(id);
      await refreshSessions();
    });
  });
  document.querySelectorAll<HTMLButtonElement>("[data-del]").forEach((btn) => {
    btn.addEventListener("click", (ev) => {
      ev.stopPropagation();
      const id = btn.dataset.del;
      if (!id) return;
      setState({ pendingDeleteSessionId: id });
    });
  });
  document.getElementById("delete-session-cancel")?.addEventListener("click", (ev) => {
    ev.stopPropagation();
    setState({ pendingDeleteSessionId: null });
  });
  document.getElementById("delete-session-confirm")?.addEventListener("click", (ev) => {
    ev.stopPropagation();
    void confirmPendingDelete();
  });

  const composer = document.getElementById("composer") as HTMLTextAreaElement | null;
  composer?.addEventListener("input", () => {
    state = { ...state, composer: composer.value };
  });
  composer?.addEventListener("keydown", (ev) => {
    if (ev.key === "Enter" && !ev.shiftKey) {
      ev.preventDefault();
      void onSend();
    }
  });
  document.getElementById("btn-send")?.addEventListener("click", () => void onSend());
  document.getElementById("btn-stop")?.addEventListener("click", async () => {
    await api.abortRun();
  });

  document.querySelectorAll<HTMLButtonElement>("[data-ask-opt]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const id = btn.dataset.askId!;
      const opt = btn.dataset.askOpt!;
      setState({ askAnswers: { ...state.askAnswers, [id]: opt } });
    });
  });
  document.querySelectorAll<HTMLInputElement>("[data-ask-input]").forEach((input) => {
    input.addEventListener("input", () => {
      const id = input.dataset.askInput!;
      state = { ...state, askAnswers: { ...state.askAnswers, [id]: input.value } };
    });
  });
  document.getElementById("ask-submit")?.addEventListener("click", () => void onSubmitAsk());

  const closeSettings = () =>
    setState({ showSettings: false, settingsDraft: null, modelsStatus: null, modelsLoading: false });
  document.getElementById("cfg-close")?.addEventListener("click", closeSettings);
  document.getElementById("settings-overlay")?.addEventListener("click", (ev) => {
    if (ev.target === ev.currentTarget) closeSettings();
  });
  document.querySelectorAll<HTMLButtonElement>("[data-provider]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const id = btn.dataset.provider;
      if (id && id !== state.settingsDraft?.providerId) onSettingsProviderChange(id);
    });
  });
  const syncDraftField = (patch: Partial<SettingsDraft>) => {
    if (!state.settingsDraft) return;
    setState({ settingsDraft: { ...state.settingsDraft, ...patch } });
  };
  document.getElementById("cfg-model")?.addEventListener("change", (ev) => {
    syncDraftField({ model: (ev.target as HTMLSelectElement).value });
  });
  document.getElementById("cfg-ctx-turns")?.addEventListener("change", (ev) => {
    syncDraftField({ maxContextTurns: parseInt((ev.target as HTMLInputElement).value, 10) || 0 });
  });
  document.getElementById("cfg-ctx-msgs")?.addEventListener("change", (ev) => {
    syncDraftField({ maxContextMessages: parseInt((ev.target as HTMLInputElement).value, 10) || 0 });
  });
  document.getElementById("cfg-run-turns")?.addEventListener("change", (ev) => {
    syncDraftField({ maxTurnsPerRun: parseInt((ev.target as HTMLInputElement).value, 10) || 0 });
  });
  document.getElementById("cfg-tool-calls")?.addEventListener("change", (ev) => {
    syncDraftField({ maxToolCallsPerRun: parseInt((ev.target as HTMLInputElement).value, 10) || 0 });
  });
  document.getElementById("cfg-open-mcp")?.addEventListener("click", () => void openMcpPanel());
  document.getElementById("mcp-close")?.addEventListener("click", () => setState({ showMcp: false }));
  document.getElementById("mcp-web-key")?.addEventListener("input", () => scheduleWebSearchAutoSave());
  document.getElementById("mcp-web-base")?.addEventListener("input", () => scheduleWebSearchAutoSave());
  document.getElementById("mcp-list")?.addEventListener("input", () => scheduleMcpAutoSave());
  document.getElementById("mcp-list")?.addEventListener("change", () => scheduleMcpAutoSave());
  document.getElementById("system-prompt-close")?.addEventListener("click", () =>
    setState({ showSystemPrompt: false, systemPromptError: null }),
  );
  document.getElementById("system-prompt-overlay")?.addEventListener("click", (ev) => {
    if (ev.target === ev.currentTarget) setState({ showSystemPrompt: false, systemPromptError: null });
  });
  document.getElementById("system-prompt-copy")?.addEventListener("click", async () => {
    if (!state.systemPromptText) return;
    try {
      await navigator.clipboard.writeText(state.systemPromptText);
      showToast("已复制系统提示词");
    } catch (e) {
      setState({ error: e instanceof Error ? e.message : String(e) });
    }
  });
  document.getElementById("mcp-add")?.addEventListener("click", () => {
    setState({
      mcpServers: [
        ...state.mcpServers,
        { name: "server", url: "https://", enabled: true, headers: {} },
      ],
    });
    scheduleMcpAutoSave();
  });
  document.querySelectorAll<HTMLButtonElement>(".mcp-remove").forEach((btn) => {
    btn.addEventListener("click", () => {
      const idx = Number(btn.dataset.mcpIndex);
      setState({ mcpServers: state.mcpServers.filter((_, i) => i !== idx) });
      scheduleMcpAutoSave();
    });
  });
  document.querySelectorAll<HTMLButtonElement>(".file-link").forEach((btn) => {
    btn.addEventListener("click", (ev) => {
      ev.stopPropagation();
      const path = btn.dataset.filePath;
      const sid = state.activeSessionId;
      if (!path || !sid) {
        showToast("无法打开文件");
        return;
      }
      if (isPreviewablePath(path)) {
        openHtmlPreview(sid, path);
      } else {
        showToast(path);
      }
    });
  });
  document.getElementById("cfg-show-tools")?.addEventListener("click", async () => {
    try {
      const { tools } = await api.fetchTools();
      setState({ showTools: true, toolsList: tools });
    } catch (e) {
      setState({ error: e instanceof Error ? e.message : String(e) });
    }
  });
  document.getElementById("tools-close")?.addEventListener("click", () => setState({ showTools: false }));
  document.getElementById("tools-overlay")?.addEventListener("click", (ev) => {
    if (ev.target === ev.currentTarget) setState({ showTools: false });
  });
  document.getElementById("cfg-refresh-models")?.addEventListener("click", () => {
    const draft = state.settingsDraft;
    if (draft) void fetchModelsForDraft(draft, true);
  });
  document.getElementById("cfg-save")?.addEventListener("click", async () => {
    const draft = state.settingsDraft;
    if (!draft) return;
    const apiKey = (document.getElementById("cfg-key") as HTMLInputElement).value;
    const baseUrl = (document.getElementById("cfg-base") as HTMLInputElement).value;
    const model = (document.getElementById("cfg-model") as HTMLSelectElement).value;
    const maxContextTurns = parseInt((document.getElementById("cfg-ctx-turns") as HTMLInputElement).value, 10);
    const maxContextMessages = parseInt((document.getElementById("cfg-ctx-msgs") as HTMLInputElement).value, 10);
    const maxTurnsPerRun = parseInt((document.getElementById("cfg-run-turns") as HTMLInputElement).value, 10);
    const maxToolCallsPerRun = parseInt((document.getElementById("cfg-tool-calls") as HTMLInputElement).value, 10);
    try {
      const config = await api.saveConfig({
        apiKey: apiKey || undefined,
        baseUrl: baseUrl || undefined,
        model: model || undefined,
        providerId: draft.providerId,
        maxContextTurns: Number.isFinite(maxContextTurns) ? maxContextTurns : 0,
        maxContextMessages: Number.isFinite(maxContextMessages) ? maxContextMessages : 0,
        maxTurnsPerRun: Number.isFinite(maxTurnsPerRun) ? maxTurnsPerRun : 0,
        maxToolCallsPerRun: Number.isFinite(maxToolCallsPerRun) ? maxToolCallsPerRun : 0,
      });
      setState({
        config,
        showSettings: false,
        settingsDraft: null,
        modelsStatus: null,
        error: null,
      });
      showToast("设置已保存");
    } catch (e) {
      setState({ error: e instanceof Error ? e.message : String(e) });
    }
  });
}

window.addEventListener("message", (ev) => {
  if (ev.origin !== window.location.origin) return;
  if (!ev.data || ev.data.type !== INSPECT_MESSAGE_TYPE) return;
  if (!state.preview || !state.previewInspectEnabled) return;
  const payload = typeof ev.data.payload === "string" ? ev.data.payload : null;
  if (!payload) return;
  setState({ previewSelectedElementJson: payload, previewOpinion: "" });
});

render();
bootstrap().catch((e) => {
  setState({ error: e instanceof Error ? e.message : String(e) });
});

window.addEventListener("beforeunload", () => disconnectEvents?.());
