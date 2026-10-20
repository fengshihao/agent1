(function(){const n=document.createElement("link").relList;if(n&&n.supports&&n.supports("modulepreload"))return;for(const i of document.querySelectorAll('link[rel="modulepreload"]'))o(i);new MutationObserver(i=>{for(const l of i)if(l.type==="childList")for(const p of l.addedNodes)p.tagName==="LINK"&&p.rel==="modulepreload"&&o(p)}).observe(document,{childList:!0,subtree:!0});function s(i){const l={};return i.integrity&&(l.integrity=i.integrity),i.referrerPolicy&&(l.referrerPolicy=i.referrerPolicy),i.crossOrigin==="use-credentials"?l.credentials="include":i.crossOrigin==="anonymous"?l.credentials="omit":l.credentials="same-origin",l}function o(i){if(i.ep)return;i.ep=!0;const l=s(i);fetch(i.href,l)}})();async function m(e){const n=await e.json();if(!e.ok)throw new Error(n.error||e.statusText);return n}async function ve(){return m(await fetch("/api/config"))}async function ye(e){return m(await fetch("/api/config",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(e)}))}async function he(){return m(await fetch("/api/usage"))}async function be(e,n=!1){const s=new URLSearchParams({provider:e});return n&&s.set("refresh","1"),m(await fetch(`/api/models?${s}`))}async function we(e=!0){return m(await fetch("/api/sessions/rollover",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({summarize:e})}))}async function Ee(){return(await m(await fetch("/api/artifacts"))).artifacts}async function $e(e,n,s){return m(await fetch(`/api/sessions/${encodeURIComponent(e)}/artifacts/import`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({sourceSessionId:n,workspaceRelativePath:s})}))}async function Ie(e){return(await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`))).files}async function ke(e,n){const s=new FormData;return s.append("file",n),m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`,{method:"POST",body:s}))}async function Se(e,n){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments/${encodeURIComponent(n)}`,{method:"DELETE"}))}function Re(e,n){const s=n.split("/").map(o=>encodeURIComponent(o)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${s}`}async function Te(e){return(await m(await fetch(`/api/capabilities?q=${encodeURIComponent(e)}`))).text}async function Ae(){return m(await fetch("/api/sessions"))}async function N(){return m(await fetch("/api/sessions",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"}))}async function B(e){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/use`,{method:"POST",body:"{}"}))}async function Le(e){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}`,{method:"DELETE"}))}async function de(e){return(await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/transcript`))).messages}async function xe(e,n){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/messages`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({text:n})}))}async function Pe(){await m(await fetch("/api/run/abort",{method:"POST",body:"{}"}))}function Ce(e){const n=new EventSource("/api/events");return n.onmessage=s=>{try{e(JSON.parse(s.data))}catch{}},()=>n.close()}const Be="ask_user",Oe=["等待用户","waiting","ask_user"];function Ue(e){try{const s=(JSON.parse(e.argumentsJson||"{}").questions||[]).map((o,i)=>({id:o.id||`q${i}`,prompt:o.prompt||o.question||"",options:o.options,allowFreeText:o.allowFreeText??o.allow_free_text??!0})).filter(o=>o.prompt);return s.length===0?null:{questions:s}}catch{return null}}function ue(e){if(!e.length)return null;for(let n=e.length-1;n>=0;n--){const s=e[n];if(s.role==="user")return null;if(s.role==="assistant"){for(const o of s.toolCalls||[])if(o.name===Be)return Ue(o)}if(s.role==="toolResult"){const o=s.content||"";if(!Oe.some(l=>o.toLowerCase().includes(l.toLowerCase())))return null}}return null}function Ne(e,n){return e.questions.map(o=>{const i=(n[o.id]||"").trim();return`${o.prompt}
→ ${i||"(未答)"}`}).join(`

`)}function De(e,n,s,o,i){if(n||i||s.trim()||o.trim())return!1;const l=Me(e);return(l==null?void 0:l.role)==="assistant"}function Me(e){return e.filter(n=>n.role==="user"||n.role==="assistant").at(-1)}function je(){return{sessions:[],activeSessionId:null,config:null,lines:[],streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,pendingAsk:null,askAnswers:{},error:null,showSettings:!1,drawerOpen:!0,composer:"",usage:null,usagePanelOpen:!1,toast:null,attachments:[],artifacts:[],showArtifacts:!1,preview:null,capQuery:"",capResult:"",modelOptions:[],settingsDraft:null,modelsLoading:!1,modelsStatus:null}}function _e(e){const n=e.toLowerCase();return n.endsWith(".html")||n.endsWith(".htm")}function D(e){return e.isRunning||e.streamingText.trim()||e.streamingReasoning.trim()||e.tools.length>0||e.pendingAsk?!1:!e.lines.some(n=>n.role==="user"||n.role==="assistant")}function pe(e){const n=[];return e.forEach((s,o)=>{var i;if(s.role==="user"){n.push({id:`u-${o}`,role:"user",content:s.content});return}if(s.role==="assistant"){(i=s.content)!=null&&i.trim()&&n.push({id:`a-${o}`,role:"assistant",content:s.content});for(const l of s.toolCalls||[])n.push({id:`t-${l.id}`,role:"tool",content:l.argsPreview||l.argumentsJson||"",toolName:l.name});return}if(s.role==="toolResult"){n.push({id:`tr-${o}`,role:"tool",content:s.content,toolName:"result",error:!!s.error});return}s.role}),n}const O=[{id:"dashscope",label:"通义",baseUrl:"https://dashscope.aliyuncs.com/compatible-mode/v1",defaultModel:"qwen3.7-flash"},{id:"deepseek",label:"DeepSeek",baseUrl:"https://api.deepseek.com",defaultModel:"deepseek-flash"},{id:"zhipu_coding",label:"智谱 Coding",baseUrl:"https://open.bigmodel.cn/api/coding/paas/v4",defaultModel:"glm-5.3"}];function Fe(e){const n=e.toLowerCase();return n.includes("deepseek.com")?"deepseek":n.includes("bigmodel.cn")?"zhipu_coding":"dashscope"}function M(e){return O.find(n=>n.id===e)??O[0]}function L(e){if(e<1e3)return String(e);const n=Math.round(e/100)/10,s=Math.trunc(n);return n===s?`${s}k`:`${n.toFixed(1)}k`}function qe(e){if(!e||!e.hasUsage)return{headline:"累计用量",detail:"暂无 usage 记录"};const n=`约 ${L(e.totalTokens)} tokens`,s=e.cacheHitPercent,o=s!=null?`输入 ${L(e.inputTokens)} · 缓存 ${s}%`:`输入 ${L(e.inputTokens)} · 输出 ${L(e.outputTokens)}`;return{headline:n,detail:o}}function Ke(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;")}function Je(e){const n=[`输入 ${e.inputTokens.toLocaleString()}`,`输出 ${e.outputTokens.toLocaleString()}`,`缓存 ${e.cachedTokens.toLocaleString()}`,`usage 事件 ${e.usageEvents}`];let s="";return e.byModel.length&&(s="<ul>"+e.byModel.map(o=>`<li><strong>${Ke(o.modelId||"（未知模型）")}</strong>：入 ${o.inputTokens.toLocaleString()} / 出 ${o.outputTokens.toLocaleString()}</li>`).join("")+"</ul>"),`<p>${n.join(" · ")}</p>${s}`}const me=document.getElementById("app");if(!me)throw new Error("#app missing");let t=je(),P=null;function r(e){t={...t,...e},ge()}function c(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}async function h(){const e=await Ae();r({sessions:e.sessions.slice().sort((n,s)=>s.updatedAt.localeCompare(n.updatedAt)),activeSessionId:e.activeSessionId??t.activeSessionId,isRunning:e.runInProgress})}async function $(){try{const e=await he();r({usage:e})}catch{}}function S(e){r({toast:e}),window.setTimeout(()=>{t.toast===e&&r({toast:null})},2200)}async function ce(){if(t.isRunning){r({error:"请先停止当前 Run"});return}if(t.activeSessionId&&D(t)){S("已是新会话");return}const e=await N();await h(),await E(e.sessionId),await $(),r({drawerOpen:!1})}async function ze(){if(t.isRunning){r({error:"请先停止当前 Run"});return}if(t.activeSessionId&&D(t)){S("已是新会话");return}const e=await we(!0);await h(),await E(e.session.sessionId),await $(),r({drawerOpen:!1}),S(e.summaryPath?"已总结并新建会话":"已新建会话")}function fe(){const e=t.config,n=(e==null?void 0:e.baseUrl)||"",s=(e==null?void 0:e.providerId)||Fe(n);return{providerId:s,baseUrl:n,model:(e==null?void 0:e.model)||M(s).defaultModel,maxContextTurns:(e==null?void 0:e.maxContextTurns)??6,maxContextMessages:(e==null?void 0:e.maxContextMessages)??0,maxTurnsPerRun:(e==null?void 0:e.maxTurnsPerRun)??24,maxToolCallsPerRun:(e==null?void 0:e.maxToolCallsPerRun)??48}}async function j(e,n){var s,o;r({modelsLoading:!0});try{const{models:i,source:l}=await be(e.providerId,n);let p=e.model;if(!i.some(g=>g.modelId===p)){const g=M(e.providerId);p=((s=i.find(y=>y.modelId===g.defaultModel))==null?void 0:s.modelId)??((o=i[0])==null?void 0:o.modelId)??p}r({modelOptions:i,modelsLoading:!1,settingsDraft:{...e,model:p},modelsStatus:n?`已从网络更新 · ${i.length} 个模型`:`共 ${i.length} 个模型`})}catch(i){r({modelsLoading:!1,error:i instanceof Error?i.message:"模型列表加载失败"})}}function He(e){const n=M(e),s={...t.settingsDraft??fe(),providerId:e,baseUrl:n.baseUrl,model:n.defaultModel};r({settingsDraft:s,error:null}),j(s,!1)}async function x(){const e=fe();r({showSettings:!0,settingsDraft:e,modelOptions:[],error:null}),await j(e,!1)}async function C(e){try{const n=await Ie(e);r({attachments:n})}catch{r({attachments:[]})}}async function E(e){const n=await de(e),s=ue(n);r({activeSessionId:e,lines:pe(n),pendingAsk:s,askAnswers:{},streamingText:"",streamingReasoning:"",tools:[],error:null}),await C(e)}async function Ge(){const[e]=await Promise.all([ve(),h()]);r({config:e});let n=t.activeSessionId;n||(t.sessions.length===0?(n=(await N()).sessionId,await h()):(n=t.sessions[0].sessionId,await B(n))),await E(n),await $(),P=Ce(Qe)}function Qe(e){if(!(e.sessionId&&t.activeSessionId&&e.sessionId!==t.activeSessionId))switch(e.type){case"AGENT_START":r({isRunning:!0,streamingText:"",streamingReasoning:"",tools:[],error:null});break;case"MESSAGE_UPDATE":r({streamingText:t.streamingText+String(e.payload.delta||""),isRunning:!0});break;case"REASONING_UPDATE":r({streamingReasoning:t.streamingReasoning+String(e.payload.delta||""),isRunning:!0});break;case"MESSAGE_RESET":r({streamingText:"",streamingReasoning:""});break;case"TOOL_EXECUTION_START":{const n=e.payload.toolCall,s={id:n.id||`tool-${Date.now()}`,toolCallId:n.id||"",toolName:n.name||"tool",argsPreview:n.argsPreview||"",running:!0};r({tools:[...t.tools,s],isRunning:!0});break}case"TOOL_EXECUTION_END":{const n=String(e.payload.toolCallId||""),s=t.tools.map(i=>i.toolCallId===n?{...i,running:!1,error:!!(e.payload.error||e.payload.toolError),resultSummary:String(e.payload.resultText||"")}:i),o=!!e.payload.stopRunWaitingUser;r({tools:s,isRunning:!o&&t.isRunning});break}case"AGENT_ERROR":r({error:String(e.payload.message||"运行出错"),isRunning:!1}),le();break;case"AGENT_END":r({isRunning:!1}),le();break}}async function le(){if(t.activeSessionId)try{const e=await de(t.activeSessionId);r({lines:pe(e),pendingAsk:ue(e),streamingText:"",streamingReasoning:"",tools:[],isRunning:!1}),await h(),await $()}catch(e){r({error:e instanceof Error?e.message:String(e)})}}async function U(){var o;const e=t.composer.trim();if(!e||!t.activeSessionId||t.isRunning||t.pendingAsk)return;if(!((o=t.config)!=null&&o.hasApiKey)){r({showSettings:!0,error:"请先配置 API Key"});return}const n={id:`local-${Date.now()}`,role:"user",content:e};r({lines:[...t.lines,n],composer:"",isRunning:!0,streamingText:"",tools:[],error:null});let s=e;if(t.attachments.length>0){const i=t.attachments.map(l=>`- ${l.workspaceRelativePath}`);s+=`

[用户附件]
${i.join(`
`)}`}try{await xe(t.activeSessionId,s)}catch(i){r({isRunning:!1,error:i instanceof Error?i.message:String(i)})}}async function We(){if(!t.pendingAsk||!t.activeSessionId)return;const e=Ne(t.pendingAsk,t.askAnswers);r({pendingAsk:null,composer:e}),await U()}function Xe(e){return e.role==="tool"?`<details class="tool-card"><summary>⚙ ${c(e.toolName||"tool")}</summary>
      <div class="tool-row ${e.error?"err":""}">${c(e.content)}</div></details>`:`<div class="bubble ${e.role}">${c(e.content)}</div>`}function Ve(){if(!t.tools.length&&!t.streamingText&&!t.streamingReasoning)return"";const e=t.tools.map(s=>{const o=s.running?"进行中…":s.error?"失败":"完成";return`<div class="tool-row ${s.error?"err":""}"><strong>${c(s.toolName)}</strong> · ${o}
        ${s.argsPreview?`<div>${c(s.argsPreview)}</div>`:""}
        ${s.resultSummary?`<div>${c(s.resultSummary)}</div>`:""}
      </div>`}).join("");return`<details class="tool-card" open><summary>思考与执行</summary>${[t.streamingReasoning?`<div class="tool-row">思考：${c(t.streamingReasoning)}</div>`:"",e].join("")}</details>
    ${t.streamingText?`<div class="bubble assistant">${c(t.streamingText)}</div>`:""}`}function Ye(){return t.pendingAsk?`<div class="ask-panel"><h3>请回答后继续</h3>${t.pendingAsk.questions.map(n=>{const s=(n.options||[]).map(o=>`<button type="button" class="chip ${t.askAnswers[n.id]===o?"selected":""}" data-ask-id="${c(n.id)}" data-ask-opt="${c(o)}">${c(o)}</button>`).join("");return`<div class="ask-q">
        <label>${c(n.prompt)}</label>
        ${s?`<div class="ask-options">${s}</div>`:""}
        <input data-ask-input="${c(n.id)}" value="${c(t.askAnswers[n.id]||"")}" placeholder="你的回答" />
      </div>`}).join("")}
    <button type="button" class="primary-btn" id="ask-submit">提交回答</button></div>`:""}function Ze(){if(!t.showArtifacts)return"";const e=t.artifacts.map(n=>{const o=_e(n.workspaceRelativePath)?`<button type="button" class="link-btn" data-art-preview="1" data-art-session="${c(n.sessionId)}" data-art-path="${c(n.workspaceRelativePath)}">预览</button>`:"",i=t.activeSessionId?`<button type="button" class="link-btn" data-art-insert="1" data-art-session="${c(n.sessionId)}" data-art-path="${c(n.workspaceRelativePath)}">插入当前会话</button>`:"";return`<li><strong>${c(n.fileName)}</strong>
        <span class="muted"> · ${c(n.sessionId.slice(0,8))} · ${Math.round(n.sizeBytes/1024)}KB</span>
        <div class="artifact-actions">${o} ${i}</div></li>`}).join("");return`<div class="overlay" id="artifacts-overlay">
    <div class="overlay-panel wide-panel">
      <h2>产物库</h2>
      <p class="muted">workspace 内 AI 产出（不含 imports/、tmp/）</p>
      <div class="cap-search">
        <input id="cap-query" placeholder="find_caps 检索…" value="${c(t.capQuery)}" />
        <button type="button" class="primary-btn" id="cap-search-btn">检索</button>
      </div>
      ${t.capResult?`<pre class="cap-result">${c(t.capResult)}</pre>`:""}
      <ul class="artifact-list">${e||"<li class='muted'>暂无产物</li>"}</ul>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="artifacts-close">关闭</button>
      </div>
    </div>
  </div>`}function et(){if(!t.preview)return"";const e=Re(t.preview.sessionId,t.preview.path);return`<div class="overlay preview-overlay" id="preview-overlay">
    <div class="preview-panel">
      <div class="preview-toolbar">
        <span>${c(t.preview.path)}</span>
        <button type="button" class="icon-btn" id="preview-close">✕</button>
      </div>
      <iframe class="preview-frame" src="${e}" title="preview"></iframe>
    </div>
  </div>`}function tt(){return!t.usagePanelOpen||!t.usage?"":`<div class="overlay" id="usage-overlay">
    <div class="overlay-panel usage-panel">
      <h2>累计用量</h2>
      <div class="usage-detail">${Je(t.usage)}</div>
      <p class="muted">来自 agentRoot/logs/events.jsonl</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="usage-close">关闭</button>
      </div>
    </div>
  </div>`}function nt(){if(!t.showSettings||!t.settingsDraft)return"";const e=t.settingsDraft,n=t.config,s=O.map(p=>`<button type="button" class="provider-chip ${p.id===e.providerId?"active":""}" data-provider="${p.id}">${c(p.label)}</button>`).join(""),o=t.modelOptions.map(p=>{const g=p.modelId===e.model?"selected":"";return`<option value="${c(p.modelId)}" ${g}>${c(p.title)} · ${c(p.subtitle)}</option>`}).join(""),i=e.model&&!t.modelOptions.some(p=>p.modelId===e.model)?`<option value="${c(e.model)}" selected>${c(e.model)}（当前）</option>`:"",l=t.modelsLoading?"正在加载模型列表…":t.modelsStatus||(t.modelOptions.length?`共 ${t.modelOptions.length} 个模型`:"");return`<div class="overlay settings-overlay" id="settings-overlay">
    <div class="overlay-panel settings-panel">
      <header class="settings-header">
        <h2>模型与限额</h2>
        <button type="button" class="icon-btn" id="cfg-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <section class="settings-section">
          <h3>服务商</h3>
          <div class="provider-row">${s}</div>
        </section>
        <section class="settings-section">
          <h3>连接</h3>
          <div class="field">
            <label for="cfg-key">API Key</label>
            <input id="cfg-key" class="settings-control" type="password" autocomplete="off"
              placeholder="${c(n!=null&&n.hasApiKey?`已配置 ${n.apiKeyHint||""}`:"粘贴 Key，留空则不修改")}" />
          </div>
          <div class="field">
            <label for="cfg-base">Base URL</label>
            <input id="cfg-base" class="settings-control" value="${c(e.baseUrl)}" />
          </div>
          <div class="field">
            <div class="field-label-row">
              <label for="cfg-model">模型</label>
              <button type="button" class="link-btn" id="cfg-refresh-models" ${t.modelsLoading?"disabled":""}>从网络刷新</button>
            </div>
            <select id="cfg-model" class="settings-control settings-select">${i}${o}</select>
            <p class="settings-hint">${c(l)}</p>
          </div>
        </section>
        <section class="settings-section">
          <h3>Run 限额</h3>
          <div class="field-row">
            <div class="field">
              <label for="cfg-ctx-turns">上下文轮次</label>
              <input id="cfg-ctx-turns" class="settings-control" type="number" min="0" value="${e.maxContextTurns}" />
            </div>
            <div class="field">
              <label for="cfg-ctx-msgs">消息条数</label>
              <input id="cfg-ctx-msgs" class="settings-control" type="number" min="0" value="${e.maxContextMessages}" />
            </div>
          </div>
          <div class="field-row">
            <div class="field">
              <label for="cfg-run-turns">单 Run 轮次</label>
              <input id="cfg-run-turns" class="settings-control" type="number" min="0" value="${e.maxTurnsPerRun}" />
            </div>
            <div class="field">
              <label for="cfg-tool-calls">工具调用</label>
              <input id="cfg-tool-calls" class="settings-control" type="number" min="0" value="${e.maxToolCallsPerRun}" />
            </div>
          </div>
          <p class="settings-hint">填 0 表示沿用环境变量或 core 默认；保存写入 ui-config.json。</p>
        </section>
      </div>
      <footer class="settings-footer">
        <button type="button" class="primary-btn settings-save" id="cfg-save">保存并生效</button>
      </footer>
    </div>
  </div>`}function ge(){var I;const e=t.sessions.map(v=>{const T=v.sessionId===t.activeSessionId?"active":"",A=v.title||v.sessionId.slice(0,8);return`<div class="session-item ${T}">
        <button type="button" class="session-title" data-use="${v.sessionId}">${c(A)}</button>
        <button type="button" class="session-delete" data-del="${v.sessionId}" title="删除">✕</button>
      </div>`}).join(""),n=De(t.lines,t.isRunning,t.streamingText,t.streamingReasoning,!!t.pendingAsk),s=t.lines.map(Xe).join(""),o=!t.isRunning&&!t.pendingAsk&&t.lines.length>0&&!D(t),i=n?'<button type="button" class="new-topic-btn" id="btn-new-topic">开始新话题</button>':"",l=o?'<button type="button" class="new-topic-btn secondary" id="btn-summarize-new">总结并新开话题</button>':"",p=t.lines.length===0&&!t.streamingText&&!t.isRunning?'<div class="landing"><h2>Agent1</h2><p>生产力助手 · 与 Android 端同款纸本风格。左侧管理会话，下方输入开始对话。</p></div>':s+i+l+Ve()+Ye(),g=qe(t.usage),y=t.isRunning||!!t.pendingAsk,R=t.pendingAsk?"请在上方卡片回答":t.isRunning?"生成中…":"输入消息，Enter 发送，Shift+Enter 换行";me.innerHTML=`
  <div class="app">
    <aside class="drawer ${t.drawerOpen?"":"collapsed"}">
      <div class="drawer-header">
        <div class="brand">Agent1</div>
        <div class="drawer-actions">
          <button type="button" class="icon-btn" id="btn-new" title="新会话">＋</button>
          <button type="button" class="icon-btn" id="btn-settings" title="设置">⚙</button>
        </div>
      </div>
      <div class="session-list">${e}</div>
      <button type="button" class="usage-footer" id="usage-footer">
        <span class="usage-headline">${c(g.headline)}</span>
        <span class="usage-detail-line">${c(g.detail)}</span>
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
      ${t.toast?`<div class="toast-banner">${c(t.toast)}</div>`:""}
      ${t.error?`<div class="error-banner">${c(t.error)}</div>`:""}
      <div class="chat-scroll" id="chat-scroll">${p}</div>
      <div class="composer-wrap">
        ${t.config&&!t.config.hasApiKey?'<div class="inline-setup">尚未配置 API Key。<button type="button" id="btn-setup">打开设置</button></div>':""}
        ${t.attachments.length?`<div class="attachment-chips">${t.attachments.map(v=>`<span class="chip">${c(v.fileName)} <button type="button" data-rm-attach="${c(v.workspaceRelativePath)}">×</button></span>`).join("")}</div>`:""}
        <div class="composer">
          <input type="file" id="file-input" class="hidden-file" multiple />
          <button type="button" class="icon-btn" id="btn-attach" title="添加附件" ${y?"disabled":""}>📎</button>
          <textarea id="composer" ${y?"disabled":""} placeholder="${R}">${c(t.composer)}</textarea>
          ${t.isRunning?'<button type="button" class="danger-btn" id="btn-stop">停止</button>':`<button type="button" class="primary-btn" id="btn-send" ${y?"disabled":""}>发送</button>`}
        </div>
        <div class="model-chip">模型 ${c(((I=t.config)==null?void 0:I.model)||"—")} ·
          <button type="button" id="btn-model">更改</button></div>
      </div>
    </section>
    ${nt()}
    ${tt()}
    ${Ze()}
    ${et()}
  </div>`,st();const b=document.getElementById("chat-scroll");b&&(b.scrollTop=b.scrollHeight)}function st(){var i,l,p,g,y,R,b,I,v,T,A,_,F,q,K,J,z,H,G,Q,W,X,V,Y,Z,ee,te,ne,se,ae;(i=document.getElementById("btn-new"))==null||i.addEventListener("click",()=>void ce()),(l=document.getElementById("btn-new-topic"))==null||l.addEventListener("click",()=>void ce()),(p=document.getElementById("btn-summarize-new"))==null||p.addEventListener("click",()=>void ze()),(g=document.getElementById("btn-drawer"))==null||g.addEventListener("click",()=>{const a=!t.drawerOpen;r({drawerOpen:a}),a&&$()}),(y=document.getElementById("usage-footer"))==null||y.addEventListener("click",()=>{$().then(()=>r({usagePanelOpen:!0}))}),(R=document.getElementById("usage-close"))==null||R.addEventListener("click",()=>r({usagePanelOpen:!1})),(b=document.getElementById("usage-overlay"))==null||b.addEventListener("click",a=>{a.target===a.currentTarget&&r({usagePanelOpen:!1})}),(I=document.getElementById("btn-settings"))==null||I.addEventListener("click",()=>void x()),(v=document.getElementById("btn-settings-2"))==null||v.addEventListener("click",()=>void x()),(T=document.getElementById("btn-setup"))==null||T.addEventListener("click",()=>void x()),(A=document.getElementById("btn-model"))==null||A.addEventListener("click",()=>void x()),(_=document.getElementById("btn-artifacts"))==null||_.addEventListener("click",async()=>{const a=await Ee();r({artifacts:a,showArtifacts:!0,capQuery:"",capResult:""})}),(F=document.getElementById("artifacts-close"))==null||F.addEventListener("click",()=>r({showArtifacts:!1})),(q=document.getElementById("artifacts-overlay"))==null||q.addEventListener("click",a=>{a.target===a.currentTarget&&r({showArtifacts:!1})}),(K=document.getElementById("cap-search-btn"))==null||K.addEventListener("click",async()=>{var u;const a=document.getElementById("cap-query"),d=((u=a==null?void 0:a.value)==null?void 0:u.trim())||"";t={...t,capQuery:d};try{const f=await Te(d);r({capResult:f})}catch(f){r({capResult:f instanceof Error?f.message:String(f)})}}),document.querySelectorAll("[data-art-preview]").forEach(a=>{a.addEventListener("click",()=>{const d=a.dataset.artSession,u=a.dataset.artPath;d&&u&&r({preview:{sessionId:d,path:u},showArtifacts:!1})})}),document.querySelectorAll("[data-art-insert]").forEach(a=>{a.addEventListener("click",async()=>{const d=a.dataset.artSession,u=a.dataset.artPath,f=t.activeSessionId;if(!(!f||!d||!u))try{await $e(f,d,u),await C(f),S("已复制到 imports/")}catch(w){r({error:w instanceof Error?w.message:String(w)})}})}),(J=document.getElementById("preview-close"))==null||J.addEventListener("click",()=>r({preview:null})),(z=document.getElementById("preview-overlay"))==null||z.addEventListener("click",a=>{a.target===a.currentTarget&&r({preview:null})});const e=document.getElementById("file-input");(H=document.getElementById("btn-attach"))==null||H.addEventListener("click",()=>e==null?void 0:e.click()),e==null||e.addEventListener("change",async()=>{var d;const a=t.activeSessionId;if(!(!a||!((d=e.files)!=null&&d.length)))try{for(const u of Array.from(e.files))await ke(a,u);await C(a),e.value=""}catch(u){r({error:u instanceof Error?u.message:String(u)})}}),document.querySelectorAll("[data-rm-attach]").forEach(a=>{a.addEventListener("click",async()=>{const d=t.activeSessionId,u=a.dataset.rmAttach;!d||!u||(await Se(d,u),await C(d))})}),document.querySelectorAll("[data-use]").forEach(a=>{a.addEventListener("click",async()=>{const d=a.dataset.use;if(t.isRunning){r({error:"请先停止当前 Run 再切换会话"});return}await B(d),await E(d),await h()})}),document.querySelectorAll("[data-del]").forEach(a=>{a.addEventListener("click",async d=>{d.stopPropagation();const u=a.dataset.del;if(confirm("删除该会话？")&&(await Le(u),await h(),t.activeSessionId===u))if(t.sessions[0])await B(t.sessions[0].sessionId),await E(t.sessions[0].sessionId);else{const f=await N();await h(),await E(f.sessionId)}})});const n=document.getElementById("composer");n==null||n.addEventListener("input",()=>{t={...t,composer:n.value}}),n==null||n.addEventListener("keydown",a=>{a.key==="Enter"&&!a.shiftKey&&(a.preventDefault(),U())}),(G=document.getElementById("btn-send"))==null||G.addEventListener("click",()=>void U()),(Q=document.getElementById("btn-stop"))==null||Q.addEventListener("click",async()=>{await Pe()}),document.querySelectorAll("[data-ask-opt]").forEach(a=>{a.addEventListener("click",()=>{const d=a.dataset.askId,u=a.dataset.askOpt;r({askAnswers:{...t.askAnswers,[d]:u}})})}),document.querySelectorAll("[data-ask-input]").forEach(a=>{a.addEventListener("input",()=>{const d=a.dataset.askInput;t={...t,askAnswers:{...t.askAnswers,[d]:a.value}}})}),(W=document.getElementById("ask-submit"))==null||W.addEventListener("click",()=>void We());const s=()=>r({showSettings:!1,settingsDraft:null,modelsStatus:null,modelsLoading:!1});(X=document.getElementById("cfg-close"))==null||X.addEventListener("click",s),(V=document.getElementById("settings-overlay"))==null||V.addEventListener("click",a=>{a.target===a.currentTarget&&s()}),document.querySelectorAll("[data-provider]").forEach(a=>{a.addEventListener("click",()=>{var u;const d=a.dataset.provider;d&&d!==((u=t.settingsDraft)==null?void 0:u.providerId)&&He(d)})});const o=a=>{t.settingsDraft&&r({settingsDraft:{...t.settingsDraft,...a}})};(Y=document.getElementById("cfg-model"))==null||Y.addEventListener("change",a=>{o({model:a.target.value})}),(Z=document.getElementById("cfg-ctx-turns"))==null||Z.addEventListener("change",a=>{o({maxContextTurns:parseInt(a.target.value,10)||0})}),(ee=document.getElementById("cfg-ctx-msgs"))==null||ee.addEventListener("change",a=>{o({maxContextMessages:parseInt(a.target.value,10)||0})}),(te=document.getElementById("cfg-run-turns"))==null||te.addEventListener("change",a=>{o({maxTurnsPerRun:parseInt(a.target.value,10)||0})}),(ne=document.getElementById("cfg-tool-calls"))==null||ne.addEventListener("change",a=>{o({maxToolCallsPerRun:parseInt(a.target.value,10)||0})}),(se=document.getElementById("cfg-refresh-models"))==null||se.addEventListener("click",()=>{const a=t.settingsDraft;a&&j(a,!0)}),(ae=document.getElementById("cfg-save"))==null||ae.addEventListener("click",async()=>{const a=t.settingsDraft;if(!a)return;const d=document.getElementById("cfg-key").value,u=document.getElementById("cfg-base").value,f=document.getElementById("cfg-model").value,w=parseInt(document.getElementById("cfg-ctx-turns").value,10),oe=parseInt(document.getElementById("cfg-ctx-msgs").value,10),ie=parseInt(document.getElementById("cfg-run-turns").value,10),re=parseInt(document.getElementById("cfg-tool-calls").value,10);try{const k=await ye({apiKey:d||void 0,baseUrl:u||void 0,model:f||void 0,providerId:a.providerId,maxContextTurns:Number.isFinite(w)?w:0,maxContextMessages:Number.isFinite(oe)?oe:0,maxTurnsPerRun:Number.isFinite(ie)?ie:0,maxToolCallsPerRun:Number.isFinite(re)?re:0});r({config:k,showSettings:!1,settingsDraft:null,modelsStatus:null,error:null}),S("设置已保存")}catch(k){r({error:k instanceof Error?k.message:String(k)})}})}ge();Ge().catch(e=>{r({error:e instanceof Error?e.message:String(e)})});window.addEventListener("beforeunload",()=>P==null?void 0:P());
