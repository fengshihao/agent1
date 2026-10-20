(function(){const t=document.createElement("link").relList;if(t&&t.supports&&t.supports("modulepreload"))return;for(const i of document.querySelectorAll('link[rel="modulepreload"]'))o(i);new MutationObserver(i=>{for(const l of i)if(l.type==="childList")for(const d of l.addedNodes)d.tagName==="LINK"&&d.rel==="modulepreload"&&o(d)}).observe(document,{childList:!0,subtree:!0});function s(i){const l={};return i.integrity&&(l.integrity=i.integrity),i.referrerPolicy&&(l.referrerPolicy=i.referrerPolicy),i.crossOrigin==="use-credentials"?l.credentials="include":i.crossOrigin==="anonymous"?l.credentials="omit":l.credentials="same-origin",l}function o(i){if(i.ep)return;i.ep=!0;const l=s(i);fetch(i.href,l)}})();async function m(e){const t=await e.json();if(!e.ok)throw new Error(t.error||e.statusText);return t}async function Re(){return m(await fetch("/api/config"))}async function Te(e){return m(await fetch("/api/config",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(e)}))}async function Ae(){return m(await fetch("/api/usage"))}async function Le(e,t=!1){const s=new URLSearchParams({provider:e});return t&&s.set("refresh","1"),m(await fetch(`/api/models?${s}`))}async function xe(){return m(await fetch("/api/tools"))}async function Pe(){var l,d;const e=await fetch("/api/diagnostics/export");if(!e.ok)throw new Error("诊断包导出失败");const t=await e.blob(),s=((d=(l=e.headers.get("Content-Disposition"))==null?void 0:l.match(/filename="([^"]+)"/))==null?void 0:d[1])??`agent1-diag-${Date.now()}.zip`,o=URL.createObjectURL(t),i=document.createElement("a");i.href=o,i.download=s,i.click(),URL.revokeObjectURL(o)}async function Be(e=!0){return m(await fetch("/api/sessions/rollover",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({summarize:e})}))}async function Ce(){return(await m(await fetch("/api/artifacts"))).artifacts}async function Oe(e,t,s){return m(await fetch(`/api/sessions/${encodeURIComponent(e)}/artifacts/import`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({sourceSessionId:t,workspaceRelativePath:s})}))}async function Ue(e){return(await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`))).files}async function De(e,t){const s=new FormData;return s.append("file",t),m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`,{method:"POST",body:s}))}async function Ne(e,t){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments/${encodeURIComponent(t)}`,{method:"DELETE"}))}function C(e,t){const s=t.split("/").map(o=>encodeURIComponent(o)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${s}`}async function je(e){return(await m(await fetch(`/api/capabilities?q=${encodeURIComponent(e)}`))).text}async function Me(){return m(await fetch("/api/sessions"))}async function N(){return m(await fetch("/api/sessions",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"}))}async function O(e){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/use`,{method:"POST",body:"{}"}))}async function _e(e){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}`,{method:"DELETE"}))}async function he(e){return(await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/transcript`))).messages}async function Fe(e,t){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/messages`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({text:t})}))}async function Ke(){await m(await fetch("/api/run/abort",{method:"POST",body:"{}"}))}function qe(e){const t=new EventSource("/api/events");return t.onmessage=s=>{try{e(JSON.parse(s.data))}catch{}},()=>t.close()}const ze="ask_user",Je=["等待用户","waiting","ask_user"];function We(e){try{const s=(JSON.parse(e.argumentsJson||"{}").questions||[]).map((o,i)=>({id:o.id||`q${i}`,prompt:o.prompt||o.question||"",options:o.options,allowFreeText:o.allowFreeText??o.allow_free_text??!0})).filter(o=>o.prompt);return s.length===0?null:{questions:s}}catch{return null}}function be(e){if(!e.length)return null;for(let t=e.length-1;t>=0;t--){const s=e[t];if(s.role==="user")return null;if(s.role==="assistant"){for(const o of s.toolCalls||[])if(o.name===ze)return We(o)}if(s.role==="toolResult"){const o=s.content||"";if(!Je.some(l=>o.toLowerCase().includes(l.toLowerCase())))return null}}return null}function Ge(e,t){return e.questions.map(o=>{const i=(t[o.id]||"").trim();return`${o.prompt}
→ ${i||"(未答)"}`}).join(`

`)}function He(e,t,s,o,i){if(t||i||s.trim()||o.trim())return!1;const l=Qe(e);return(l==null?void 0:l.role)==="assistant"}function Qe(e){return e.filter(t=>t.role==="user"||t.role==="assistant").at(-1)}function Xe(){return{sessions:[],activeSessionId:null,config:null,lines:[],streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,pendingAsk:null,askAnswers:{},error:null,showSettings:!1,drawerOpen:!0,composer:"",usage:null,usagePanelOpen:!1,toast:null,attachments:[],artifacts:[],showArtifacts:!1,preview:null,capQuery:"",capResult:"",modelOptions:[],settingsDraft:null,modelsLoading:!1,modelsStatus:null,showTools:!1,toolsList:[]}}function Ve(e){const t=e.toLowerCase();return t.endsWith(".html")||t.endsWith(".htm")}function j(e){return e.isRunning||e.streamingText.trim()||e.streamingReasoning.trim()||e.tools.length>0||e.pendingAsk?!1:!e.lines.some(t=>t.role==="user"||t.role==="assistant")}function ye(e){const t=[];return e.forEach((s,o)=>{var i;if(s.role==="user"){t.push({id:`u-${o}`,role:"user",content:s.content});return}if(s.role==="assistant"){(i=s.content)!=null&&i.trim()&&t.push({id:`a-${o}`,role:"assistant",content:s.content});for(const l of s.toolCalls||[])t.push({id:`t-${l.id}`,role:"tool",content:l.argsPreview||l.argumentsJson||"",toolName:l.name});return}if(s.role==="toolResult"){t.push({id:`tr-${o}`,role:"tool",content:s.content,toolName:"result",error:!!s.error});return}s.role}),t}const Ye=new Set(["png","jpg","jpeg","webp","gif"]);function me(e){var s;const t=((s=e.split(".").pop())==null?void 0:s.toLowerCase())??"";return Ye.has(t)}function Ze(e){const t=/!\[[^\]]*]\(([^)]+)\)/g,s=[];let o;for(;(o=t.exec(e))!==null;){const i=o[1].trim();i&&!i.startsWith("http://")&&!i.startsWith("https://")&&s.push(we(i))}return s}function we(e){return e.replace(/^\.\//,"").replace(/^workspace\//,"")}function fe(e){const t=Ze(e).filter(me),s=new Set,o=[];for(const d of t)s.has(d)||(s.add(d),o.push(d));const i=/(?:imports|out|tmp|staging)[/][\w./-]+\.(?:png|jpe?g|webp|gif)/gi;let l;for(;(l=i.exec(e))!==null;){const d=we(l[0]);me(d)&&!s.has(d)&&(s.add(d),o.push(d))}return o}const U=[{id:"dashscope",label:"通义",baseUrl:"https://dashscope.aliyuncs.com/compatible-mode/v1",defaultModel:"qwen3.7-flash"},{id:"deepseek",label:"DeepSeek",baseUrl:"https://api.deepseek.com",defaultModel:"deepseek-flash"},{id:"zhipu_coding",label:"智谱 Coding",baseUrl:"https://open.bigmodel.cn/api/coding/paas/v4",defaultModel:"glm-5.3"}];function et(e){const t=e.toLowerCase();return t.includes("deepseek.com")?"deepseek":t.includes("bigmodel.cn")?"zhipu_coding":"dashscope"}function M(e){return U.find(t=>t.id===e)??U[0]}function L(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,s=Math.trunc(t);return t===s?`${s}k`:`${t.toFixed(1)}k`}function tt(e){if(!e||!e.hasUsage)return{headline:"累计用量",detail:"暂无 usage 记录"};const t=`约 ${L(e.totalTokens)} tokens`,s=e.cacheHitPercent,o=s!=null?`输入 ${L(e.inputTokens)} · 缓存 ${s}%`:`输入 ${L(e.inputTokens)} · 输出 ${L(e.outputTokens)}`;return{headline:t,detail:o}}function nt(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;")}function st(e){const t=[`输入 ${e.inputTokens.toLocaleString()}`,`输出 ${e.outputTokens.toLocaleString()}`,`缓存 ${e.cachedTokens.toLocaleString()}`,`usage 事件 ${e.usageEvents}`];let s="";return e.byModel.length&&(s="<ul>"+e.byModel.map(o=>`<li><strong>${nt(o.modelId||"（未知模型）")}</strong>：入 ${o.inputTokens.toLocaleString()} / 出 ${o.outputTokens.toLocaleString()}</li>`).join("")+"</ul>"),`<p>${t.join(" · ")}</p>${s}`}const Ee=document.getElementById("app");if(!Ee)throw new Error("#app missing");let n=Xe(),P=null;function r(e){n={...n,...e},Ie()}function c(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}async function b(){const e=await Me();r({sessions:e.sessions.slice().sort((t,s)=>s.updatedAt.localeCompare(t.updatedAt)),activeSessionId:e.activeSessionId??n.activeSessionId,isRunning:e.runInProgress})}async function I(){try{const e=await Ae();r({usage:e})}catch{}}function E(e){r({toast:e}),window.setTimeout(()=>{n.toast===e&&r({toast:null})},2200)}async function ge(){if(n.isRunning){r({error:"请先停止当前 Run"});return}if(n.activeSessionId&&j(n)){E("已是新会话");return}const e=await N();await b(),await $(e.sessionId),await I(),r({drawerOpen:!1})}async function at(){if(n.isRunning){r({error:"请先停止当前 Run"});return}if(n.activeSessionId&&j(n)){E("已是新会话");return}const e=await Be(!0);await b(),await $(e.session.sessionId),await I(),r({drawerOpen:!1}),E(e.summaryPath?"已总结并新建会话":"已新建会话")}function $e(){const e=n.config,t=(e==null?void 0:e.baseUrl)||"",s=(e==null?void 0:e.providerId)||et(t);return{providerId:s,baseUrl:t,model:(e==null?void 0:e.model)||M(s).defaultModel,maxContextTurns:(e==null?void 0:e.maxContextTurns)??6,maxContextMessages:(e==null?void 0:e.maxContextMessages)??0,maxTurnsPerRun:(e==null?void 0:e.maxTurnsPerRun)??24,maxToolCallsPerRun:(e==null?void 0:e.maxToolCallsPerRun)??48,webSearchBaseUrl:(e==null?void 0:e.webSearchBaseUrl)||"https://api.tavily.com"}}async function _(e,t){var s,o;r({modelsLoading:!0});try{const{models:i,source:l}=await Le(e.providerId,t);let d=e.model;if(!i.some(v=>v.modelId===d)){const v=M(e.providerId);d=((s=i.find(h=>h.modelId===v.defaultModel))==null?void 0:s.modelId)??((o=i[0])==null?void 0:o.modelId)??d}r({modelOptions:i,modelsLoading:!1,settingsDraft:{...e,model:d},modelsStatus:t?`已从网络更新 · ${i.length} 个模型`:`共 ${i.length} 个模型`})}catch(i){r({modelsLoading:!1,error:i instanceof Error?i.message:"模型列表加载失败"})}}function ot(e){const t=M(e),s={...n.settingsDraft??$e(),providerId:e,baseUrl:t.baseUrl,model:t.defaultModel};r({settingsDraft:s,error:null}),_(s,!1)}async function x(){const e=$e();r({showSettings:!0,settingsDraft:e,modelOptions:[],error:null}),await _(e,!1)}async function B(e){try{const t=await Ue(e);r({attachments:t})}catch{r({attachments:[]})}}async function $(e){const t=await he(e),s=be(t);r({activeSessionId:e,lines:ye(t),pendingAsk:s,askAnswers:{},streamingText:"",streamingReasoning:"",tools:[],error:null}),await B(e)}async function it(){const[e]=await Promise.all([Re(),b()]);r({config:e});let t=n.activeSessionId;t||(n.sessions.length===0?(t=(await N()).sessionId,await b()):(t=n.sessions[0].sessionId,await O(t))),await $(t),await I(),P=qe(rt)}function rt(e){if(!(e.sessionId&&n.activeSessionId&&e.sessionId!==n.activeSessionId))switch(e.type){case"AGENT_START":r({isRunning:!0,streamingText:"",streamingReasoning:"",tools:[],error:null});break;case"MESSAGE_UPDATE":r({streamingText:n.streamingText+String(e.payload.delta||""),isRunning:!0});break;case"REASONING_UPDATE":r({streamingReasoning:n.streamingReasoning+String(e.payload.delta||""),isRunning:!0});break;case"MESSAGE_RESET":r({streamingText:"",streamingReasoning:""});break;case"TOOL_EXECUTION_START":{const t=e.payload.toolCall,s={id:t.id||`tool-${Date.now()}`,toolCallId:t.id||"",toolName:t.name||"tool",argsPreview:t.argsPreview||"",running:!0};r({tools:[...n.tools,s],isRunning:!0});break}case"TOOL_EXECUTION_END":{const t=String(e.payload.toolCallId||""),s=n.tools.map(i=>i.toolCallId===t?{...i,running:!1,error:!!(e.payload.error||e.payload.toolError),resultSummary:String(e.payload.resultText||"")}:i),o=!!e.payload.stopRunWaitingUser;r({tools:s,isRunning:!o&&n.isRunning});break}case"AGENT_ERROR":r({error:String(e.payload.message||"运行出错"),isRunning:!1}),ve();break;case"AGENT_END":r({isRunning:!1}),ve();break}}async function ve(){if(n.activeSessionId)try{const e=await he(n.activeSessionId);r({lines:ye(e),pendingAsk:be(e),streamingText:"",streamingReasoning:"",tools:[],isRunning:!1}),await b(),await I()}catch(e){r({error:e instanceof Error?e.message:String(e)})}}async function D(){var o;const e=n.composer.trim();if(!e||!n.activeSessionId||n.isRunning||n.pendingAsk)return;if(!((o=n.config)!=null&&o.hasApiKey)){r({showSettings:!0,error:"请先配置 API Key"});return}const t={id:`local-${Date.now()}`,role:"user",content:e};r({lines:[...n.lines,t],composer:"",isRunning:!0,streamingText:"",tools:[],error:null});let s=e;if(n.attachments.length>0){const i=n.attachments.map(l=>`- ${l.workspaceRelativePath}`);s+=`

[用户附件]
${i.join(`
`)}`}try{await Fe(n.activeSessionId,s)}catch(i){r({isRunning:!1,error:i instanceof Error?i.message:String(i)})}}async function ct(){if(!n.pendingAsk||!n.activeSessionId)return;const e=Ge(n.pendingAsk,n.askAnswers);r({pendingAsk:null,composer:e}),await D()}function lt(e,t){if(e.role==="tool"){const l=(t?fe(e.content):[]).map(d=>`<figure class="bubble-image"><img src="${C(t,d)}" alt="${c(d)}" loading="lazy"/></figure>`).join("");return`<details class="tool-card"><summary>⚙ ${c(e.toolName||"tool")}</summary>
      <div class="tool-row ${e.error?"err":""}">${c(e.content)}${l}</div></details>`}const o=(t?fe(e.content):[]).map(i=>`<figure class="bubble-image"><img src="${C(t,i)}" alt="${c(i)}" loading="lazy"/></figure>`).join("");return`<div class="bubble ${e.role}"><div class="bubble-text">${c(e.content)}</div>${o}</div>`}function dt(){if(!n.tools.length&&!n.streamingText&&!n.streamingReasoning)return"";const e=n.tools.map(s=>{const o=s.running?"进行中…":s.error?"失败":"完成";return`<div class="tool-row ${s.error?"err":""}"><strong>${c(s.toolName)}</strong> · ${o}
        ${s.argsPreview?`<div>${c(s.argsPreview)}</div>`:""}
        ${s.resultSummary?`<div>${c(s.resultSummary)}</div>`:""}
      </div>`}).join("");return`<details class="tool-card" open><summary>思考与执行</summary>${[n.streamingReasoning?`<div class="tool-row">思考：${c(n.streamingReasoning)}</div>`:"",e].join("")}</details>
    ${n.streamingText?`<div class="bubble assistant">${c(n.streamingText)}</div>`:""}`}function ut(){return n.pendingAsk?`<div class="ask-panel"><h3>请回答后继续</h3>${n.pendingAsk.questions.map(t=>{const s=(t.options||[]).map(o=>`<button type="button" class="chip ${n.askAnswers[t.id]===o?"selected":""}" data-ask-id="${c(t.id)}" data-ask-opt="${c(o)}">${c(o)}</button>`).join("");return`<div class="ask-q">
        <label>${c(t.prompt)}</label>
        ${s?`<div class="ask-options">${s}</div>`:""}
        <input data-ask-input="${c(t.id)}" value="${c(n.askAnswers[t.id]||"")}" placeholder="你的回答" />
      </div>`}).join("")}
    <button type="button" class="primary-btn" id="ask-submit">提交回答</button></div>`:""}function pt(){if(!n.showArtifacts)return"";const e=n.artifacts.map(t=>{const o=Ve(t.workspaceRelativePath)?`<button type="button" class="link-btn" data-art-preview="1" data-art-session="${c(t.sessionId)}" data-art-path="${c(t.workspaceRelativePath)}">预览</button>`:"",i=n.activeSessionId?`<button type="button" class="link-btn" data-art-insert="1" data-art-session="${c(t.sessionId)}" data-art-path="${c(t.workspaceRelativePath)}">插入当前会话</button>`:"";return`<li><strong>${c(t.fileName)}</strong>
        <span class="muted"> · ${c(t.sessionId.slice(0,8))} · ${Math.round(t.sizeBytes/1024)}KB</span>
        <div class="artifact-actions">${o} ${i}</div></li>`}).join("");return`<div class="overlay" id="artifacts-overlay">
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
  </div>`}function mt(){if(!n.preview)return"";const e=C(n.preview.sessionId,n.preview.path);return`<div class="overlay preview-overlay" id="preview-overlay">
    <div class="preview-panel">
      <div class="preview-toolbar">
        <span>${c(n.preview.path)}</span>
        <button type="button" class="icon-btn" id="preview-close">✕</button>
      </div>
      <iframe class="preview-frame" src="${e}" title="preview"></iframe>
    </div>
  </div>`}function ft(){return n.showTools?`<div class="overlay" id="tools-overlay">
    <div class="overlay-panel usage-panel">
      <h2>Agent 工具</h2>
      <ul class="tools-list">${n.toolsList.map(t=>`<li><strong>${c(t.name)}</strong><span class="muted">${c(t.description)}</span></li>`).join("")||"<li class='muted'>无</li>"}</ul>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="tools-close">关闭</button>
      </div>
    </div>
  </div>`:""}function gt(){return!n.usagePanelOpen||!n.usage?"":`<div class="overlay" id="usage-overlay">
    <div class="overlay-panel usage-panel">
      <h2>累计用量</h2>
      <div class="usage-detail">${st(n.usage)}</div>
      <p class="muted">来自 agentRoot/logs/events.jsonl</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="usage-close">关闭</button>
      </div>
    </div>
  </div>`}function vt(){if(!n.showSettings||!n.settingsDraft)return"";const e=n.settingsDraft,t=n.config,s=U.map(d=>`<button type="button" class="provider-chip ${d.id===e.providerId?"active":""}" data-provider="${d.id}">${c(d.label)}</button>`).join(""),o=n.modelOptions.map(d=>{const v=d.modelId===e.model?"selected":"";return`<option value="${c(d.modelId)}" ${v}>${c(d.title)} · ${c(d.subtitle)}</option>`}).join(""),i=e.model&&!n.modelOptions.some(d=>d.modelId===e.model)?`<option value="${c(e.model)}" selected>${c(e.model)}（当前）</option>`:"",l=n.modelsLoading?"正在加载模型列表…":n.modelsStatus||(n.modelOptions.length?`共 ${n.modelOptions.length} 个模型`:"");return`<div class="overlay settings-overlay" id="settings-overlay">
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
              placeholder="${c(t!=null&&t.hasApiKey?`已配置 ${t.apiKeyHint||""}`:"粘贴 Key，留空则不修改")}" />
          </div>
          <div class="field">
            <label for="cfg-base">Base URL</label>
            <input id="cfg-base" class="settings-control" value="${c(e.baseUrl)}" />
          </div>
          <div class="field">
            <div class="field-label-row">
              <label for="cfg-model">模型</label>
              <button type="button" class="link-btn" id="cfg-refresh-models" ${n.modelsLoading?"disabled":""}>从网络刷新</button>
            </div>
            <select id="cfg-model" class="settings-control settings-select">${i}${o}</select>
            <p class="settings-hint">${c(l)}</p>
          </div>
        </section>
        <section class="settings-section">
          <h3>联网搜索（Tavily）</h3>
          <div class="field">
            <label for="cfg-web-key">Web Search API Key</label>
            <input id="cfg-web-key" class="settings-control" type="password" autocomplete="off"
              placeholder="${t!=null&&t.hasWebSearch?"已配置，留空不修改":"tvly-…"}" />
          </div>
          <div class="field">
            <label for="cfg-web-base">Tavily Base URL</label>
            <input id="cfg-web-base" class="settings-control" value="${c(e.webSearchBaseUrl)}" />
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
        <button type="button" class="link-btn" id="cfg-show-tools">查看已注册工具</button>
        <button type="button" class="primary-btn settings-save" id="cfg-save">保存并生效</button>
      </footer>
    </div>
  </div>`}function Ie(){var k;const e=n.sessions.map(f=>{const T=f.sessionId===n.activeSessionId?"active":"",A=f.title||f.sessionId.slice(0,8);return`<div class="session-item ${T}">
        <button type="button" class="session-title" data-use="${f.sessionId}">${c(A)}</button>
        <button type="button" class="session-delete" data-del="${f.sessionId}" title="删除">✕</button>
      </div>`}).join(""),t=He(n.lines,n.isRunning,n.streamingText,n.streamingReasoning,!!n.pendingAsk),s=n.lines.map(f=>lt(f,n.activeSessionId)).join(""),o=!n.isRunning&&!n.pendingAsk&&n.lines.length>0&&!j(n),i=t?'<button type="button" class="new-topic-btn" id="btn-new-topic">开始新话题</button>':"",l=o?'<button type="button" class="new-topic-btn secondary" id="btn-summarize-new">总结并新开话题</button>':"",d=n.lines.length===0&&!n.streamingText&&!n.isRunning?'<div class="landing"><h2>Agent1</h2><p>生产力助手 · 与 Android 端同款纸本风格。左侧管理会话，下方输入开始对话。</p></div>':s+i+l+dt()+ut(),v=tt(n.usage),h=n.isRunning||!!n.pendingAsk,R=n.pendingAsk?"请在上方卡片回答":n.isRunning?"生成中…":"输入消息，Enter 发送，Shift+Enter 换行";Ee.innerHTML=`
  <div class="app">
    <aside class="drawer ${n.drawerOpen?"":"collapsed"}">
      <div class="drawer-header">
        <div class="brand">Agent1</div>
        <div class="drawer-actions">
          <button type="button" class="icon-btn" id="btn-new" title="新会话">＋</button>
          <button type="button" class="icon-btn" id="btn-diag" title="导出诊断包">🩺</button>
          <button type="button" class="icon-btn" id="btn-settings" title="设置">⚙</button>
        </div>
      </div>
      <div class="session-list">${e}</div>
      <button type="button" class="usage-footer" id="usage-footer">
        <span class="usage-headline">${c(v.headline)}</span>
        <span class="usage-detail-line">${c(v.detail)}</span>
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
      <div class="chat-scroll" id="chat-scroll">${d}</div>
      <div class="composer-wrap">
        ${n.config&&!n.config.hasApiKey?'<div class="inline-setup">尚未配置 API Key。<button type="button" id="btn-setup">打开设置</button></div>':""}
        ${n.attachments.length?`<div class="attachment-chips">${n.attachments.map(f=>`<span class="chip">${c(f.fileName)} <button type="button" data-rm-attach="${c(f.workspaceRelativePath)}">×</button></span>`).join("")}</div>`:""}
        <div class="composer">
          <input type="file" id="file-input" class="hidden-file" multiple />
          <button type="button" class="icon-btn" id="btn-attach" title="添加附件" ${h?"disabled":""}>📎</button>
          <textarea id="composer" ${h?"disabled":""} placeholder="${R}">${c(n.composer)}</textarea>
          ${n.isRunning?'<button type="button" class="danger-btn" id="btn-stop">停止</button>':`<button type="button" class="primary-btn" id="btn-send" ${h?"disabled":""}>发送</button>`}
        </div>
        <div class="model-chip">模型 ${c(((k=n.config)==null?void 0:k.model)||"—")} ·
          <button type="button" id="btn-model">更改</button></div>
      </div>
    </section>
    ${vt()}
    ${ft()}
    ${gt()}
    ${pt()}
    ${mt()}
  </div>`,ht();const y=document.getElementById("chat-scroll");y&&(y.scrollTop=y.scrollHeight)}function ht(){var i,l,d,v,h,R,y,k,f,T,A,F,K,q,z,J,W,G,H,Q,X,V,Y,Z,ee,te,ne,se,ae,oe,ie,re,ce,le;(i=document.getElementById("btn-new"))==null||i.addEventListener("click",()=>void ge()),(l=document.getElementById("btn-diag"))==null||l.addEventListener("click",async()=>{try{await Pe(),E("诊断包已下载")}catch(a){r({error:a instanceof Error?a.message:String(a)})}}),(d=document.getElementById("btn-new-topic"))==null||d.addEventListener("click",()=>void ge()),(v=document.getElementById("btn-summarize-new"))==null||v.addEventListener("click",()=>void at()),(h=document.getElementById("btn-drawer"))==null||h.addEventListener("click",()=>{const a=!n.drawerOpen;r({drawerOpen:a}),a&&I()}),(R=document.getElementById("usage-footer"))==null||R.addEventListener("click",()=>{I().then(()=>r({usagePanelOpen:!0}))}),(y=document.getElementById("usage-close"))==null||y.addEventListener("click",()=>r({usagePanelOpen:!1})),(k=document.getElementById("usage-overlay"))==null||k.addEventListener("click",a=>{a.target===a.currentTarget&&r({usagePanelOpen:!1})}),(f=document.getElementById("btn-settings"))==null||f.addEventListener("click",()=>void x()),(T=document.getElementById("btn-settings-2"))==null||T.addEventListener("click",()=>void x()),(A=document.getElementById("btn-setup"))==null||A.addEventListener("click",()=>void x()),(F=document.getElementById("btn-model"))==null||F.addEventListener("click",()=>void x()),(K=document.getElementById("btn-artifacts"))==null||K.addEventListener("click",async()=>{const a=await Ce();r({artifacts:a,showArtifacts:!0,capQuery:"",capResult:""})}),(q=document.getElementById("artifacts-close"))==null||q.addEventListener("click",()=>r({showArtifacts:!1})),(z=document.getElementById("artifacts-overlay"))==null||z.addEventListener("click",a=>{a.target===a.currentTarget&&r({showArtifacts:!1})}),(J=document.getElementById("cap-search-btn"))==null||J.addEventListener("click",async()=>{var p;const a=document.getElementById("cap-query"),u=((p=a==null?void 0:a.value)==null?void 0:p.trim())||"";n={...n,capQuery:u};try{const g=await je(u);r({capResult:g})}catch(g){r({capResult:g instanceof Error?g.message:String(g)})}}),document.querySelectorAll("[data-art-preview]").forEach(a=>{a.addEventListener("click",()=>{const u=a.dataset.artSession,p=a.dataset.artPath;u&&p&&r({preview:{sessionId:u,path:p},showArtifacts:!1})})}),document.querySelectorAll("[data-art-insert]").forEach(a=>{a.addEventListener("click",async()=>{const u=a.dataset.artSession,p=a.dataset.artPath,g=n.activeSessionId;if(!(!g||!u||!p))try{await Oe(g,u,p),await B(g),E("已复制到 imports/")}catch(w){r({error:w instanceof Error?w.message:String(w)})}})}),(W=document.getElementById("preview-close"))==null||W.addEventListener("click",()=>r({preview:null})),(G=document.getElementById("preview-overlay"))==null||G.addEventListener("click",a=>{a.target===a.currentTarget&&r({preview:null})});const e=document.getElementById("file-input");(H=document.getElementById("btn-attach"))==null||H.addEventListener("click",()=>e==null?void 0:e.click()),e==null||e.addEventListener("change",async()=>{var u;const a=n.activeSessionId;if(!(!a||!((u=e.files)!=null&&u.length)))try{for(const p of Array.from(e.files))await De(a,p);await B(a),e.value=""}catch(p){r({error:p instanceof Error?p.message:String(p)})}}),document.querySelectorAll("[data-rm-attach]").forEach(a=>{a.addEventListener("click",async()=>{const u=n.activeSessionId,p=a.dataset.rmAttach;!u||!p||(await Ne(u,p),await B(u))})}),document.querySelectorAll("[data-use]").forEach(a=>{a.addEventListener("click",async()=>{const u=a.dataset.use;if(n.isRunning){r({error:"请先停止当前 Run 再切换会话"});return}await O(u),await $(u),await b()})}),document.querySelectorAll("[data-del]").forEach(a=>{a.addEventListener("click",async u=>{u.stopPropagation();const p=a.dataset.del;if(confirm("删除该会话？")&&(await _e(p),await b(),n.activeSessionId===p))if(n.sessions[0])await O(n.sessions[0].sessionId),await $(n.sessions[0].sessionId);else{const g=await N();await b(),await $(g.sessionId)}})});const t=document.getElementById("composer");t==null||t.addEventListener("input",()=>{n={...n,composer:t.value}}),t==null||t.addEventListener("keydown",a=>{a.key==="Enter"&&!a.shiftKey&&(a.preventDefault(),D())}),(Q=document.getElementById("btn-send"))==null||Q.addEventListener("click",()=>void D()),(X=document.getElementById("btn-stop"))==null||X.addEventListener("click",async()=>{await Ke()}),document.querySelectorAll("[data-ask-opt]").forEach(a=>{a.addEventListener("click",()=>{const u=a.dataset.askId,p=a.dataset.askOpt;r({askAnswers:{...n.askAnswers,[u]:p}})})}),document.querySelectorAll("[data-ask-input]").forEach(a=>{a.addEventListener("input",()=>{const u=a.dataset.askInput;n={...n,askAnswers:{...n.askAnswers,[u]:a.value}}})}),(V=document.getElementById("ask-submit"))==null||V.addEventListener("click",()=>void ct());const s=()=>r({showSettings:!1,settingsDraft:null,modelsStatus:null,modelsLoading:!1});(Y=document.getElementById("cfg-close"))==null||Y.addEventListener("click",s),(Z=document.getElementById("settings-overlay"))==null||Z.addEventListener("click",a=>{a.target===a.currentTarget&&s()}),document.querySelectorAll("[data-provider]").forEach(a=>{a.addEventListener("click",()=>{var p;const u=a.dataset.provider;u&&u!==((p=n.settingsDraft)==null?void 0:p.providerId)&&ot(u)})});const o=a=>{n.settingsDraft&&r({settingsDraft:{...n.settingsDraft,...a}})};(ee=document.getElementById("cfg-model"))==null||ee.addEventListener("change",a=>{o({model:a.target.value})}),(te=document.getElementById("cfg-ctx-turns"))==null||te.addEventListener("change",a=>{o({maxContextTurns:parseInt(a.target.value,10)||0})}),(ne=document.getElementById("cfg-ctx-msgs"))==null||ne.addEventListener("change",a=>{o({maxContextMessages:parseInt(a.target.value,10)||0})}),(se=document.getElementById("cfg-run-turns"))==null||se.addEventListener("change",a=>{o({maxTurnsPerRun:parseInt(a.target.value,10)||0})}),(ae=document.getElementById("cfg-tool-calls"))==null||ae.addEventListener("change",a=>{o({maxToolCallsPerRun:parseInt(a.target.value,10)||0})}),(oe=document.getElementById("cfg-show-tools"))==null||oe.addEventListener("click",async()=>{try{const{tools:a}=await xe();r({showTools:!0,toolsList:a})}catch(a){r({error:a instanceof Error?a.message:String(a)})}}),(ie=document.getElementById("tools-close"))==null||ie.addEventListener("click",()=>r({showTools:!1})),(re=document.getElementById("tools-overlay"))==null||re.addEventListener("click",a=>{a.target===a.currentTarget&&r({showTools:!1})}),(ce=document.getElementById("cfg-refresh-models"))==null||ce.addEventListener("click",()=>{const a=n.settingsDraft;a&&_(a,!0)}),(le=document.getElementById("cfg-save"))==null||le.addEventListener("click",async()=>{const a=n.settingsDraft;if(!a)return;const u=document.getElementById("cfg-key").value,p=document.getElementById("cfg-base").value,g=document.getElementById("cfg-model").value,w=parseInt(document.getElementById("cfg-ctx-turns").value,10),de=parseInt(document.getElementById("cfg-ctx-msgs").value,10),ue=parseInt(document.getElementById("cfg-run-turns").value,10),pe=parseInt(document.getElementById("cfg-tool-calls").value,10),ke=document.getElementById("cfg-web-key").value,Se=document.getElementById("cfg-web-base").value;try{const S=await Te({apiKey:u||void 0,baseUrl:p||void 0,model:g||void 0,providerId:a.providerId,maxContextTurns:Number.isFinite(w)?w:0,maxContextMessages:Number.isFinite(de)?de:0,maxTurnsPerRun:Number.isFinite(ue)?ue:0,maxToolCallsPerRun:Number.isFinite(pe)?pe:0,webSearchApiKey:ke||void 0,webSearchBaseUrl:Se||void 0});r({config:S,showSettings:!1,settingsDraft:null,modelsStatus:null,error:null}),E("设置已保存")}catch(S){r({error:S instanceof Error?S.message:String(S)})}})}Ie();it().catch(e=>{r({error:e instanceof Error?e.message:String(e)})});window.addEventListener("beforeunload",()=>P==null?void 0:P());
