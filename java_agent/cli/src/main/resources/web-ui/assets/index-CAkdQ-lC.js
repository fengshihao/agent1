(function(){const t=document.createElement("link").relList;if(t&&t.supports&&t.supports("modulepreload"))return;for(const r of document.querySelectorAll('link[rel="modulepreload"]'))o(r);new MutationObserver(r=>{for(const d of r)if(d.type==="childList")for(const p of d.addedNodes)p.tagName==="LINK"&&p.rel==="modulepreload"&&o(p)}).observe(document,{childList:!0,subtree:!0});function s(r){const d={};return r.integrity&&(d.integrity=r.integrity),r.referrerPolicy&&(d.referrerPolicy=r.referrerPolicy),r.crossOrigin==="use-credentials"?d.credentials="include":r.crossOrigin==="anonymous"?d.credentials="omit":d.credentials="same-origin",d}function o(r){if(r.ep)return;r.ep=!0;const d=s(r);fetch(r.href,d)}})();async function m(e){const t=await e.json();if(!e.ok)throw new Error(t.error||e.statusText);return t}async function et(){return m(await fetch("/api/config"))}async function tt(e){return m(await fetch("/api/config",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(e)}))}async function nt(){return m(await fetch("/api/usage"))}async function st(e,t=!1){const s=new URLSearchParams({provider:e});return t&&s.set("refresh","1"),m(await fetch(`/api/models?${s}`))}async function Ke(){return m(await fetch("/api/mcp/servers"))}async function at(e){return m(await fetch("/api/mcp/servers",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({servers:e})}))}async function ot(){return m(await fetch("/api/tools"))}async function it(){var d,p;const e=await fetch("/api/diagnostics/export");if(!e.ok)throw new Error("诊断包导出失败");const t=await e.blob(),s=((p=(d=e.headers.get("Content-Disposition"))==null?void 0:d.match(/filename="([^"]+)"/))==null?void 0:p[1])??`agent1-diag-${Date.now()}.zip`,o=URL.createObjectURL(t),r=document.createElement("a");r.href=o,r.download=s,r.click(),URL.revokeObjectURL(o)}async function rt(e=!0){return m(await fetch("/api/sessions/rollover",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({summarize:e})}))}async function We(){return(await m(await fetch("/api/artifacts"))).artifacts}async function ct(e,t,s){return m(await fetch(`/api/sessions/${encodeURIComponent(e)}/artifacts/import`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({sourceSessionId:t,workspaceRelativePath:s})}))}async function lt(e){return(await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`))).files}async function dt(e,t){const s=new FormData;return s.append("file",t),m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`,{method:"POST",body:s}))}async function ut(e,t){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments/${encodeURIComponent(t)}`,{method:"DELETE"}))}function pt(e,t){const s=t.split("/").map(o=>encodeURIComponent(o)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${s}`}async function mt(e){return(await m(await fetch(`/api/capabilities?q=${encodeURIComponent(e)}`))).text}async function ft(){return m(await fetch("/api/sessions"))}async function G(){return m(await fetch("/api/sessions",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"}))}async function _(e){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/use`,{method:"POST",body:"{}"}))}async function gt(e){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}`,{method:"DELETE"}))}async function vt(e){return m(await fetch(`/api/sessions/${encodeURIComponent(e)}/brief`))}async function ht(e){const t=e?`?sessionId=${encodeURIComponent(e)}`:"";return(await m(await fetch(`/api/logs/failed${t}`))).items}async function Je(e){return(await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/transcript`))).messages}async function bt(e,t){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/messages`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({text:t})}))}async function yt(){await m(await fetch("/api/run/abort",{method:"POST",body:"{}"}))}function wt(e){const t=new EventSource("/api/events");return t.onmessage=s=>{try{e(JSON.parse(s.data))}catch{}},()=>t.close()}const kt="ask_user",Et=["等待用户","waiting","ask_user"];function St(e){try{const s=(JSON.parse(e.argumentsJson||"{}").questions||[]).map((o,r)=>({id:o.id||`q${r}`,prompt:o.prompt||o.question||"",options:o.options,allowFreeText:o.allowFreeText??o.allow_free_text??!0})).filter(o=>o.prompt);return s.length===0?null:{questions:s}}catch{return null}}function ze(e){if(!e.length)return null;for(let t=e.length-1;t>=0;t--){const s=e[t];if(s.role==="user")return null;if(s.role==="assistant"){for(const o of s.toolCalls||[])if(o.name===kt)return St(o)}if(s.role==="toolResult"){const o=s.content||"";if(!Et.some(d=>o.toLowerCase().includes(d.toLowerCase())))return null}}return null}function $t(e,t){return e.questions.map(o=>{const r=(t[o.id]||"").trim();return`${o.prompt}
→ ${r||"(未答)"}`}).join(`

`)}function It(e,t,s,o,r){if(t||r||s.trim()||o.trim())return!1;const d=Tt(e);return(d==null?void 0:d.role)==="assistant"}function Tt(e){return e.filter(t=>t.role==="user"||t.role==="assistant").at(-1)}function Rt(){return{sessions:[],activeSessionId:null,config:null,lines:[],streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,pendingAsk:null,askAnswers:{},error:null,showSettings:!1,drawerOpen:!0,composer:"",usage:null,usagePanelOpen:!1,toast:null,attachments:[],artifacts:[],showArtifacts:!1,preview:null,capQuery:"",capResult:"",modelOptions:[],settingsDraft:null,modelsLoading:!1,modelsStatus:null,showTools:!1,toolsList:[],lastRunTokenSummary:null,showMcp:!1,mcpServers:[],mcpStatus:null,showFailedLogs:!1,failedLogs:[],failedLogsScopeSession:!0,moreMenuOpen:!1,showAbout:!1}}function He(e){const t=e.toLowerCase();return t.endsWith(".html")||t.endsWith(".htm")}function Q(e){return e.isRunning||e.streamingText.trim()||e.streamingReasoning.trim()||e.tools.length>0||e.pendingAsk?!1:!e.lines.some(t=>t.role==="user"||t.role==="assistant")}function Ge(e){const t=[];return e.forEach((s,o)=>{var r;if(s.role==="user"){t.push({id:`u-${o}`,role:"user",content:s.content});return}if(s.role==="assistant"){(r=s.content)!=null&&r.trim()&&t.push({id:`a-${o}`,role:"assistant",content:s.content});for(const d of s.toolCalls||[])t.push({id:`t-${d.id}`,role:"tool",content:d.argsPreview||d.argumentsJson||"",toolName:d.name});return}if(s.role==="toolResult"){t.push({id:`tr-${o}`,role:"tool",content:s.content,toolName:"result",error:!!s.error});return}s.role}),t}const Lt=new Set(["png","jpg","jpeg","webp","gif"]);function K(e){var s;const t=((s=e.split(".").pop())==null?void 0:s.toLowerCase())??"";return Lt.has(t)}function Qe(e){const t=/!\[[^\]]*]\(([^)]+)\)/g,s=[];let o;for(;(o=t.exec(e))!==null;){const r=o[1].trim();r&&!r.startsWith("http://")&&!r.startsWith("https://")&&s.push(X(r))}return s}function X(e){return e.replace(/^\.\//,"").replace(/^workspace\//,"")}function At(e){const t=[],s=/```[\w-]*\n?([\s\S]*?)```/g;let o=0,r;for(;(r=s.exec(e))!==null;)r.index>o&&t.push({kind:"text",text:e.slice(o,r.index)}),t.push({kind:"code",text:r[1]??""}),o=r.index+r[0].length;return o<e.length&&t.push({kind:"text",text:e.slice(o)}),t.length?t:[{kind:"text",text:e}]}function Oe(e,t){return e?At(e).map(s=>s.kind==="code"?`<pre class="bubble-code"><code>${S(s.text.trimEnd())}</code></pre>`:xt(s.text,t)).join(""):""}function xt(e,t){const s=/!?\[([^\]]*)]\(([^)]+)\)/g;let o="",r=0,d;for(;(d=s.exec(e))!==null;){o+=Me(e.slice(r,d.index));const p=d[1],g=d[2].trim(),y=d[0].startsWith("!");if(t&&!g.startsWith("http://")&&!g.startsWith("https://")){const b=X(g);y&&K(b)?o+=`<figure class="bubble-image"><img src="${Xe(t,b)}" alt="${W(b)}" loading="lazy"/></figure>`:y?o+=S(d[0]):o+=`<button type="button" class="file-link" data-file-path="${W(b)}">${S(p||b)}</button>`}else o+=S(d[0]);r=d.index+d[0].length}return o+=Me(e.slice(r)),o}function Me(e){const t=e.split(/`([^`\n]+)`/);let s="";for(let o=0;o<t.length;o++)o%2===0?s+=S(t[o]):s+=`<code class="bubble-inline-code">${S(t[o])}</code>`;return s}function Xe(e,t){const s=t.split("/").map(o=>encodeURIComponent(o)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${s}`}function S(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}function W(e){return S(e).replaceAll("'","&#39;")}function Pt(e){const t=Qe(e).filter(K),s=new Set,o=[];for(const p of t)s.has(p)||(s.add(p),o.push(p));const r=/(?:imports|out|tmp|staging)[/][\w./-]+\.(?:png|jpe?g|webp|gif)/gi;let d;for(;(d=r.exec(e))!==null;){const p=X(d[0]);K(p)&&!s.has(p)&&(s.add(p),o.push(p))}return o}function Ue(e,t){const s=new Set(Qe(e));return Pt(e).filter(o=>!s.has(o)).map(o=>`<figure class="bubble-image"><img src="${Xe(t,o)}" alt="${W(o)}" loading="lazy"/></figure>`).join("")}let D=0,F=0,V=0;function Ct(){D=0,F=0,V=0}function Bt(e){D+=Number(e.inputTokens)||0,F+=Number(e.outputTokens)||0;const t=e.cachedTokens;t!=null&&(V+=Number(t)||0)}function Ne(e){const t=D>0||F>0;return!e&&!t?null:{inputTokens:D,outputTokens:F,cachedTokens:V,failed:e}}function Ot(e){const t=[`输入 ${je(e.inputTokens)}`,`输出 ${je(e.outputTokens)}`];if(e.inputTokens>0){const s=Math.min(100,Math.round(e.cachedTokens*100/e.inputTokens));t.push(`缓存命中 ${s}%`)}return e.failed&&t.push("未完成"),t.join(" · ")}function je(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,s=Math.trunc(t);return t===s?`${s}k`:`${t.toFixed(1)}k`}const J=[{id:"dashscope",label:"通义",baseUrl:"https://dashscope.aliyuncs.com/compatible-mode/v1",defaultModel:"qwen3.7-flash"},{id:"deepseek",label:"DeepSeek",baseUrl:"https://api.deepseek.com",defaultModel:"deepseek-flash"},{id:"zhipu_coding",label:"智谱 Coding",baseUrl:"https://open.bigmodel.cn/api/coding/paas/v4",defaultModel:"glm-5.3"}];function Mt(e){const t=e.toLowerCase();return t.includes("deepseek.com")?"deepseek":t.includes("bigmodel.cn")?"zhipu_coding":"dashscope"}function Y(e){return J.find(t=>t.id===e)??J[0]}function U(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,s=Math.trunc(t);return t===s?`${s}k`:`${t.toFixed(1)}k`}function Ut(e){if(!e||!e.hasUsage)return{headline:"累计用量",detail:"暂无 usage 记录"};const t=`约 ${U(e.totalTokens)} tokens`,s=e.cacheHitPercent,o=s!=null?`输入 ${U(e.inputTokens)} · 缓存 ${s}%`:`输入 ${U(e.inputTokens)} · 输出 ${U(e.outputTokens)}`;return{headline:t,detail:o}}function Nt(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;")}function jt(e){const t=[`输入 ${e.inputTokens.toLocaleString()}`,`输出 ${e.outputTokens.toLocaleString()}`,`缓存 ${e.cachedTokens.toLocaleString()}`,`usage 事件 ${e.usageEvents}`];let s="";return e.byModel.length&&(s="<ul>"+e.byModel.map(o=>`<li><strong>${Nt(o.modelId||"（未知模型）")}</strong>：入 ${o.inputTokens.toLocaleString()} / 出 ${o.outputTokens.toLocaleString()}</li>`).join("")+"</ul>"),`<p>${t.join(" · ")}</p>${s}`}const Ve=document.getElementById("app");if(!Ve)throw new Error("#app missing");let n=Rt(),N=null;function i(e){n={...n,...e},Ze()}function c(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}async function E(){const e=await ft();i({sessions:e.sessions.slice().sort((t,s)=>s.updatedAt.localeCompare(t.updatedAt)),activeSessionId:e.activeSessionId??n.activeSessionId,isRunning:e.runInProgress})}async function T(){try{const e=await nt();i({usage:e})}catch{}}async function z(){const e=n.failedLogsScopeSession&&n.activeSessionId?n.activeSessionId:null,t=await ht(e);i({failedLogs:t})}async function Dt(){i({showFailedLogs:!0});try{await z()}catch(e){i({error:e instanceof Error?e.message:String(e)})}}function w(e){i({toast:e}),window.setTimeout(()=>{n.toast===e&&i({toast:null})},2200)}async function De(){if(n.isRunning){i({error:"请先停止当前 Run"});return}if(n.activeSessionId&&Q(n)){w("已是新会话");return}const e=await G();await E(),await I(e.sessionId),await T(),i({drawerOpen:!1})}async function Ft(){if(n.isRunning){i({error:"请先停止当前 Run"});return}if(n.activeSessionId&&Q(n)){w("已是新会话");return}const e=await rt(!0);await E(),await I(e.session.sessionId),await T(),i({drawerOpen:!1}),w(e.summaryPath?"已总结并新建会话":"已新建会话")}function Ye(){const e=n.config,t=(e==null?void 0:e.baseUrl)||"",s=(e==null?void 0:e.providerId)||Mt(t);return{providerId:s,baseUrl:t,model:(e==null?void 0:e.model)||Y(s).defaultModel,maxContextTurns:(e==null?void 0:e.maxContextTurns)??6,maxContextMessages:(e==null?void 0:e.maxContextMessages)??0,maxTurnsPerRun:(e==null?void 0:e.maxTurnsPerRun)??24,maxToolCallsPerRun:(e==null?void 0:e.maxToolCallsPerRun)??48,webSearchBaseUrl:(e==null?void 0:e.webSearchBaseUrl)||"https://api.tavily.com"}}async function Z(e,t){var s,o;i({modelsLoading:!0});try{const{models:r,source:d}=await st(e.providerId,t);let p=e.model;if(!r.some(g=>g.modelId===p)){const g=Y(e.providerId);p=((s=r.find(y=>y.modelId===g.defaultModel))==null?void 0:s.modelId)??((o=r[0])==null?void 0:o.modelId)??p}i({modelOptions:r,modelsLoading:!1,settingsDraft:{...e,model:p},modelsStatus:t?`已从网络更新 · ${r.length} 个模型`:`共 ${r.length} 个模型`})}catch(r){i({modelsLoading:!1,error:r instanceof Error?r.message:"模型列表加载失败"})}}function qt(e){const t=Y(e),s={...n.settingsDraft??Ye(),providerId:e,baseUrl:t.baseUrl,model:t.defaultModel};i({settingsDraft:s,error:null}),Z(s,!1)}async function q(){const e=Ye();i({showSettings:!0,settingsDraft:e,modelOptions:[],error:null,moreMenuOpen:!1}),await Z(e,!1)}async function Fe(){try{const{servers:e}=await Ke();i({showMcp:!0,mcpServers:e,mcpStatus:null,showSettings:!1,moreMenuOpen:!1})}catch(e){i({error:e instanceof Error?e.message:String(e)})}}async function _t(){try{const e=await We();i({artifacts:e,showArtifacts:!0,capQuery:"",capResult:"",moreMenuOpen:!1})}catch(e){i({error:e instanceof Error?e.message:String(e)})}}async function Kt(){const e=n.activeSessionId;if(!e)return;const{text:t}=await vt(e);await navigator.clipboard.writeText(t),w("简报已复制到剪贴板")}function qe(){i({moreMenuOpen:!1})}async function j(e){try{const t=await lt(e);i({attachments:t})}catch{i({attachments:[]})}}async function I(e){const t=await Je(e),s=ze(t);i({activeSessionId:e,lines:Ge(t),pendingAsk:s,askAnswers:{},streamingText:"",streamingReasoning:"",tools:[],error:null}),await j(e)}async function Wt(){const[e]=await Promise.all([et(),E()]);i({config:e});let t=n.activeSessionId;t||(n.sessions.length===0?(t=(await G()).sessionId,await E()):(t=n.sessions[0].sessionId,await _(t))),await I(t),await T(),N=wt(Jt)}function Jt(e){if(!(e.sessionId&&n.activeSessionId&&e.sessionId!==n.activeSessionId))switch(e.type){case"AGENT_START":Ct(),i({isRunning:!0,streamingText:"",streamingReasoning:"",tools:[],error:null,lastRunTokenSummary:null});break;case"USAGE":Bt(e.payload);break;case"MESSAGE_UPDATE":i({streamingText:n.streamingText+String(e.payload.delta||""),isRunning:!0});break;case"REASONING_UPDATE":i({streamingReasoning:n.streamingReasoning+String(e.payload.delta||""),isRunning:!0});break;case"MESSAGE_RESET":i({streamingText:"",streamingReasoning:""});break;case"TOOL_EXECUTION_START":{const t=e.payload.toolCall,s={id:t.id||`tool-${Date.now()}`,toolCallId:t.id||"",toolName:t.name||"tool",argsPreview:t.argsPreview||"",running:!0};i({tools:[...n.tools,s],isRunning:!0});break}case"TOOL_EXECUTION_END":{const t=String(e.payload.toolCallId||""),s=n.tools.map(r=>r.toolCallId===t?{...r,running:!1,error:!!(e.payload.error||e.payload.toolError),resultSummary:String(e.payload.resultText||"")}:r),o=!!e.payload.stopRunWaitingUser;i({tools:s,isRunning:!o&&n.isRunning});break}case"AGENT_ERROR":i({error:String(e.payload.message||"运行出错"),isRunning:!1,lastRunTokenSummary:Ne(!0)}),_e();break;case"AGENT_END":i({isRunning:!1,lastRunTokenSummary:Ne(!1)}),_e();break}}async function _e(){if(!n.activeSessionId)return;const e=n.lastRunTokenSummary;try{const t=await Je(n.activeSessionId);i({lines:Ge(t),pendingAsk:ze(t),streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,lastRunTokenSummary:e}),await E(),await T()}catch(t){i({error:t instanceof Error?t.message:String(t)})}}async function H(){var o;const e=n.composer.trim();if(!e||!n.activeSessionId||n.isRunning||n.pendingAsk)return;if(!((o=n.config)!=null&&o.hasApiKey)){i({showSettings:!0,error:"请先配置 API Key"});return}const t={id:`local-${Date.now()}`,role:"user",content:e};i({lines:[...n.lines,t],composer:"",isRunning:!0,streamingText:"",tools:[],error:null,lastRunTokenSummary:null});let s=e;if(n.attachments.length>0){const r=n.attachments.map(d=>`- ${d.workspaceRelativePath}`);s+=`

[用户附件]
${r.join(`
`)}`}try{await bt(n.activeSessionId,s)}catch(r){i({isRunning:!1,error:r instanceof Error?r.message:String(r)})}}async function zt(){if(!n.pendingAsk||!n.activeSessionId)return;const e=$t(n.pendingAsk,n.askAnswers);i({pendingAsk:null,composer:e}),await H()}function Ht(e,t){if(e.role==="tool"){const o=t?Oe(e.content,t)+Ue(e.content,t):c(e.content);return`<details class="tool-card"><summary>⚙ ${c(e.toolName||"tool")}</summary>
      <div class="tool-row ${e.error?"err":""}">${o}</div></details>`}const s=t?Oe(e.content,t)+Ue(e.content,t):c(e.content);return`<div class="bubble ${e.role}"><div class="bubble-text">${s}</div></div>`}function Gt(){if(!n.tools.length&&!n.streamingText&&!n.streamingReasoning)return"";const e=n.tools.map(s=>{const o=s.running?"进行中…":s.error?"失败":"完成";return`<div class="tool-row ${s.error?"err":""}"><strong>${c(s.toolName)}</strong> · ${o}
        ${s.argsPreview?`<div>${c(s.argsPreview)}</div>`:""}
        ${s.resultSummary?`<div>${c(s.resultSummary)}</div>`:""}
      </div>`}).join("");return`<details class="tool-card" open><summary>思考与执行</summary>${[n.streamingReasoning?`<div class="tool-row">思考：${c(n.streamingReasoning)}</div>`:"",e].join("")}</details>
    ${n.streamingText?`<div class="bubble assistant">${c(n.streamingText)}</div>`:""}`}function Qt(){return n.pendingAsk?`<div class="ask-panel"><h3>请回答后继续</h3>${n.pendingAsk.questions.map(t=>{const s=(t.options||[]).map(o=>`<button type="button" class="chip ${n.askAnswers[t.id]===o?"selected":""}" data-ask-id="${c(t.id)}" data-ask-opt="${c(o)}">${c(o)}</button>`).join("");return`<div class="ask-q">
        <label>${c(t.prompt)}</label>
        ${s?`<div class="ask-options">${s}</div>`:""}
        <input data-ask-input="${c(t.id)}" value="${c(n.askAnswers[t.id]||"")}" placeholder="你的回答" />
      </div>`}).join("")}
    <button type="button" class="primary-btn" id="ask-submit">提交回答</button></div>`:""}function Xt(){if(!n.showArtifacts)return"";const e=n.artifacts.map(t=>{const o=He(t.workspaceRelativePath)?`<button type="button" class="link-btn" data-art-preview="1" data-art-session="${c(t.sessionId)}" data-art-path="${c(t.workspaceRelativePath)}">预览</button>`:"",r=n.activeSessionId?`<button type="button" class="link-btn" data-art-insert="1" data-art-session="${c(t.sessionId)}" data-art-path="${c(t.workspaceRelativePath)}">插入当前会话</button>`:"";return`<li><strong>${c(t.fileName)}</strong>
        <span class="muted"> · ${c(t.sessionId.slice(0,8))} · ${Math.round(t.sizeBytes/1024)}KB</span>
        <div class="artifact-actions">${o} ${r}</div></li>`}).join("");return`<div class="overlay" id="artifacts-overlay">
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
  </div>`}function Vt(){if(!n.preview)return"";const e=pt(n.preview.sessionId,n.preview.path);return`<div class="overlay preview-overlay" id="preview-overlay">
    <div class="preview-panel">
      <div class="preview-toolbar">
        <span>${c(n.preview.path)}</span>
        <button type="button" class="icon-btn" id="preview-close">✕</button>
      </div>
      <iframe class="preview-frame" src="${e}" title="preview"></iframe>
    </div>
  </div>`}function Yt(){if(!n.showMcp)return"";const e=n.mcpServers.map((t,s)=>{const o=JSON.stringify(t.headers||{});return`<div class="mcp-card" data-mcp-index="${s}">
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
  </div>`}function Zt(){return n.showTools?`<div class="overlay" id="tools-overlay">
    <div class="overlay-panel usage-panel">
      <h2>Agent 工具</h2>
      <ul class="tools-list">${n.toolsList.map(t=>`<li><strong>${c(t.name)}</strong><span class="muted">${c(t.description)}</span></li>`).join("")||"<li class='muted'>无</li>"}</ul>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="tools-close">关闭</button>
      </div>
    </div>
  </div>`:""}function en(){return n.showAbout?`<div class="overlay" id="about-overlay">
    <div class="overlay-panel usage-panel">
      <h2>关于 Agent1</h2>
      <p>桌面 Web UI（<code>./agent1 ui</code>）— 对齐 Android 生产力聊天壳。</p>
      <p class="muted">本机 HTTP · 仅 127.0.0.1</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="about-close">关闭</button>
      </div>
    </div>
  </div>`:""}function tn(){return n.moreMenuOpen?`<div class="more-menu" role="menu" id="more-menu">
    <button type="button" class="more-menu-item" data-more-action="settings">模型设置</button>
    <button type="button" class="more-menu-item" data-more-action="mcp">MCP 与联网搜索</button>
    <button type="button" class="more-menu-item" data-more-action="capabilities">能力检索</button>
    <button type="button" class="more-menu-item" data-more-action="system-prompt">系统提示词</button>
    <button type="button" class="more-menu-item" data-more-action="brief">会话简报</button>
    <button type="button" class="more-menu-item" data-more-action="diagnostics">诊断包</button>
    <button type="button" class="more-menu-item" data-more-action="failed">失败工具调用</button>
    <button type="button" class="more-menu-item" data-more-action="about">关于</button>
  </div>`:""}function nn(){if(!n.showFailedLogs)return"";const e=n.failedLogsScopeSession?"当前会话":"全部会话",t=n.failedLogs.map(s=>`<li class="failed-log-item">
          <div><strong>${c(s.toolName||"tool")}</strong>
            <span class="muted"> · ${c(s.toolCallId)}</span></div>
          <div class="muted small">run ${c(s.runId)} · session ${c(s.sessionId)}</div>
          <div>${c(s.errorMessage||"—")}</div>
        </li>`).join("");return`<div class="overlay" id="failed-logs-overlay">
    <div class="overlay-panel usage-panel wide-panel">
      <h2>失败工具调用</h2>
      <p class="muted">范围：${c(e)} · 对齐 <code>./agent1 logs failed</code></p>
      <ul class="failed-log-list">${t||"<li class='muted'>（无失败记录）</li>"}</ul>
      <div class="overlay-actions">
        <label class="checkbox-row"><input type="checkbox" id="failed-scope-session" ${n.failedLogsScopeSession?"checked":""}/> 仅当前会话</label>
        <button type="button" id="failed-refresh">刷新</button>
        <button type="button" class="primary-btn" id="failed-close">关闭</button>
      </div>
    </div>
  </div>`}function sn(){return!n.usagePanelOpen||!n.usage?"":`<div class="overlay" id="usage-overlay">
    <div class="overlay-panel usage-panel">
      <h2>累计用量</h2>
      <div class="usage-detail">${jt(n.usage)}</div>
      <p class="muted">来自 agentRoot/logs/events.jsonl</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="usage-close">关闭</button>
      </div>
    </div>
  </div>`}function an(){if(!n.showSettings||!n.settingsDraft)return"";const e=n.settingsDraft,t=n.config,s=J.map(p=>`<button type="button" class="provider-chip ${p.id===e.providerId?"active":""}" data-provider="${p.id}">${c(p.label)}</button>`).join(""),o=n.modelOptions.map(p=>{const g=p.modelId===e.model?"selected":"";return`<option value="${c(p.modelId)}" ${g}>${c(p.title)} · ${c(p.subtitle)}</option>`}).join(""),r=e.model&&!n.modelOptions.some(p=>p.modelId===e.model)?`<option value="${c(e.model)}" selected>${c(e.model)}（当前）</option>`:"",d=n.modelsLoading?"正在加载模型列表…":n.modelsStatus||(n.modelOptions.length?`共 ${n.modelOptions.length} 个模型`:"");return`<div class="overlay settings-overlay" id="settings-overlay">
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
            <select id="cfg-model" class="settings-control settings-select">${r}${o}</select>
            <p class="settings-hint">${c(d)}</p>
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
  </div>`}function Ze(){var R;const e=n.sessions.map(h=>{const A=h.sessionId===n.activeSessionId?"active":"",x=h.title||h.sessionId.slice(0,8);return`<div class="session-item ${A}">
        <button type="button" class="session-title" data-use="${h.sessionId}">${c(x)}</button>
        <button type="button" class="session-delete" data-del="${h.sessionId}" title="删除">✕</button>
      </div>`}).join(""),t=It(n.lines,n.isRunning,n.streamingText,n.streamingReasoning,!!n.pendingAsk),s=n.lines.map(h=>Ht(h,n.activeSessionId)).join(""),o=!n.isRunning&&!n.pendingAsk&&n.lines.length>0&&!Q(n),r=t?'<button type="button" class="new-topic-btn" id="btn-new-topic">开始新话题</button>':"",d=o?'<button type="button" class="new-topic-btn secondary" id="btn-summarize-new">总结并新开话题</button>':"",p=n.lastRunTokenSummary&&!n.isRunning?`<div class="run-token-summary ${n.lastRunTokenSummary.failed?"failed":""}">${c(Ot(n.lastRunTokenSummary))}</div>`:"",g=n.lines.length===0&&!n.streamingText&&!n.isRunning?'<div class="landing"><h2>Agent1</h2><p>生产力助手 · 与 Android 端同款纸本风格。左侧管理会话，下方输入开始对话。</p></div>':s+p+r+d+Gt()+Qt(),y=Ut(n.usage),b=n.isRunning||!!n.pendingAsk,L=n.pendingAsk?"请在上方卡片回答":n.isRunning?"生成中…":"输入消息，Enter 发送，Shift+Enter 换行";Ve.innerHTML=`
  <div class="app">
    <aside class="drawer ${n.drawerOpen?"":"collapsed"}">
      <div class="drawer-header">
        <div class="brand">Agent1</div>
        <div class="drawer-actions">
          <button type="button" class="icon-btn" id="btn-new" title="新会话">＋</button>
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
          <div class="more-menu-anchor">
            <button type="button" class="icon-btn" id="btn-more" title="更多" aria-haspopup="menu" aria-expanded="${n.moreMenuOpen}">⋮</button>
            ${tn()}
          </div>
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
          <textarea id="composer" ${b?"disabled":""} placeholder="${L}">${c(n.composer)}</textarea>
          ${n.isRunning?'<button type="button" class="danger-btn" id="btn-stop">停止</button>':`<button type="button" class="primary-btn" id="btn-send" ${b?"disabled":""}>发送</button>`}
        </div>
        <div class="model-chip">模型 ${c(((R=n.config)==null?void 0:R.model)||"—")} ·
          <button type="button" id="btn-model">更改</button></div>
      </div>
    </section>
    ${an()}
    ${Yt()}
    ${Zt()}
    ${sn()}
    ${nn()}
    ${en()}
    ${Xt()}
    ${Vt()}
  </div>`,on();const $=document.getElementById("chat-scroll");$&&($.scrollTop=$.scrollHeight)}function on(){var r,d,p,g,y,b,L,$,R,h,A,x,ee,te,ne,se,ae,oe,ie,re,ce,le,de,ue,pe,me,fe,ge,ve,he,be,ye,we,ke,Ee,Se,$e,Ie,Te,Re,Le,Ae,xe,Pe;(r=document.getElementById("btn-new"))==null||r.addEventListener("click",()=>void De()),(d=document.getElementById("btn-new-topic"))==null||d.addEventListener("click",()=>void De()),(p=document.getElementById("btn-summarize-new"))==null||p.addEventListener("click",()=>void Ft()),(g=document.getElementById("btn-drawer"))==null||g.addEventListener("click",()=>{const a=!n.drawerOpen;i({drawerOpen:a}),a&&T()}),(y=document.getElementById("usage-footer"))==null||y.addEventListener("click",()=>{T().then(()=>i({usagePanelOpen:!0}))}),(b=document.getElementById("usage-close"))==null||b.addEventListener("click",()=>i({usagePanelOpen:!1})),(L=document.getElementById("usage-overlay"))==null||L.addEventListener("click",a=>{a.target===a.currentTarget&&i({usagePanelOpen:!1})}),($=document.getElementById("failed-close"))==null||$.addEventListener("click",()=>i({showFailedLogs:!1})),(R=document.getElementById("failed-refresh"))==null||R.addEventListener("click",()=>void z()),(h=document.getElementById("failed-scope-session"))==null||h.addEventListener("change",a=>{const u=a.target.checked;i({failedLogsScopeSession:u}),z()}),(A=document.getElementById("failed-logs-overlay"))==null||A.addEventListener("click",a=>{a.target===a.currentTarget&&i({showFailedLogs:!1})}),(x=document.getElementById("btn-more"))==null||x.addEventListener("click",a=>{a.stopPropagation(),i({moreMenuOpen:!n.moreMenuOpen})}),(ee=document.getElementById("more-menu"))==null||ee.addEventListener("click",a=>a.stopPropagation()),document.querySelectorAll("[data-more-action]").forEach(a=>{a.addEventListener("click",()=>{const u=a.dataset.moreAction;switch(qe(),u){case"settings":q();break;case"mcp":Fe();break;case"capabilities":_t();break;case"system-prompt":w("系统提示词页暂未在 Web UI 提供");break;case"brief":Kt().catch(l=>i({error:l instanceof Error?l.message:String(l)}));break;case"diagnostics":it().then(()=>w("诊断包已下载")).catch(l=>i({error:l instanceof Error?l.message:String(l)}));break;case"failed":Dt();break;case"about":i({showAbout:!0});break}})}),n.moreMenuOpen&&window.setTimeout(()=>{const a=()=>{qe(),document.removeEventListener("click",a)};document.addEventListener("click",a)},0),(te=document.getElementById("about-close"))==null||te.addEventListener("click",()=>i({showAbout:!1})),(ne=document.getElementById("about-overlay"))==null||ne.addEventListener("click",a=>{a.target===a.currentTarget&&i({showAbout:!1})}),(se=document.getElementById("btn-setup"))==null||se.addEventListener("click",()=>void q()),(ae=document.getElementById("btn-model"))==null||ae.addEventListener("click",()=>void q()),(oe=document.getElementById("btn-artifacts"))==null||oe.addEventListener("click",async()=>{const a=await We();i({artifacts:a,showArtifacts:!0,capQuery:"",capResult:""})}),(ie=document.getElementById("artifacts-close"))==null||ie.addEventListener("click",()=>i({showArtifacts:!1})),(re=document.getElementById("artifacts-overlay"))==null||re.addEventListener("click",a=>{a.target===a.currentTarget&&i({showArtifacts:!1})}),(ce=document.getElementById("cap-search-btn"))==null||ce.addEventListener("click",async()=>{var l;const a=document.getElementById("cap-query"),u=((l=a==null?void 0:a.value)==null?void 0:l.trim())||"";n={...n,capQuery:u};try{const f=await mt(u);i({capResult:f})}catch(f){i({capResult:f instanceof Error?f.message:String(f)})}}),document.querySelectorAll("[data-art-preview]").forEach(a=>{a.addEventListener("click",()=>{const u=a.dataset.artSession,l=a.dataset.artPath;u&&l&&i({preview:{sessionId:u,path:l},showArtifacts:!1})})}),document.querySelectorAll("[data-art-insert]").forEach(a=>{a.addEventListener("click",async()=>{const u=a.dataset.artSession,l=a.dataset.artPath,f=n.activeSessionId;if(!(!f||!u||!l))try{await ct(f,u,l),await j(f),w("已复制到 imports/")}catch(v){i({error:v instanceof Error?v.message:String(v)})}})}),(le=document.getElementById("preview-close"))==null||le.addEventListener("click",()=>i({preview:null})),(de=document.getElementById("preview-overlay"))==null||de.addEventListener("click",a=>{a.target===a.currentTarget&&i({preview:null})});const e=document.getElementById("file-input");(ue=document.getElementById("btn-attach"))==null||ue.addEventListener("click",()=>e==null?void 0:e.click()),e==null||e.addEventListener("change",async()=>{var u;const a=n.activeSessionId;if(!(!a||!((u=e.files)!=null&&u.length)))try{for(const l of Array.from(e.files))await dt(a,l);await j(a),e.value=""}catch(l){i({error:l instanceof Error?l.message:String(l)})}}),document.querySelectorAll("[data-rm-attach]").forEach(a=>{a.addEventListener("click",async()=>{const u=n.activeSessionId,l=a.dataset.rmAttach;!u||!l||(await ut(u,l),await j(u))})}),document.querySelectorAll("[data-use]").forEach(a=>{a.addEventListener("click",async()=>{const u=a.dataset.use;if(n.isRunning){i({error:"请先停止当前 Run 再切换会话"});return}await _(u),await I(u),await E()})}),document.querySelectorAll("[data-del]").forEach(a=>{a.addEventListener("click",async u=>{u.stopPropagation();const l=a.dataset.del;if(confirm("删除该会话？")&&(await gt(l),await E(),n.activeSessionId===l))if(n.sessions[0])await _(n.sessions[0].sessionId),await I(n.sessions[0].sessionId);else{const f=await G();await E(),await I(f.sessionId)}})});const t=document.getElementById("composer");t==null||t.addEventListener("input",()=>{n={...n,composer:t.value}}),t==null||t.addEventListener("keydown",a=>{a.key==="Enter"&&!a.shiftKey&&(a.preventDefault(),H())}),(pe=document.getElementById("btn-send"))==null||pe.addEventListener("click",()=>void H()),(me=document.getElementById("btn-stop"))==null||me.addEventListener("click",async()=>{await yt()}),document.querySelectorAll("[data-ask-opt]").forEach(a=>{a.addEventListener("click",()=>{const u=a.dataset.askId,l=a.dataset.askOpt;i({askAnswers:{...n.askAnswers,[u]:l}})})}),document.querySelectorAll("[data-ask-input]").forEach(a=>{a.addEventListener("input",()=>{const u=a.dataset.askInput;n={...n,askAnswers:{...n.askAnswers,[u]:a.value}}})}),(fe=document.getElementById("ask-submit"))==null||fe.addEventListener("click",()=>void zt());const s=()=>i({showSettings:!1,settingsDraft:null,modelsStatus:null,modelsLoading:!1});(ge=document.getElementById("cfg-close"))==null||ge.addEventListener("click",s),(ve=document.getElementById("settings-overlay"))==null||ve.addEventListener("click",a=>{a.target===a.currentTarget&&s()}),document.querySelectorAll("[data-provider]").forEach(a=>{a.addEventListener("click",()=>{var l;const u=a.dataset.provider;u&&u!==((l=n.settingsDraft)==null?void 0:l.providerId)&&qt(u)})});const o=a=>{n.settingsDraft&&i({settingsDraft:{...n.settingsDraft,...a}})};(he=document.getElementById("cfg-model"))==null||he.addEventListener("change",a=>{o({model:a.target.value})}),(be=document.getElementById("cfg-ctx-turns"))==null||be.addEventListener("change",a=>{o({maxContextTurns:parseInt(a.target.value,10)||0})}),(ye=document.getElementById("cfg-ctx-msgs"))==null||ye.addEventListener("change",a=>{o({maxContextMessages:parseInt(a.target.value,10)||0})}),(we=document.getElementById("cfg-run-turns"))==null||we.addEventListener("change",a=>{o({maxTurnsPerRun:parseInt(a.target.value,10)||0})}),(ke=document.getElementById("cfg-tool-calls"))==null||ke.addEventListener("change",a=>{o({maxToolCallsPerRun:parseInt(a.target.value,10)||0})}),(Ee=document.getElementById("cfg-open-mcp"))==null||Ee.addEventListener("click",()=>void Fe()),(Se=document.getElementById("mcp-close"))==null||Se.addEventListener("click",()=>i({showMcp:!1})),($e=document.getElementById("mcp-overlay"))==null||$e.addEventListener("click",a=>{a.target===a.currentTarget&&i({showMcp:!1})}),(Ie=document.getElementById("mcp-add"))==null||Ie.addEventListener("click",()=>{i({mcpServers:[...n.mcpServers,{name:"server",url:"https://",enabled:!0,headers:{}}]})}),document.querySelectorAll(".mcp-remove").forEach(a=>{a.addEventListener("click",()=>{const u=Number(a.dataset.mcpIndex);i({mcpServers:n.mcpServers.filter((l,f)=>f!==u)})})}),(Te=document.getElementById("mcp-save"))==null||Te.addEventListener("click",async()=>{const a=document.querySelectorAll(".mcp-card"),u=[];a.forEach((l,f)=>{var M,k,Ce,Be;const v=n.mcpServers[f],P=((M=l.querySelector(".mcp-name"))==null?void 0:M.value.trim())||"",C=((k=l.querySelector(".mcp-url"))==null?void 0:k.value.trim())||"",B=((Ce=l.querySelector(".mcp-enabled"))==null?void 0:Ce.checked)??!0;let O={};try{O=JSON.parse(((Be=l.querySelector(".mcp-headers"))==null?void 0:Be.value)||"{}")}catch{}u.push({name:P,url:C,enabled:B,headers:O,toolCount:v==null?void 0:v.toolCount,lastListedAt:v==null?void 0:v.lastListedAt})});try{const l=await at(u),{servers:f}=await Ke(),v=l.warnings.length?` · ${l.warnings.join("; ")}`:"";i({mcpServers:f,mcpStatus:`已索引 ${l.toolCount} 个 MCP 工具${v}`}),w("MCP 已保存")}catch(l){i({error:l instanceof Error?l.message:String(l)})}}),document.querySelectorAll(".file-link").forEach(a=>{a.addEventListener("click",()=>{const u=a.dataset.filePath,l=n.activeSessionId;!u||!l||(He(u)?i({preview:{sessionId:l,path:u}}):w(u))})}),(Re=document.getElementById("cfg-show-tools"))==null||Re.addEventListener("click",async()=>{try{const{tools:a}=await ot();i({showTools:!0,toolsList:a})}catch(a){i({error:a instanceof Error?a.message:String(a)})}}),(Le=document.getElementById("tools-close"))==null||Le.addEventListener("click",()=>i({showTools:!1})),(Ae=document.getElementById("tools-overlay"))==null||Ae.addEventListener("click",a=>{a.target===a.currentTarget&&i({showTools:!1})}),(xe=document.getElementById("cfg-refresh-models"))==null||xe.addEventListener("click",()=>{const a=n.settingsDraft;a&&Z(a,!0)}),(Pe=document.getElementById("cfg-save"))==null||Pe.addEventListener("click",async()=>{const a=n.settingsDraft;if(!a)return;const u=document.getElementById("cfg-key").value,l=document.getElementById("cfg-base").value,f=document.getElementById("cfg-model").value,v=parseInt(document.getElementById("cfg-ctx-turns").value,10),P=parseInt(document.getElementById("cfg-ctx-msgs").value,10),C=parseInt(document.getElementById("cfg-run-turns").value,10),B=parseInt(document.getElementById("cfg-tool-calls").value,10),O=document.getElementById("cfg-web-key").value,M=document.getElementById("cfg-web-base").value;try{const k=await tt({apiKey:u||void 0,baseUrl:l||void 0,model:f||void 0,providerId:a.providerId,maxContextTurns:Number.isFinite(v)?v:0,maxContextMessages:Number.isFinite(P)?P:0,maxTurnsPerRun:Number.isFinite(C)?C:0,maxToolCallsPerRun:Number.isFinite(B)?B:0,webSearchApiKey:O||void 0,webSearchBaseUrl:M||void 0});i({config:k,showSettings:!1,settingsDraft:null,modelsStatus:null,error:null}),w("设置已保存")}catch(k){i({error:k instanceof Error?k.message:String(k)})}})}Ze();Wt().catch(e=>{i({error:e instanceof Error?e.message:String(e)})});window.addEventListener("beforeunload",()=>N==null?void 0:N());
