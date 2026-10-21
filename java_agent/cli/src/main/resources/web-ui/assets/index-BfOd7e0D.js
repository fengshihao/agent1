(function(){const t=document.createElement("link").relList;if(t&&t.supports&&t.supports("modulepreload"))return;for(const r of document.querySelectorAll('link[rel="modulepreload"]'))o(r);new MutationObserver(r=>{for(const u of r)if(u.type==="childList")for(const p of u.addedNodes)p.tagName==="LINK"&&p.rel==="modulepreload"&&o(p)}).observe(document,{childList:!0,subtree:!0});function s(r){const u={};return r.integrity&&(u.integrity=r.integrity),r.referrerPolicy&&(u.referrerPolicy=r.referrerPolicy),r.crossOrigin==="use-credentials"?u.credentials="include":r.crossOrigin==="anonymous"?u.credentials="omit":u.credentials="same-origin",u}function o(r){if(r.ep)return;r.ep=!0;const u=s(r);fetch(r.href,u)}})();async function m(e){const t=await e.json();if(!e.ok)throw new Error(t.error||e.statusText);return t}async function it(){return m(await fetch("/api/config"))}async function rt(e){return m(await fetch("/api/config",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(e)}))}async function ct(){return m(await fetch("/api/usage"))}async function lt(e,t=!1){const s=new URLSearchParams({provider:e});return t&&s.set("refresh","1"),m(await fetch(`/api/models?${s}`))}async function Ge(){return m(await fetch("/api/mcp/servers"))}async function dt(e){return m(await fetch("/api/mcp/servers",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({servers:e})}))}async function ut(){return m(await fetch("/api/tools"))}async function je(){var u,p;const e=await fetch("/api/diagnostics/export");if(!e.ok)throw new Error("诊断包导出失败");const t=await e.blob(),s=((p=(u=e.headers.get("Content-Disposition"))==null?void 0:u.match(/filename="([^"]+)"/))==null?void 0:p[1])??`agent1-diag-${Date.now()}.zip`,o=URL.createObjectURL(t),r=document.createElement("a");r.href=o,r.download=s,r.click(),URL.revokeObjectURL(o)}async function pt(e=!0){return m(await fetch("/api/sessions/rollover",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({summarize:e})}))}async function Qe(){return(await m(await fetch("/api/artifacts"))).artifacts}async function mt(e,t,s){return m(await fetch(`/api/sessions/${encodeURIComponent(e)}/artifacts/import`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({sourceSessionId:t,workspaceRelativePath:s})}))}async function ft(e){return(await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`))).files}async function gt(e,t){const s=new FormData;return s.append("file",t),m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`,{method:"POST",body:s}))}async function vt(e,t){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments/${encodeURIComponent(t)}`,{method:"DELETE"}))}function bt(e,t){const s=t.split("/").map(o=>encodeURIComponent(o)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${s}`}async function ht(e){return(await m(await fetch(`/api/capabilities?q=${encodeURIComponent(e)}`))).text}async function yt(){return m(await fetch("/api/sessions"))}async function G(){return m(await fetch("/api/sessions",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"}))}async function _(e){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/use`,{method:"POST",body:"{}"}))}async function wt(e){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}`,{method:"DELETE"}))}async function Xe(e){return m(await fetch(`/api/sessions/${encodeURIComponent(e)}/brief`))}async function Et(e){const t=e?`?sessionId=${encodeURIComponent(e)}`:"";return(await m(await fetch(`/api/logs/failed${t}`))).items}async function Ve(e){return(await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/transcript`))).messages}async function kt(e,t){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/messages`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({text:t})}))}async function St(){await m(await fetch("/api/run/abort",{method:"POST",body:"{}"}))}function $t(e){const t=new EventSource("/api/events");return t.onmessage=s=>{try{e(JSON.parse(s.data))}catch{}},()=>t.close()}const It="ask_user",Tt=["等待用户","waiting","ask_user"];function Lt(e){try{const s=(JSON.parse(e.argumentsJson||"{}").questions||[]).map((o,r)=>({id:o.id||`q${r}`,prompt:o.prompt||o.question||"",options:o.options,allowFreeText:o.allowFreeText??o.allow_free_text??!0})).filter(o=>o.prompt);return s.length===0?null:{questions:s}}catch{return null}}function Ye(e){if(!e.length)return null;for(let t=e.length-1;t>=0;t--){const s=e[t];if(s.role==="user")return null;if(s.role==="assistant"){for(const o of s.toolCalls||[])if(o.name===It)return Lt(o)}if(s.role==="toolResult"){const o=s.content||"";if(!Tt.some(u=>o.toLowerCase().includes(u.toLowerCase())))return null}}return null}function Rt(e,t){return e.questions.map(o=>{const r=(t[o.id]||"").trim();return`${o.prompt}
→ ${r||"(未答)"}`}).join(`

`)}function At(e,t,s,o,r){if(t||r||s.trim()||o.trim())return!1;const u=xt(e);return(u==null?void 0:u.role)==="assistant"}function xt(e){return e.filter(t=>t.role==="user"||t.role==="assistant").at(-1)}function Pt(){return{sessions:[],activeSessionId:null,config:null,lines:[],streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,pendingAsk:null,askAnswers:{},error:null,showSettings:!1,drawerOpen:!0,composer:"",usage:null,usagePanelOpen:!1,toast:null,attachments:[],artifacts:[],showArtifacts:!1,preview:null,capQuery:"",capResult:"",modelOptions:[],settingsDraft:null,modelsLoading:!1,modelsStatus:null,showTools:!1,toolsList:[],lastRunTokenSummary:null,showMcp:!1,mcpServers:[],mcpStatus:null,showFailedLogs:!1,failedLogs:[],failedLogsScopeSession:!0,moreMenuOpen:!1,showAbout:!1}}function Ze(e){const t=e.toLowerCase();return t.endsWith(".html")||t.endsWith(".htm")}function Q(e){return e.isRunning||e.streamingText.trim()||e.streamingReasoning.trim()||e.tools.length>0||e.pendingAsk?!1:!e.lines.some(t=>t.role==="user"||t.role==="assistant")}function et(e){const t=[];return e.forEach((s,o)=>{var r;if(s.role==="user"){t.push({id:`u-${o}`,role:"user",content:s.content});return}if(s.role==="assistant"){(r=s.content)!=null&&r.trim()&&t.push({id:`a-${o}`,role:"assistant",content:s.content});for(const u of s.toolCalls||[])t.push({id:`t-${u.id}`,role:"tool",content:u.argsPreview||u.argumentsJson||"",toolName:u.name});return}if(s.role==="toolResult"){t.push({id:`tr-${o}`,role:"tool",content:s.content,toolName:"result",error:!!s.error});return}s.role}),t}const Ct=new Set(["png","jpg","jpeg","webp","gif"]);function K(e){var s;const t=((s=e.split(".").pop())==null?void 0:s.toLowerCase())??"";return Ct.has(t)}function tt(e){const t=/!\[[^\]]*]\(([^)]+)\)/g,s=[];let o;for(;(o=t.exec(e))!==null;){const r=o[1].trim();r&&!r.startsWith("http://")&&!r.startsWith("https://")&&s.push(X(r))}return s}function X(e){return e.replace(/^\.\//,"").replace(/^workspace\//,"")}function Bt(e){const t=[],s=/```[\w-]*\n?([\s\S]*?)```/g;let o=0,r;for(;(r=s.exec(e))!==null;)r.index>o&&t.push({kind:"text",text:e.slice(o,r.index)}),t.push({kind:"code",text:r[1]??""}),o=r.index+r[0].length;return o<e.length&&t.push({kind:"text",text:e.slice(o)}),t.length?t:[{kind:"text",text:e}]}function De(e,t){return e?Bt(e).map(s=>s.kind==="code"?`<pre class="bubble-code"><code>${S(s.text.trimEnd())}</code></pre>`:Ot(s.text,t)).join(""):""}function Ot(e,t){const s=/!?\[([^\]]*)]\(([^)]+)\)/g;let o="",r=0,u;for(;(u=s.exec(e))!==null;){o+=Fe(e.slice(r,u.index));const p=u[1],g=u[2].trim(),w=u[0].startsWith("!");if(t&&!g.startsWith("http://")&&!g.startsWith("https://")){const h=X(g);w&&K(h)?o+=`<figure class="bubble-image"><img src="${nt(t,h)}" alt="${W(h)}" loading="lazy"/></figure>`:w?o+=S(u[0]):o+=`<button type="button" class="file-link" data-file-path="${W(h)}">${S(p||h)}</button>`}else o+=S(u[0]);r=u.index+u[0].length}return o+=Fe(e.slice(r)),o}function Fe(e){const t=e.split(/`([^`\n]+)`/);let s="";for(let o=0;o<t.length;o++)o%2===0?s+=S(t[o]):s+=`<code class="bubble-inline-code">${S(t[o])}</code>`;return s}function nt(e,t){const s=t.split("/").map(o=>encodeURIComponent(o)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${s}`}function S(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}function W(e){return S(e).replaceAll("'","&#39;")}function Mt(e){const t=tt(e).filter(K),s=new Set,o=[];for(const p of t)s.has(p)||(s.add(p),o.push(p));const r=/(?:imports|out|tmp|staging)[/][\w./-]+\.(?:png|jpe?g|webp|gif)/gi;let u;for(;(u=r.exec(e))!==null;){const p=X(u[0]);K(p)&&!s.has(p)&&(s.add(p),o.push(p))}return o}function qe(e,t){const s=new Set(tt(e));return Mt(e).filter(o=>!s.has(o)).map(o=>`<figure class="bubble-image"><img src="${nt(t,o)}" alt="${W(o)}" loading="lazy"/></figure>`).join("")}let F=0,q=0,V=0;function Ut(){F=0,q=0,V=0}function Nt(e){F+=Number(e.inputTokens)||0,q+=Number(e.outputTokens)||0;const t=e.cachedTokens;t!=null&&(V+=Number(t)||0)}function _e(e){const t=F>0||q>0;return!e&&!t?null:{inputTokens:F,outputTokens:q,cachedTokens:V,failed:e}}function jt(e){const t=[`输入 ${Ke(e.inputTokens)}`,`输出 ${Ke(e.outputTokens)}`];if(e.inputTokens>0){const s=Math.min(100,Math.round(e.cachedTokens*100/e.inputTokens));t.push(`缓存命中 ${s}%`)}return e.failed&&t.push("未完成"),t.join(" · ")}function Ke(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,s=Math.trunc(t);return t===s?`${s}k`:`${t.toFixed(1)}k`}const J=[{id:"dashscope",label:"通义",baseUrl:"https://dashscope.aliyuncs.com/compatible-mode/v1",defaultModel:"qwen3.7-flash"},{id:"deepseek",label:"DeepSeek",baseUrl:"https://api.deepseek.com",defaultModel:"deepseek-flash"},{id:"zhipu_coding",label:"智谱 Coding",baseUrl:"https://open.bigmodel.cn/api/coding/paas/v4",defaultModel:"glm-5.3"}];function Dt(e){const t=e.toLowerCase();return t.includes("deepseek.com")?"deepseek":t.includes("bigmodel.cn")?"zhipu_coding":"dashscope"}function Y(e){return J.find(t=>t.id===e)??J[0]}function U(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,s=Math.trunc(t);return t===s?`${s}k`:`${t.toFixed(1)}k`}function Ft(e){if(!e||!e.hasUsage)return{headline:"累计用量",detail:"暂无 usage 记录"};const t=`约 ${U(e.totalTokens)} tokens`,s=e.cacheHitPercent,o=s!=null?`输入 ${U(e.inputTokens)} · 缓存 ${s}%`:`输入 ${U(e.inputTokens)} · 输出 ${U(e.outputTokens)}`;return{headline:t,detail:o}}function qt(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;")}function _t(e){const t=[`输入 ${e.inputTokens.toLocaleString()}`,`输出 ${e.outputTokens.toLocaleString()}`,`缓存 ${e.cachedTokens.toLocaleString()}`,`usage 事件 ${e.usageEvents}`];let s="";return e.byModel.length&&(s="<ul>"+e.byModel.map(o=>`<li><strong>${qt(o.modelId||"（未知模型）")}</strong>：入 ${o.inputTokens.toLocaleString()} / 出 ${o.outputTokens.toLocaleString()}</li>`).join("")+"</ul>"),`<p>${t.join(" · ")}</p>${s}`}const st=document.getElementById("app");if(!st)throw new Error("#app missing");let n=Pt(),j=null;function i(e){n={...n,...e},ot()}function c(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}async function k(){const e=await yt();i({sessions:e.sessions.slice().sort((t,s)=>s.updatedAt.localeCompare(t.updatedAt)),activeSessionId:e.activeSessionId??n.activeSessionId,isRunning:e.runInProgress})}async function T(){try{const e=await ct();i({usage:e})}catch{}}async function z(){const e=n.failedLogsScopeSession&&n.activeSessionId?n.activeSessionId:null,t=await Et(e);i({failedLogs:t})}async function Kt(){i({showFailedLogs:!0});try{await z()}catch(e){i({error:e instanceof Error?e.message:String(e)})}}function y(e){i({toast:e}),window.setTimeout(()=>{n.toast===e&&i({toast:null})},2200)}async function We(){if(n.isRunning){i({error:"请先停止当前 Run"});return}if(n.activeSessionId&&Q(n)){y("已是新会话");return}const e=await G();await k(),await I(e.sessionId),await T(),i({drawerOpen:!1})}async function Wt(){if(n.isRunning){i({error:"请先停止当前 Run"});return}if(n.activeSessionId&&Q(n)){y("已是新会话");return}const e=await pt(!0);await k(),await I(e.session.sessionId),await T(),i({drawerOpen:!1}),y(e.summaryPath?"已总结并新建会话":"已新建会话")}function at(){const e=n.config,t=(e==null?void 0:e.baseUrl)||"",s=(e==null?void 0:e.providerId)||Dt(t);return{providerId:s,baseUrl:t,model:(e==null?void 0:e.model)||Y(s).defaultModel,maxContextTurns:(e==null?void 0:e.maxContextTurns)??6,maxContextMessages:(e==null?void 0:e.maxContextMessages)??0,maxTurnsPerRun:(e==null?void 0:e.maxTurnsPerRun)??24,maxToolCallsPerRun:(e==null?void 0:e.maxToolCallsPerRun)??48,webSearchBaseUrl:(e==null?void 0:e.webSearchBaseUrl)||"https://api.tavily.com"}}async function Z(e,t){var s,o;i({modelsLoading:!0});try{const{models:r,source:u}=await lt(e.providerId,t);let p=e.model;if(!r.some(g=>g.modelId===p)){const g=Y(e.providerId);p=((s=r.find(w=>w.modelId===g.defaultModel))==null?void 0:s.modelId)??((o=r[0])==null?void 0:o.modelId)??p}i({modelOptions:r,modelsLoading:!1,settingsDraft:{...e,model:p},modelsStatus:t?`已从网络更新 · ${r.length} 个模型`:`共 ${r.length} 个模型`})}catch(r){i({modelsLoading:!1,error:r instanceof Error?r.message:"模型列表加载失败"})}}function Jt(e){const t=Y(e),s={...n.settingsDraft??at(),providerId:e,baseUrl:t.baseUrl,model:t.defaultModel};i({settingsDraft:s,error:null}),Z(s,!1)}async function N(){const e=at();i({showSettings:!0,settingsDraft:e,modelOptions:[],error:null,moreMenuOpen:!1}),await Z(e,!1)}async function Je(){try{const{servers:e}=await Ge();i({showMcp:!0,mcpServers:e,mcpStatus:null,showSettings:!1,moreMenuOpen:!1})}catch(e){i({error:e instanceof Error?e.message:String(e)})}}async function zt(){try{const e=await Qe();i({artifacts:e,showArtifacts:!0,capQuery:"",capResult:"",moreMenuOpen:!1})}catch(e){i({error:e instanceof Error?e.message:String(e)})}}async function Ht(){const e=n.activeSessionId;if(!e)return;const{text:t}=await Xe(e);await navigator.clipboard.writeText(t),y("简报已复制到剪贴板")}function ze(){i({moreMenuOpen:!1})}async function D(e){try{const t=await ft(e);i({attachments:t})}catch{i({attachments:[]})}}async function I(e){const t=await Ve(e),s=Ye(t);i({activeSessionId:e,lines:et(t),pendingAsk:s,askAnswers:{},streamingText:"",streamingReasoning:"",tools:[],error:null}),await D(e)}async function Gt(){const[e]=await Promise.all([it(),k()]);i({config:e});let t=n.activeSessionId;t||(n.sessions.length===0?(t=(await G()).sessionId,await k()):(t=n.sessions[0].sessionId,await _(t))),await I(t),await T(),j=$t(Qt)}function Qt(e){if(!(e.sessionId&&n.activeSessionId&&e.sessionId!==n.activeSessionId))switch(e.type){case"AGENT_START":Ut(),i({isRunning:!0,streamingText:"",streamingReasoning:"",tools:[],error:null,lastRunTokenSummary:null});break;case"USAGE":Nt(e.payload);break;case"MESSAGE_UPDATE":i({streamingText:n.streamingText+String(e.payload.delta||""),isRunning:!0});break;case"REASONING_UPDATE":i({streamingReasoning:n.streamingReasoning+String(e.payload.delta||""),isRunning:!0});break;case"MESSAGE_RESET":i({streamingText:"",streamingReasoning:""});break;case"TOOL_EXECUTION_START":{const t=e.payload.toolCall,s={id:t.id||`tool-${Date.now()}`,toolCallId:t.id||"",toolName:t.name||"tool",argsPreview:t.argsPreview||"",running:!0};i({tools:[...n.tools,s],isRunning:!0});break}case"TOOL_EXECUTION_END":{const t=String(e.payload.toolCallId||""),s=n.tools.map(r=>r.toolCallId===t?{...r,running:!1,error:!!(e.payload.error||e.payload.toolError),resultSummary:String(e.payload.resultText||"")}:r),o=!!e.payload.stopRunWaitingUser;i({tools:s,isRunning:!o&&n.isRunning});break}case"AGENT_ERROR":i({error:String(e.payload.message||"运行出错"),isRunning:!1,lastRunTokenSummary:_e(!0)}),He();break;case"AGENT_END":i({isRunning:!1,lastRunTokenSummary:_e(!1)}),He();break}}async function He(){if(!n.activeSessionId)return;const e=n.lastRunTokenSummary;try{const t=await Ve(n.activeSessionId);i({lines:et(t),pendingAsk:Ye(t),streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,lastRunTokenSummary:e}),await k(),await T()}catch(t){i({error:t instanceof Error?t.message:String(t)})}}async function H(){var o;const e=n.composer.trim();if(!e||!n.activeSessionId||n.isRunning||n.pendingAsk)return;if(!((o=n.config)!=null&&o.hasApiKey)){i({showSettings:!0,error:"请先配置 API Key"});return}const t={id:`local-${Date.now()}`,role:"user",content:e};i({lines:[...n.lines,t],composer:"",isRunning:!0,streamingText:"",tools:[],error:null,lastRunTokenSummary:null});let s=e;if(n.attachments.length>0){const r=n.attachments.map(u=>`- ${u.workspaceRelativePath}`);s+=`

[用户附件]
${r.join(`
`)}`}try{await kt(n.activeSessionId,s)}catch(r){i({isRunning:!1,error:r instanceof Error?r.message:String(r)})}}async function Xt(){if(!n.pendingAsk||!n.activeSessionId)return;const e=Rt(n.pendingAsk,n.askAnswers);i({pendingAsk:null,composer:e}),await H()}function Vt(e,t){if(e.role==="tool"){const o=t?De(e.content,t)+qe(e.content,t):c(e.content);return`<details class="tool-card"><summary>⚙ ${c(e.toolName||"tool")}</summary>
      <div class="tool-row ${e.error?"err":""}">${o}</div></details>`}const s=t?De(e.content,t)+qe(e.content,t):c(e.content);return`<div class="bubble ${e.role}"><div class="bubble-text">${s}</div></div>`}function Yt(){if(!n.tools.length&&!n.streamingText&&!n.streamingReasoning)return"";const e=n.tools.map(s=>{const o=s.running?"进行中…":s.error?"失败":"完成";return`<div class="tool-row ${s.error?"err":""}"><strong>${c(s.toolName)}</strong> · ${o}
        ${s.argsPreview?`<div>${c(s.argsPreview)}</div>`:""}
        ${s.resultSummary?`<div>${c(s.resultSummary)}</div>`:""}
      </div>`}).join("");return`<details class="tool-card" open><summary>思考与执行</summary>${[n.streamingReasoning?`<div class="tool-row">思考：${c(n.streamingReasoning)}</div>`:"",e].join("")}</details>
    ${n.streamingText?`<div class="bubble assistant">${c(n.streamingText)}</div>`:""}`}function Zt(){return n.pendingAsk?`<div class="ask-panel"><h3>请回答后继续</h3>${n.pendingAsk.questions.map(t=>{const s=(t.options||[]).map(o=>`<button type="button" class="chip ${n.askAnswers[t.id]===o?"selected":""}" data-ask-id="${c(t.id)}" data-ask-opt="${c(o)}">${c(o)}</button>`).join("");return`<div class="ask-q">
        <label>${c(t.prompt)}</label>
        ${s?`<div class="ask-options">${s}</div>`:""}
        <input data-ask-input="${c(t.id)}" value="${c(n.askAnswers[t.id]||"")}" placeholder="你的回答" />
      </div>`}).join("")}
    <button type="button" class="primary-btn" id="ask-submit">提交回答</button></div>`:""}function en(){if(!n.showArtifacts)return"";const e=n.artifacts.map(t=>{const o=Ze(t.workspaceRelativePath)?`<button type="button" class="link-btn" data-art-preview="1" data-art-session="${c(t.sessionId)}" data-art-path="${c(t.workspaceRelativePath)}">预览</button>`:"",r=n.activeSessionId?`<button type="button" class="link-btn" data-art-insert="1" data-art-session="${c(t.sessionId)}" data-art-path="${c(t.workspaceRelativePath)}">插入当前会话</button>`:"";return`<li><strong>${c(t.fileName)}</strong>
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
  </div>`}function tn(){if(!n.preview)return"";const e=bt(n.preview.sessionId,n.preview.path);return`<div class="overlay preview-overlay" id="preview-overlay">
    <div class="preview-panel">
      <div class="preview-toolbar">
        <span>${c(n.preview.path)}</span>
        <button type="button" class="icon-btn" id="preview-close">✕</button>
      </div>
      <iframe class="preview-frame" src="${e}" title="preview"></iframe>
    </div>
  </div>`}function nn(){if(!n.showMcp)return"";const e=n.mcpServers.map((t,s)=>{const o=JSON.stringify(t.headers||{});return`<div class="mcp-card" data-mcp-index="${s}">
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
  </div>`}function sn(){return n.showTools?`<div class="overlay" id="tools-overlay">
    <div class="overlay-panel usage-panel">
      <h2>Agent 工具</h2>
      <ul class="tools-list">${n.toolsList.map(t=>`<li><strong>${c(t.name)}</strong><span class="muted">${c(t.description)}</span></li>`).join("")||"<li class='muted'>无</li>"}</ul>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="tools-close">关闭</button>
      </div>
    </div>
  </div>`:""}function an(){return n.showAbout?`<div class="overlay" id="about-overlay">
    <div class="overlay-panel usage-panel">
      <h2>关于 Agent1</h2>
      <p>桌面 Web UI（<code>./agent1 ui</code>）— 对齐 Android 生产力聊天壳。</p>
      <p class="muted">本机 HTTP · 仅 127.0.0.1</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="about-close">关闭</button>
      </div>
    </div>
  </div>`:""}function on(){return n.moreMenuOpen?`<div class="more-menu" role="menu" id="more-menu">
    <button type="button" class="more-menu-item" data-more-action="settings">模型设置</button>
    <button type="button" class="more-menu-item" data-more-action="mcp">MCP 与联网搜索</button>
    <button type="button" class="more-menu-item" data-more-action="capabilities">能力检索</button>
    <button type="button" class="more-menu-item" data-more-action="system-prompt">系统提示词</button>
    <button type="button" class="more-menu-item" data-more-action="brief">会话简报</button>
    <button type="button" class="more-menu-item" data-more-action="diagnostics">诊断包</button>
    <button type="button" class="more-menu-item" data-more-action="about">关于</button>
  </div>`:""}function rn(){if(!n.showFailedLogs)return"";const e=n.failedLogsScopeSession?"当前会话":"全部会话",t=n.failedLogs.map(s=>`<li class="failed-log-item">
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
  </div>`}function cn(){return!n.usagePanelOpen||!n.usage?"":`<div class="overlay" id="usage-overlay">
    <div class="overlay-panel usage-panel">
      <h2>累计用量</h2>
      <div class="usage-detail">${_t(n.usage)}</div>
      <p class="muted">来自 agentRoot/logs/events.jsonl</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="usage-close">关闭</button>
      </div>
    </div>
  </div>`}function ln(){if(!n.showSettings||!n.settingsDraft)return"";const e=n.settingsDraft,t=n.config,s=J.map(p=>`<button type="button" class="provider-chip ${p.id===e.providerId?"active":""}" data-provider="${p.id}">${c(p.label)}</button>`).join(""),o=n.modelOptions.map(p=>{const g=p.modelId===e.model?"selected":"";return`<option value="${c(p.modelId)}" ${g}>${c(p.title)} · ${c(p.subtitle)}</option>`}).join(""),r=e.model&&!n.modelOptions.some(p=>p.modelId===e.model)?`<option value="${c(e.model)}" selected>${c(e.model)}（当前）</option>`:"",u=n.modelsLoading?"正在加载模型列表…":n.modelsStatus||(n.modelOptions.length?`共 ${n.modelOptions.length} 个模型`:"");return`<div class="overlay settings-overlay" id="settings-overlay">
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
            <p class="settings-hint">${c(u)}</p>
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
  </div>`}function ot(){var L;const e=n.sessions.map(b=>{const A=b.sessionId===n.activeSessionId?"active":"",x=b.title||b.sessionId.slice(0,8);return`<div class="session-item ${A}">
        <button type="button" class="session-title" data-use="${b.sessionId}">${c(x)}</button>
        <button type="button" class="session-delete" data-del="${b.sessionId}" title="删除">✕</button>
      </div>`}).join(""),t=At(n.lines,n.isRunning,n.streamingText,n.streamingReasoning,!!n.pendingAsk),s=n.lines.map(b=>Vt(b,n.activeSessionId)).join(""),o=!n.isRunning&&!n.pendingAsk&&n.lines.length>0&&!Q(n),r=t?'<button type="button" class="new-topic-btn" id="btn-new-topic">开始新话题</button>':"",u=o?'<button type="button" class="new-topic-btn secondary" id="btn-summarize-new">总结并新开话题</button>':"",p=n.lastRunTokenSummary&&!n.isRunning?`<div class="run-token-summary ${n.lastRunTokenSummary.failed?"failed":""}">${c(jt(n.lastRunTokenSummary))}</div>`:"",g=n.lines.length===0&&!n.streamingText&&!n.isRunning?'<div class="landing"><h2>Agent1</h2><p>生产力助手 · 与 Android 端同款纸本风格。左侧管理会话，下方输入开始对话。</p></div>':s+p+r+u+Yt()+Zt(),w=Ft(n.usage),h=n.isRunning||!!n.pendingAsk,R=n.pendingAsk?"请在上方卡片回答":n.isRunning?"生成中…":"输入消息，Enter 发送，Shift+Enter 换行";st.innerHTML=`
  <div class="app">
    <aside class="drawer ${n.drawerOpen?"":"collapsed"}">
      <div class="drawer-header">
        <div class="brand">Agent1</div>
        <div class="drawer-actions">
          <button type="button" class="icon-btn" id="btn-new" title="新会话">＋</button>
          <button type="button" class="icon-btn" id="btn-brief" title="复制会话简报">📋</button>
          <button type="button" class="icon-btn" id="btn-diag" title="导出诊断包">🩺</button>
          <button type="button" class="icon-btn" id="btn-failed" title="失败工具调用">⚠</button>
          <button type="button" class="icon-btn" id="btn-settings" title="设置">⚙</button>
        </div>
      </div>
      <div class="session-list">${e}</div>
      <button type="button" class="usage-footer" id="usage-footer">
        <span class="usage-headline">${c(w.headline)}</span>
        <span class="usage-detail-line">${c(w.detail)}</span>
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
            ${on()}
          </div>
        </div>
      </div>
      ${n.toast?`<div class="toast-banner">${c(n.toast)}</div>`:""}
      ${n.error?`<div class="error-banner">${c(n.error)}</div>`:""}
      <div class="chat-scroll" id="chat-scroll">${g}</div>
      <div class="composer-wrap">
        ${n.config&&!n.config.hasApiKey?'<div class="inline-setup">尚未配置 API Key。<button type="button" id="btn-setup">打开设置</button></div>':""}
        ${n.attachments.length?`<div class="attachment-chips">${n.attachments.map(b=>`<span class="chip">${c(b.fileName)} <button type="button" data-rm-attach="${c(b.workspaceRelativePath)}">×</button></span>`).join("")}</div>`:""}
        <div class="composer">
          <input type="file" id="file-input" class="hidden-file" multiple />
          <button type="button" class="icon-btn" id="btn-attach" title="添加附件" ${h?"disabled":""}>📎</button>
          <textarea id="composer" ${h?"disabled":""} placeholder="${R}">${c(n.composer)}</textarea>
          ${n.isRunning?'<button type="button" class="danger-btn" id="btn-stop">停止</button>':`<button type="button" class="primary-btn" id="btn-send" ${h?"disabled":""}>发送</button>`}
        </div>
        <div class="model-chip">模型 ${c(((L=n.config)==null?void 0:L.model)||"—")} ·
          <button type="button" id="btn-model">更改</button></div>
      </div>
    </section>
    ${ln()}
    ${nn()}
    ${sn()}
    ${cn()}
    ${rn()}
    ${an()}
    ${en()}
    ${tn()}
  </div>`,dn();const $=document.getElementById("chat-scroll");$&&($.scrollTop=$.scrollHeight)}function dn(){var r,u,p,g,w,h,R,$,L,b,A,x,ee,te,ne,se,ae,oe,ie,re,ce,le,de,ue,pe,me,fe,ge,ve,be,he,ye,we,Ee,ke,Se,$e,Ie,Te,Le,Re,Ae,xe,Pe,Ce,Be,Oe,Me;(r=document.getElementById("btn-new"))==null||r.addEventListener("click",()=>void We()),(u=document.getElementById("btn-brief"))==null||u.addEventListener("click",async()=>{const a=n.activeSessionId;if(a)try{const{text:d}=await Xe(a);await navigator.clipboard.writeText(d),y("简报已复制到剪贴板")}catch(d){i({error:d instanceof Error?d.message:String(d)})}}),(p=document.getElementById("btn-diag"))==null||p.addEventListener("click",async()=>{try{await je(),y("诊断包已下载")}catch(a){i({error:a instanceof Error?a.message:String(a)})}}),(g=document.getElementById("btn-failed"))==null||g.addEventListener("click",()=>void Kt()),(w=document.getElementById("btn-new-topic"))==null||w.addEventListener("click",()=>void We()),(h=document.getElementById("btn-summarize-new"))==null||h.addEventListener("click",()=>void Wt()),(R=document.getElementById("btn-drawer"))==null||R.addEventListener("click",()=>{const a=!n.drawerOpen;i({drawerOpen:a}),a&&T()}),($=document.getElementById("usage-footer"))==null||$.addEventListener("click",()=>{T().then(()=>i({usagePanelOpen:!0}))}),(L=document.getElementById("usage-close"))==null||L.addEventListener("click",()=>i({usagePanelOpen:!1})),(b=document.getElementById("usage-overlay"))==null||b.addEventListener("click",a=>{a.target===a.currentTarget&&i({usagePanelOpen:!1})}),(A=document.getElementById("failed-close"))==null||A.addEventListener("click",()=>i({showFailedLogs:!1})),(x=document.getElementById("failed-refresh"))==null||x.addEventListener("click",()=>void z()),(ee=document.getElementById("failed-scope-session"))==null||ee.addEventListener("change",a=>{const d=a.target.checked;i({failedLogsScopeSession:d}),z()}),(te=document.getElementById("failed-logs-overlay"))==null||te.addEventListener("click",a=>{a.target===a.currentTarget&&i({showFailedLogs:!1})}),(ne=document.getElementById("btn-settings"))==null||ne.addEventListener("click",()=>void N()),(se=document.getElementById("btn-more"))==null||se.addEventListener("click",a=>{a.stopPropagation(),i({moreMenuOpen:!n.moreMenuOpen})}),(ae=document.getElementById("more-menu"))==null||ae.addEventListener("click",a=>a.stopPropagation()),document.querySelectorAll("[data-more-action]").forEach(a=>{a.addEventListener("click",()=>{const d=a.dataset.moreAction;switch(ze(),d){case"settings":N();break;case"mcp":Je();break;case"capabilities":zt();break;case"system-prompt":y("系统提示词页暂未在 Web UI 提供");break;case"brief":Ht().catch(l=>i({error:l instanceof Error?l.message:String(l)}));break;case"diagnostics":je().then(()=>y("诊断包已下载")).catch(l=>i({error:l instanceof Error?l.message:String(l)}));break;case"about":i({showAbout:!0});break}})}),n.moreMenuOpen&&window.setTimeout(()=>{const a=()=>{ze(),document.removeEventListener("click",a)};document.addEventListener("click",a)},0),(oe=document.getElementById("about-close"))==null||oe.addEventListener("click",()=>i({showAbout:!1})),(ie=document.getElementById("about-overlay"))==null||ie.addEventListener("click",a=>{a.target===a.currentTarget&&i({showAbout:!1})}),(re=document.getElementById("btn-setup"))==null||re.addEventListener("click",()=>void N()),(ce=document.getElementById("btn-model"))==null||ce.addEventListener("click",()=>void N()),(le=document.getElementById("btn-artifacts"))==null||le.addEventListener("click",async()=>{const a=await Qe();i({artifacts:a,showArtifacts:!0,capQuery:"",capResult:""})}),(de=document.getElementById("artifacts-close"))==null||de.addEventListener("click",()=>i({showArtifacts:!1})),(ue=document.getElementById("artifacts-overlay"))==null||ue.addEventListener("click",a=>{a.target===a.currentTarget&&i({showArtifacts:!1})}),(pe=document.getElementById("cap-search-btn"))==null||pe.addEventListener("click",async()=>{var l;const a=document.getElementById("cap-query"),d=((l=a==null?void 0:a.value)==null?void 0:l.trim())||"";n={...n,capQuery:d};try{const f=await ht(d);i({capResult:f})}catch(f){i({capResult:f instanceof Error?f.message:String(f)})}}),document.querySelectorAll("[data-art-preview]").forEach(a=>{a.addEventListener("click",()=>{const d=a.dataset.artSession,l=a.dataset.artPath;d&&l&&i({preview:{sessionId:d,path:l},showArtifacts:!1})})}),document.querySelectorAll("[data-art-insert]").forEach(a=>{a.addEventListener("click",async()=>{const d=a.dataset.artSession,l=a.dataset.artPath,f=n.activeSessionId;if(!(!f||!d||!l))try{await mt(f,d,l),await D(f),y("已复制到 imports/")}catch(v){i({error:v instanceof Error?v.message:String(v)})}})}),(me=document.getElementById("preview-close"))==null||me.addEventListener("click",()=>i({preview:null})),(fe=document.getElementById("preview-overlay"))==null||fe.addEventListener("click",a=>{a.target===a.currentTarget&&i({preview:null})});const e=document.getElementById("file-input");(ge=document.getElementById("btn-attach"))==null||ge.addEventListener("click",()=>e==null?void 0:e.click()),e==null||e.addEventListener("change",async()=>{var d;const a=n.activeSessionId;if(!(!a||!((d=e.files)!=null&&d.length)))try{for(const l of Array.from(e.files))await gt(a,l);await D(a),e.value=""}catch(l){i({error:l instanceof Error?l.message:String(l)})}}),document.querySelectorAll("[data-rm-attach]").forEach(a=>{a.addEventListener("click",async()=>{const d=n.activeSessionId,l=a.dataset.rmAttach;!d||!l||(await vt(d,l),await D(d))})}),document.querySelectorAll("[data-use]").forEach(a=>{a.addEventListener("click",async()=>{const d=a.dataset.use;if(n.isRunning){i({error:"请先停止当前 Run 再切换会话"});return}await _(d),await I(d),await k()})}),document.querySelectorAll("[data-del]").forEach(a=>{a.addEventListener("click",async d=>{d.stopPropagation();const l=a.dataset.del;if(confirm("删除该会话？")&&(await wt(l),await k(),n.activeSessionId===l))if(n.sessions[0])await _(n.sessions[0].sessionId),await I(n.sessions[0].sessionId);else{const f=await G();await k(),await I(f.sessionId)}})});const t=document.getElementById("composer");t==null||t.addEventListener("input",()=>{n={...n,composer:t.value}}),t==null||t.addEventListener("keydown",a=>{a.key==="Enter"&&!a.shiftKey&&(a.preventDefault(),H())}),(ve=document.getElementById("btn-send"))==null||ve.addEventListener("click",()=>void H()),(be=document.getElementById("btn-stop"))==null||be.addEventListener("click",async()=>{await St()}),document.querySelectorAll("[data-ask-opt]").forEach(a=>{a.addEventListener("click",()=>{const d=a.dataset.askId,l=a.dataset.askOpt;i({askAnswers:{...n.askAnswers,[d]:l}})})}),document.querySelectorAll("[data-ask-input]").forEach(a=>{a.addEventListener("input",()=>{const d=a.dataset.askInput;n={...n,askAnswers:{...n.askAnswers,[d]:a.value}}})}),(he=document.getElementById("ask-submit"))==null||he.addEventListener("click",()=>void Xt());const s=()=>i({showSettings:!1,settingsDraft:null,modelsStatus:null,modelsLoading:!1});(ye=document.getElementById("cfg-close"))==null||ye.addEventListener("click",s),(we=document.getElementById("settings-overlay"))==null||we.addEventListener("click",a=>{a.target===a.currentTarget&&s()}),document.querySelectorAll("[data-provider]").forEach(a=>{a.addEventListener("click",()=>{var l;const d=a.dataset.provider;d&&d!==((l=n.settingsDraft)==null?void 0:l.providerId)&&Jt(d)})});const o=a=>{n.settingsDraft&&i({settingsDraft:{...n.settingsDraft,...a}})};(Ee=document.getElementById("cfg-model"))==null||Ee.addEventListener("change",a=>{o({model:a.target.value})}),(ke=document.getElementById("cfg-ctx-turns"))==null||ke.addEventListener("change",a=>{o({maxContextTurns:parseInt(a.target.value,10)||0})}),(Se=document.getElementById("cfg-ctx-msgs"))==null||Se.addEventListener("change",a=>{o({maxContextMessages:parseInt(a.target.value,10)||0})}),($e=document.getElementById("cfg-run-turns"))==null||$e.addEventListener("change",a=>{o({maxTurnsPerRun:parseInt(a.target.value,10)||0})}),(Ie=document.getElementById("cfg-tool-calls"))==null||Ie.addEventListener("change",a=>{o({maxToolCallsPerRun:parseInt(a.target.value,10)||0})}),(Te=document.getElementById("cfg-open-mcp"))==null||Te.addEventListener("click",()=>void Je()),(Le=document.getElementById("mcp-close"))==null||Le.addEventListener("click",()=>i({showMcp:!1})),(Re=document.getElementById("mcp-overlay"))==null||Re.addEventListener("click",a=>{a.target===a.currentTarget&&i({showMcp:!1})}),(Ae=document.getElementById("mcp-add"))==null||Ae.addEventListener("click",()=>{i({mcpServers:[...n.mcpServers,{name:"server",url:"https://",enabled:!0,headers:{}}]})}),document.querySelectorAll(".mcp-remove").forEach(a=>{a.addEventListener("click",()=>{const d=Number(a.dataset.mcpIndex);i({mcpServers:n.mcpServers.filter((l,f)=>f!==d)})})}),(xe=document.getElementById("mcp-save"))==null||xe.addEventListener("click",async()=>{const a=document.querySelectorAll(".mcp-card"),d=[];a.forEach((l,f)=>{var M,E,Ue,Ne;const v=n.mcpServers[f],P=((M=l.querySelector(".mcp-name"))==null?void 0:M.value.trim())||"",C=((E=l.querySelector(".mcp-url"))==null?void 0:E.value.trim())||"",B=((Ue=l.querySelector(".mcp-enabled"))==null?void 0:Ue.checked)??!0;let O={};try{O=JSON.parse(((Ne=l.querySelector(".mcp-headers"))==null?void 0:Ne.value)||"{}")}catch{}d.push({name:P,url:C,enabled:B,headers:O,toolCount:v==null?void 0:v.toolCount,lastListedAt:v==null?void 0:v.lastListedAt})});try{const l=await dt(d),{servers:f}=await Ge(),v=l.warnings.length?` · ${l.warnings.join("; ")}`:"";i({mcpServers:f,mcpStatus:`已索引 ${l.toolCount} 个 MCP 工具${v}`}),y("MCP 已保存")}catch(l){i({error:l instanceof Error?l.message:String(l)})}}),document.querySelectorAll(".file-link").forEach(a=>{a.addEventListener("click",()=>{const d=a.dataset.filePath,l=n.activeSessionId;!d||!l||(Ze(d)?i({preview:{sessionId:l,path:d}}):y(d))})}),(Pe=document.getElementById("cfg-show-tools"))==null||Pe.addEventListener("click",async()=>{try{const{tools:a}=await ut();i({showTools:!0,toolsList:a})}catch(a){i({error:a instanceof Error?a.message:String(a)})}}),(Ce=document.getElementById("tools-close"))==null||Ce.addEventListener("click",()=>i({showTools:!1})),(Be=document.getElementById("tools-overlay"))==null||Be.addEventListener("click",a=>{a.target===a.currentTarget&&i({showTools:!1})}),(Oe=document.getElementById("cfg-refresh-models"))==null||Oe.addEventListener("click",()=>{const a=n.settingsDraft;a&&Z(a,!0)}),(Me=document.getElementById("cfg-save"))==null||Me.addEventListener("click",async()=>{const a=n.settingsDraft;if(!a)return;const d=document.getElementById("cfg-key").value,l=document.getElementById("cfg-base").value,f=document.getElementById("cfg-model").value,v=parseInt(document.getElementById("cfg-ctx-turns").value,10),P=parseInt(document.getElementById("cfg-ctx-msgs").value,10),C=parseInt(document.getElementById("cfg-run-turns").value,10),B=parseInt(document.getElementById("cfg-tool-calls").value,10),O=document.getElementById("cfg-web-key").value,M=document.getElementById("cfg-web-base").value;try{const E=await rt({apiKey:d||void 0,baseUrl:l||void 0,model:f||void 0,providerId:a.providerId,maxContextTurns:Number.isFinite(v)?v:0,maxContextMessages:Number.isFinite(P)?P:0,maxTurnsPerRun:Number.isFinite(C)?C:0,maxToolCallsPerRun:Number.isFinite(B)?B:0,webSearchApiKey:O||void 0,webSearchBaseUrl:M||void 0});i({config:E,showSettings:!1,settingsDraft:null,modelsStatus:null,error:null}),y("设置已保存")}catch(E){i({error:E instanceof Error?E.message:String(E)})}})}ot();Gt().catch(e=>{i({error:e instanceof Error?e.message:String(e)})});window.addEventListener("beforeunload",()=>j==null?void 0:j());
