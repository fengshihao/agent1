(function(){const t=document.createElement("link").relList;if(t&&t.supports&&t.supports("modulepreload"))return;for(const i of document.querySelectorAll('link[rel="modulepreload"]'))o(i);new MutationObserver(i=>{for(const l of i)if(l.type==="childList")for(const p of l.addedNodes)p.tagName==="LINK"&&p.rel==="modulepreload"&&o(p)}).observe(document,{childList:!0,subtree:!0});function s(i){const l={};return i.integrity&&(l.integrity=i.integrity),i.referrerPolicy&&(l.referrerPolicy=i.referrerPolicy),i.crossOrigin==="use-credentials"?l.credentials="include":i.crossOrigin==="anonymous"?l.credentials="omit":l.credentials="same-origin",l}function o(i){if(i.ep)return;i.ep=!0;const l=s(i);fetch(i.href,l)}})();async function m(e){const t=await e.json();if(!e.ok)throw new Error(t.error||e.statusText);return t}async function ze(){return m(await fetch("/api/config"))}async function He(e){return m(await fetch("/api/config",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(e)}))}async function We(){return m(await fetch("/api/usage"))}async function Ge(e,t=!1){const s=new URLSearchParams({provider:e});return t&&s.set("refresh","1"),m(await fetch(`/api/models?${s}`))}async function Le(){return m(await fetch("/api/mcp/servers"))}async function Qe(e){return m(await fetch("/api/mcp/servers",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({servers:e})}))}async function Xe(){return m(await fetch("/api/tools"))}async function Ve(){var l,p;const e=await fetch("/api/diagnostics/export");if(!e.ok)throw new Error("诊断包导出失败");const t=await e.blob(),s=((p=(l=e.headers.get("Content-Disposition"))==null?void 0:l.match(/filename="([^"]+)"/))==null?void 0:p[1])??`agent1-diag-${Date.now()}.zip`,o=URL.createObjectURL(t),i=document.createElement("a");i.href=o,i.download=s,i.click(),URL.revokeObjectURL(o)}async function Ye(e=!0){return m(await fetch("/api/sessions/rollover",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({summarize:e})}))}async function Ze(){return(await m(await fetch("/api/artifacts"))).artifacts}async function et(e,t,s){return m(await fetch(`/api/sessions/${encodeURIComponent(e)}/artifacts/import`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({sourceSessionId:t,workspaceRelativePath:s})}))}async function tt(e){return(await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`))).files}async function nt(e,t){const s=new FormData;return s.append("file",t),m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`,{method:"POST",body:s}))}async function st(e,t){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments/${encodeURIComponent(t)}`,{method:"DELETE"}))}function at(e,t){const s=t.split("/").map(o=>encodeURIComponent(o)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${s}`}async function ot(e){return(await m(await fetch(`/api/capabilities?q=${encodeURIComponent(e)}`))).text}async function it(){return m(await fetch("/api/sessions"))}async function W(){return m(await fetch("/api/sessions",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"}))}async function F(e){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/use`,{method:"POST",body:"{}"}))}async function rt(e){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}`,{method:"DELETE"}))}async function Me(e){return(await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/transcript`))).messages}async function ct(e,t){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/messages`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({text:t})}))}async function lt(){await m(await fetch("/api/run/abort",{method:"POST",body:"{}"}))}function dt(e){const t=new EventSource("/api/events");return t.onmessage=s=>{try{e(JSON.parse(s.data))}catch{}},()=>t.close()}const ut="ask_user",pt=["等待用户","waiting","ask_user"];function mt(e){try{const s=(JSON.parse(e.argumentsJson||"{}").questions||[]).map((o,i)=>({id:o.id||`q${i}`,prompt:o.prompt||o.question||"",options:o.options,allowFreeText:o.allowFreeText??o.allow_free_text??!0})).filter(o=>o.prompt);return s.length===0?null:{questions:s}}catch{return null}}function Ne(e){if(!e.length)return null;for(let t=e.length-1;t>=0;t--){const s=e[t];if(s.role==="user")return null;if(s.role==="assistant"){for(const o of s.toolCalls||[])if(o.name===ut)return mt(o)}if(s.role==="toolResult"){const o=s.content||"";if(!pt.some(l=>o.toLowerCase().includes(l.toLowerCase())))return null}}return null}function ft(e,t){return e.questions.map(o=>{const i=(t[o.id]||"").trim();return`${o.prompt}
→ ${i||"(未答)"}`}).join(`

`)}function gt(e,t,s,o,i){if(t||i||s.trim()||o.trim())return!1;const l=vt(e);return(l==null?void 0:l.role)==="assistant"}function vt(e){return e.filter(t=>t.role==="user"||t.role==="assistant").at(-1)}function ht(){return{sessions:[],activeSessionId:null,config:null,lines:[],streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,pendingAsk:null,askAnswers:{},error:null,showSettings:!1,drawerOpen:!0,composer:"",usage:null,usagePanelOpen:!1,toast:null,attachments:[],artifacts:[],showArtifacts:!1,preview:null,capQuery:"",capResult:"",modelOptions:[],settingsDraft:null,modelsLoading:!1,modelsStatus:null,showTools:!1,toolsList:[],lastRunTokenSummary:null,showMcp:!1,mcpServers:[],mcpStatus:null}}function je(e){const t=e.toLowerCase();return t.endsWith(".html")||t.endsWith(".htm")}function G(e){return e.isRunning||e.streamingText.trim()||e.streamingReasoning.trim()||e.tools.length>0||e.pendingAsk?!1:!e.lines.some(t=>t.role==="user"||t.role==="assistant")}function De(e){const t=[];return e.forEach((s,o)=>{var i;if(s.role==="user"){t.push({id:`u-${o}`,role:"user",content:s.content});return}if(s.role==="assistant"){(i=s.content)!=null&&i.trim()&&t.push({id:`a-${o}`,role:"assistant",content:s.content});for(const l of s.toolCalls||[])t.push({id:`t-${l.id}`,role:"tool",content:l.argsPreview||l.argumentsJson||"",toolName:l.name});return}if(s.role==="toolResult"){t.push({id:`tr-${o}`,role:"tool",content:s.content,toolName:"result",error:!!s.error});return}s.role}),t}const bt=new Set(["png","jpg","jpeg","webp","gif"]);function K(e){var s;const t=((s=e.split(".").pop())==null?void 0:s.toLowerCase())??"";return bt.has(t)}function _e(e){const t=/!\[[^\]]*]\(([^)]+)\)/g,s=[];let o;for(;(o=t.exec(e))!==null;){const i=o[1].trim();i&&!i.startsWith("http://")&&!i.startsWith("https://")&&s.push(Q(i))}return s}function Q(e){return e.replace(/^\.\//,"").replace(/^workspace\//,"")}function Pe(e,t){if(!e)return"";const s=/!?\[([^\]]*)]\(([^)]+)\)/g;let o="",i=0,l;for(;(l=s.exec(e))!==null;){o+=S(e.slice(i,l.index));const p=l[1],g=l[2].trim(),y=l[0].startsWith("!");if(t&&!g.startsWith("http://")&&!g.startsWith("https://")){const b=Q(g);y&&K(b)?o+=`<figure class="bubble-image"><img src="${qe(t,b)}" alt="${J(b)}" loading="lazy"/></figure>`:y?o+=S(l[0]):o+=`<button type="button" class="file-link" data-file-path="${J(b)}">${S(p||b)}</button>`}else o+=S(l[0]);i=l.index+l[0].length}return o+=S(e.slice(i)),o}function qe(e,t){const s=t.split("/").map(o=>encodeURIComponent(o)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${s}`}function S(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}function J(e){return S(e).replaceAll("'","&#39;")}function yt(e){const t=_e(e).filter(K),s=new Set,o=[];for(const p of t)s.has(p)||(s.add(p),o.push(p));const i=/(?:imports|out|tmp|staging)[/][\w./-]+\.(?:png|jpe?g|webp|gif)/gi;let l;for(;(l=i.exec(e))!==null;){const p=Q(l[0]);K(p)&&!s.has(p)&&(s.add(p),o.push(p))}return o}function xe(e,t){const s=new Set(_e(e));return yt(e).filter(o=>!s.has(o)).map(o=>`<figure class="bubble-image"><img src="${qe(t,o)}" alt="${J(o)}" loading="lazy"/></figure>`).join("")}let _=0,q=0,X=0;function wt(){_=0,q=0,X=0}function Et(e){_+=Number(e.inputTokens)||0,q+=Number(e.outputTokens)||0;const t=e.cachedTokens;t!=null&&(X+=Number(t)||0)}function Ce(e){const t=_>0||q>0;return!e&&!t?null:{inputTokens:_,outputTokens:q,cachedTokens:X,failed:e}}function $t(e){const t=[`输入 ${Be(e.inputTokens)}`,`输出 ${Be(e.outputTokens)}`];if(e.inputTokens>0){const s=Math.min(100,Math.round(e.cachedTokens*100/e.inputTokens));t.push(`缓存命中 ${s}%`)}return e.failed&&t.push("未完成"),t.join(" · ")}function Be(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,s=Math.trunc(t);return t===s?`${s}k`:`${t.toFixed(1)}k`}const z=[{id:"dashscope",label:"通义",baseUrl:"https://dashscope.aliyuncs.com/compatible-mode/v1",defaultModel:"qwen3.7-flash"},{id:"deepseek",label:"DeepSeek",baseUrl:"https://api.deepseek.com",defaultModel:"deepseek-flash"},{id:"zhipu_coding",label:"智谱 Coding",baseUrl:"https://open.bigmodel.cn/api/coding/paas/v4",defaultModel:"glm-5.3"}];function kt(e){const t=e.toLowerCase();return t.includes("deepseek.com")?"deepseek":t.includes("bigmodel.cn")?"zhipu_coding":"dashscope"}function V(e){return z.find(t=>t.id===e)??z[0]}function M(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,s=Math.trunc(t);return t===s?`${s}k`:`${t.toFixed(1)}k`}function St(e){if(!e||!e.hasUsage)return{headline:"累计用量",detail:"暂无 usage 记录"};const t=`约 ${M(e.totalTokens)} tokens`,s=e.cacheHitPercent,o=s!=null?`输入 ${M(e.inputTokens)} · 缓存 ${s}%`:`输入 ${M(e.inputTokens)} · 输出 ${M(e.outputTokens)}`;return{headline:t,detail:o}}function It(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;")}function Tt(e){const t=[`输入 ${e.inputTokens.toLocaleString()}`,`输出 ${e.outputTokens.toLocaleString()}`,`缓存 ${e.cachedTokens.toLocaleString()}`,`usage 事件 ${e.usageEvents}`];let s="";return e.byModel.length&&(s="<ul>"+e.byModel.map(o=>`<li><strong>${It(o.modelId||"（未知模型）")}</strong>：入 ${o.inputTokens.toLocaleString()} / 出 ${o.outputTokens.toLocaleString()}</li>`).join("")+"</ul>"),`<p>${t.join(" · ")}</p>${s}`}const Fe=document.getElementById("app");if(!Fe)throw new Error("#app missing");let n=ht(),j=null;function r(e){n={...n,...e},Je()}function c(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}async function $(){const e=await it();r({sessions:e.sessions.slice().sort((t,s)=>s.updatedAt.localeCompare(t.updatedAt)),activeSessionId:e.activeSessionId??n.activeSessionId,isRunning:e.runInProgress})}async function T(){try{const e=await We();r({usage:e})}catch{}}function E(e){r({toast:e}),window.setTimeout(()=>{n.toast===e&&r({toast:null})},2200)}async function Oe(){if(n.isRunning){r({error:"请先停止当前 Run"});return}if(n.activeSessionId&&G(n)){E("已是新会话");return}const e=await W();await $(),await I(e.sessionId),await T(),r({drawerOpen:!1})}async function Rt(){if(n.isRunning){r({error:"请先停止当前 Run"});return}if(n.activeSessionId&&G(n)){E("已是新会话");return}const e=await Ye(!0);await $(),await I(e.session.sessionId),await T(),r({drawerOpen:!1}),E(e.summaryPath?"已总结并新建会话":"已新建会话")}function Ke(){const e=n.config,t=(e==null?void 0:e.baseUrl)||"",s=(e==null?void 0:e.providerId)||kt(t);return{providerId:s,baseUrl:t,model:(e==null?void 0:e.model)||V(s).defaultModel,maxContextTurns:(e==null?void 0:e.maxContextTurns)??6,maxContextMessages:(e==null?void 0:e.maxContextMessages)??0,maxTurnsPerRun:(e==null?void 0:e.maxTurnsPerRun)??24,maxToolCallsPerRun:(e==null?void 0:e.maxToolCallsPerRun)??48,webSearchBaseUrl:(e==null?void 0:e.webSearchBaseUrl)||"https://api.tavily.com"}}async function Y(e,t){var s,o;r({modelsLoading:!0});try{const{models:i,source:l}=await Ge(e.providerId,t);let p=e.model;if(!i.some(g=>g.modelId===p)){const g=V(e.providerId);p=((s=i.find(y=>y.modelId===g.defaultModel))==null?void 0:s.modelId)??((o=i[0])==null?void 0:o.modelId)??p}r({modelOptions:i,modelsLoading:!1,settingsDraft:{...e,model:p},modelsStatus:t?`已从网络更新 · ${i.length} 个模型`:`共 ${i.length} 个模型`})}catch(i){r({modelsLoading:!1,error:i instanceof Error?i.message:"模型列表加载失败"})}}function At(e){const t=V(e),s={...n.settingsDraft??Ke(),providerId:e,baseUrl:t.baseUrl,model:t.defaultModel};r({settingsDraft:s,error:null}),Y(s,!1)}async function N(){const e=Ke();r({showSettings:!0,settingsDraft:e,modelOptions:[],error:null}),await Y(e,!1)}async function D(e){try{const t=await tt(e);r({attachments:t})}catch{r({attachments:[]})}}async function I(e){const t=await Me(e),s=Ne(t);r({activeSessionId:e,lines:De(t),pendingAsk:s,askAnswers:{},streamingText:"",streamingReasoning:"",tools:[],error:null}),await D(e)}async function Lt(){const[e]=await Promise.all([ze(),$()]);r({config:e});let t=n.activeSessionId;t||(n.sessions.length===0?(t=(await W()).sessionId,await $()):(t=n.sessions[0].sessionId,await F(t))),await I(t),await T(),j=dt(Pt)}function Pt(e){if(!(e.sessionId&&n.activeSessionId&&e.sessionId!==n.activeSessionId))switch(e.type){case"AGENT_START":wt(),r({isRunning:!0,streamingText:"",streamingReasoning:"",tools:[],error:null,lastRunTokenSummary:null});break;case"USAGE":Et(e.payload);break;case"MESSAGE_UPDATE":r({streamingText:n.streamingText+String(e.payload.delta||""),isRunning:!0});break;case"REASONING_UPDATE":r({streamingReasoning:n.streamingReasoning+String(e.payload.delta||""),isRunning:!0});break;case"MESSAGE_RESET":r({streamingText:"",streamingReasoning:""});break;case"TOOL_EXECUTION_START":{const t=e.payload.toolCall,s={id:t.id||`tool-${Date.now()}`,toolCallId:t.id||"",toolName:t.name||"tool",argsPreview:t.argsPreview||"",running:!0};r({tools:[...n.tools,s],isRunning:!0});break}case"TOOL_EXECUTION_END":{const t=String(e.payload.toolCallId||""),s=n.tools.map(i=>i.toolCallId===t?{...i,running:!1,error:!!(e.payload.error||e.payload.toolError),resultSummary:String(e.payload.resultText||"")}:i),o=!!e.payload.stopRunWaitingUser;r({tools:s,isRunning:!o&&n.isRunning});break}case"AGENT_ERROR":r({error:String(e.payload.message||"运行出错"),isRunning:!1,lastRunTokenSummary:Ce(!0)}),Ue();break;case"AGENT_END":r({isRunning:!1,lastRunTokenSummary:Ce(!1)}),Ue();break}}async function Ue(){if(!n.activeSessionId)return;const e=n.lastRunTokenSummary;try{const t=await Me(n.activeSessionId);r({lines:De(t),pendingAsk:Ne(t),streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,lastRunTokenSummary:e}),await $(),await T()}catch(t){r({error:t instanceof Error?t.message:String(t)})}}async function H(){var o;const e=n.composer.trim();if(!e||!n.activeSessionId||n.isRunning||n.pendingAsk)return;if(!((o=n.config)!=null&&o.hasApiKey)){r({showSettings:!0,error:"请先配置 API Key"});return}const t={id:`local-${Date.now()}`,role:"user",content:e};r({lines:[...n.lines,t],composer:"",isRunning:!0,streamingText:"",tools:[],error:null,lastRunTokenSummary:null});let s=e;if(n.attachments.length>0){const i=n.attachments.map(l=>`- ${l.workspaceRelativePath}`);s+=`

[用户附件]
${i.join(`
`)}`}try{await ct(n.activeSessionId,s)}catch(i){r({isRunning:!1,error:i instanceof Error?i.message:String(i)})}}async function xt(){if(!n.pendingAsk||!n.activeSessionId)return;const e=ft(n.pendingAsk,n.askAnswers);r({pendingAsk:null,composer:e}),await H()}function Ct(e,t){if(e.role==="tool"){const o=t?Pe(e.content,t)+xe(e.content,t):c(e.content);return`<details class="tool-card"><summary>⚙ ${c(e.toolName||"tool")}</summary>
      <div class="tool-row ${e.error?"err":""}">${o}</div></details>`}const s=t?Pe(e.content,t)+xe(e.content,t):c(e.content);return`<div class="bubble ${e.role}"><div class="bubble-text">${s}</div></div>`}function Bt(){if(!n.tools.length&&!n.streamingText&&!n.streamingReasoning)return"";const e=n.tools.map(s=>{const o=s.running?"进行中…":s.error?"失败":"完成";return`<div class="tool-row ${s.error?"err":""}"><strong>${c(s.toolName)}</strong> · ${o}
        ${s.argsPreview?`<div>${c(s.argsPreview)}</div>`:""}
        ${s.resultSummary?`<div>${c(s.resultSummary)}</div>`:""}
      </div>`}).join("");return`<details class="tool-card" open><summary>思考与执行</summary>${[n.streamingReasoning?`<div class="tool-row">思考：${c(n.streamingReasoning)}</div>`:"",e].join("")}</details>
    ${n.streamingText?`<div class="bubble assistant">${c(n.streamingText)}</div>`:""}`}function Ot(){return n.pendingAsk?`<div class="ask-panel"><h3>请回答后继续</h3>${n.pendingAsk.questions.map(t=>{const s=(t.options||[]).map(o=>`<button type="button" class="chip ${n.askAnswers[t.id]===o?"selected":""}" data-ask-id="${c(t.id)}" data-ask-opt="${c(o)}">${c(o)}</button>`).join("");return`<div class="ask-q">
        <label>${c(t.prompt)}</label>
        ${s?`<div class="ask-options">${s}</div>`:""}
        <input data-ask-input="${c(t.id)}" value="${c(n.askAnswers[t.id]||"")}" placeholder="你的回答" />
      </div>`}).join("")}
    <button type="button" class="primary-btn" id="ask-submit">提交回答</button></div>`:""}function Ut(){if(!n.showArtifacts)return"";const e=n.artifacts.map(t=>{const o=je(t.workspaceRelativePath)?`<button type="button" class="link-btn" data-art-preview="1" data-art-session="${c(t.sessionId)}" data-art-path="${c(t.workspaceRelativePath)}">预览</button>`:"",i=n.activeSessionId?`<button type="button" class="link-btn" data-art-insert="1" data-art-session="${c(t.sessionId)}" data-art-path="${c(t.workspaceRelativePath)}">插入当前会话</button>`:"";return`<li><strong>${c(t.fileName)}</strong>
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
  </div>`}function Mt(){if(!n.preview)return"";const e=at(n.preview.sessionId,n.preview.path);return`<div class="overlay preview-overlay" id="preview-overlay">
    <div class="preview-panel">
      <div class="preview-toolbar">
        <span>${c(n.preview.path)}</span>
        <button type="button" class="icon-btn" id="preview-close">✕</button>
      </div>
      <iframe class="preview-frame" src="${e}" title="preview"></iframe>
    </div>
  </div>`}function Nt(){if(!n.showMcp)return"";const e=n.mcpServers.map((t,s)=>{const o=JSON.stringify(t.headers||{});return`<div class="mcp-card" data-mcp-index="${s}">
        <div class="field-row">
          <div class="field"><label>名称</label><input class="settings-control mcp-name" value="${c(t.name)}" /></div>
          <label class="mcp-enable"><input type="checkbox" class="mcp-enabled" ${t.enabled?"checked":""}/> 启用</label>
        </div>
        <div class="field"><label>URL</label><input class="settings-control mcp-url" value="${c(t.url)}" /></div>
        <div class="field"><label>Headers (JSON)</label><input class="settings-control mcp-headers" value="${c(o)}" /></div>
        <p class="settings-hint">${t.toolCount?`${t.toolCount} 个工具`:"保存后尝试索引工具"}</p>
        <button type="button" class="link-btn mcp-remove" data-mcp-index="${s}">删除</button>
      </div>`}).join("");return`<div class="overlay settings-overlay" id="mcp-overlay">
    <div class="overlay-panel settings-panel">
      <header class="settings-header">
        <h2>MCP 服务器</h2>
        <button type="button" class="icon-btn" id="mcp-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <p class="muted">配置写入 agentRoot/mcp_servers.json；脚本内通过 $mcp 调用。保存后会刷新 find_caps 索引。</p>
        ${n.mcpStatus?`<p class="settings-hint">${c(n.mcpStatus)}</p>`:""}
        <div id="mcp-list">${e}</div>
        <button type="button" class="link-btn" id="mcp-add">添加服务器</button>
      </div>
      <footer class="settings-footer">
        <button type="button" class="primary-btn" id="mcp-save">保存并索引</button>
      </footer>
    </div>
  </div>`}function jt(){return n.showTools?`<div class="overlay" id="tools-overlay">
    <div class="overlay-panel usage-panel">
      <h2>Agent 工具</h2>
      <ul class="tools-list">${n.toolsList.map(t=>`<li><strong>${c(t.name)}</strong><span class="muted">${c(t.description)}</span></li>`).join("")||"<li class='muted'>无</li>"}</ul>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="tools-close">关闭</button>
      </div>
    </div>
  </div>`:""}function Dt(){return!n.usagePanelOpen||!n.usage?"":`<div class="overlay" id="usage-overlay">
    <div class="overlay-panel usage-panel">
      <h2>累计用量</h2>
      <div class="usage-detail">${Tt(n.usage)}</div>
      <p class="muted">来自 agentRoot/logs/events.jsonl</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="usage-close">关闭</button>
      </div>
    </div>
  </div>`}function _t(){if(!n.showSettings||!n.settingsDraft)return"";const e=n.settingsDraft,t=n.config,s=z.map(p=>`<button type="button" class="provider-chip ${p.id===e.providerId?"active":""}" data-provider="${p.id}">${c(p.label)}</button>`).join(""),o=n.modelOptions.map(p=>{const g=p.modelId===e.model?"selected":"";return`<option value="${c(p.modelId)}" ${g}>${c(p.title)} · ${c(p.subtitle)}</option>`}).join(""),i=e.model&&!n.modelOptions.some(p=>p.modelId===e.model)?`<option value="${c(e.model)}" selected>${c(e.model)}（当前）</option>`:"",l=n.modelsLoading?"正在加载模型列表…":n.modelsStatus||(n.modelOptions.length?`共 ${n.modelOptions.length} 个模型`:"");return`<div class="overlay settings-overlay" id="settings-overlay">
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
        <button type="button" class="link-btn" id="cfg-open-mcp">MCP 服务器</button>
        <button type="button" class="link-btn" id="cfg-show-tools">已注册工具</button>
        <button type="button" class="primary-btn settings-save" id="cfg-save">保存并生效</button>
      </footer>
    </div>
  </div>`}function Je(){var R;const e=n.sessions.map(h=>{const L=h.sessionId===n.activeSessionId?"active":"",P=h.title||h.sessionId.slice(0,8);return`<div class="session-item ${L}">
        <button type="button" class="session-title" data-use="${h.sessionId}">${c(P)}</button>
        <button type="button" class="session-delete" data-del="${h.sessionId}" title="删除">✕</button>
      </div>`}).join(""),t=gt(n.lines,n.isRunning,n.streamingText,n.streamingReasoning,!!n.pendingAsk),s=n.lines.map(h=>Ct(h,n.activeSessionId)).join(""),o=!n.isRunning&&!n.pendingAsk&&n.lines.length>0&&!G(n),i=t?'<button type="button" class="new-topic-btn" id="btn-new-topic">开始新话题</button>':"",l=o?'<button type="button" class="new-topic-btn secondary" id="btn-summarize-new">总结并新开话题</button>':"",p=n.lastRunTokenSummary&&!n.isRunning?`<div class="run-token-summary ${n.lastRunTokenSummary.failed?"failed":""}">${c($t(n.lastRunTokenSummary))}</div>`:"",g=n.lines.length===0&&!n.streamingText&&!n.isRunning?'<div class="landing"><h2>Agent1</h2><p>生产力助手 · 与 Android 端同款纸本风格。左侧管理会话，下方输入开始对话。</p></div>':s+p+i+l+Bt()+Ot(),y=St(n.usage),b=n.isRunning||!!n.pendingAsk,A=n.pendingAsk?"请在上方卡片回答":n.isRunning?"生成中…":"输入消息，Enter 发送，Shift+Enter 换行";Fe.innerHTML=`
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
        <span class="usage-headline">${c(y.headline)}</span>
        <span class="usage-detail-line">${c(y.detail)}</span>
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
        ${n.attachments.length?`<div class="attachment-chips">${n.attachments.map(h=>`<span class="chip">${c(h.fileName)} <button type="button" data-rm-attach="${c(h.workspaceRelativePath)}">×</button></span>`).join("")}</div>`:""}
        <div class="composer">
          <input type="file" id="file-input" class="hidden-file" multiple />
          <button type="button" class="icon-btn" id="btn-attach" title="添加附件" ${b?"disabled":""}>📎</button>
          <textarea id="composer" ${b?"disabled":""} placeholder="${A}">${c(n.composer)}</textarea>
          ${n.isRunning?'<button type="button" class="danger-btn" id="btn-stop">停止</button>':`<button type="button" class="primary-btn" id="btn-send" ${b?"disabled":""}>发送</button>`}
        </div>
        <div class="model-chip">模型 ${c(((R=n.config)==null?void 0:R.model)||"—")} ·
          <button type="button" id="btn-model">更改</button></div>
      </div>
    </section>
    ${_t()}
    ${Nt()}
    ${jt()}
    ${Dt()}
    ${Ut()}
    ${Mt()}
  </div>`,qt();const k=document.getElementById("chat-scroll");k&&(k.scrollTop=k.scrollHeight)}function qt(){var l,p,g,y,b,A,k,R,h,L,P,Z,ee,te,ne,se,ae,oe,ie,re,ce,le,de,ue,pe,me,fe,ge,ve,he,be,ye,we,Ee,$e,ke,Se,Ie,Te;(l=document.getElementById("btn-new"))==null||l.addEventListener("click",()=>void Oe()),(p=document.getElementById("btn-diag"))==null||p.addEventListener("click",async()=>{try{await Ve(),E("诊断包已下载")}catch(a){r({error:a instanceof Error?a.message:String(a)})}}),(g=document.getElementById("btn-new-topic"))==null||g.addEventListener("click",()=>void Oe()),(y=document.getElementById("btn-summarize-new"))==null||y.addEventListener("click",()=>void Rt()),(b=document.getElementById("btn-drawer"))==null||b.addEventListener("click",()=>{const a=!n.drawerOpen;r({drawerOpen:a}),a&&T()}),(A=document.getElementById("usage-footer"))==null||A.addEventListener("click",()=>{T().then(()=>r({usagePanelOpen:!0}))}),(k=document.getElementById("usage-close"))==null||k.addEventListener("click",()=>r({usagePanelOpen:!1})),(R=document.getElementById("usage-overlay"))==null||R.addEventListener("click",a=>{a.target===a.currentTarget&&r({usagePanelOpen:!1})}),(h=document.getElementById("btn-settings"))==null||h.addEventListener("click",()=>void N()),(L=document.getElementById("btn-settings-2"))==null||L.addEventListener("click",()=>void N()),(P=document.getElementById("btn-setup"))==null||P.addEventListener("click",()=>void N()),(Z=document.getElementById("btn-model"))==null||Z.addEventListener("click",()=>void N()),(ee=document.getElementById("btn-artifacts"))==null||ee.addEventListener("click",async()=>{const a=await Ze();r({artifacts:a,showArtifacts:!0,capQuery:"",capResult:""})}),(te=document.getElementById("artifacts-close"))==null||te.addEventListener("click",()=>r({showArtifacts:!1})),(ne=document.getElementById("artifacts-overlay"))==null||ne.addEventListener("click",a=>{a.target===a.currentTarget&&r({showArtifacts:!1})}),(se=document.getElementById("cap-search-btn"))==null||se.addEventListener("click",async()=>{var d;const a=document.getElementById("cap-query"),u=((d=a==null?void 0:a.value)==null?void 0:d.trim())||"";n={...n,capQuery:u};try{const f=await ot(u);r({capResult:f})}catch(f){r({capResult:f instanceof Error?f.message:String(f)})}}),document.querySelectorAll("[data-art-preview]").forEach(a=>{a.addEventListener("click",()=>{const u=a.dataset.artSession,d=a.dataset.artPath;u&&d&&r({preview:{sessionId:u,path:d},showArtifacts:!1})})}),document.querySelectorAll("[data-art-insert]").forEach(a=>{a.addEventListener("click",async()=>{const u=a.dataset.artSession,d=a.dataset.artPath,f=n.activeSessionId;if(!(!f||!u||!d))try{await et(f,u,d),await D(f),E("已复制到 imports/")}catch(v){r({error:v instanceof Error?v.message:String(v)})}})}),(ae=document.getElementById("preview-close"))==null||ae.addEventListener("click",()=>r({preview:null})),(oe=document.getElementById("preview-overlay"))==null||oe.addEventListener("click",a=>{a.target===a.currentTarget&&r({preview:null})});const e=document.getElementById("file-input");(ie=document.getElementById("btn-attach"))==null||ie.addEventListener("click",()=>e==null?void 0:e.click()),e==null||e.addEventListener("change",async()=>{var u;const a=n.activeSessionId;if(!(!a||!((u=e.files)!=null&&u.length)))try{for(const d of Array.from(e.files))await nt(a,d);await D(a),e.value=""}catch(d){r({error:d instanceof Error?d.message:String(d)})}}),document.querySelectorAll("[data-rm-attach]").forEach(a=>{a.addEventListener("click",async()=>{const u=n.activeSessionId,d=a.dataset.rmAttach;!u||!d||(await st(u,d),await D(u))})}),document.querySelectorAll("[data-use]").forEach(a=>{a.addEventListener("click",async()=>{const u=a.dataset.use;if(n.isRunning){r({error:"请先停止当前 Run 再切换会话"});return}await F(u),await I(u),await $()})}),document.querySelectorAll("[data-del]").forEach(a=>{a.addEventListener("click",async u=>{u.stopPropagation();const d=a.dataset.del;if(confirm("删除该会话？")&&(await rt(d),await $(),n.activeSessionId===d))if(n.sessions[0])await F(n.sessions[0].sessionId),await I(n.sessions[0].sessionId);else{const f=await W();await $(),await I(f.sessionId)}})});const t=document.getElementById("composer");t==null||t.addEventListener("input",()=>{n={...n,composer:t.value}}),t==null||t.addEventListener("keydown",a=>{a.key==="Enter"&&!a.shiftKey&&(a.preventDefault(),H())}),(re=document.getElementById("btn-send"))==null||re.addEventListener("click",()=>void H()),(ce=document.getElementById("btn-stop"))==null||ce.addEventListener("click",async()=>{await lt()}),document.querySelectorAll("[data-ask-opt]").forEach(a=>{a.addEventListener("click",()=>{const u=a.dataset.askId,d=a.dataset.askOpt;r({askAnswers:{...n.askAnswers,[u]:d}})})}),document.querySelectorAll("[data-ask-input]").forEach(a=>{a.addEventListener("input",()=>{const u=a.dataset.askInput;n={...n,askAnswers:{...n.askAnswers,[u]:a.value}}})}),(le=document.getElementById("ask-submit"))==null||le.addEventListener("click",()=>void xt());const s=()=>r({showSettings:!1,settingsDraft:null,modelsStatus:null,modelsLoading:!1});(de=document.getElementById("cfg-close"))==null||de.addEventListener("click",s),(ue=document.getElementById("settings-overlay"))==null||ue.addEventListener("click",a=>{a.target===a.currentTarget&&s()}),document.querySelectorAll("[data-provider]").forEach(a=>{a.addEventListener("click",()=>{var d;const u=a.dataset.provider;u&&u!==((d=n.settingsDraft)==null?void 0:d.providerId)&&At(u)})});const o=a=>{n.settingsDraft&&r({settingsDraft:{...n.settingsDraft,...a}})};(pe=document.getElementById("cfg-model"))==null||pe.addEventListener("change",a=>{o({model:a.target.value})}),(me=document.getElementById("cfg-ctx-turns"))==null||me.addEventListener("change",a=>{o({maxContextTurns:parseInt(a.target.value,10)||0})}),(fe=document.getElementById("cfg-ctx-msgs"))==null||fe.addEventListener("change",a=>{o({maxContextMessages:parseInt(a.target.value,10)||0})}),(ge=document.getElementById("cfg-run-turns"))==null||ge.addEventListener("change",a=>{o({maxTurnsPerRun:parseInt(a.target.value,10)||0})}),(ve=document.getElementById("cfg-tool-calls"))==null||ve.addEventListener("change",a=>{o({maxToolCallsPerRun:parseInt(a.target.value,10)||0})});async function i(){try{const{servers:a}=await Le();r({showMcp:!0,mcpServers:a,mcpStatus:null,showSettings:!1})}catch(a){r({error:a instanceof Error?a.message:String(a)})}}(he=document.getElementById("cfg-open-mcp"))==null||he.addEventListener("click",()=>void i()),(be=document.getElementById("mcp-close"))==null||be.addEventListener("click",()=>r({showMcp:!1})),(ye=document.getElementById("mcp-overlay"))==null||ye.addEventListener("click",a=>{a.target===a.currentTarget&&r({showMcp:!1})}),(we=document.getElementById("mcp-add"))==null||we.addEventListener("click",()=>{r({mcpServers:[...n.mcpServers,{name:"server",url:"https://",enabled:!0,headers:{}}]})}),document.querySelectorAll(".mcp-remove").forEach(a=>{a.addEventListener("click",()=>{const u=Number(a.dataset.mcpIndex);r({mcpServers:n.mcpServers.filter((d,f)=>f!==u)})})}),(Ee=document.getElementById("mcp-save"))==null||Ee.addEventListener("click",async()=>{const a=document.querySelectorAll(".mcp-card"),u=[];a.forEach((d,f)=>{var U,w,Re,Ae;const v=n.mcpServers[f],x=((U=d.querySelector(".mcp-name"))==null?void 0:U.value.trim())||"",C=((w=d.querySelector(".mcp-url"))==null?void 0:w.value.trim())||"",B=((Re=d.querySelector(".mcp-enabled"))==null?void 0:Re.checked)??!0;let O={};try{O=JSON.parse(((Ae=d.querySelector(".mcp-headers"))==null?void 0:Ae.value)||"{}")}catch{}u.push({name:x,url:C,enabled:B,headers:O,toolCount:v==null?void 0:v.toolCount,lastListedAt:v==null?void 0:v.lastListedAt})});try{const d=await Qe(u),{servers:f}=await Le(),v=d.warnings.length?` · ${d.warnings.join("; ")}`:"";r({mcpServers:f,mcpStatus:`已索引 ${d.toolCount} 个 MCP 工具${v}`}),E("MCP 已保存")}catch(d){r({error:d instanceof Error?d.message:String(d)})}}),document.querySelectorAll(".file-link").forEach(a=>{a.addEventListener("click",()=>{const u=a.dataset.filePath,d=n.activeSessionId;!u||!d||(je(u)?r({preview:{sessionId:d,path:u}}):E(u))})}),($e=document.getElementById("cfg-show-tools"))==null||$e.addEventListener("click",async()=>{try{const{tools:a}=await Xe();r({showTools:!0,toolsList:a})}catch(a){r({error:a instanceof Error?a.message:String(a)})}}),(ke=document.getElementById("tools-close"))==null||ke.addEventListener("click",()=>r({showTools:!1})),(Se=document.getElementById("tools-overlay"))==null||Se.addEventListener("click",a=>{a.target===a.currentTarget&&r({showTools:!1})}),(Ie=document.getElementById("cfg-refresh-models"))==null||Ie.addEventListener("click",()=>{const a=n.settingsDraft;a&&Y(a,!0)}),(Te=document.getElementById("cfg-save"))==null||Te.addEventListener("click",async()=>{const a=n.settingsDraft;if(!a)return;const u=document.getElementById("cfg-key").value,d=document.getElementById("cfg-base").value,f=document.getElementById("cfg-model").value,v=parseInt(document.getElementById("cfg-ctx-turns").value,10),x=parseInt(document.getElementById("cfg-ctx-msgs").value,10),C=parseInt(document.getElementById("cfg-run-turns").value,10),B=parseInt(document.getElementById("cfg-tool-calls").value,10),O=document.getElementById("cfg-web-key").value,U=document.getElementById("cfg-web-base").value;try{const w=await He({apiKey:u||void 0,baseUrl:d||void 0,model:f||void 0,providerId:a.providerId,maxContextTurns:Number.isFinite(v)?v:0,maxContextMessages:Number.isFinite(x)?x:0,maxTurnsPerRun:Number.isFinite(C)?C:0,maxToolCallsPerRun:Number.isFinite(B)?B:0,webSearchApiKey:O||void 0,webSearchBaseUrl:U||void 0});r({config:w,showSettings:!1,settingsDraft:null,modelsStatus:null,error:null}),E("设置已保存")}catch(w){r({error:w instanceof Error?w.message:String(w)})}})}Je();Lt().catch(e=>{r({error:e instanceof Error?e.message:String(e)})});window.addEventListener("beforeunload",()=>j==null?void 0:j());
