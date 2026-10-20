import "./styles.css";
import * as api from "./api";
import { detectPendingAsk, formatAskReply } from "./askUser";
import { shouldOfferNewTopic } from "./newTopic";
import {
  AppState,
  ChatLine,
  createInitialState,
  isFreshSession,
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

const root = document.getElementById("app");
if (!root) {
  throw new Error("#app missing");
}

let state: AppState = createInitialState();
let disconnectEvents: (() => void) | null = null;

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

function showToast(message: string) {
  setState({ toast: message });
  window.setTimeout(() => {
    if (state.toast === message) setState({ toast: null });
  }, 2200);
}

async function startNewChat() {
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
  setState({ drawerOpen: false });
}

async function summarizeAndNewChat() {
  if (state.isRunning) {
    setState({ error: "请先停止当前 Run" });
    return;
  }
  if (state.activeSessionId && isFreshSession(state)) {
    showToast("已是新会话");
    return;
  }
  const result = await api.rolloverSession(true);
  await refreshSessions();
  await loadActiveTranscript(result.session.sessionId);
  await refreshUsage();
  setState({ drawerOpen: false });
  showToast(result.summaryPath ? "已总结并新建会话" : "已新建会话");
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
    webSearchBaseUrl: cfg?.webSearchBaseUrl || "https://api.tavily.com",
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
  setState({ showSettings: true, settingsDraft: draft, modelOptions: [], error: null });
  await fetchModelsForDraft(draft, false);
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
    return;
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
  return `<div class="overlay" id="artifacts-overlay">
    <div class="overlay-panel wide-panel">
      <h2>产物库</h2>
      <p class="muted">workspace 内 AI 产出（不含 imports/、tmp/）</p>
      <div class="cap-search">
        <input id="cap-query" placeholder="find_caps 检索…" value="${escapeHtml(state.capQuery)}" />
        <button type="button" class="primary-btn" id="cap-search-btn">检索</button>
      </div>
      ${state.capResult ? `<pre class="cap-result">${escapeHtml(state.capResult)}</pre>` : ""}
      <ul class="artifact-list">${rows || "<li class='muted'>暂无产物</li>"}</ul>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="artifacts-close">关闭</button>
      </div>
    </div>
  </div>`;
}

function renderPreviewPanel(): string {
  if (!state.preview) return "";
  const url = api.workspaceFileUrl(state.preview.sessionId, state.preview.path);
  return `<div class="overlay preview-overlay" id="preview-overlay">
    <div class="preview-panel">
      <div class="preview-toolbar">
        <span>${escapeHtml(state.preview.path)}</span>
        <button type="button" class="icon-btn" id="preview-close">✕</button>
      </div>
      <iframe class="preview-frame" src="${url}" title="preview"></iframe>
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
  return `<div class="overlay settings-overlay" id="mcp-overlay">
    <div class="overlay-panel settings-panel">
      <header class="settings-header">
        <h2>MCP 服务器</h2>
        <button type="button" class="icon-btn" id="mcp-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <p class="muted">配置写入 agentRoot/mcp_servers.json；脚本内通过 $mcp 调用。保存后会刷新 find_caps 索引。</p>
        ${state.mcpStatus ? `<p class="settings-hint">${escapeHtml(state.mcpStatus)}</p>` : ""}
        <div id="mcp-list">${rows}</div>
        <button type="button" class="link-btn" id="mcp-add">添加服务器</button>
      </div>
      <footer class="settings-footer">
        <button type="button" class="primary-btn" id="mcp-save">保存并索引</button>
      </footer>
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
          <h3>联网搜索（Tavily）</h3>
          <div class="field">
            <label for="cfg-web-key">Web Search API Key</label>
            <input id="cfg-web-key" class="settings-control" type="password" autocomplete="off"
              placeholder="${cfg?.hasWebSearch ? "已配置，留空不修改" : "tvly-…"}" />
          </div>
          <div class="field">
            <label for="cfg-web-base">Tavily Base URL</label>
            <input id="cfg-web-base" class="settings-control" value="${escapeHtml(draft.webSearchBaseUrl)}" />
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
        <button type="button" class="link-btn" id="cfg-open-mcp">MCP 服务器</button>
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
      return `<div class="session-item ${active}">
        <button type="button" class="session-title" data-use="${s.sessionId}">${escapeHtml(title)}</button>
        <button type="button" class="session-delete" data-del="${s.sessionId}" title="删除">✕</button>
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
  const canSummarizeRollover =
    !state.isRunning && !state.pendingAsk && state.lines.length > 0 && !isFreshSession(state);
  const newTopicBtn = offerNewTopic
    ? `<button type="button" class="new-topic-btn" id="btn-new-topic">开始新话题</button>`
    : "";
  const summarizeBtn = canSummarizeRollover
    ? `<button type="button" class="new-topic-btn secondary" id="btn-summarize-new">总结并新开话题</button>`
    : "";
  const runTokenRow =
    state.lastRunTokenSummary && !state.isRunning
      ? `<div class="run-token-summary ${state.lastRunTokenSummary.failed ? "failed" : ""}">${escapeHtml(runTokenSummaryLine(state.lastRunTokenSummary))}</div>`
      : "";
  const chatBody =
    state.lines.length === 0 && !state.streamingText && !state.isRunning
      ? `<div class="landing"><h2>Agent1</h2><p>生产力助手 · 与 Android 端同款纸本风格。左侧管理会话，下方输入开始对话。</p></div>`
      : chatLinesHtml + runTokenRow + newTopicBtn + summarizeBtn + renderLiveTools() + renderAskPanel();

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
          <button type="button" class="icon-btn" id="btn-brief" title="复制会话简报">📋</button>
          <button type="button" class="icon-btn" id="btn-diag" title="导出诊断包">🩺</button>
          <button type="button" class="icon-btn" id="btn-settings" title="设置">⚙</button>
        </div>
      </div>
      <div class="session-list">${sessions}</div>
      <button type="button" class="usage-footer" id="usage-footer">
        <span class="usage-headline">${escapeHtml(usageFoot.headline)}</span>
        <span class="usage-detail-line">${escapeHtml(usageFoot.detail)}</span>
      </button>
    </aside>
    <section class="main">
      <div class="topbar">
        <button type="button" class="icon-btn" id="btn-drawer" title="会话">☰</button>
        <h1>对话</h1>
        <div class="topbar-actions">
          <button type="button" class="icon-btn" id="btn-artifacts" title="产物库">📁</button>
          <button type="button" class="icon-btn" id="btn-settings-2" title="设置">⚙</button>
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
    ${renderToolsPanel()}
    ${renderUsagePanel()}
    ${renderArtifactsPanel()}
    ${renderPreviewPanel()}
  </div>`;

  bindEvents();
  const scroll = document.getElementById("chat-scroll");
  if (scroll) scroll.scrollTop = scroll.scrollHeight;
}

function bindEvents() {
  document.getElementById("btn-new")?.addEventListener("click", () => void startNewChat());
  document.getElementById("btn-brief")?.addEventListener("click", async () => {
    const sid = state.activeSessionId;
    if (!sid) return;
    try {
      const { text } = await api.fetchBrief(sid);
      await navigator.clipboard.writeText(text);
      showToast("简报已复制到剪贴板");
    } catch (e) {
      setState({ error: e instanceof Error ? e.message : String(e) });
    }
  });
  document.getElementById("btn-diag")?.addEventListener("click", async () => {
    try {
      await api.downloadDiagnostics();
      showToast("诊断包已下载");
    } catch (e) {
      setState({ error: e instanceof Error ? e.message : String(e) });
    }
  });
  document.getElementById("btn-new-topic")?.addEventListener("click", () => void startNewChat());
  document.getElementById("btn-summarize-new")?.addEventListener("click", () => void summarizeAndNewChat());
  document.getElementById("btn-drawer")?.addEventListener("click", () => {
    const next = !state.drawerOpen;
    setState({ drawerOpen: next });
    if (next) void refreshUsage();
  });
  document.getElementById("usage-footer")?.addEventListener("click", () => {
    void refreshUsage().then(() => setState({ usagePanelOpen: true }));
  });
  document.getElementById("usage-close")?.addEventListener("click", () => setState({ usagePanelOpen: false }));
  document.getElementById("usage-overlay")?.addEventListener("click", (ev) => {
    if (ev.target === ev.currentTarget) setState({ usagePanelOpen: false });
  });
  document.getElementById("btn-settings")?.addEventListener("click", () => void openSettings());
  document.getElementById("btn-settings-2")?.addEventListener("click", () => void openSettings());
  document.getElementById("btn-setup")?.addEventListener("click", () => void openSettings());
  document.getElementById("btn-model")?.addEventListener("click", () => void openSettings());
  document.getElementById("btn-artifacts")?.addEventListener("click", async () => {
    const artifacts = await api.fetchArtifacts();
    setState({ artifacts, showArtifacts: true, capQuery: "", capResult: "" });
  });
  document.getElementById("artifacts-close")?.addEventListener("click", () => setState({ showArtifacts: false }));
  document.getElementById("artifacts-overlay")?.addEventListener("click", (ev) => {
    if (ev.target === ev.currentTarget) setState({ showArtifacts: false });
  });
  document.getElementById("cap-search-btn")?.addEventListener("click", async () => {
    const input = document.getElementById("cap-query") as HTMLInputElement;
    const q = input?.value?.trim() || "";
    state = { ...state, capQuery: q };
    try {
      const text = await api.searchCapabilities(q);
      setState({ capResult: text });
    } catch (e) {
      setState({ capResult: e instanceof Error ? e.message : String(e) });
    }
  });
  document.querySelectorAll<HTMLButtonElement>("[data-art-preview]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const sessionId = btn.dataset.artSession;
      const path = btn.dataset.artPath;
      if (sessionId && path) setState({ preview: { sessionId, path }, showArtifacts: false });
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
  document.getElementById("preview-close")?.addEventListener("click", () => setState({ preview: null }));
  document.getElementById("preview-overlay")?.addEventListener("click", (ev) => {
    if (ev.target === ev.currentTarget) setState({ preview: null });
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
    btn.addEventListener("click", async (ev) => {
      ev.stopPropagation();
      const id = btn.dataset.del!;
      if (!confirm("删除该会话？")) return;
      await api.deleteSession(id);
      await refreshSessions();
      if (state.activeSessionId === id) {
        if (state.sessions[0]) {
          await api.useSession(state.sessions[0].sessionId);
          await loadActiveTranscript(state.sessions[0].sessionId);
        } else {
          const created = await api.createSession();
          await refreshSessions();
          await loadActiveTranscript(created.sessionId);
        }
      }
    });
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
  async function openMcpPanel() {
    try {
      const { servers } = await api.fetchMcpServers();
      setState({ showMcp: true, mcpServers: servers, mcpStatus: null, showSettings: false });
    } catch (e) {
      setState({ error: e instanceof Error ? e.message : String(e) });
    }
  }
  document.getElementById("cfg-open-mcp")?.addEventListener("click", () => void openMcpPanel());
  document.getElementById("mcp-close")?.addEventListener("click", () => setState({ showMcp: false }));
  document.getElementById("mcp-overlay")?.addEventListener("click", (ev) => {
    if (ev.target === ev.currentTarget) setState({ showMcp: false });
  });
  document.getElementById("mcp-add")?.addEventListener("click", () => {
    setState({
      mcpServers: [
        ...state.mcpServers,
        { name: "server", url: "https://", enabled: true, headers: {} },
      ],
    });
  });
  document.querySelectorAll<HTMLButtonElement>(".mcp-remove").forEach((btn) => {
    btn.addEventListener("click", () => {
      const idx = Number(btn.dataset.mcpIndex);
      setState({ mcpServers: state.mcpServers.filter((_, i) => i !== idx) });
    });
  });
  document.getElementById("mcp-save")?.addEventListener("click", async () => {
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
    try {
      const result = await api.saveMcpServers(servers);
      const { servers: reloaded } = await api.fetchMcpServers();
      const warn = result.warnings.length ? ` · ${result.warnings.join("; ")}` : "";
      setState({
        mcpServers: reloaded,
        mcpStatus: `已索引 ${result.toolCount} 个 MCP 工具${warn}`,
      });
      showToast("MCP 已保存");
    } catch (e) {
      setState({ error: e instanceof Error ? e.message : String(e) });
    }
  });
  document.querySelectorAll<HTMLButtonElement>(".file-link").forEach((btn) => {
    btn.addEventListener("click", () => {
      const path = btn.dataset.filePath;
      const sid = state.activeSessionId;
      if (!path || !sid) return;
      if (isPreviewablePath(path)) {
        setState({ preview: { sessionId: sid, path } });
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
    const webSearchApiKey = (document.getElementById("cfg-web-key") as HTMLInputElement).value;
    const webSearchBaseUrl = (document.getElementById("cfg-web-base") as HTMLInputElement).value;
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
        webSearchApiKey: webSearchApiKey || undefined,
        webSearchBaseUrl: webSearchBaseUrl || undefined,
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

render();
bootstrap().catch((e) => {
  setState({ error: e instanceof Error ? e.message : String(e) });
});

window.addEventListener("beforeunload", () => disconnectEvents?.());
