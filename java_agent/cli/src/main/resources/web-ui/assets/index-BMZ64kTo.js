(function(){const e=document.createElement("link").relList;if(e&&e.supports&&e.supports("modulepreload"))return;for(const r of document.querySelectorAll('link[rel="modulepreload"]'))i(r);new MutationObserver(r=>{for(const l of r)if(l.type==="childList")for(const g of l.addedNodes)g.tagName==="LINK"&&g.rel==="modulepreload"&&i(g)}).observe(document,{childList:!0,subtree:!0});function s(r){const l={};return r.integrity&&(l.integrity=r.integrity),r.referrerPolicy&&(l.referrerPolicy=r.referrerPolicy),r.crossOrigin==="use-credentials"?l.credentials="include":r.crossOrigin==="anonymous"?l.credentials="omit":l.credentials="same-origin",l}function i(r){if(r.ep)return;r.ep=!0;const l=s(r);fetch(r.href,l)}})();async function f(t){const e=await t.json();if(!t.ok)throw new Error(e.error||t.statusText);return e}async function Y(){return f(await fetch("/api/config"))}async function Z(t){return f(await fetch("/api/config",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(t)}))}async function tt(){return f(await fetch("/api/usage"))}async function et(){return(await f(await fetch("/api/artifacts"))).artifacts}async function nt(t,e,s){return f(await fetch(`/api/sessions/${encodeURIComponent(t)}/artifacts/import`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({sourceSessionId:e,workspaceRelativePath:s})}))}async function st(t){return(await f(await fetch(`/api/sessions/${encodeURIComponent(t)}/attachments`))).files}async function at(t,e){const s=new FormData;return s.append("file",e),f(await fetch(`/api/sessions/${encodeURIComponent(t)}/attachments`,{method:"POST",body:s}))}async function it(t,e){await f(await fetch(`/api/sessions/${encodeURIComponent(t)}/attachments/${encodeURIComponent(e)}`,{method:"DELETE"}))}function ot(t,e){const s=e.split("/").map(i=>encodeURIComponent(i)).join("/");return`/api/sessions/${encodeURIComponent(t)}/files/${s}`}async function rt(t){return(await f(await fetch(`/api/capabilities?q=${encodeURIComponent(t)}`))).text}async function ct(){return f(await fetch("/api/sessions"))}async function P(){return f(await fetch("/api/sessions",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"}))}async function T(t){await f(await fetch(`/api/sessions/${encodeURIComponent(t)}/use`,{method:"POST",body:"{}"}))}async function lt(t){await f(await fetch(`/api/sessions/${encodeURIComponent(t)}`,{method:"DELETE"}))}async function G(t){return(await f(await fetch(`/api/sessions/${encodeURIComponent(t)}/transcript`))).messages}async function dt(t,e){await f(await fetch(`/api/sessions/${encodeURIComponent(t)}/messages`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({text:e})}))}async function ut(){await f(await fetch("/api/run/abort",{method:"POST",body:"{}"}))}function pt(t){const e=new EventSource("/api/events");return e.onmessage=s=>{try{t(JSON.parse(s.data))}catch{}},()=>e.close()}const ft="ask_user",mt=["等待用户","waiting","ask_user"];function gt(t){try{const s=(JSON.parse(t.argumentsJson||"{}").questions||[]).map((i,r)=>({id:i.id||`q${r}`,prompt:i.prompt||i.question||"",options:i.options,allowFreeText:i.allowFreeText??i.allow_free_text??!0})).filter(i=>i.prompt);return s.length===0?null:{questions:s}}catch{return null}}function Q(t){if(!t.length)return null;for(let e=t.length-1;e>=0;e--){const s=t[e];if(s.role==="user")return null;if(s.role==="assistant"){for(const i of s.toolCalls||[])if(i.name===ft)return gt(i)}if(s.role==="toolResult"){const i=s.content||"";if(!mt.some(l=>i.toLowerCase().includes(l.toLowerCase())))return null}}return null}function vt(t,e){return t.questions.map(i=>{const r=(e[i.id]||"").trim();return`${i.prompt}
→ ${r||"(未答)"}`}).join(`

`)}function yt(t,e,s,i,r){if(e||r||s.trim()||i.trim())return!1;const l=ht(t);return(l==null?void 0:l.role)==="assistant"}function ht(t){return t.filter(e=>e.role==="user"||e.role==="assistant").at(-1)}function wt(){return{sessions:[],activeSessionId:null,config:null,lines:[],streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,pendingAsk:null,askAnswers:{},error:null,showSettings:!1,drawerOpen:!0,composer:"",usage:null,usagePanelOpen:!1,toast:null,attachments:[],artifacts:[],showArtifacts:!1,preview:null,capQuery:"",capResult:""}}function bt(t){const e=t.toLowerCase();return e.endsWith(".html")||e.endsWith(".htm")}function Et(t){return t.isRunning||t.streamingText.trim()||t.streamingReasoning.trim()||t.tools.length>0||t.pendingAsk?!1:!t.lines.some(e=>e.role==="user"||e.role==="assistant")}function W(t){const e=[];return t.forEach((s,i)=>{var r;if(s.role==="user"){e.push({id:`u-${i}`,role:"user",content:s.content});return}if(s.role==="assistant"){(r=s.content)!=null&&r.trim()&&e.push({id:`a-${i}`,role:"assistant",content:s.content});for(const l of s.toolCalls||[])e.push({id:`t-${l.id}`,role:"tool",content:l.argsPreview||l.argumentsJson||"",toolName:l.name});return}if(s.role==="toolResult"){e.push({id:`tr-${i}`,role:"tool",content:s.content,toolName:"result",error:!!s.error});return}s.role}),e}function S(t){if(t<1e3)return String(t);const e=Math.round(t/100)/10,s=Math.trunc(e);return e===s?`${s}k`:`${e.toFixed(1)}k`}function $t(t){if(!t||!t.hasUsage)return{headline:"累计用量",detail:"暂无 usage 记录"};const e=`约 ${S(t.totalTokens)} tokens`,s=t.cacheHitPercent,i=s!=null?`输入 ${S(t.inputTokens)} · 缓存 ${s}%`:`输入 ${S(t.inputTokens)} · 输出 ${S(t.outputTokens)}`;return{headline:e,detail:i}}function kt(t){return t.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;")}function St(t){const e=[`输入 ${t.inputTokens.toLocaleString()}`,`输出 ${t.outputTokens.toLocaleString()}`,`缓存 ${t.cachedTokens.toLocaleString()}`,`usage 事件 ${t.usageEvents}`];let s="";return t.byModel.length&&(s="<ul>"+t.byModel.map(i=>`<li><strong>${kt(i.modelId||"（未知模型）")}</strong>：入 ${i.inputTokens.toLocaleString()} / 出 ${i.outputTokens.toLocaleString()}</li>`).join("")+"</ul>"),`<p>${e.join(" · ")}</p>${s}`}const X=document.getElementById("app");if(!X)throw new Error("#app missing");let n=wt(),I=null;function o(t){n={...n,...t},V()}function c(t){return t.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}async function v(){const t=await ct();o({sessions:t.sessions.slice().sort((e,s)=>s.updatedAt.localeCompare(e.updatedAt)),activeSessionId:t.activeSessionId??n.activeSessionId,isRunning:t.runInProgress})}async function b(){try{const t=await tt();o({usage:t})}catch{}}function z(t){o({toast:t}),window.setTimeout(()=>{n.toast===t&&o({toast:null})},2200)}async function J(){if(n.isRunning){o({error:"请先停止当前 Run"});return}if(n.activeSessionId&&Et(n)){z("已是新会话");return}const t=await P();await v(),await w(t.sessionId),await b(),o({drawerOpen:!1})}async function A(t){try{const e=await st(t);o({attachments:e})}catch{o({attachments:[]})}}async function w(t){const e=await G(t),s=Q(e);o({activeSessionId:t,lines:W(e),pendingAsk:s,askAnswers:{},streamingText:"",streamingReasoning:"",tools:[],error:null}),await A(t)}async function It(){const[t]=await Promise.all([Y(),v()]);o({config:t});let e=n.activeSessionId;e||(n.sessions.length===0?(e=(await P()).sessionId,await v()):(e=n.sessions[0].sessionId,await T(e))),await w(e),await b(),I=pt(At)}function At(t){if(!(t.sessionId&&n.activeSessionId&&t.sessionId!==n.activeSessionId))switch(t.type){case"AGENT_START":o({isRunning:!0,streamingText:"",streamingReasoning:"",tools:[],error:null});break;case"MESSAGE_UPDATE":o({streamingText:n.streamingText+String(t.payload.delta||""),isRunning:!0});break;case"REASONING_UPDATE":o({streamingReasoning:n.streamingReasoning+String(t.payload.delta||""),isRunning:!0});break;case"MESSAGE_RESET":o({streamingText:"",streamingReasoning:""});break;case"TOOL_EXECUTION_START":{const e=t.payload.toolCall,s={id:e.id||`tool-${Date.now()}`,toolCallId:e.id||"",toolName:e.name||"tool",argsPreview:e.argsPreview||"",running:!0};o({tools:[...n.tools,s],isRunning:!0});break}case"TOOL_EXECUTION_END":{const e=String(t.payload.toolCallId||""),s=n.tools.map(r=>r.toolCallId===e?{...r,running:!1,error:!!(t.payload.error||t.payload.toolError),resultSummary:String(t.payload.resultText||"")}:r),i=!!t.payload.stopRunWaitingUser;o({tools:s,isRunning:!i&&n.isRunning});break}case"AGENT_ERROR":o({error:String(t.payload.message||"运行出错"),isRunning:!1}),H();break;case"AGENT_END":o({isRunning:!1}),H();break}}async function H(){if(n.activeSessionId)try{const t=await G(n.activeSessionId);o({lines:W(t),pendingAsk:Q(t),streamingText:"",streamingReasoning:"",tools:[],isRunning:!1}),await v(),await b()}catch(t){o({error:t instanceof Error?t.message:String(t)})}}async function L(){var i;const t=n.composer.trim();if(!t||!n.activeSessionId||n.isRunning||n.pendingAsk)return;if(!((i=n.config)!=null&&i.hasApiKey)){o({showSettings:!0,error:"请先配置 API Key"});return}const e={id:`local-${Date.now()}`,role:"user",content:t};o({lines:[...n.lines,e],composer:"",isRunning:!0,streamingText:"",tools:[],error:null});let s=t;if(n.attachments.length>0){const r=n.attachments.map(l=>`- ${l.workspaceRelativePath}`);s+=`

[用户附件]
${r.join(`
`)}`}try{await dt(n.activeSessionId,s)}catch(r){o({isRunning:!1,error:r instanceof Error?r.message:String(r)})}}async function Rt(){if(!n.pendingAsk||!n.activeSessionId)return;const t=vt(n.pendingAsk,n.askAnswers);o({pendingAsk:null,composer:t}),await L()}function Tt(t){return t.role==="tool"?`<details class="tool-card"><summary>⚙ ${c(t.toolName||"tool")}</summary>
      <div class="tool-row ${t.error?"err":""}">${c(t.content)}</div></details>`:`<div class="bubble ${t.role}">${c(t.content)}</div>`}function Lt(){if(!n.tools.length&&!n.streamingText&&!n.streamingReasoning)return"";const t=n.tools.map(s=>{const i=s.running?"进行中…":s.error?"失败":"完成";return`<div class="tool-row ${s.error?"err":""}"><strong>${c(s.toolName)}</strong> · ${i}
        ${s.argsPreview?`<div>${c(s.argsPreview)}</div>`:""}
        ${s.resultSummary?`<div>${c(s.resultSummary)}</div>`:""}
      </div>`}).join("");return`<details class="tool-card" open><summary>思考与执行</summary>${[n.streamingReasoning?`<div class="tool-row">思考：${c(n.streamingReasoning)}</div>`:"",t].join("")}</details>
    ${n.streamingText?`<div class="bubble assistant">${c(n.streamingText)}</div>`:""}`}function Pt(){return n.pendingAsk?`<div class="ask-panel"><h3>请回答后继续</h3>${n.pendingAsk.questions.map(e=>{const s=(e.options||[]).map(i=>`<button type="button" class="chip ${n.askAnswers[e.id]===i?"selected":""}" data-ask-id="${c(e.id)}" data-ask-opt="${c(i)}">${c(i)}</button>`).join("");return`<div class="ask-q">
        <label>${c(e.prompt)}</label>
        ${s?`<div class="ask-options">${s}</div>`:""}
        <input data-ask-input="${c(e.id)}" value="${c(n.askAnswers[e.id]||"")}" placeholder="你的回答" />
      </div>`}).join("")}
    <button type="button" class="primary-btn" id="ask-submit">提交回答</button></div>`:""}function Bt(){if(!n.showArtifacts)return"";const t=n.artifacts.map(e=>{const i=bt(e.workspaceRelativePath)?`<button type="button" class="link-btn" data-art-preview="1" data-art-session="${c(e.sessionId)}" data-art-path="${c(e.workspaceRelativePath)}">预览</button>`:"",r=n.activeSessionId?`<button type="button" class="link-btn" data-art-insert="1" data-art-session="${c(e.sessionId)}" data-art-path="${c(e.workspaceRelativePath)}">插入当前会话</button>`:"";return`<li><strong>${c(e.fileName)}</strong>
        <span class="muted"> · ${c(e.sessionId.slice(0,8))} · ${Math.round(e.sizeBytes/1024)}KB</span>
        <div class="artifact-actions">${i} ${r}</div></li>`}).join("");return`<div class="overlay" id="artifacts-overlay">
    <div class="overlay-panel wide-panel">
      <h2>产物库</h2>
      <p class="muted">workspace 内 AI 产出（不含 imports/、tmp/）</p>
      <div class="cap-search">
        <input id="cap-query" placeholder="find_caps 检索…" value="${c(n.capQuery)}" />
        <button type="button" class="primary-btn" id="cap-search-btn">检索</button>
      </div>
      ${n.capResult?`<pre class="cap-result">${c(n.capResult)}</pre>`:""}
      <ul class="artifact-list">${t||"<li class='muted'>暂无产物</li>"}</ul>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="artifacts-close">关闭</button>
      </div>
    </div>
  </div>`}function Ot(){if(!n.preview)return"";const t=ot(n.preview.sessionId,n.preview.path);return`<div class="overlay preview-overlay" id="preview-overlay">
    <div class="preview-panel">
      <div class="preview-toolbar">
        <span>${c(n.preview.path)}</span>
        <button type="button" class="icon-btn" id="preview-close">✕</button>
      </div>
      <iframe class="preview-frame" src="${t}" title="preview"></iframe>
    </div>
  </div>`}function Ct(){return!n.usagePanelOpen||!n.usage?"":`<div class="overlay" id="usage-overlay">
    <div class="overlay-panel usage-panel">
      <h2>累计用量</h2>
      <div class="usage-detail">${St(n.usage)}</div>
      <p class="muted">来自 agentRoot/logs/events.jsonl</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="usage-close">关闭</button>
      </div>
    </div>
  </div>`}function Nt(){if(!n.showSettings)return"";const t=n.config;return`<div class="overlay" id="settings-overlay">
    <div class="overlay-panel">
      <h2>模型设置</h2>
      <p class="muted">保存到 agentRoot/ui-config.json；非空字段覆盖环境变量。</p>
      <div class="field"><label>API Key</label>
        <input id="cfg-key" type="password" placeholder="${c((t==null?void 0:t.apiKeyHint)||"sk-…")}" /></div>
      <div class="field"><label>Base URL</label>
        <input id="cfg-base" value="${c((t==null?void 0:t.baseUrl)||"")}" /></div>
      <div class="field"><label>Model</label>
        <input id="cfg-model" value="${c((t==null?void 0:t.model)||"")}" /></div>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="cfg-save">保存</button>
        <button type="button" class="icon-btn" id="cfg-close">关闭</button>
      </div>
    </div>
  </div>`}function V(){var h;const t=n.sessions.map(m=>{const $=m.sessionId===n.activeSessionId?"active":"",k=m.title||m.sessionId.slice(0,8);return`<div class="session-item ${$}">
        <button type="button" class="session-title" data-use="${m.sessionId}">${c(k)}</button>
        <button type="button" class="session-delete" data-del="${m.sessionId}" title="删除">✕</button>
      </div>`}).join(""),e=yt(n.lines,n.isRunning,n.streamingText,n.streamingReasoning,!!n.pendingAsk),s=n.lines.map(Tt).join(""),i=e?'<button type="button" class="new-topic-btn" id="btn-new-topic">开始新话题</button>':"",r=n.lines.length===0&&!n.streamingText&&!n.isRunning?'<div class="landing"><h2>Agent1</h2><p>生产力助手 · 与 Android 端同款纸本风格。左侧管理会话，下方输入开始对话。</p></div>':s+i+Lt()+Pt(),l=$t(n.usage),g=n.isRunning||!!n.pendingAsk,E=n.pendingAsk?"请在上方卡片回答":n.isRunning?"生成中…":"输入消息，Enter 发送，Shift+Enter 换行";X.innerHTML=`
  <div class="app">
    <aside class="drawer ${n.drawerOpen?"":"collapsed"}">
      <div class="drawer-header">
        <div class="brand">Agent1</div>
        <div class="drawer-actions">
          <button type="button" class="icon-btn" id="btn-new" title="新会话">＋</button>
          <button type="button" class="icon-btn" id="btn-settings" title="设置">⚙</button>
        </div>
      </div>
      <div class="session-list">${t}</div>
      <button type="button" class="usage-footer" id="usage-footer">
        <span class="usage-headline">${c(l.headline)}</span>
        <span class="usage-detail-line">${c(l.detail)}</span>
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
      <div class="chat-scroll" id="chat-scroll">${r}</div>
      <div class="composer-wrap">
        ${n.config&&!n.config.hasApiKey?'<div class="inline-setup">尚未配置 API Key。<button type="button" id="btn-setup">打开设置</button></div>':""}
        ${n.attachments.length?`<div class="attachment-chips">${n.attachments.map(m=>`<span class="chip">${c(m.fileName)} <button type="button" data-rm-attach="${c(m.workspaceRelativePath)}">×</button></span>`).join("")}</div>`:""}
        <div class="composer">
          <input type="file" id="file-input" class="hidden-file" multiple />
          <button type="button" class="icon-btn" id="btn-attach" title="添加附件" ${g?"disabled":""}>📎</button>
          <textarea id="composer" ${g?"disabled":""} placeholder="${E}">${c(n.composer)}</textarea>
          ${n.isRunning?'<button type="button" class="danger-btn" id="btn-stop">停止</button>':`<button type="button" class="primary-btn" id="btn-send" ${g?"disabled":""}>发送</button>`}
        </div>
        <div class="model-chip">模型 ${c(((h=n.config)==null?void 0:h.model)||"—")} ·
          <button type="button" id="btn-model">更改</button></div>
      </div>
    </section>
    ${Nt()}
    ${Ct()}
    ${Bt()}
    ${Ot()}
  </div>`,Ut();const y=document.getElementById("chat-scroll");y&&(y.scrollTop=y.scrollHeight)}function Ut(){var i,r,l,g,E,y,h,m,$,k,B,O,C,N,U,x,j,_,M,q,D,K,F;(i=document.getElementById("btn-new"))==null||i.addEventListener("click",()=>void J()),(r=document.getElementById("btn-new-topic"))==null||r.addEventListener("click",()=>void J()),(l=document.getElementById("btn-drawer"))==null||l.addEventListener("click",()=>{const a=!n.drawerOpen;o({drawerOpen:a}),a&&b()}),(g=document.getElementById("usage-footer"))==null||g.addEventListener("click",()=>{b().then(()=>o({usagePanelOpen:!0}))}),(E=document.getElementById("usage-close"))==null||E.addEventListener("click",()=>o({usagePanelOpen:!1})),(y=document.getElementById("usage-overlay"))==null||y.addEventListener("click",a=>{a.target===a.currentTarget&&o({usagePanelOpen:!1})});const t=()=>o({showSettings:!0});(h=document.getElementById("btn-settings"))==null||h.addEventListener("click",t),(m=document.getElementById("btn-settings-2"))==null||m.addEventListener("click",t),($=document.getElementById("btn-setup"))==null||$.addEventListener("click",t),(k=document.getElementById("btn-model"))==null||k.addEventListener("click",t),(B=document.getElementById("btn-artifacts"))==null||B.addEventListener("click",async()=>{const a=await et();o({artifacts:a,showArtifacts:!0,capQuery:"",capResult:""})}),(O=document.getElementById("artifacts-close"))==null||O.addEventListener("click",()=>o({showArtifacts:!1})),(C=document.getElementById("artifacts-overlay"))==null||C.addEventListener("click",a=>{a.target===a.currentTarget&&o({showArtifacts:!1})}),(N=document.getElementById("cap-search-btn"))==null||N.addEventListener("click",async()=>{var u;const a=document.getElementById("cap-query"),d=((u=a==null?void 0:a.value)==null?void 0:u.trim())||"";n={...n,capQuery:d};try{const p=await rt(d);o({capResult:p})}catch(p){o({capResult:p instanceof Error?p.message:String(p)})}}),document.querySelectorAll("[data-art-preview]").forEach(a=>{a.addEventListener("click",()=>{const d=a.dataset.artSession,u=a.dataset.artPath;d&&u&&o({preview:{sessionId:d,path:u},showArtifacts:!1})})}),document.querySelectorAll("[data-art-insert]").forEach(a=>{a.addEventListener("click",async()=>{const d=a.dataset.artSession,u=a.dataset.artPath,p=n.activeSessionId;if(!(!p||!d||!u))try{await nt(p,d,u),await A(p),z("已复制到 imports/")}catch(R){o({error:R instanceof Error?R.message:String(R)})}})}),(U=document.getElementById("preview-close"))==null||U.addEventListener("click",()=>o({preview:null})),(x=document.getElementById("preview-overlay"))==null||x.addEventListener("click",a=>{a.target===a.currentTarget&&o({preview:null})});const e=document.getElementById("file-input");(j=document.getElementById("btn-attach"))==null||j.addEventListener("click",()=>e==null?void 0:e.click()),e==null||e.addEventListener("change",async()=>{var d;const a=n.activeSessionId;if(!(!a||!((d=e.files)!=null&&d.length)))try{for(const u of Array.from(e.files))await at(a,u);await A(a),e.value=""}catch(u){o({error:u instanceof Error?u.message:String(u)})}}),document.querySelectorAll("[data-rm-attach]").forEach(a=>{a.addEventListener("click",async()=>{const d=n.activeSessionId,u=a.dataset.rmAttach;!d||!u||(await it(d,u),await A(d))})}),document.querySelectorAll("[data-use]").forEach(a=>{a.addEventListener("click",async()=>{const d=a.dataset.use;if(n.isRunning){o({error:"请先停止当前 Run 再切换会话"});return}await T(d),await w(d),await v()})}),document.querySelectorAll("[data-del]").forEach(a=>{a.addEventListener("click",async d=>{d.stopPropagation();const u=a.dataset.del;if(confirm("删除该会话？")&&(await lt(u),await v(),n.activeSessionId===u))if(n.sessions[0])await T(n.sessions[0].sessionId),await w(n.sessions[0].sessionId);else{const p=await P();await v(),await w(p.sessionId)}})});const s=document.getElementById("composer");s==null||s.addEventListener("input",()=>{n={...n,composer:s.value}}),s==null||s.addEventListener("keydown",a=>{a.key==="Enter"&&!a.shiftKey&&(a.preventDefault(),L())}),(_=document.getElementById("btn-send"))==null||_.addEventListener("click",()=>void L()),(M=document.getElementById("btn-stop"))==null||M.addEventListener("click",async()=>{await ut()}),document.querySelectorAll("[data-ask-opt]").forEach(a=>{a.addEventListener("click",()=>{const d=a.dataset.askId,u=a.dataset.askOpt;o({askAnswers:{...n.askAnswers,[d]:u}})})}),document.querySelectorAll("[data-ask-input]").forEach(a=>{a.addEventListener("input",()=>{const d=a.dataset.askInput;n={...n,askAnswers:{...n.askAnswers,[d]:a.value}}})}),(q=document.getElementById("ask-submit"))==null||q.addEventListener("click",()=>void Rt()),(D=document.getElementById("cfg-close"))==null||D.addEventListener("click",()=>o({showSettings:!1})),(K=document.getElementById("settings-overlay"))==null||K.addEventListener("click",a=>{a.target===a.currentTarget&&o({showSettings:!1})}),(F=document.getElementById("cfg-save"))==null||F.addEventListener("click",async()=>{const a=document.getElementById("cfg-key").value,d=document.getElementById("cfg-base").value,u=document.getElementById("cfg-model").value;try{const p=await Z({apiKey:a||void 0,baseUrl:d||void 0,model:u||void 0});o({config:p,showSettings:!1,error:null})}catch(p){o({error:p instanceof Error?p.message:String(p)})}})}V();It().catch(t=>{o({error:t instanceof Error?t.message:String(t)})});window.addEventListener("beforeunload",()=>I==null?void 0:I());
