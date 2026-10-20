(function(){const t=document.createElement("link").relList;if(t&&t.supports&&t.supports("modulepreload"))return;for(const r of document.querySelectorAll('link[rel="modulepreload"]'))a(r);new MutationObserver(r=>{for(const l of r)if(l.type==="childList")for(const g of l.addedNodes)g.tagName==="LINK"&&g.rel==="modulepreload"&&a(g)}).observe(document,{childList:!0,subtree:!0});function s(r){const l={};return r.integrity&&(l.integrity=r.integrity),r.referrerPolicy&&(l.referrerPolicy=r.referrerPolicy),r.crossOrigin==="use-credentials"?l.credentials="include":r.crossOrigin==="anonymous"?l.credentials="omit":l.credentials="same-origin",l}function a(r){if(r.ep)return;r.ep=!0;const l=s(r);fetch(r.href,l)}})();async function p(e){const t=await e.json();if(!e.ok)throw new Error(t.error||e.statusText);return t}async function ce(){return p(await fetch("/api/config"))}async function le(e){return p(await fetch("/api/config",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(e)}))}async function de(){return p(await fetch("/api/usage"))}async function ne(e,t=!1){const s=new URLSearchParams({provider:e});return t&&s.set("refresh","1"),p(await fetch(`/api/models?${s}`))}async function ue(e=!0){return p(await fetch("/api/sessions/rollover",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({summarize:e})}))}async function pe(){return(await p(await fetch("/api/artifacts"))).artifacts}async function me(e,t,s){return p(await fetch(`/api/sessions/${encodeURIComponent(e)}/artifacts/import`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({sourceSessionId:t,workspaceRelativePath:s})}))}async function fe(e){return(await p(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`))).files}async function ge(e,t){const s=new FormData;return s.append("file",t),p(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`,{method:"POST",body:s}))}async function ve(e,t){await p(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments/${encodeURIComponent(t)}`,{method:"DELETE"}))}function ye(e,t){const s=t.split("/").map(a=>encodeURIComponent(a)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${s}`}async function he(e){return(await p(await fetch(`/api/capabilities?q=${encodeURIComponent(e)}`))).text}async function be(){return p(await fetch("/api/sessions"))}async function N(){return p(await fetch("/api/sessions",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"}))}async function O(e){await p(await fetch(`/api/sessions/${encodeURIComponent(e)}/use`,{method:"POST",body:"{}"}))}async function we(e){await p(await fetch(`/api/sessions/${encodeURIComponent(e)}`,{method:"DELETE"}))}async function se(e){return(await p(await fetch(`/api/sessions/${encodeURIComponent(e)}/transcript`))).messages}async function Ee(e,t){await p(await fetch(`/api/sessions/${encodeURIComponent(e)}/messages`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({text:t})}))}async function $e(){await p(await fetch("/api/run/abort",{method:"POST",body:"{}"}))}function Ie(e){const t=new EventSource("/api/events");return t.onmessage=s=>{try{e(JSON.parse(s.data))}catch{}},()=>t.close()}const ke="ask_user",Se=["等待用户","waiting","ask_user"];function Re(e){try{const s=(JSON.parse(e.argumentsJson||"{}").questions||[]).map((a,r)=>({id:a.id||`q${r}`,prompt:a.prompt||a.question||"",options:a.options,allowFreeText:a.allowFreeText??a.allow_free_text??!0})).filter(a=>a.prompt);return s.length===0?null:{questions:s}}catch{return null}}function ae(e){if(!e.length)return null;for(let t=e.length-1;t>=0;t--){const s=e[t];if(s.role==="user")return null;if(s.role==="assistant"){for(const a of s.toolCalls||[])if(a.name===ke)return Re(a)}if(s.role==="toolResult"){const a=s.content||"";if(!Se.some(l=>a.toLowerCase().includes(l.toLowerCase())))return null}}return null}function Ae(e,t){return e.questions.map(a=>{const r=(t[a.id]||"").trim();return`${a.prompt}
→ ${r||"(未答)"}`}).join(`

`)}function Te(e,t,s,a,r){if(t||r||s.trim()||a.trim())return!1;const l=Le(e);return(l==null?void 0:l.role)==="assistant"}function Le(e){return e.filter(t=>t.role==="user"||t.role==="assistant").at(-1)}function Pe(){return{sessions:[],activeSessionId:null,config:null,lines:[],streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,pendingAsk:null,askAnswers:{},error:null,showSettings:!1,drawerOpen:!0,composer:"",usage:null,usagePanelOpen:!1,toast:null,attachments:[],artifacts:[],showArtifacts:!1,preview:null,capQuery:"",capResult:"",modelOptions:[]}}function xe(e){const t=e.toLowerCase();return t.endsWith(".html")||t.endsWith(".htm")}function U(e){return e.isRunning||e.streamingText.trim()||e.streamingReasoning.trim()||e.tools.length>0||e.pendingAsk?!1:!e.lines.some(t=>t.role==="user"||t.role==="assistant")}function ie(e){const t=[];return e.forEach((s,a)=>{var r;if(s.role==="user"){t.push({id:`u-${a}`,role:"user",content:s.content});return}if(s.role==="assistant"){(r=s.content)!=null&&r.trim()&&t.push({id:`a-${a}`,role:"assistant",content:s.content});for(const l of s.toolCalls||[])t.push({id:`t-${l.id}`,role:"tool",content:l.argsPreview||l.argumentsJson||"",toolName:l.name});return}if(s.role==="toolResult"){t.push({id:`tr-${a}`,role:"tool",content:s.content,toolName:"result",error:!!s.error});return}s.role}),t}function T(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,s=Math.trunc(t);return t===s?`${s}k`:`${t.toFixed(1)}k`}function Be(e){if(!e||!e.hasUsage)return{headline:"累计用量",detail:"暂无 usage 记录"};const t=`约 ${T(e.totalTokens)} tokens`,s=e.cacheHitPercent,a=s!=null?`输入 ${T(e.inputTokens)} · 缓存 ${s}%`:`输入 ${T(e.inputTokens)} · 输出 ${T(e.outputTokens)}`;return{headline:t,detail:a}}function Oe(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;")}function Ce(e){const t=[`输入 ${e.inputTokens.toLocaleString()}`,`输出 ${e.outputTokens.toLocaleString()}`,`缓存 ${e.cachedTokens.toLocaleString()}`,`usage 事件 ${e.usageEvents}`];let s="";return e.byModel.length&&(s="<ul>"+e.byModel.map(a=>`<li><strong>${Oe(a.modelId||"（未知模型）")}</strong>：入 ${a.inputTokens.toLocaleString()} / 出 ${a.outputTokens.toLocaleString()}</li>`).join("")+"</ul>"),`<p>${t.join(" · ")}</p>${s}`}const oe=document.getElementById("app");if(!oe)throw new Error("#app missing");let n=Pe(),P=null;function o(e){n={...n,...e},re()}function c(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}async function v(){const e=await be();o({sessions:e.sessions.slice().sort((t,s)=>s.updatedAt.localeCompare(t.updatedAt)),activeSessionId:e.activeSessionId??n.activeSessionId,isRunning:e.runInProgress})}async function E(){try{const e=await de();o({usage:e})}catch{}}function B(e){o({toast:e}),window.setTimeout(()=>{n.toast===e&&o({toast:null})},2200)}async function Z(){if(n.isRunning){o({error:"请先停止当前 Run"});return}if(n.activeSessionId&&U(n)){B("已是新会话");return}const e=await N();await v(),await w(e.sessionId),await E(),o({drawerOpen:!1})}async function Ne(){if(n.isRunning){o({error:"请先停止当前 Run"});return}if(n.activeSessionId&&U(n)){B("已是新会话");return}const e=await ue(!0);await v(),await w(e.session.sessionId),await E(),o({drawerOpen:!1}),B(e.summaryPath?"已总结并新建会话":"已新建会话")}async function ee(e=!1){var s,a;const t=((s=document.getElementById("cfg-provider"))==null?void 0:s.value)||((a=n.config)==null?void 0:a.providerId)||"dashscope";try{const{models:r}=await ne(t,e);o({modelOptions:r})}catch{}}async function L(){var t;const e=((t=n.config)==null?void 0:t.providerId)||"dashscope";try{const{models:s}=await ne(e,!1);o({showSettings:!0,modelOptions:s})}catch{o({showSettings:!0})}}async function x(e){try{const t=await fe(e);o({attachments:t})}catch{o({attachments:[]})}}async function w(e){const t=await se(e),s=ae(t);o({activeSessionId:e,lines:ie(t),pendingAsk:s,askAnswers:{},streamingText:"",streamingReasoning:"",tools:[],error:null}),await x(e)}async function Ue(){const[e]=await Promise.all([ce(),v()]);o({config:e});let t=n.activeSessionId;t||(n.sessions.length===0?(t=(await N()).sessionId,await v()):(t=n.sessions[0].sessionId,await O(t))),await w(t),await E(),P=Ie(je)}function je(e){if(!(e.sessionId&&n.activeSessionId&&e.sessionId!==n.activeSessionId))switch(e.type){case"AGENT_START":o({isRunning:!0,streamingText:"",streamingReasoning:"",tools:[],error:null});break;case"MESSAGE_UPDATE":o({streamingText:n.streamingText+String(e.payload.delta||""),isRunning:!0});break;case"REASONING_UPDATE":o({streamingReasoning:n.streamingReasoning+String(e.payload.delta||""),isRunning:!0});break;case"MESSAGE_RESET":o({streamingText:"",streamingReasoning:""});break;case"TOOL_EXECUTION_START":{const t=e.payload.toolCall,s={id:t.id||`tool-${Date.now()}`,toolCallId:t.id||"",toolName:t.name||"tool",argsPreview:t.argsPreview||"",running:!0};o({tools:[...n.tools,s],isRunning:!0});break}case"TOOL_EXECUTION_END":{const t=String(e.payload.toolCallId||""),s=n.tools.map(r=>r.toolCallId===t?{...r,running:!1,error:!!(e.payload.error||e.payload.toolError),resultSummary:String(e.payload.resultText||"")}:r),a=!!e.payload.stopRunWaitingUser;o({tools:s,isRunning:!a&&n.isRunning});break}case"AGENT_ERROR":o({error:String(e.payload.message||"运行出错"),isRunning:!1}),te();break;case"AGENT_END":o({isRunning:!1}),te();break}}async function te(){if(n.activeSessionId)try{const e=await se(n.activeSessionId);o({lines:ie(e),pendingAsk:ae(e),streamingText:"",streamingReasoning:"",tools:[],isRunning:!1}),await v(),await E()}catch(e){o({error:e instanceof Error?e.message:String(e)})}}async function C(){var a;const e=n.composer.trim();if(!e||!n.activeSessionId||n.isRunning||n.pendingAsk)return;if(!((a=n.config)!=null&&a.hasApiKey)){o({showSettings:!0,error:"请先配置 API Key"});return}const t={id:`local-${Date.now()}`,role:"user",content:e};o({lines:[...n.lines,t],composer:"",isRunning:!0,streamingText:"",tools:[],error:null});let s=e;if(n.attachments.length>0){const r=n.attachments.map(l=>`- ${l.workspaceRelativePath}`);s+=`

[用户附件]
${r.join(`
`)}`}try{await Ee(n.activeSessionId,s)}catch(r){o({isRunning:!1,error:r instanceof Error?r.message:String(r)})}}async function _e(){if(!n.pendingAsk||!n.activeSessionId)return;const e=Ae(n.pendingAsk,n.askAnswers);o({pendingAsk:null,composer:e}),await C()}function Me(e){return e.role==="tool"?`<details class="tool-card"><summary>⚙ ${c(e.toolName||"tool")}</summary>
      <div class="tool-row ${e.error?"err":""}">${c(e.content)}</div></details>`:`<div class="bubble ${e.role}">${c(e.content)}</div>`}function De(){if(!n.tools.length&&!n.streamingText&&!n.streamingReasoning)return"";const e=n.tools.map(s=>{const a=s.running?"进行中…":s.error?"失败":"完成";return`<div class="tool-row ${s.error?"err":""}"><strong>${c(s.toolName)}</strong> · ${a}
        ${s.argsPreview?`<div>${c(s.argsPreview)}</div>`:""}
        ${s.resultSummary?`<div>${c(s.resultSummary)}</div>`:""}
      </div>`}).join("");return`<details class="tool-card" open><summary>思考与执行</summary>${[n.streamingReasoning?`<div class="tool-row">思考：${c(n.streamingReasoning)}</div>`:"",e].join("")}</details>
    ${n.streamingText?`<div class="bubble assistant">${c(n.streamingText)}</div>`:""}`}function qe(){return n.pendingAsk?`<div class="ask-panel"><h3>请回答后继续</h3>${n.pendingAsk.questions.map(t=>{const s=(t.options||[]).map(a=>`<button type="button" class="chip ${n.askAnswers[t.id]===a?"selected":""}" data-ask-id="${c(t.id)}" data-ask-opt="${c(a)}">${c(a)}</button>`).join("");return`<div class="ask-q">
        <label>${c(t.prompt)}</label>
        ${s?`<div class="ask-options">${s}</div>`:""}
        <input data-ask-input="${c(t.id)}" value="${c(n.askAnswers[t.id]||"")}" placeholder="你的回答" />
      </div>`}).join("")}
    <button type="button" class="primary-btn" id="ask-submit">提交回答</button></div>`:""}function Fe(){if(!n.showArtifacts)return"";const e=n.artifacts.map(t=>{const a=xe(t.workspaceRelativePath)?`<button type="button" class="link-btn" data-art-preview="1" data-art-session="${c(t.sessionId)}" data-art-path="${c(t.workspaceRelativePath)}">预览</button>`:"",r=n.activeSessionId?`<button type="button" class="link-btn" data-art-insert="1" data-art-session="${c(t.sessionId)}" data-art-path="${c(t.workspaceRelativePath)}">插入当前会话</button>`:"";return`<li><strong>${c(t.fileName)}</strong>
        <span class="muted"> · ${c(t.sessionId.slice(0,8))} · ${Math.round(t.sizeBytes/1024)}KB</span>
        <div class="artifact-actions">${a} ${r}</div></li>`}).join("");return`<div class="overlay" id="artifacts-overlay">
    <div class="overlay-panel wide-panel">
      <h2>产物库</h2>
      <p class="muted">workspace 内 AI 产出（不含 imports/、tmp/）</p>
      <div class="cap-search">
        <input id="cap-query" placeholder="find_caps 检索…" value="${c(n.capQuery)}" />
        <button type="button" class="primary-btn" id="cap-search-btn">检索</button>
      </div>
      ${n.capResult?`<pre class="cap-result">${c(n.capResult)}</pre>`:""}
      <ul class="artifact-list">${e||"<li class='muted'>暂无产物</li>"}</ul>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="artifacts-close">关闭</button>
      </div>
    </div>
  </div>`}function Ke(){if(!n.preview)return"";const e=ye(n.preview.sessionId,n.preview.path);return`<div class="overlay preview-overlay" id="preview-overlay">
    <div class="preview-panel">
      <div class="preview-toolbar">
        <span>${c(n.preview.path)}</span>
        <button type="button" class="icon-btn" id="preview-close">✕</button>
      </div>
      <iframe class="preview-frame" src="${e}" title="preview"></iframe>
    </div>
  </div>`}function Je(){return!n.usagePanelOpen||!n.usage?"":`<div class="overlay" id="usage-overlay">
    <div class="overlay-panel usage-panel">
      <h2>累计用量</h2>
      <div class="usage-detail">${Ce(n.usage)}</div>
      <p class="muted">来自 agentRoot/logs/events.jsonl</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="usage-close">关闭</button>
      </div>
    </div>
  </div>`}function ze(){if(!n.showSettings)return"";const e=n.config,t=(e==null?void 0:e.providerId)||"dashscope",s=n.modelOptions.map(a=>`<option value="${c(a.modelId)}">${c(a.title)} — ${c(a.subtitle)}</option>`).join("");return`<div class="overlay" id="settings-overlay">
    <div class="overlay-panel settings-panel">
      <h2>模型设置</h2>
      <p class="muted">保存到 agentRoot/ui-config.json；对齐 Android 模型与 Run 限额。</p>
      <div class="field"><label>服务商</label>
        <select id="cfg-provider">
          <option value="dashscope" ${t==="dashscope"?"selected":""}>DashScope / 通义</option>
          <option value="deepseek" ${t==="deepseek"?"selected":""}>DeepSeek</option>
          <option value="zhipu_coding" ${t==="zhipu_coding"?"selected":""}>智谱 Coding Plan</option>
        </select></div>
      <div class="field"><label>API Key</label>
        <input id="cfg-key" type="password" placeholder="${c((e==null?void 0:e.apiKeyHint)||"sk-…")}" /></div>
      <div class="field"><label>Base URL</label>
        <input id="cfg-base" value="${c((e==null?void 0:e.baseUrl)||"")}" /></div>
      <div class="field"><label>Model</label>
        <input id="cfg-model" list="cfg-model-list" value="${c((e==null?void 0:e.model)||"")}" />
        <datalist id="cfg-model-list">${s}</datalist>
        <button type="button" class="text-btn" id="cfg-refresh-models">从网络刷新模型列表</button></div>
      <div class="field-row">
        <div class="field"><label>上下文轮次</label>
          <input id="cfg-ctx-turns" type="number" min="0" value="${(e==null?void 0:e.maxContextTurns)??6}" /></div>
        <div class="field"><label>消息条数上限</label>
          <input id="cfg-ctx-msgs" type="number" min="0" value="${(e==null?void 0:e.maxContextMessages)??0}" /></div>
      </div>
      <div class="field-row">
        <div class="field"><label>单 Run 轮次</label>
          <input id="cfg-run-turns" type="number" min="0" value="${(e==null?void 0:e.maxTurnsPerRun)??24}" /></div>
        <div class="field"><label>工具调用上限</label>
          <input id="cfg-tool-calls" type="number" min="0" value="${(e==null?void 0:e.maxToolCallsPerRun)??48}" /></div>
      </div>
      <p class="muted">限额填 0 表示沿用环境变量或 core 默认。</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="cfg-save">保存</button>
        <button type="button" class="icon-btn" id="cfg-close">关闭</button>
      </div>
    </div>
  </div>`}function re(){var I;const e=n.sessions.map(f=>{const R=f.sessionId===n.activeSessionId?"active":"",A=f.title||f.sessionId.slice(0,8);return`<div class="session-item ${R}">
        <button type="button" class="session-title" data-use="${f.sessionId}">${c(A)}</button>
        <button type="button" class="session-delete" data-del="${f.sessionId}" title="删除">✕</button>
      </div>`}).join(""),t=Te(n.lines,n.isRunning,n.streamingText,n.streamingReasoning,!!n.pendingAsk),s=n.lines.map(Me).join(""),a=!n.isRunning&&!n.pendingAsk&&n.lines.length>0&&!U(n),r=t?'<button type="button" class="new-topic-btn" id="btn-new-topic">开始新话题</button>':"",l=a?'<button type="button" class="new-topic-btn secondary" id="btn-summarize-new">总结并新开话题</button>':"",g=n.lines.length===0&&!n.streamingText&&!n.isRunning?'<div class="landing"><h2>Agent1</h2><p>生产力助手 · 与 Android 端同款纸本风格。左侧管理会话，下方输入开始对话。</p></div>':s+r+l+De()+qe(),$=Be(n.usage),y=n.isRunning||!!n.pendingAsk,S=n.pendingAsk?"请在上方卡片回答":n.isRunning?"生成中…":"输入消息，Enter 发送，Shift+Enter 换行";oe.innerHTML=`
  <div class="app">
    <aside class="drawer ${n.drawerOpen?"":"collapsed"}">
      <div class="drawer-header">
        <div class="brand">Agent1</div>
        <div class="drawer-actions">
          <button type="button" class="icon-btn" id="btn-new" title="新会话">＋</button>
          <button type="button" class="icon-btn" id="btn-settings" title="设置">⚙</button>
        </div>
      </div>
      <div class="session-list">${e}</div>
      <button type="button" class="usage-footer" id="usage-footer">
        <span class="usage-headline">${c($.headline)}</span>
        <span class="usage-detail-line">${c($.detail)}</span>
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
      ${n.toast?`<div class="toast-banner">${c(n.toast)}</div>`:""}
      ${n.error?`<div class="error-banner">${c(n.error)}</div>`:""}
      <div class="chat-scroll" id="chat-scroll">${g}</div>
      <div class="composer-wrap">
        ${n.config&&!n.config.hasApiKey?'<div class="inline-setup">尚未配置 API Key。<button type="button" id="btn-setup">打开设置</button></div>':""}
        ${n.attachments.length?`<div class="attachment-chips">${n.attachments.map(f=>`<span class="chip">${c(f.fileName)} <button type="button" data-rm-attach="${c(f.workspaceRelativePath)}">×</button></span>`).join("")}</div>`:""}
        <div class="composer">
          <input type="file" id="file-input" class="hidden-file" multiple />
          <button type="button" class="icon-btn" id="btn-attach" title="添加附件" ${y?"disabled":""}>📎</button>
          <textarea id="composer" ${y?"disabled":""} placeholder="${S}">${c(n.composer)}</textarea>
          ${n.isRunning?'<button type="button" class="danger-btn" id="btn-stop">停止</button>':`<button type="button" class="primary-btn" id="btn-send" ${y?"disabled":""}>发送</button>`}
        </div>
        <div class="model-chip">模型 ${c(((I=n.config)==null?void 0:I.model)||"—")} ·
          <button type="button" id="btn-model">更改</button></div>
      </div>
    </section>
    ${ze()}
    ${Je()}
    ${Fe()}
    ${Ke()}
  </div>`,He();const h=document.getElementById("chat-scroll");h&&(h.scrollTop=h.scrollHeight)}function He(){var s,a,r,l,g,$,y,S,h,I,f,R,A,j,_,M,D,q,F,K,J,z,H,G,Q,W;(s=document.getElementById("btn-new"))==null||s.addEventListener("click",()=>void Z()),(a=document.getElementById("btn-new-topic"))==null||a.addEventListener("click",()=>void Z()),(r=document.getElementById("btn-summarize-new"))==null||r.addEventListener("click",()=>void Ne()),(l=document.getElementById("btn-drawer"))==null||l.addEventListener("click",()=>{const i=!n.drawerOpen;o({drawerOpen:i}),i&&E()}),(g=document.getElementById("usage-footer"))==null||g.addEventListener("click",()=>{E().then(()=>o({usagePanelOpen:!0}))}),($=document.getElementById("usage-close"))==null||$.addEventListener("click",()=>o({usagePanelOpen:!1})),(y=document.getElementById("usage-overlay"))==null||y.addEventListener("click",i=>{i.target===i.currentTarget&&o({usagePanelOpen:!1})}),(S=document.getElementById("btn-settings"))==null||S.addEventListener("click",()=>void L()),(h=document.getElementById("btn-settings-2"))==null||h.addEventListener("click",()=>void L()),(I=document.getElementById("btn-setup"))==null||I.addEventListener("click",()=>void L()),(f=document.getElementById("btn-model"))==null||f.addEventListener("click",()=>void L()),(R=document.getElementById("btn-artifacts"))==null||R.addEventListener("click",async()=>{const i=await pe();o({artifacts:i,showArtifacts:!0,capQuery:"",capResult:""})}),(A=document.getElementById("artifacts-close"))==null||A.addEventListener("click",()=>o({showArtifacts:!1})),(j=document.getElementById("artifacts-overlay"))==null||j.addEventListener("click",i=>{i.target===i.currentTarget&&o({showArtifacts:!1})}),(_=document.getElementById("cap-search-btn"))==null||_.addEventListener("click",async()=>{var u;const i=document.getElementById("cap-query"),d=((u=i==null?void 0:i.value)==null?void 0:u.trim())||"";n={...n,capQuery:d};try{const m=await he(d);o({capResult:m})}catch(m){o({capResult:m instanceof Error?m.message:String(m)})}}),document.querySelectorAll("[data-art-preview]").forEach(i=>{i.addEventListener("click",()=>{const d=i.dataset.artSession,u=i.dataset.artPath;d&&u&&o({preview:{sessionId:d,path:u},showArtifacts:!1})})}),document.querySelectorAll("[data-art-insert]").forEach(i=>{i.addEventListener("click",async()=>{const d=i.dataset.artSession,u=i.dataset.artPath,m=n.activeSessionId;if(!(!m||!d||!u))try{await me(m,d,u),await x(m),B("已复制到 imports/")}catch(b){o({error:b instanceof Error?b.message:String(b)})}})}),(M=document.getElementById("preview-close"))==null||M.addEventListener("click",()=>o({preview:null})),(D=document.getElementById("preview-overlay"))==null||D.addEventListener("click",i=>{i.target===i.currentTarget&&o({preview:null})});const e=document.getElementById("file-input");(q=document.getElementById("btn-attach"))==null||q.addEventListener("click",()=>e==null?void 0:e.click()),e==null||e.addEventListener("change",async()=>{var d;const i=n.activeSessionId;if(!(!i||!((d=e.files)!=null&&d.length)))try{for(const u of Array.from(e.files))await ge(i,u);await x(i),e.value=""}catch(u){o({error:u instanceof Error?u.message:String(u)})}}),document.querySelectorAll("[data-rm-attach]").forEach(i=>{i.addEventListener("click",async()=>{const d=n.activeSessionId,u=i.dataset.rmAttach;!d||!u||(await ve(d,u),await x(d))})}),document.querySelectorAll("[data-use]").forEach(i=>{i.addEventListener("click",async()=>{const d=i.dataset.use;if(n.isRunning){o({error:"请先停止当前 Run 再切换会话"});return}await O(d),await w(d),await v()})}),document.querySelectorAll("[data-del]").forEach(i=>{i.addEventListener("click",async d=>{d.stopPropagation();const u=i.dataset.del;if(confirm("删除该会话？")&&(await we(u),await v(),n.activeSessionId===u))if(n.sessions[0])await O(n.sessions[0].sessionId),await w(n.sessions[0].sessionId);else{const m=await N();await v(),await w(m.sessionId)}})});const t=document.getElementById("composer");t==null||t.addEventListener("input",()=>{n={...n,composer:t.value}}),t==null||t.addEventListener("keydown",i=>{i.key==="Enter"&&!i.shiftKey&&(i.preventDefault(),C())}),(F=document.getElementById("btn-send"))==null||F.addEventListener("click",()=>void C()),(K=document.getElementById("btn-stop"))==null||K.addEventListener("click",async()=>{await $e()}),document.querySelectorAll("[data-ask-opt]").forEach(i=>{i.addEventListener("click",()=>{const d=i.dataset.askId,u=i.dataset.askOpt;o({askAnswers:{...n.askAnswers,[d]:u}})})}),document.querySelectorAll("[data-ask-input]").forEach(i=>{i.addEventListener("input",()=>{const d=i.dataset.askInput;n={...n,askAnswers:{...n.askAnswers,[d]:i.value}}})}),(J=document.getElementById("ask-submit"))==null||J.addEventListener("click",()=>void _e()),(z=document.getElementById("cfg-close"))==null||z.addEventListener("click",()=>o({showSettings:!1})),(H=document.getElementById("settings-overlay"))==null||H.addEventListener("click",i=>{i.target===i.currentTarget&&o({showSettings:!1})}),(G=document.getElementById("cfg-provider"))==null||G.addEventListener("change",()=>void ee(!1)),(Q=document.getElementById("cfg-refresh-models"))==null||Q.addEventListener("click",()=>void ee(!0)),(W=document.getElementById("cfg-save"))==null||W.addEventListener("click",async()=>{const i=document.getElementById("cfg-key").value,d=document.getElementById("cfg-base").value,u=document.getElementById("cfg-model").value,m=document.getElementById("cfg-provider").value,b=parseInt(document.getElementById("cfg-ctx-turns").value,10),X=parseInt(document.getElementById("cfg-ctx-msgs").value,10),V=parseInt(document.getElementById("cfg-run-turns").value,10),Y=parseInt(document.getElementById("cfg-tool-calls").value,10);try{const k=await le({apiKey:i||void 0,baseUrl:d||void 0,model:u||void 0,providerId:m,maxContextTurns:Number.isFinite(b)?b:0,maxContextMessages:Number.isFinite(X)?X:0,maxTurnsPerRun:Number.isFinite(V)?V:0,maxToolCallsPerRun:Number.isFinite(Y)?Y:0});o({config:k,showSettings:!1,error:null})}catch(k){o({error:k instanceof Error?k.message:String(k)})}})}re();Ue().catch(e=>{o({error:e instanceof Error?e.message:String(e)})});window.addEventListener("beforeunload",()=>P==null?void 0:P());
