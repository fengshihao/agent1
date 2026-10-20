import "./styles.css";
import * as api from "./api";
import { detectPendingAsk, formatAskReply } from "./askUser";
import { shouldOfferNewTopic } from "./newTopic";
import {
  AppState,
  ChatLine,
  createInitialState,
  isFreshSession,
  messagesToLines,
  ToolPart,
} from "./state";
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
      setState({
        isRunning: true,
        streamingText: "",
        streamingReasoning: "",
        tools: [],
        error: null,
      });
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
      });
      void reloadAfterRun();
      break;
    case "AGENT_END":
      setState({ isRunning: false });
      void reloadAfterRun();
      break;
    default:
      break;
  }
}

async function reloadAfterRun() {
  if (!state.activeSessionId) return;
  try {
    const messages = await api.fetchTranscript(state.activeSessionId);
    setState({
      lines: messagesToLines(messages),
      pendingAsk: detectPendingAsk(messages),
      streamingText: "",
      streamingReasoning: "",
      tools: [],
      isRunning: false,
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
  });
  try {
    await api.sendMessage(state.activeSessionId, text);
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

function renderBubble(line: ChatLine): string {
  if (line.role === "tool") {
    return `<details class="tool-card"><summary>⚙ ${escapeHtml(line.toolName || "tool")}</summary>
      <div class="tool-row ${line.error ? "err" : ""}">${escapeHtml(line.content)}</div></details>`;
  }
  return `<div class="bubble ${line.role}">${escapeHtml(line.content)}</div>`;
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
  if (!state.showSettings) return "";
  const cfg = state.config;
  return `<div class="overlay" id="settings-overlay">
    <div class="overlay-panel">
      <h2>模型设置</h2>
      <p class="muted">保存到 agentRoot/ui-config.json；非空字段覆盖环境变量。</p>
      <div class="field"><label>API Key</label>
        <input id="cfg-key" type="password" placeholder="${escapeHtml(cfg?.apiKeyHint || "sk-…")}" /></div>
      <div class="field"><label>Base URL</label>
        <input id="cfg-base" value="${escapeHtml(cfg?.baseUrl || "")}" /></div>
      <div class="field"><label>Model</label>
        <input id="cfg-model" value="${escapeHtml(cfg?.model || "")}" /></div>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="cfg-save">保存</button>
        <button type="button" class="icon-btn" id="cfg-close">关闭</button>
      </div>
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
  const chatLinesHtml = state.lines.map(renderBubble).join("");
  const newTopicBtn = offerNewTopic
    ? `<button type="button" class="new-topic-btn" id="btn-new-topic">开始新话题</button>`
    : "";
  const chatBody =
    state.lines.length === 0 && !state.streamingText && !state.isRunning
      ? `<div class="landing"><h2>Agent1</h2><p>生产力助手 · 与 Android 端同款纸本风格。左侧管理会话，下方输入开始对话。</p></div>`
      : chatLinesHtml + newTopicBtn + renderLiveTools() + renderAskPanel();

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
        <button type="button" class="icon-btn" id="btn-settings-2" title="设置">⚙</button>
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
        <div class="composer">
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
    ${renderUsagePanel()}
  </div>`;

  bindEvents();
  const scroll = document.getElementById("chat-scroll");
  if (scroll) scroll.scrollTop = scroll.scrollHeight;
}

function bindEvents() {
  document.getElementById("btn-new")?.addEventListener("click", () => void startNewChat());
  document.getElementById("btn-new-topic")?.addEventListener("click", () => void startNewChat());
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
  const openSettings = () => setState({ showSettings: true });
  document.getElementById("btn-settings")?.addEventListener("click", openSettings);
  document.getElementById("btn-settings-2")?.addEventListener("click", openSettings);
  document.getElementById("btn-setup")?.addEventListener("click", openSettings);
  document.getElementById("btn-model")?.addEventListener("click", openSettings);

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

  document.getElementById("cfg-close")?.addEventListener("click", () => setState({ showSettings: false }));
  document.getElementById("settings-overlay")?.addEventListener("click", (ev) => {
    if (ev.target === ev.currentTarget) setState({ showSettings: false });
  });
  document.getElementById("cfg-save")?.addEventListener("click", async () => {
    const apiKey = (document.getElementById("cfg-key") as HTMLInputElement).value;
    const baseUrl = (document.getElementById("cfg-base") as HTMLInputElement).value;
    const model = (document.getElementById("cfg-model") as HTMLInputElement).value;
    try {
      const config = await api.saveConfig({
        apiKey: apiKey || undefined,
        baseUrl: baseUrl || undefined,
        model: model || undefined,
      });
      setState({ config, showSettings: false, error: null });
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
