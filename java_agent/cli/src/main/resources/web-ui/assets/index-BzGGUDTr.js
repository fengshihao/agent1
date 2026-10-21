(function(){const t=document.createElement("link").relList;if(t&&t.supports&&t.supports("modulepreload"))return;for(const r of document.querySelectorAll('link[rel="modulepreload"]'))a(r);new MutationObserver(r=>{for(const d of r)if(d.type==="childList")for(const u of d.addedNodes)u.tagName==="LINK"&&u.rel==="modulepreload"&&a(u)}).observe(document,{childList:!0,subtree:!0});function n(r){const d={};return r.integrity&&(d.integrity=r.integrity),r.referrerPolicy&&(d.referrerPolicy=r.referrerPolicy),r.crossOrigin==="use-credentials"?d.credentials="include":r.crossOrigin==="anonymous"?d.credentials="omit":d.credentials="same-origin",d}function a(r){if(r.ep)return;r.ep=!0;const d=n(r);fetch(r.href,d)}})();async function p(e){const t=await e.json();if(!e.ok)throw new Error(t.error||e.statusText);return t}async function Qe(){return p(await fetch("/api/config"))}async function Xe(e){return p(await fetch("/api/config",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(e)}))}async function ct(){return p(await fetch("/api/usage"))}async function lt(e,t=!1){const n=new URLSearchParams({provider:e});return t&&n.set("refresh","1"),p(await fetch(`/api/models?${n}`))}async function Ve(){return p(await fetch("/api/mcp/servers"))}async function dt(e){return p(await fetch("/api/mcp/servers",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({servers:e})}))}async function ut(){return p(await fetch("/api/tools"))}async function mt(e){return p(await fetch(`/api/sessions/${encodeURIComponent(e)}/system-prompt`))}async function pt(){var d,u;const e=await fetch("/api/diagnostics/export");if(!e.ok)throw new Error("诊断包导出失败");const t=await e.blob(),n=((u=(d=e.headers.get("Content-Disposition"))==null?void 0:d.match(/filename="([^"]+)"/))==null?void 0:u[1])??`agent1-diag-${Date.now()}.zip`,a=URL.createObjectURL(t),r=document.createElement("a");r.href=a,r.download=n,r.click(),URL.revokeObjectURL(a)}async function ft(e=!0){return p(await fetch("/api/sessions/rollover",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({summarize:e})}))}async function Ye(){return(await p(await fetch("/api/artifacts"))).artifacts}async function gt(e,t,n){return p(await fetch(`/api/sessions/${encodeURIComponent(e)}/artifacts/import`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({sourceSessionId:t,workspaceRelativePath:n})}))}async function vt(e){return(await p(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`))).files}async function yt(e,t){const n=new FormData;return n.append("file",t),p(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`,{method:"POST",body:n}))}async function ht(e,t){await p(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments/${encodeURIComponent(t)}`,{method:"DELETE"}))}function bt(e,t){const n=t.split("/").map(a=>encodeURIComponent(a)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${n}`}async function wt(e){return(await p(await fetch(`/api/capabilities?q=${encodeURIComponent(e)}`))).text}async function St(){return p(await fetch("/api/sessions"))}async function H(){return p(await fetch("/api/sessions",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"}))}async function F(e){await p(await fetch(`/api/sessions/${encodeURIComponent(e)}/use`,{method:"POST",body:"{}"}))}async function Et(e){await p(await fetch(`/api/sessions/${encodeURIComponent(e)}`,{method:"DELETE"}))}async function kt(e){return p(await fetch(`/api/sessions/${encodeURIComponent(e)}/brief`))}async function $t(e){const t=e?`?sessionId=${encodeURIComponent(e)}`:"";return(await p(await fetch(`/api/logs/failed${t}`))).items}async function Ze(e){return(await p(await fetch(`/api/sessions/${encodeURIComponent(e)}/transcript`))).messages}async function It(e,t){await p(await fetch(`/api/sessions/${encodeURIComponent(e)}/messages`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({text:t})}))}async function Tt(){await p(await fetch("/api/run/abort",{method:"POST",body:"{}"}))}function Pt(e){const t=new EventSource("/api/events");return t.onmessage=n=>{try{e(JSON.parse(n.data))}catch{}},()=>t.close()}const Lt="ask_user",Rt=["等待用户","waiting","ask_user"];function At(e){try{const n=(JSON.parse(e.argumentsJson||"{}").questions||[]).map((a,r)=>({id:a.id||`q${r}`,prompt:a.prompt||a.question||"",options:a.options,allowFreeText:a.allowFreeText??a.allow_free_text??!0})).filter(a=>a.prompt);return n.length===0?null:{questions:n}}catch{return null}}function et(e){if(!e.length)return null;for(let t=e.length-1;t>=0;t--){const n=e[t];if(n.role==="user")return null;if(n.role==="assistant"){for(const a of n.toolCalls||[])if(a.name===Lt)return At(a)}if(n.role==="toolResult"){const a=n.content||"";if(!Rt.some(d=>a.toLowerCase().includes(d.toLowerCase())))return null}}return null}function xt(e,t){return e.questions.map(a=>{const r=(t[a.id]||"").trim();return`${a.prompt}
→ ${r||"(未答)"}`}).join(`

`)}function Bt(e,t,n,a,r){if(t||r||n.trim()||a.trim())return!1;const d=Ct(e);return(d==null?void 0:d.role)==="assistant"}function Ct(e){return e.filter(t=>t.role==="user"||t.role==="assistant").at(-1)}function Mt(){return{sessions:[],activeSessionId:null,config:null,lines:[],streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,pendingAsk:null,askAnswers:{},error:null,showSettings:!1,drawerOpen:!0,composer:"",usage:null,usagePanelOpen:!1,toast:null,attachments:[],artifacts:[],showArtifacts:!1,preview:null,capQuery:"",capResult:"",modelOptions:[],settingsDraft:null,modelsLoading:!1,modelsStatus:null,showTools:!1,toolsList:[],lastRunTokenSummary:null,showMcp:!1,mcpServers:[],mcpStatus:null,showFailedLogs:!1,failedLogs:[],failedLogsScopeSession:!0,moreMenuOpen:!1,showAbout:!1,showSystemPrompt:!1,systemPromptBusy:!1,systemPromptError:null,systemPromptText:"",systemPromptTools:[],mcpWebSearchBaseUrl:"https://api.tavily.com",mcpWebSearchApiKey:""}}function tt(e){const t=e.toLowerCase();return t.endsWith(".html")||t.endsWith(".htm")}function G(e){return e.isRunning||e.streamingText.trim()||e.streamingReasoning.trim()||e.tools.length>0||e.pendingAsk?!1:!e.lines.some(t=>t.role==="user"||t.role==="assistant")}function st(e){const t=[];return e.forEach((n,a)=>{var r;if(n.role==="user"){t.push({id:`u-${a}`,role:"user",content:n.content});return}if(n.role==="assistant"){(r=n.content)!=null&&r.trim()&&t.push({id:`a-${a}`,role:"assistant",content:n.content});for(const d of n.toolCalls||[])t.push({id:`t-${d.id}`,role:"tool",content:d.argsPreview||d.argumentsJson||"",toolName:d.name});return}if(n.role==="toolResult"){t.push({id:`tr-${a}`,role:"tool",content:n.content,toolName:"result",error:!!n.error});return}n.role}),t}const Ot=new Set(["png","jpg","jpeg","webp","gif"]);function q(e){var n;const t=((n=e.split(".").pop())==null?void 0:n.toLowerCase())??"";return Ot.has(t)}function nt(e){const t=/!\[[^\]]*]\(([^)]+)\)/g,n=[];let a;for(;(a=t.exec(e))!==null;){const r=a[1].trim();r&&!r.startsWith("http://")&&!r.startsWith("https://")&&n.push(Q(r))}return n}function Q(e){return e.replace(/^\.\//,"").replace(/^workspace\//,"")}function Ut(e){const t=[],n=/```[\w-]*\n?([\s\S]*?)```/g;let a=0,r;for(;(r=n.exec(e))!==null;)r.index>a&&t.push({kind:"text",text:e.slice(a,r.index)}),t.push({kind:"code",text:r[1]??""}),a=r.index+r[0].length;return a<e.length&&t.push({kind:"text",text:e.slice(a)}),t.length?t:[{kind:"text",text:e}]}function W(e,t){return e?Ut(e).map(n=>n.kind==="code"?`<pre class="bubble-code"><code>${k(n.text.trimEnd())}</code></pre>`:Nt(n.text,t)).join(""):""}function Nt(e,t){const n=/!?\[([^\]]*)]\(([^)]+)\)/g;let a="",r=0,d;for(;(d=n.exec(e))!==null;){a+=qe(e.slice(r,d.index));const u=d[1],g=d[2].trim(),w=d[0].startsWith("!");if(t&&!g.startsWith("http://")&&!g.startsWith("https://")){const h=Q(g);w&&q(h)?a+=`<figure class="bubble-image"><img src="${ot(t,h)}" alt="${_(h)}" loading="lazy"/></figure>`:w?a+=k(d[0]):a+=`<button type="button" class="file-link" data-file-path="${_(h)}">${k(u||h)}</button>`}else a+=k(d[0]);r=d.index+d[0].length}return a+=qe(e.slice(r)),a}function qe(e){const t=e.split(/`([^`\n]+)`/);let n="";for(let a=0;a<t.length;a++)a%2===0?n+=k(t[a]):n+=`<code class="bubble-inline-code">${k(t[a])}</code>`;return n}function ot(e,t){const n=t.split("/").map(a=>encodeURIComponent(a)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${n}`}function k(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}function _(e){return k(e).replaceAll("'","&#39;")}function jt(e){const t=nt(e).filter(q),n=new Set,a=[];for(const u of t)n.has(u)||(n.add(u),a.push(u));const r=/(?:imports|out|tmp|staging)[/][\w./-]+\.(?:png|jpe?g|webp|gif)/gi;let d;for(;(d=r.exec(e))!==null;){const u=Q(d[0]);q(u)&&!n.has(u)&&(n.add(u),a.push(u))}return a}function We(e,t){const n=new Set(nt(e));return jt(e).filter(a=>!n.has(a)).map(a=>`<figure class="bubble-image"><img src="${ot(t,a)}" alt="${_(a)}" loading="lazy"/></figure>`).join("")}let N=0,j=0,X=0;function Dt(){N=0,j=0,X=0}function Ft(e){N+=Number(e.inputTokens)||0,j+=Number(e.outputTokens)||0;const t=e.cachedTokens;t!=null&&(X+=Number(t)||0)}function _e(e){const t=N>0||j>0;return!e&&!t?null:{inputTokens:N,outputTokens:j,cachedTokens:X,failed:e}}function qt(e){const t=[`输入 ${Ke(e.inputTokens)}`,`输出 ${Ke(e.outputTokens)}`];if(e.inputTokens>0){const n=Math.min(100,Math.round(e.cachedTokens*100/e.inputTokens));t.push(`缓存命中 ${n}%`)}return e.failed&&t.push("未完成"),t.join(" · ")}function Ke(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,n=Math.trunc(t);return t===n?`${n}k`:`${t.toFixed(1)}k`}const K=[{id:"dashscope",label:"通义",baseUrl:"https://dashscope.aliyuncs.com/compatible-mode/v1",defaultModel:"qwen3.7-flash"},{id:"deepseek",label:"DeepSeek",baseUrl:"https://api.deepseek.com",defaultModel:"deepseek-flash"},{id:"zhipu_coding",label:"智谱 Coding",baseUrl:"https://open.bigmodel.cn/api/coding/paas/v4",defaultModel:"glm-5.3"}];function Wt(e){const t=e.toLowerCase();return t.includes("deepseek.com")?"deepseek":t.includes("bigmodel.cn")?"zhipu_coding":"dashscope"}function V(e){return K.find(t=>t.id===e)??K[0]}function M(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,n=Math.trunc(t);return t===n?`${n}k`:`${t.toFixed(1)}k`}function _t(e){if(!e||!e.hasUsage)return{headline:"累计用量",detail:"暂无 usage 记录"};const t=`约 ${M(e.totalTokens)} tokens`,n=e.cacheHitPercent,a=n!=null?`输入 ${M(e.inputTokens)} · 缓存 ${n}%`:`输入 ${M(e.inputTokens)} · 输出 ${M(e.outputTokens)}`;return{headline:t,detail:a}}function Kt(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;")}function Jt(e){const t=[`输入 ${e.inputTokens.toLocaleString()}`,`输出 ${e.outputTokens.toLocaleString()}`,`缓存 ${e.cachedTokens.toLocaleString()}`,`usage 事件 ${e.usageEvents}`];let n="";return e.byModel.length&&(n="<ul>"+e.byModel.map(a=>`<li><strong>${Kt(a.modelId||"（未知模型）")}</strong>：入 ${a.inputTokens.toLocaleString()} / 出 ${a.outputTokens.toLocaleString()}</li>`).join("")+"</ul>"),`<p>${t.join(" · ")}</p>${n}`}const at=document.getElementById("app");if(!at)throw new Error("#app missing");let s=Mt(),O=null;function i(e){s={...s,...e},rt()}function c(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}async function E(){const e=await St();i({sessions:e.sessions.slice().sort((t,n)=>n.updatedAt.localeCompare(t.updatedAt)),activeSessionId:e.activeSessionId??s.activeSessionId,isRunning:e.runInProgress})}async function T(){try{const e=await ct();i({usage:e})}catch{}}async function J(){const e=s.failedLogsScopeSession&&s.activeSessionId?s.activeSessionId:null,t=await $t(e);i({failedLogs:t})}async function zt(){i({showFailedLogs:!0});try{await J()}catch(e){i({error:e instanceof Error?e.message:String(e)})}}function b(e){i({toast:e}),window.setTimeout(()=>{s.toast===e&&i({toast:null})},2200)}async function Je(){if(s.isRunning){i({error:"请先停止当前 Run"});return}if(s.activeSessionId&&G(s)){b("已是新会话");return}const e=await H();await E(),await I(e.sessionId),await T(),i({drawerOpen:!1})}async function Ht(){if(s.isRunning){i({error:"请先停止当前 Run"});return}if(s.activeSessionId&&G(s)){b("已是新会话");return}const e=await ft(!0);await E(),await I(e.session.sessionId),await T(),i({drawerOpen:!1}),b(e.summaryPath?"已总结并新建会话":"已新建会话")}function it(){const e=s.config,t=(e==null?void 0:e.baseUrl)||"",n=(e==null?void 0:e.providerId)||Wt(t);return{providerId:n,baseUrl:t,model:(e==null?void 0:e.model)||V(n).defaultModel,maxContextTurns:(e==null?void 0:e.maxContextTurns)??6,maxContextMessages:(e==null?void 0:e.maxContextMessages)??0,maxTurnsPerRun:(e==null?void 0:e.maxTurnsPerRun)??24,maxToolCallsPerRun:(e==null?void 0:e.maxToolCallsPerRun)??48}}async function Y(e,t){var n,a;i({modelsLoading:!0});try{const{models:r,source:d}=await lt(e.providerId,t);let u=e.model;if(!r.some(g=>g.modelId===u)){const g=V(e.providerId);u=((n=r.find(w=>w.modelId===g.defaultModel))==null?void 0:n.modelId)??((a=r[0])==null?void 0:a.modelId)??u}i({modelOptions:r,modelsLoading:!1,settingsDraft:{...e,model:u},modelsStatus:t?`已从网络更新 · ${r.length} 个模型`:`共 ${r.length} 个模型`})}catch(r){i({modelsLoading:!1,error:r instanceof Error?r.message:"模型列表加载失败"})}}function Gt(e){const t=V(e),n={...s.settingsDraft??it(),providerId:e,baseUrl:t.baseUrl,model:t.defaultModel};i({settingsDraft:n,error:null}),Y(n,!1)}async function D(){const e=it();i({showSettings:!0,settingsDraft:e,modelOptions:[],error:null,moreMenuOpen:!1,showMcp:!1,showSystemPrompt:!1}),await Y(e,!1)}async function ze(){try{const[{servers:e},t]=await Promise.all([Ve(),Qe()]);i({config:t,showMcp:!0,mcpServers:e,mcpStatus:null,showSettings:!1,showSystemPrompt:!1,moreMenuOpen:!1,mcpWebSearchBaseUrl:t.webSearchBaseUrl||"https://api.tavily.com",mcpWebSearchApiKey:""})}catch(e){i({error:e instanceof Error?e.message:String(e)})}}async function Qt(){const e=s.activeSessionId;if(!e){i({showSystemPrompt:!0,showSettings:!1,showMcp:!1,systemPromptBusy:!1,systemPromptError:"先打开或新建一个会话，再查看发给模型的系统提示词。",systemPromptText:"",systemPromptTools:[],moreMenuOpen:!1});return}i({showSystemPrompt:!0,showSettings:!1,showMcp:!1,systemPromptBusy:!0,systemPromptError:null,moreMenuOpen:!1});try{const t=await mt(e);i({showSystemPrompt:!0,systemPromptBusy:!1,systemPromptError:null,systemPromptText:t.prompt||"",systemPromptTools:t.tools||[]})}catch(t){i({showSystemPrompt:!0,systemPromptBusy:!1,systemPromptError:t instanceof Error?t.message:String(t)})}}async function Xt(){var r,d;const e=document.getElementById("mcp-web-key"),t=document.getElementById("mcp-web-base"),n=((r=e==null?void 0:e.value)==null?void 0:r.trim())||void 0,a=((d=t==null?void 0:t.value)==null?void 0:d.trim())||void 0;try{const u=await Xe({webSearchApiKey:n,webSearchBaseUrl:a});i({config:u,mcpWebSearchApiKey:"",mcpWebSearchBaseUrl:u.webSearchBaseUrl||a||"https://api.tavily.com",mcpStatus:u.hasWebSearch?"联网搜索已保存":"已保存 Base URL（尚未配置 Key，web_search 不会注册）"}),b("联网搜索已保存")}catch(u){i({error:u instanceof Error?u.message:String(u)})}}async function Vt(){try{const e=await Ye();i({artifacts:e,showArtifacts:!0,capQuery:"",capResult:"",moreMenuOpen:!1})}catch(e){i({error:e instanceof Error?e.message:String(e)})}}async function Yt(){const e=s.activeSessionId;if(!e)return;const{text:t}=await kt(e);await navigator.clipboard.writeText(t),b("简报已复制到剪贴板")}function He(){i({moreMenuOpen:!1})}async function U(e){try{const t=await vt(e);i({attachments:t})}catch{i({attachments:[]})}}async function I(e){const t=await Ze(e),n=et(t);i({activeSessionId:e,lines:st(t),pendingAsk:n,askAnswers:{},streamingText:"",streamingReasoning:"",tools:[],error:null}),await U(e)}async function Zt(){const[e]=await Promise.all([Qe(),E()]);i({config:e});let t=s.activeSessionId;t||(s.sessions.length===0?(t=(await H()).sessionId,await E()):(t=s.sessions[0].sessionId,await F(t))),await I(t),await T(),O=Pt(es)}function es(e){if(!(e.sessionId&&s.activeSessionId&&e.sessionId!==s.activeSessionId))switch(e.type){case"AGENT_START":Dt(),i({isRunning:!0,streamingText:"",streamingReasoning:"",tools:[],error:null,lastRunTokenSummary:null});break;case"USAGE":Ft(e.payload);break;case"MESSAGE_UPDATE":i({streamingText:s.streamingText+String(e.payload.delta||""),isRunning:!0});break;case"REASONING_UPDATE":i({streamingReasoning:s.streamingReasoning+String(e.payload.delta||""),isRunning:!0});break;case"MESSAGE_RESET":i({streamingText:"",streamingReasoning:""});break;case"TOOL_EXECUTION_START":{const t=e.payload.toolCall,n={id:t.id||`tool-${Date.now()}`,toolCallId:t.id||"",toolName:t.name||"tool",argsPreview:t.argsPreview||"",running:!0};i({tools:[...s.tools,n],isRunning:!0});break}case"TOOL_EXECUTION_END":{const t=String(e.payload.toolCallId||""),n=s.tools.map(r=>r.toolCallId===t?{...r,running:!1,error:!!(e.payload.error||e.payload.toolError),resultSummary:String(e.payload.resultText||"")}:r),a=!!e.payload.stopRunWaitingUser;i({tools:n,isRunning:!a&&s.isRunning});break}case"AGENT_ERROR":i({error:String(e.payload.message||"运行出错"),isRunning:!1,lastRunTokenSummary:_e(!0)}),Ge();break;case"AGENT_END":i({isRunning:!1,lastRunTokenSummary:_e(!1)}),Ge();break}}async function Ge(){if(!s.activeSessionId)return;const e=s.lastRunTokenSummary;try{const t=await Ze(s.activeSessionId);i({lines:st(t),pendingAsk:et(t),streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,lastRunTokenSummary:e}),await E(),await T()}catch(t){i({error:t instanceof Error?t.message:String(t)})}}async function z(){var a;const e=s.composer.trim();if(!e||!s.activeSessionId||s.isRunning||s.pendingAsk)return;if(!((a=s.config)!=null&&a.hasApiKey)){i({showSettings:!0,error:"请先配置 API Key"});return}const t={id:`local-${Date.now()}`,role:"user",content:e};i({lines:[...s.lines,t],composer:"",isRunning:!0,streamingText:"",tools:[],error:null,lastRunTokenSummary:null});let n=e;if(s.attachments.length>0){const r=s.attachments.map(d=>`- ${d.workspaceRelativePath}`);n+=`

[用户附件]
${r.join(`
`)}`}try{await It(s.activeSessionId,n)}catch(r){i({isRunning:!1,error:r instanceof Error?r.message:String(r)})}}async function ts(){if(!s.pendingAsk||!s.activeSessionId)return;const e=xt(s.pendingAsk,s.askAnswers);i({pendingAsk:null,composer:e}),await z()}function ss(e,t){if(e.role==="tool"){const a=t?W(e.content,t)+We(e.content,t):c(e.content);return`<details class="tool-card"><summary>⚙ ${c(e.toolName||"tool")}</summary>
      <div class="tool-row ${e.error?"err":""}">${a}</div></details>`}const n=t?W(e.content,t)+We(e.content,t):c(e.content);return`<div class="bubble ${e.role}"><div class="bubble-text">${n}</div></div>`}function ns(){if(!s.tools.length&&!s.streamingText&&!s.streamingReasoning)return"";const e=s.tools.map(n=>{const a=n.running?"进行中…":n.error?"失败":"完成";return`<div class="tool-row ${n.error?"err":""}"><strong>${c(n.toolName)}</strong> · ${a}
        ${n.argsPreview?`<div>${c(n.argsPreview)}</div>`:""}
        ${n.resultSummary?`<div>${c(n.resultSummary)}</div>`:""}
      </div>`}).join("");return`<details class="tool-card" open><summary>思考与执行</summary>${[s.streamingReasoning?`<div class="tool-row">思考：${c(s.streamingReasoning)}</div>`:"",e].join("")}</details>
    ${s.streamingText?`<div class="bubble assistant">${c(s.streamingText)}</div>`:""}`}function os(){return s.pendingAsk?`<div class="ask-panel"><h3>请回答后继续</h3>${s.pendingAsk.questions.map(t=>{const n=(t.options||[]).map(a=>`<button type="button" class="chip ${s.askAnswers[t.id]===a?"selected":""}" data-ask-id="${c(t.id)}" data-ask-opt="${c(a)}">${c(a)}</button>`).join("");return`<div class="ask-q">
        <label>${c(t.prompt)}</label>
        ${n?`<div class="ask-options">${n}</div>`:""}
        <input data-ask-input="${c(t.id)}" value="${c(s.askAnswers[t.id]||"")}" placeholder="你的回答" />
      </div>`}).join("")}
    <button type="button" class="primary-btn" id="ask-submit">提交回答</button></div>`:""}function as(){if(!s.showArtifacts)return"";const e=s.artifacts.map(t=>{const a=tt(t.workspaceRelativePath)?`<button type="button" class="link-btn" data-art-preview="1" data-art-session="${c(t.sessionId)}" data-art-path="${c(t.workspaceRelativePath)}">预览</button>`:"",r=s.activeSessionId?`<button type="button" class="link-btn" data-art-insert="1" data-art-session="${c(t.sessionId)}" data-art-path="${c(t.workspaceRelativePath)}">插入当前会话</button>`:"";return`<li><strong>${c(t.fileName)}</strong>
        <span class="muted"> · ${c(t.sessionId.slice(0,8))} · ${Math.round(t.sizeBytes/1024)}KB</span>
        <div class="artifact-actions">${a} ${r}</div></li>`}).join("");return`<div class="overlay" id="artifacts-overlay">
    <div class="overlay-panel wide-panel">
      <h2>产物库</h2>
      <p class="muted">workspace 内 AI 产出（不含 imports/、tmp/）</p>
      <div class="cap-search">
        <input id="cap-query" placeholder="find_caps 检索…" value="${c(s.capQuery)}" />
        <button type="button" class="primary-btn" id="cap-search-btn">检索</button>
      </div>
      ${s.capResult?`<pre class="cap-result">${c(s.capResult)}</pre>`:""}
      <ul class="artifact-list">${e||"<li class='muted'>暂无产物</li>"}</ul>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="artifacts-close">关闭</button>
      </div>
    </div>
  </div>`}function is(){if(!s.preview)return"";const e=bt(s.preview.sessionId,s.preview.path);return`<div class="overlay preview-overlay" id="preview-overlay">
    <div class="preview-panel">
      <div class="preview-toolbar">
        <span>${c(s.preview.path)}</span>
        <button type="button" class="icon-btn" id="preview-close">✕</button>
      </div>
      <iframe class="preview-frame" src="${e}" title="preview"></iframe>
    </div>
  </div>`}function rs(){if(!s.showMcp)return"";const e=s.mcpServers.map((n,a)=>{const r=JSON.stringify(n.headers||{});return`<div class="mcp-card" data-mcp-index="${a}">
        <div class="field-row">
          <div class="field"><label>名称</label><input class="settings-control mcp-name" value="${c(n.name)}" /></div>
          <label class="mcp-enable"><input type="checkbox" class="mcp-enabled" ${n.enabled?"checked":""}/> 启用</label>
        </div>
        <div class="field"><label>URL</label><input class="settings-control mcp-url" value="${c(n.url)}" /></div>
        <div class="field"><label>Headers (JSON)</label><input class="settings-control mcp-headers" value="${c(r)}" /></div>
        <p class="settings-hint">${n.toolCount?`${n.toolCount} 个工具`:"保存后尝试索引工具"}</p>
        <button type="button" class="link-btn mcp-remove" data-mcp-index="${a}">删除</button>
      </div>`}).join(""),t=s.config;return`<div class="overlay settings-overlay" id="mcp-overlay">
    <div class="overlay-panel settings-panel">
      <header class="settings-header">
        <h2>MCP 与联网搜索</h2>
        <button type="button" class="icon-btn" id="mcp-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <p class="muted">添加服务器后，助手用 find_caps 查找接口，再在脚本里调用 $mcp.名称.工具。保存后会刷新索引。</p>
        ${s.mcpStatus?`<p class="settings-hint">${c(s.mcpStatus)}</p>`:""}
        <div id="mcp-list">${e}</div>
        <button type="button" class="link-btn" id="mcp-add">添加服务器</button>
        <section class="settings-section">
          <h3>联网搜索（Tavily）</h3>
          <div class="field">
            <label for="mcp-web-key">Web Search API Key</label>
            <input id="mcp-web-key" class="settings-control" type="password" autocomplete="off"
              value="${c(s.mcpWebSearchApiKey)}"
              placeholder="${t!=null&&t.hasWebSearch?"已配置，留空不修改":"tvly-…"}" />
          </div>
          <div class="field">
            <label for="mcp-web-base">Tavily Base URL</label>
            <input id="mcp-web-base" class="settings-control" value="${c(s.mcpWebSearchBaseUrl)}" />
          </div>
          <button type="button" class="link-btn" id="mcp-save-search">保存搜索配置</button>
        </section>
      </div>
      <footer class="settings-footer">
        <button type="button" class="primary-btn" id="mcp-save">保存 MCP 并索引</button>
      </footer>
    </div>
  </div>`}function cs(){if(!s.showSystemPrompt)return"";const e=s.systemPromptTools.map(n=>{const a=n.description?`<p class="muted">${c(n.description)}</p>`:"",r=n.parametersSchema?`<pre class="prompt-schema">${c(n.parametersSchema)}</pre>`:"";return`<details class="prompt-tool">
        <summary>${c(n.name)}</summary>
        ${a}${r}
      </details>`}).join(""),t=s.systemPromptBusy?'<p class="muted">正在读取系统提示词…</p>':[s.systemPromptError?`<p class="error-inline">${c(s.systemPromptError)}</p>`:"",s.systemPromptText?`<div class="prompt-body">${W(s.systemPromptText,null)}</div>`:s.systemPromptError?"":'<p class="muted">当前会话尚未装入系统提示词。</p>',s.systemPromptTools.length?`<h3 class="prompt-tools-title">工具 ${s.systemPromptTools.length}</h3>${e}`:""].join("");return`<div class="overlay settings-overlay" id="system-prompt-overlay">
    <div class="overlay-panel settings-panel wide-panel">
      <header class="settings-header">
        <h2>系统提示词</h2>
        <div class="settings-header-actions">
          <button type="button" class="link-btn" id="system-prompt-copy" ${s.systemPromptText?"":"disabled"}>复制</button>
          <button type="button" class="icon-btn" id="system-prompt-close" title="关闭">✕</button>
        </div>
      </header>
      <div class="settings-body prompt-scroll">${t}</div>
    </div>
  </div>`}function ls(){return s.showTools?`<div class="overlay" id="tools-overlay">
    <div class="overlay-panel usage-panel">
      <h2>Agent 工具</h2>
      <ul class="tools-list">${s.toolsList.map(t=>`<li><strong>${c(t.name)}</strong><span class="muted">${c(t.description)}</span></li>`).join("")||"<li class='muted'>无</li>"}</ul>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="tools-close">关闭</button>
      </div>
    </div>
  </div>`:""}function ds(){return s.showAbout?`<div class="overlay" id="about-overlay">
    <div class="overlay-panel usage-panel">
      <h2>关于 Agent1</h2>
      <p>桌面 Web UI（<code>./agent1 ui</code>）— 对齐 Android 生产力聊天壳。</p>
      <p class="muted">本机 HTTP · 仅 127.0.0.1</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="about-close">关闭</button>
      </div>
    </div>
  </div>`:""}function us(){return s.moreMenuOpen?`<div class="more-menu" role="menu" id="more-menu">
    <button type="button" class="more-menu-item" data-more-action="settings">模型设置</button>
    <button type="button" class="more-menu-item" data-more-action="mcp">MCP 与联网搜索</button>
    <button type="button" class="more-menu-item" data-more-action="capabilities">能力检索</button>
    <button type="button" class="more-menu-item" data-more-action="system-prompt">系统提示词</button>
    <button type="button" class="more-menu-item" data-more-action="brief">会话简报</button>
    <button type="button" class="more-menu-item" data-more-action="diagnostics">诊断包</button>
    <button type="button" class="more-menu-item" data-more-action="failed">失败工具调用</button>
    <button type="button" class="more-menu-item" data-more-action="about">关于</button>
  </div>`:""}function ms(){if(!s.showFailedLogs)return"";const e=s.failedLogsScopeSession?"当前会话":"全部会话",t=s.failedLogs.map(n=>`<li class="failed-log-item">
          <div><strong>${c(n.toolName||"tool")}</strong>
            <span class="muted"> · ${c(n.toolCallId)}</span></div>
          <div class="muted small">run ${c(n.runId)} · session ${c(n.sessionId)}</div>
          <div>${c(n.errorMessage||"—")}</div>
        </li>`).join("");return`<div class="overlay" id="failed-logs-overlay">
    <div class="overlay-panel usage-panel wide-panel">
      <h2>失败工具调用</h2>
      <p class="muted">范围：${c(e)} · 对齐 <code>./agent1 logs failed</code></p>
      <ul class="failed-log-list">${t||"<li class='muted'>（无失败记录）</li>"}</ul>
      <div class="overlay-actions">
        <label class="checkbox-row"><input type="checkbox" id="failed-scope-session" ${s.failedLogsScopeSession?"checked":""}/> 仅当前会话</label>
        <button type="button" id="failed-refresh">刷新</button>
        <button type="button" class="primary-btn" id="failed-close">关闭</button>
      </div>
    </div>
  </div>`}function ps(){return!s.usagePanelOpen||!s.usage?"":`<div class="overlay" id="usage-overlay">
    <div class="overlay-panel usage-panel">
      <h2>累计用量</h2>
      <div class="usage-detail">${Jt(s.usage)}</div>
      <p class="muted">来自 agentRoot/logs/events.jsonl</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="usage-close">关闭</button>
      </div>
    </div>
  </div>`}function fs(){if(!s.showSettings||!s.settingsDraft)return"";const e=s.settingsDraft,t=s.config,n=K.map(u=>`<button type="button" class="provider-chip ${u.id===e.providerId?"active":""}" data-provider="${u.id}">${c(u.label)}</button>`).join(""),a=s.modelOptions.map(u=>{const g=u.modelId===e.model?"selected":"";return`<option value="${c(u.modelId)}" ${g}>${c(u.title)} · ${c(u.subtitle)}</option>`}).join(""),r=e.model&&!s.modelOptions.some(u=>u.modelId===e.model)?`<option value="${c(e.model)}" selected>${c(e.model)}（当前）</option>`:"",d=s.modelsLoading?"正在加载模型列表…":s.modelsStatus||(s.modelOptions.length?`共 ${s.modelOptions.length} 个模型`:"");return`<div class="overlay settings-overlay" id="settings-overlay">
    <div class="overlay-panel settings-panel">
      <header class="settings-header">
        <h2>模型与限额</h2>
        <button type="button" class="icon-btn" id="cfg-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <section class="settings-section">
          <h3>服务商</h3>
          <div class="provider-row">${n}</div>
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
              <button type="button" class="link-btn" id="cfg-refresh-models" ${s.modelsLoading?"disabled":""}>从网络刷新</button>
            </div>
            <select id="cfg-model" class="settings-control settings-select">${r}${a}</select>
            <p class="settings-hint">${c(d)}</p>
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
        <button type="button" class="link-btn" id="cfg-open-mcp">MCP 与联网搜索</button>
        <button type="button" class="link-btn" id="cfg-show-tools">已注册工具</button>
        <button type="button" class="primary-btn settings-save" id="cfg-save">保存并生效</button>
      </footer>
    </div>
  </div>`}function rt(){var P;const e=s.sessions.map(y=>{const R=y.sessionId===s.activeSessionId?"active":"",A=y.title||y.sessionId.slice(0,8);return`<div class="session-item ${R}">
        <button type="button" class="session-title" data-use="${y.sessionId}">${c(A)}</button>
        <button type="button" class="session-delete" data-del="${y.sessionId}" title="删除">✕</button>
      </div>`}).join(""),t=Bt(s.lines,s.isRunning,s.streamingText,s.streamingReasoning,!!s.pendingAsk),n=s.lines.map(y=>ss(y,s.activeSessionId)).join(""),a=!s.isRunning&&!s.pendingAsk&&s.lines.length>0&&!G(s),r=t?'<button type="button" class="new-topic-btn" id="btn-new-topic">开始新话题</button>':"",d=a?'<button type="button" class="new-topic-btn secondary" id="btn-summarize-new">总结并新开话题</button>':"",u=s.lastRunTokenSummary&&!s.isRunning?`<div class="run-token-summary ${s.lastRunTokenSummary.failed?"failed":""}">${c(qt(s.lastRunTokenSummary))}</div>`:"",g=s.lines.length===0&&!s.streamingText&&!s.isRunning?'<div class="landing"><h2>Agent1</h2><p>生产力助手 · 与 Android 端同款纸本风格。左侧管理会话，下方输入开始对话。</p></div>':n+u+r+d+ns()+os(),w=_t(s.usage),h=s.isRunning||!!s.pendingAsk,L=s.pendingAsk?"请在上方卡片回答":s.isRunning?"生成中…":"输入消息，Enter 发送，Shift+Enter 换行";at.innerHTML=`
  <div class="app">
    <aside class="drawer ${s.drawerOpen?"":"collapsed"}">
      <div class="drawer-header">
        <div class="brand">Agent1</div>
        <div class="drawer-actions">
          <button type="button" class="icon-btn" id="btn-new" title="新会话">＋</button>
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
            <button type="button" class="icon-btn" id="btn-more" title="更多" aria-haspopup="menu" aria-expanded="${s.moreMenuOpen}">⋮</button>
            ${us()}
          </div>
        </div>
      </div>
      ${s.toast?`<div class="toast-banner">${c(s.toast)}</div>`:""}
      ${s.error?`<div class="error-banner">${c(s.error)}</div>`:""}
      <div class="chat-scroll" id="chat-scroll">${g}</div>
      <div class="composer-wrap">
        ${s.config&&!s.config.hasApiKey?'<div class="inline-setup">尚未配置 API Key。<button type="button" id="btn-setup">打开设置</button></div>':""}
        ${s.attachments.length?`<div class="attachment-chips">${s.attachments.map(y=>`<span class="chip">${c(y.fileName)} <button type="button" data-rm-attach="${c(y.workspaceRelativePath)}">×</button></span>`).join("")}</div>`:""}
        <div class="composer">
          <input type="file" id="file-input" class="hidden-file" multiple />
          <button type="button" class="icon-btn" id="btn-attach" title="添加附件" ${h?"disabled":""}>📎</button>
          <textarea id="composer" ${h?"disabled":""} placeholder="${L}">${c(s.composer)}</textarea>
          ${s.isRunning?'<button type="button" class="danger-btn" id="btn-stop">停止</button>':`<button type="button" class="primary-btn" id="btn-send" ${h?"disabled":""}>发送</button>`}
        </div>
        <div class="model-chip">模型 ${c(((P=s.config)==null?void 0:P.model)||"—")} ·
          <button type="button" id="btn-model">更改</button></div>
      </div>
    </section>
    ${fs()}
    ${rs()}
    ${cs()}
    ${ls()}
    ${ps()}
    ${ms()}
    ${ds()}
    ${as()}
    ${is()}
  </div>`,gs();const $=document.getElementById("chat-scroll");$&&($.scrollTop=$.scrollHeight)}function gs(){var r,d,u,g,w,h,L,$,P,y,R,A,Z,ee,te,se,ne,oe,ae,ie,re,ce,le,de,ue,me,pe,fe,ge,ve,ye,he,be,we,Se,Ee,ke,$e,Ie,Te,Pe,Le,Re,Ae,xe,Be,Ce,Me,Oe,Ue;(r=document.getElementById("btn-new"))==null||r.addEventListener("click",()=>void Je()),(d=document.getElementById("btn-new-topic"))==null||d.addEventListener("click",()=>void Je()),(u=document.getElementById("btn-summarize-new"))==null||u.addEventListener("click",()=>void Ht()),(g=document.getElementById("btn-drawer"))==null||g.addEventListener("click",()=>{const o=!s.drawerOpen;i({drawerOpen:o}),o&&T()}),(w=document.getElementById("usage-footer"))==null||w.addEventListener("click",()=>{T().then(()=>i({usagePanelOpen:!0}))}),(h=document.getElementById("usage-close"))==null||h.addEventListener("click",()=>i({usagePanelOpen:!1})),(L=document.getElementById("usage-overlay"))==null||L.addEventListener("click",o=>{o.target===o.currentTarget&&i({usagePanelOpen:!1})}),($=document.getElementById("failed-close"))==null||$.addEventListener("click",()=>i({showFailedLogs:!1})),(P=document.getElementById("failed-refresh"))==null||P.addEventListener("click",()=>void J()),(y=document.getElementById("failed-scope-session"))==null||y.addEventListener("change",o=>{const m=o.target.checked;i({failedLogsScopeSession:m}),J()}),(R=document.getElementById("failed-logs-overlay"))==null||R.addEventListener("click",o=>{o.target===o.currentTarget&&i({showFailedLogs:!1})}),(A=document.getElementById("btn-more"))==null||A.addEventListener("click",o=>{o.stopPropagation(),i({moreMenuOpen:!s.moreMenuOpen})}),(Z=document.getElementById("more-menu"))==null||Z.addEventListener("click",o=>o.stopPropagation()),document.querySelectorAll("[data-more-action]").forEach(o=>{o.addEventListener("click",()=>{const m=o.dataset.moreAction;switch(He(),m){case"settings":D();break;case"mcp":ze();break;case"capabilities":Vt();break;case"system-prompt":Qt();break;case"brief":Yt().catch(l=>i({error:l instanceof Error?l.message:String(l)}));break;case"diagnostics":pt().then(()=>b("诊断包已下载")).catch(l=>i({error:l instanceof Error?l.message:String(l)}));break;case"failed":zt();break;case"about":i({showAbout:!0});break}})}),s.moreMenuOpen&&window.setTimeout(()=>{const o=()=>{He(),document.removeEventListener("click",o)};document.addEventListener("click",o)},0),(ee=document.getElementById("about-close"))==null||ee.addEventListener("click",()=>i({showAbout:!1})),(te=document.getElementById("about-overlay"))==null||te.addEventListener("click",o=>{o.target===o.currentTarget&&i({showAbout:!1})}),(se=document.getElementById("btn-setup"))==null||se.addEventListener("click",()=>void D()),(ne=document.getElementById("btn-model"))==null||ne.addEventListener("click",()=>void D()),(oe=document.getElementById("btn-artifacts"))==null||oe.addEventListener("click",async()=>{const o=await Ye();i({artifacts:o,showArtifacts:!0,capQuery:"",capResult:""})}),(ae=document.getElementById("artifacts-close"))==null||ae.addEventListener("click",()=>i({showArtifacts:!1})),(ie=document.getElementById("artifacts-overlay"))==null||ie.addEventListener("click",o=>{o.target===o.currentTarget&&i({showArtifacts:!1})}),(re=document.getElementById("cap-search-btn"))==null||re.addEventListener("click",async()=>{var l;const o=document.getElementById("cap-query"),m=((l=o==null?void 0:o.value)==null?void 0:l.trim())||"";s={...s,capQuery:m};try{const f=await wt(m);i({capResult:f})}catch(f){i({capResult:f instanceof Error?f.message:String(f)})}}),document.querySelectorAll("[data-art-preview]").forEach(o=>{o.addEventListener("click",()=>{const m=o.dataset.artSession,l=o.dataset.artPath;m&&l&&i({preview:{sessionId:m,path:l},showArtifacts:!1})})}),document.querySelectorAll("[data-art-insert]").forEach(o=>{o.addEventListener("click",async()=>{const m=o.dataset.artSession,l=o.dataset.artPath,f=s.activeSessionId;if(!(!f||!m||!l))try{await gt(f,m,l),await U(f),b("已复制到 imports/")}catch(v){i({error:v instanceof Error?v.message:String(v)})}})}),(ce=document.getElementById("preview-close"))==null||ce.addEventListener("click",()=>i({preview:null})),(le=document.getElementById("preview-overlay"))==null||le.addEventListener("click",o=>{o.target===o.currentTarget&&i({preview:null})});const e=document.getElementById("file-input");(de=document.getElementById("btn-attach"))==null||de.addEventListener("click",()=>e==null?void 0:e.click()),e==null||e.addEventListener("change",async()=>{var m;const o=s.activeSessionId;if(!(!o||!((m=e.files)!=null&&m.length)))try{for(const l of Array.from(e.files))await yt(o,l);await U(o),e.value=""}catch(l){i({error:l instanceof Error?l.message:String(l)})}}),document.querySelectorAll("[data-rm-attach]").forEach(o=>{o.addEventListener("click",async()=>{const m=s.activeSessionId,l=o.dataset.rmAttach;!m||!l||(await ht(m,l),await U(m))})}),document.querySelectorAll("[data-use]").forEach(o=>{o.addEventListener("click",async()=>{const m=o.dataset.use;if(s.isRunning){i({error:"请先停止当前 Run 再切换会话"});return}await F(m),await I(m),await E()})}),document.querySelectorAll("[data-del]").forEach(o=>{o.addEventListener("click",async m=>{m.stopPropagation();const l=o.dataset.del;if(confirm("删除该会话？")&&(await Et(l),await E(),s.activeSessionId===l))if(s.sessions[0])await F(s.sessions[0].sessionId),await I(s.sessions[0].sessionId);else{const f=await H();await E(),await I(f.sessionId)}})});const t=document.getElementById("composer");t==null||t.addEventListener("input",()=>{s={...s,composer:t.value}}),t==null||t.addEventListener("keydown",o=>{o.key==="Enter"&&!o.shiftKey&&(o.preventDefault(),z())}),(ue=document.getElementById("btn-send"))==null||ue.addEventListener("click",()=>void z()),(me=document.getElementById("btn-stop"))==null||me.addEventListener("click",async()=>{await Tt()}),document.querySelectorAll("[data-ask-opt]").forEach(o=>{o.addEventListener("click",()=>{const m=o.dataset.askId,l=o.dataset.askOpt;i({askAnswers:{...s.askAnswers,[m]:l}})})}),document.querySelectorAll("[data-ask-input]").forEach(o=>{o.addEventListener("input",()=>{const m=o.dataset.askInput;s={...s,askAnswers:{...s.askAnswers,[m]:o.value}}})}),(pe=document.getElementById("ask-submit"))==null||pe.addEventListener("click",()=>void ts());const n=()=>i({showSettings:!1,settingsDraft:null,modelsStatus:null,modelsLoading:!1});(fe=document.getElementById("cfg-close"))==null||fe.addEventListener("click",n),(ge=document.getElementById("settings-overlay"))==null||ge.addEventListener("click",o=>{o.target===o.currentTarget&&n()}),document.querySelectorAll("[data-provider]").forEach(o=>{o.addEventListener("click",()=>{var l;const m=o.dataset.provider;m&&m!==((l=s.settingsDraft)==null?void 0:l.providerId)&&Gt(m)})});const a=o=>{s.settingsDraft&&i({settingsDraft:{...s.settingsDraft,...o}})};(ve=document.getElementById("cfg-model"))==null||ve.addEventListener("change",o=>{a({model:o.target.value})}),(ye=document.getElementById("cfg-ctx-turns"))==null||ye.addEventListener("change",o=>{a({maxContextTurns:parseInt(o.target.value,10)||0})}),(he=document.getElementById("cfg-ctx-msgs"))==null||he.addEventListener("change",o=>{a({maxContextMessages:parseInt(o.target.value,10)||0})}),(be=document.getElementById("cfg-run-turns"))==null||be.addEventListener("change",o=>{a({maxTurnsPerRun:parseInt(o.target.value,10)||0})}),(we=document.getElementById("cfg-tool-calls"))==null||we.addEventListener("change",o=>{a({maxToolCallsPerRun:parseInt(o.target.value,10)||0})}),(Se=document.getElementById("cfg-open-mcp"))==null||Se.addEventListener("click",()=>void ze()),(Ee=document.getElementById("mcp-close"))==null||Ee.addEventListener("click",()=>i({showMcp:!1})),(ke=document.getElementById("mcp-overlay"))==null||ke.addEventListener("click",o=>{o.target===o.currentTarget&&i({showMcp:!1})}),($e=document.getElementById("mcp-web-key"))==null||$e.addEventListener("input",o=>{s={...s,mcpWebSearchApiKey:o.target.value}}),(Ie=document.getElementById("mcp-web-base"))==null||Ie.addEventListener("input",o=>{s={...s,mcpWebSearchBaseUrl:o.target.value}}),(Te=document.getElementById("mcp-save-search"))==null||Te.addEventListener("click",()=>void Xt()),(Pe=document.getElementById("system-prompt-close"))==null||Pe.addEventListener("click",()=>i({showSystemPrompt:!1,systemPromptError:null})),(Le=document.getElementById("system-prompt-overlay"))==null||Le.addEventListener("click",o=>{o.target===o.currentTarget&&i({showSystemPrompt:!1,systemPromptError:null})}),(Re=document.getElementById("system-prompt-copy"))==null||Re.addEventListener("click",async()=>{if(s.systemPromptText)try{await navigator.clipboard.writeText(s.systemPromptText),b("已复制系统提示词")}catch(o){i({error:o instanceof Error?o.message:String(o)})}}),(Ae=document.getElementById("mcp-add"))==null||Ae.addEventListener("click",()=>{i({mcpServers:[...s.mcpServers,{name:"server",url:"https://",enabled:!0,headers:{}}]})}),document.querySelectorAll(".mcp-remove").forEach(o=>{o.addEventListener("click",()=>{const m=Number(o.dataset.mcpIndex);i({mcpServers:s.mcpServers.filter((l,f)=>f!==m)})})}),(xe=document.getElementById("mcp-save"))==null||xe.addEventListener("click",async()=>{const o=document.querySelectorAll(".mcp-card"),m=[];o.forEach((l,f)=>{var Ne,je,De,Fe;const v=s.mcpServers[f],x=((Ne=l.querySelector(".mcp-name"))==null?void 0:Ne.value.trim())||"",B=((je=l.querySelector(".mcp-url"))==null?void 0:je.value.trim())||"",C=((De=l.querySelector(".mcp-enabled"))==null?void 0:De.checked)??!0;let S={};try{S=JSON.parse(((Fe=l.querySelector(".mcp-headers"))==null?void 0:Fe.value)||"{}")}catch{}m.push({name:x,url:B,enabled:C,headers:S,toolCount:v==null?void 0:v.toolCount,lastListedAt:v==null?void 0:v.lastListedAt})});try{const l=await dt(m),{servers:f}=await Ve(),v=l.warnings.length?` · ${l.warnings.join("; ")}`:"";i({mcpServers:f,mcpStatus:`已索引 ${l.toolCount} 个 MCP 工具${v}`}),b("MCP 已保存")}catch(l){i({error:l instanceof Error?l.message:String(l)})}}),document.querySelectorAll(".file-link").forEach(o=>{o.addEventListener("click",()=>{const m=o.dataset.filePath,l=s.activeSessionId;!m||!l||(tt(m)?i({preview:{sessionId:l,path:m}}):b(m))})}),(Be=document.getElementById("cfg-show-tools"))==null||Be.addEventListener("click",async()=>{try{const{tools:o}=await ut();i({showTools:!0,toolsList:o})}catch(o){i({error:o instanceof Error?o.message:String(o)})}}),(Ce=document.getElementById("tools-close"))==null||Ce.addEventListener("click",()=>i({showTools:!1})),(Me=document.getElementById("tools-overlay"))==null||Me.addEventListener("click",o=>{o.target===o.currentTarget&&i({showTools:!1})}),(Oe=document.getElementById("cfg-refresh-models"))==null||Oe.addEventListener("click",()=>{const o=s.settingsDraft;o&&Y(o,!0)}),(Ue=document.getElementById("cfg-save"))==null||Ue.addEventListener("click",async()=>{const o=s.settingsDraft;if(!o)return;const m=document.getElementById("cfg-key").value,l=document.getElementById("cfg-base").value,f=document.getElementById("cfg-model").value,v=parseInt(document.getElementById("cfg-ctx-turns").value,10),x=parseInt(document.getElementById("cfg-ctx-msgs").value,10),B=parseInt(document.getElementById("cfg-run-turns").value,10),C=parseInt(document.getElementById("cfg-tool-calls").value,10);try{const S=await Xe({apiKey:m||void 0,baseUrl:l||void 0,model:f||void 0,providerId:o.providerId,maxContextTurns:Number.isFinite(v)?v:0,maxContextMessages:Number.isFinite(x)?x:0,maxTurnsPerRun:Number.isFinite(B)?B:0,maxToolCallsPerRun:Number.isFinite(C)?C:0});i({config:S,showSettings:!1,settingsDraft:null,modelsStatus:null,error:null}),b("设置已保存")}catch(S){i({error:S instanceof Error?S.message:String(S)})}})}rt();Zt().catch(e=>{i({error:e instanceof Error?e.message:String(e)})});window.addEventListener("beforeunload",()=>O==null?void 0:O());
