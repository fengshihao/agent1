(function(){const t=document.createElement("link").relList;if(t&&t.supports&&t.supports("modulepreload"))return;for(const i of document.querySelectorAll('link[rel="modulepreload"]'))a(i);new MutationObserver(i=>{for(const c of i)if(c.type==="childList")for(const d of c.addedNodes)d.tagName==="LINK"&&d.rel==="modulepreload"&&a(d)}).observe(document,{childList:!0,subtree:!0});function n(i){const c={};return i.integrity&&(c.integrity=i.integrity),i.referrerPolicy&&(c.referrerPolicy=i.referrerPolicy),i.crossOrigin==="use-credentials"?c.credentials="include":i.crossOrigin==="anonymous"?c.credentials="omit":c.credentials="same-origin",c}function a(i){if(i.ep)return;i.ep=!0;const c=n(i);fetch(i.href,c)}})();async function m(e){const t=await e.json();if(!e.ok)throw new Error(t.error||e.statusText);return t}async function Ve(){return m(await fetch("/api/config"))}async function Ye(e){return m(await fetch("/api/config",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(e)}))}async function dt(){return m(await fetch("/api/usage"))}async function ut(e,t=!1){const n=new URLSearchParams({provider:e});return t&&n.set("refresh","1"),m(await fetch(`/api/models?${n}`))}async function Ze(){return m(await fetch("/api/mcp/servers"))}async function pt(e){return m(await fetch("/api/mcp/servers",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({servers:e})}))}async function mt(){return m(await fetch("/api/tools"))}async function ft(e){return m(await fetch(`/api/sessions/${encodeURIComponent(e)}/system-prompt`))}async function gt(){var c,d;const e=await fetch("/api/diagnostics/export");if(!e.ok)throw new Error("诊断包导出失败");const t=await e.blob(),n=((d=(c=e.headers.get("Content-Disposition"))==null?void 0:c.match(/filename="([^"]+)"/))==null?void 0:d[1])??`agent1-diag-${Date.now()}.zip`,a=URL.createObjectURL(t),i=document.createElement("a");i.href=a,i.download=n,i.click(),URL.revokeObjectURL(a)}async function vt(){return(await m(await fetch("/api/artifacts"))).artifacts}async function bt(e,t,n){return m(await fetch(`/api/sessions/${encodeURIComponent(e)}/artifacts/import`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({sourceSessionId:t,workspaceRelativePath:n})}))}async function ht(e){return(await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`))).files}async function yt(e,t){const n=new FormData;return n.append("file",t),m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`,{method:"POST",body:n}))}async function wt(e,t){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments/${encodeURIComponent(t)}`,{method:"DELETE"}))}function $t(e,t){const n=t.split("/").map(a=>encodeURIComponent(a)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${n}`}async function St(e,t,n){const a=new URLSearchParams;return a.set("q",e),a.set("limit",String(n)),t.length&&a.set("kinds",t.join(",")),m(await fetch(`/api/capabilities?${a.toString()}`))}async function Et(){return m(await fetch("/api/sessions"))}async function W(){return m(await fetch("/api/sessions",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"}))}async function j(e){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/use`,{method:"POST",body:"{}"}))}async function kt(e){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}`,{method:"DELETE"}))}async function It(){return m(await fetch("/api/about"))}async function Tt(e){return m(await fetch(`/api/sessions/${encodeURIComponent(e)}/brief`))}async function Lt(e){const t=e?`?sessionId=${encodeURIComponent(e)}`:"";return(await m(await fetch(`/api/logs/failed${t}`))).items}async function et(e){return(await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/transcript`))).messages}async function Pt(e,t){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/messages`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({text:t})}))}async function At(){await m(await fetch("/api/run/abort",{method:"POST",body:"{}"}))}function Rt(e){const t=new EventSource("/api/events");return t.onmessage=n=>{try{e(JSON.parse(n.data))}catch{}},()=>t.close()}const xt="ask_user",Bt=["等待用户","waiting","ask_user"];function Ct(e){try{const n=(JSON.parse(e.argumentsJson||"{}").questions||[]).map((a,i)=>({id:a.id||`q${i}`,prompt:a.prompt||a.question||"",options:a.options,allowFreeText:a.allowFreeText??a.allow_free_text??!0})).filter(a=>a.prompt);return n.length===0?null:{questions:n}}catch{return null}}function tt(e){if(!e.length)return null;for(let t=e.length-1;t>=0;t--){const n=e[t];if(n.role==="user")return null;if(n.role==="assistant"){for(const a of n.toolCalls||[])if(a.name===xt)return Ct(a)}if(n.role==="toolResult"){const a=n.content||"";if(!Bt.some(c=>a.toLowerCase().includes(c.toLowerCase())))return null}}return null}function Mt(e,t){return e.questions.map(a=>{const i=(t[a.id]||"").trim();return`${a.prompt}
→ ${i||"(未答)"}`}).join(`

`)}function Ot(e,t,n,a,i){if(t||i||n.trim()||a.trim())return!1;const c=Ut(e);return(c==null?void 0:c.role)==="assistant"}function Ut(e){return e.filter(t=>t.role==="user"||t.role==="assistant").at(-1)}function Nt(){return{sessions:[],activeSessionId:null,config:null,lines:[],streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,pendingAsk:null,askAnswers:{},error:null,showSettings:!1,drawerOpen:!0,composer:"",usage:null,usagePanelOpen:!1,toast:null,attachments:[],artifacts:[],showArtifacts:!1,preview:null,showCapabilities:!1,capQuery:"",capSelectedKinds:[],capLimit:8,capBusy:!1,capError:null,capSearched:!1,capModelText:"",capHostPlatform:"",capHiddenHits:[],capDisabledMcp:[],modelOptions:[],settingsDraft:null,modelsLoading:!1,modelsStatus:null,showTools:!1,toolsList:[],lastRunTokenSummary:null,showMcp:!1,mcpServers:[],mcpStatus:null,showFailedLogs:!1,failedLogs:[],failedLogsScopeSession:!0,moreMenuOpen:!1,showAbout:!1,aboutLoading:!1,aboutInfo:null,showSystemPrompt:!1,systemPromptBusy:!1,systemPromptError:null,systemPromptText:"",systemPromptTools:[],mcpWebSearchBaseUrl:"https://api.tavily.com",mcpWebSearchApiKey:""}}function st(e){const t=e.toLowerCase();return t.endsWith(".html")||t.endsWith(".htm")}function jt(e){return e.isRunning||e.streamingText.trim()||e.streamingReasoning.trim()||e.tools.length>0||e.pendingAsk?!1:!e.lines.some(t=>t.role==="user"||t.role==="assistant")}function nt(e){const t=[];return e.forEach((n,a)=>{var i;if(n.role==="user"){t.push({id:`u-${a}`,role:"user",content:n.content});return}if(n.role==="assistant"){(i=n.content)!=null&&i.trim()&&t.push({id:`a-${a}`,role:"assistant",content:n.content});for(const c of n.toolCalls||[])t.push({id:`t-${c.id}`,role:"tool",content:c.argsPreview||c.argumentsJson||"",toolName:c.name});return}if(n.role==="toolResult"){t.push({id:`tr-${a}`,role:"tool",content:n.content,toolName:"result",error:!!n.error});return}n.role}),t}const Dt=new Set(["png","jpg","jpeg","webp","gif"]);function D(e){var n;const t=((n=e.split(".").pop())==null?void 0:n.toLowerCase())??"";return Dt.has(t)}function at(e){const t=/!\[[^\]]*]\(([^)]+)\)/g,n=[];let a;for(;(a=t.exec(e))!==null;){const i=a[1].trim();i&&!i.startsWith("http://")&&!i.startsWith("https://")&&n.push(J(i))}return n}function J(e){return e.replace(/^\.\//,"").replace(/^workspace\//,"")}function _t(e){const t=[],n=/```[\w-]*\n?([\s\S]*?)```/g;let a=0,i;for(;(i=n.exec(e))!==null;)i.index>a&&t.push({kind:"text",text:e.slice(a,i.index)}),t.push({kind:"code",text:i[1]??""}),a=i.index+i[0].length;return a<e.length&&t.push({kind:"text",text:e.slice(a)}),t.length?t:[{kind:"text",text:e}]}function _(e,t){return e?_t(e).map(n=>n.kind==="code"?`<pre class="bubble-code"><code>${y(n.text.trimEnd())}</code></pre>`:Ft(n.text,t)).join(""):""}function Ft(e,t){const n=/!?\[([^\]]*)]\(([^)]+)\)/g;let a="",i=0,c;for(;(c=n.exec(e))!==null;){a+=_e(e.slice(i,c.index));const d=c[1],f=c[2].trim(),b=c[0].startsWith("!");if(t&&!f.startsWith("http://")&&!f.startsWith("https://")){const v=J(f);b&&D(v)?a+=`<figure class="bubble-image"><img src="${ot(t,v)}" alt="${F(v)}" loading="lazy"/></figure>`:b?a+=y(c[0]):a+=`<button type="button" class="file-link" data-file-path="${F(v)}">${y(d||v)}</button>`}else a+=y(c[0]);i=c.index+c[0].length}return a+=_e(e.slice(i)),a}function _e(e){if(!e)return"";const t=e.split(`
`);let n="",a=!1;for(const i of t){const c=i.trimEnd(),d=c.match(/^[-—]\s+(.*)$/);if(d){a||(n+='<ul class="bubble-list">',a=!0),n+=`<li>${Fe(d[1])}</li>`;continue}a&&(n+="</ul>",a=!1),c===""?n+='<div class="bubble-gap"></div>':n+=`<p class="bubble-p">${Fe(c)}</p>`}return a&&(n+="</ul>"),n}function Fe(e){const t=e.split(/`([^`\n]+)`/);let n="";for(let a=0;a<t.length;a++)a%2===0?n+=Kt(t[a]):n+=`<code class="bubble-inline-code">${y(t[a])}</code>`;return n}function Kt(e){const t=/\*\*(.+?)\*\*/g;let n="",a=0,i;for(;(i=t.exec(e))!==null;)n+=y(e.slice(a,i.index)),n+=`<strong>${y(i[1])}</strong>`,a=i.index+i[0].length;return n+=y(e.slice(a)),n}function ot(e,t){const n=t.split("/").map(a=>encodeURIComponent(a)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${n}`}function y(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}function F(e){return y(e).replaceAll("'","&#39;")}function qt(e){const t=at(e).filter(D),n=new Set,a=[];for(const d of t)n.has(d)||(n.add(d),a.push(d));const i=/(?:imports|out|tmp|staging)[/][\w./-]+\.(?:png|jpe?g|webp|gif)/gi;let c;for(;(c=i.exec(e))!==null;){const d=J(c[0]);D(d)&&!n.has(d)&&(n.add(d),a.push(d))}return a}function Ke(e,t){const n=new Set(at(e));return qt(e).filter(a=>!n.has(a)).map(a=>`<figure class="bubble-image"><img src="${ot(t,a)}" alt="${F(a)}" loading="lazy"/></figure>`).join("")}let C=0,M=0,z=0;function Ht(){C=0,M=0,z=0}function Wt(e){C+=Number(e.inputTokens)||0,M+=Number(e.outputTokens)||0;const t=e.cachedTokens;t!=null&&(z+=Number(t)||0)}function qe(e){const t=C>0||M>0;return!e&&!t?null:{inputTokens:C,outputTokens:M,cachedTokens:z,failed:e}}function Jt(e){const t=[`输入 ${He(e.inputTokens)}`,`输出 ${He(e.outputTokens)}`];if(e.inputTokens>0){const n=Math.min(100,Math.round(e.cachedTokens*100/e.inputTokens));t.push(`缓存命中 ${n}%`)}return e.failed&&t.push("未完成"),t.join(" · ")}function He(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,n=Math.trunc(t);return t===n?`${n}k`:`${t.toFixed(1)}k`}const K=[{id:"dashscope",label:"通义",baseUrl:"https://dashscope.aliyuncs.com/compatible-mode/v1",defaultModel:"qwen3.7-flash"},{id:"deepseek",label:"DeepSeek",baseUrl:"https://api.deepseek.com",defaultModel:"deepseek-flash"},{id:"zhipu_coding",label:"智谱 Coding",baseUrl:"https://open.bigmodel.cn/api/coding/paas/v4",defaultModel:"glm-5.3"}];function zt(e){const t=e.toLowerCase();return t.includes("deepseek.com")?"deepseek":t.includes("bigmodel.cn")?"zhipu_coding":"dashscope"}function G(e){return K.find(t=>t.id===e)??K[0]}function A(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,n=Math.trunc(t);return t===n?`${n}k`:`${t.toFixed(1)}k`}function Gt(e){if(!e||!e.hasUsage)return{headline:"累计用量",detail:"暂无 usage 记录"};const t=`约 ${A(e.totalTokens)} tokens`,n=e.cacheHitPercent,a=n!=null?`输入 ${A(e.inputTokens)} · 缓存 ${n}%`:`输入 ${A(e.inputTokens)} · 输出 ${A(e.outputTokens)}`;return{headline:t,detail:a}}function Qt(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;")}function Xt(e){const t=[`输入 ${e.inputTokens.toLocaleString()}`,`输出 ${e.outputTokens.toLocaleString()}`,`缓存 ${e.cachedTokens.toLocaleString()}`,`usage 事件 ${e.usageEvents}`];let n="";return e.byModel.length&&(n="<ul>"+e.byModel.map(a=>`<li><strong>${Qt(a.modelId||"（未知模型）")}</strong>：入 ${a.inputTokens.toLocaleString()} / 出 ${a.outputTokens.toLocaleString()}</li>`).join("")+"</ul>"),`<p>${t.join(" · ")}</p>${n}`}const Vt=[{id:"caps",label:"Caps"},{id:"builtin",label:"内置"},{id:"weizhi_package",label:"官方包"},{id:"catalog_script",label:"catalog 脚本"},{id:"catalog_lib",label:"catalog 库"},{id:"native",label:"native"},{id:"skill",label:"Skill"},{id:"mcp",label:"MCP"},{id:"bridge_tool",label:"桥接"},{id:"agent_tool",label:"外层工具"}],it=20;function Yt(e){return e==="listed_but_unusable"?"已索引，当前不可用":e==="platform_hidden"?"当前平台不可用":"可用"}const rt=document.getElementById("app");if(!rt)throw new Error("#app missing");let s=Nt(),x=null,O,U;function r(e){s={...s,...e},ct()}function l(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}async function E(){const e=await Et();r({sessions:e.sessions.slice().sort((t,n)=>n.updatedAt.localeCompare(t.updatedAt)),activeSessionId:e.activeSessionId??s.activeSessionId,isRunning:e.runInProgress})}async function L(){try{const e=await dt();r({usage:e})}catch{}}async function q(){const e=s.failedLogsScopeSession&&s.activeSessionId?s.activeSessionId:null,t=await Lt(e);r({failedLogs:t})}async function Zt(){r({showFailedLogs:!0});try{await q()}catch(e){r({error:e instanceof Error?e.message:String(e)})}}function S(e){r({toast:e}),window.setTimeout(()=>{s.toast===e&&r({toast:null})},2200)}async function We(){if(s.isRunning){r({error:"请先停止当前 Run"});return}if(s.activeSessionId&&jt(s)){S("已是新会话");return}const e=await W();await E(),await T(e.sessionId),await L(),r({drawerOpen:!1})}function lt(){const e=s.config,t=(e==null?void 0:e.baseUrl)||"",n=(e==null?void 0:e.providerId)||zt(t);return{providerId:n,baseUrl:t,model:(e==null?void 0:e.model)||G(n).defaultModel,maxContextTurns:(e==null?void 0:e.maxContextTurns)??6,maxContextMessages:(e==null?void 0:e.maxContextMessages)??0,maxTurnsPerRun:(e==null?void 0:e.maxTurnsPerRun)??24,maxToolCallsPerRun:(e==null?void 0:e.maxToolCallsPerRun)??48}}async function Q(e,t){var n,a;r({modelsLoading:!0});try{const{models:i,source:c}=await ut(e.providerId,t);let d=e.model;if(!i.some(f=>f.modelId===d)){const f=G(e.providerId);d=((n=i.find(b=>b.modelId===f.defaultModel))==null?void 0:n.modelId)??((a=i[0])==null?void 0:a.modelId)??d}r({modelOptions:i,modelsLoading:!1,settingsDraft:{...e,model:d},modelsStatus:t?`已从网络更新 · ${i.length} 个模型`:`共 ${i.length} 个模型`})}catch(i){r({modelsLoading:!1,error:i instanceof Error?i.message:"模型列表加载失败"})}}function es(e){const t=G(e),n={...s.settingsDraft??lt(),providerId:e,baseUrl:t.baseUrl,model:t.defaultModel};r({settingsDraft:n,error:null}),Q(n,!1)}async function N(){const e=lt();r({showSettings:!0,settingsDraft:e,modelOptions:[],error:null,moreMenuOpen:!1,showMcp:!1,showSystemPrompt:!1}),await Q(e,!1)}async function Je(){try{const[{servers:e},t]=await Promise.all([Ze(),Ve()]);r({config:t,showMcp:!0,mcpServers:e,mcpStatus:null,showSettings:!1,showSystemPrompt:!1,moreMenuOpen:!1,mcpWebSearchBaseUrl:t.webSearchBaseUrl||"https://api.tavily.com",mcpWebSearchApiKey:""})}catch(e){r({error:e instanceof Error?e.message:String(e)})}}async function ts(){const e=s.activeSessionId;if(!e){r({showSystemPrompt:!0,showSettings:!1,showMcp:!1,systemPromptBusy:!1,systemPromptError:"先打开或新建一个会话，再查看发给模型的系统提示词。",systemPromptText:"",systemPromptTools:[],moreMenuOpen:!1});return}r({showSystemPrompt:!0,showSettings:!1,showMcp:!1,systemPromptBusy:!0,systemPromptError:null,moreMenuOpen:!1});try{const t=await ft(e);r({showSystemPrompt:!0,systemPromptBusy:!1,systemPromptError:null,systemPromptText:t.prompt||"",systemPromptTools:t.tools||[]})}catch(t){r({showSystemPrompt:!0,systemPromptBusy:!1,systemPromptError:t instanceof Error?t.message:String(t)})}}function ss(){const e=document.querySelectorAll(".mcp-card"),t=[];return e.forEach((n,a)=>{var v,w,g,$;const i=s.mcpServers[a],c=((v=n.querySelector(".mcp-name"))==null?void 0:v.value.trim())||"",d=((w=n.querySelector(".mcp-url"))==null?void 0:w.value.trim())||"",f=((g=n.querySelector(".mcp-enabled"))==null?void 0:g.checked)??!0;let b={};try{b=JSON.parse((($=n.querySelector(".mcp-headers"))==null?void 0:$.value)||"{}")}catch{}t.push({name:c,url:d,enabled:f,headers:b,toolCount:i==null?void 0:i.toolCount,lastListedAt:i==null?void 0:i.lastListedAt})}),t}function R(){s.showMcp&&(O&&clearTimeout(O),O=setTimeout(()=>void ns(),700))}function ze(){s.showMcp&&(U&&clearTimeout(U),U=setTimeout(()=>void as(),700))}async function ns(){if(!s.showMcp)return;const e=ss();try{const t=await pt(e),{servers:n}=await Ze(),a=t.warnings.length?` · ${t.warnings.join("; ")}`:"";r({mcpServers:n,mcpStatus:`已自动保存 · 索引 ${t.toolCount} 个 MCP 工具${a}`})}catch(t){r({error:t instanceof Error?t.message:String(t)})}}async function as(){var i,c;const e=document.getElementById("mcp-web-key"),t=document.getElementById("mcp-web-base"),n=((i=e==null?void 0:e.value)==null?void 0:i.trim())||void 0,a=((c=t==null?void 0:t.value)==null?void 0:c.trim())||void 0;try{const d=await Ye({webSearchApiKey:n,webSearchBaseUrl:a});r({config:d,mcpWebSearchApiKey:(e==null?void 0:e.value)??"",mcpWebSearchBaseUrl:d.webSearchBaseUrl||a||"https://api.tavily.com",mcpStatus:d.hasWebSearch?"已自动保存 · 联网搜索已配置":"已自动保存 · 尚未配置 Key（web_search 不会注册）"})}catch(d){r({error:d instanceof Error?d.message:String(d)})}}function os(){r({showCapabilities:!0,showArtifacts:!1,moreMenuOpen:!1,capError:null})}async function is(){const e=document.getElementById("cap-query"),t=((e==null?void 0:e.value)??s.capQuery).trim();if(!(!t||s.capBusy)){r({capQuery:t,capBusy:!0,capError:null});try{const n=await St(t,s.capSelectedKinds,s.capLimit);r({capBusy:!1,capSearched:!0,capModelText:n.modelText,capHostPlatform:n.hostPlatform,capLimit:n.limit,capHiddenHits:n.hiddenByPlatform,capDisabledMcp:n.disabledMcp})}catch(n){r({capBusy:!1,capSearched:!0,capError:n instanceof Error?n.message:String(n)})}}}async function rs(){const e=s.activeSessionId;if(!e)return;const{text:t}=await Tt(e);await navigator.clipboard.writeText(t),S("简报已复制到剪贴板")}function Ge(){r({moreMenuOpen:!1})}async function B(e){try{const t=await ht(e);r({attachments:t})}catch{r({attachments:[]})}}async function T(e){const t=await et(e),n=tt(t);r({activeSessionId:e,lines:nt(t),pendingAsk:n,askAnswers:{},streamingText:"",streamingReasoning:"",tools:[],error:null}),await B(e)}async function ls(){const[e]=await Promise.all([Ve(),E()]);r({config:e});let t=s.activeSessionId;t||(s.sessions.length===0?(t=(await W()).sessionId,await E()):(t=s.sessions[0].sessionId,await j(t))),await T(t),await L(),x=Rt(cs)}function cs(e){if(!(e.sessionId&&s.activeSessionId&&e.sessionId!==s.activeSessionId))switch(e.type){case"AGENT_START":Ht(),r({isRunning:!0,streamingText:"",streamingReasoning:"",tools:[],error:null,lastRunTokenSummary:null});break;case"USAGE":Wt(e.payload);break;case"MESSAGE_UPDATE":r({streamingText:s.streamingText+String(e.payload.delta||""),isRunning:!0});break;case"REASONING_UPDATE":r({streamingReasoning:s.streamingReasoning+String(e.payload.delta||""),isRunning:!0});break;case"MESSAGE_RESET":r({streamingText:"",streamingReasoning:""});break;case"TOOL_EXECUTION_START":{const t=e.payload.toolCall,n={id:t.id||`tool-${Date.now()}`,toolCallId:t.id||"",toolName:t.name||"tool",argsPreview:t.argsPreview||"",running:!0};r({tools:[...s.tools,n],isRunning:!0});break}case"TOOL_EXECUTION_END":{const t=String(e.payload.toolCallId||""),n=s.tools.map(i=>i.toolCallId===t?{...i,running:!1,error:!!(e.payload.error||e.payload.toolError),resultSummary:String(e.payload.resultText||"")}:i),a=!!e.payload.stopRunWaitingUser;r({tools:n,isRunning:!a&&s.isRunning});break}case"AGENT_ERROR":r({error:String(e.payload.message||"运行出错"),isRunning:!1,lastRunTokenSummary:qe(!0)}),Qe();break;case"AGENT_END":r({isRunning:!1,lastRunTokenSummary:qe(!1)}),Qe();break}}async function Qe(){if(!s.activeSessionId)return;const e=s.lastRunTokenSummary;try{const t=await et(s.activeSessionId);r({lines:nt(t),pendingAsk:tt(t),streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,lastRunTokenSummary:e}),await E(),await L()}catch(t){r({error:t instanceof Error?t.message:String(t)})}}async function H(){var a;const e=s.composer.trim();if(!e||!s.activeSessionId||s.isRunning||s.pendingAsk)return;if(!((a=s.config)!=null&&a.hasApiKey)){r({showSettings:!0,error:"请先配置 API Key"});return}const t={id:`local-${Date.now()}`,role:"user",content:e};r({lines:[...s.lines,t],composer:"",isRunning:!0,streamingText:"",tools:[],error:null,lastRunTokenSummary:null});let n=e;if(s.attachments.length>0){const i=s.attachments.map(c=>`- ${c.workspaceRelativePath}`);n+=`

[用户附件]
${i.join(`
`)}`}try{await Pt(s.activeSessionId,n)}catch(i){r({isRunning:!1,error:i instanceof Error?i.message:String(i)})}}async function ds(){if(!s.pendingAsk||!s.activeSessionId)return;const e=Mt(s.pendingAsk,s.askAnswers);r({pendingAsk:null,composer:e}),await H()}function us(e,t){if(e.role==="tool"){const a=t?_(e.content,t)+Ke(e.content,t):l(e.content);return`<details class="tool-card"><summary>⚙ ${l(e.toolName||"tool")}</summary>
      <div class="tool-row ${e.error?"err":""}">${a}</div></details>`}const n=t?_(e.content,t)+Ke(e.content,t):l(e.content);return`<div class="bubble ${e.role}"><div class="bubble-text">${n}</div></div>`}function ps(){if(!s.tools.length&&!s.streamingText&&!s.streamingReasoning)return"";const e=s.tools.map(n=>{const a=n.running?"进行中…":n.error?"失败":"完成";return`<div class="tool-row ${n.error?"err":""}"><strong>${l(n.toolName)}</strong> · ${a}
        ${n.argsPreview?`<div>${l(n.argsPreview)}</div>`:""}
        ${n.resultSummary?`<div>${l(n.resultSummary)}</div>`:""}
      </div>`}).join("");return`<details class="tool-card" open><summary>思考与执行</summary>${[s.streamingReasoning?`<div class="tool-row">思考：${l(s.streamingReasoning)}</div>`:"",e].join("")}</details>
    ${s.streamingText?`<div class="bubble assistant">${l(s.streamingText)}</div>`:""}`}function ms(){return s.pendingAsk?`<div class="ask-panel"><h3>请回答后继续</h3>${s.pendingAsk.questions.map(t=>{const n=(t.options||[]).map(a=>`<button type="button" class="chip ${s.askAnswers[t.id]===a?"selected":""}" data-ask-id="${l(t.id)}" data-ask-opt="${l(a)}">${l(a)}</button>`).join("");return`<div class="ask-q">
        <label>${l(t.prompt)}</label>
        ${n?`<div class="ask-options">${n}</div>`:""}
        <input data-ask-input="${l(t.id)}" value="${l(s.askAnswers[t.id]||"")}" placeholder="你的回答" />
      </div>`}).join("")}
    <button type="button" class="primary-btn" id="ask-submit">提交回答</button></div>`:""}function fs(e){const t=e.title||e.id;return`<div class="cap-hit-card">
    <div class="cap-hit-title">[${l(e.kind)}] ${l(t)}</div>
    <div class="muted small">${l(Yt(e.availability))}</div>
    ${e.summary?`<div>${l(e.summary)}</div>`:""}
  </div>`}function gs(){if(!s.showCapabilities)return"";const e=Vt.map(({id:a,label:i})=>`<button type="button" class="provider-chip cap-kind-chip ${s.capSelectedKinds.includes(a)?"active":""}" data-cap-kind="${a}">${l(i)}</button>`).join(""),t=s.capHostPlatform?`（平台 ${l(s.capHostPlatform)}）`:"",n=s.capSearched?[s.capError?`<p class="error-inline">${l(s.capError)}</p>`:"",s.capError?"":`<h3 class="cap-section-title">模型看到的正文${t}</h3>
            <pre class="cap-result">${l(s.capModelText||"（空）")}</pre>`,s.capHiddenHits.length?`<h3 class="cap-section-title">当前平台搜不到</h3>
            <p class="muted">索引里有这些条目，但宿主平台过滤后不会出现在上面的正文里。</p>
            ${s.capHiddenHits.map(fs).join("")}`:"",s.capDisabledMcp.length?`<h3 class="cap-section-title">未启用的 MCP</h3>
            <p class="muted">未启用的服务器不会写入能力索引，模型的 find_caps 也找不到。</p>
            ${s.capDisabledMcp.map(a=>`<div class="cap-hit-card"><strong>${l(a.name)}</strong><div class="muted small">${l(a.url)}</div><div>${l(a.description)}</div></div>`).join("")}`:""].join(""):"";return`<div class="overlay settings-overlay" id="capabilities-overlay">
    <div class="overlay-panel settings-panel wide-panel">
      <header class="settings-header">
        <h2>能力检索</h2>
        <button type="button" class="icon-btn" id="capabilities-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <div class="cap-search">
          <input id="cap-query" placeholder="搜索" value="${l(s.capQuery)}" />
          <button type="button" class="primary-btn" id="cap-search-btn" ${s.capBusy?"disabled":""}>${s.capBusy?"…":"搜索"}</button>
        </div>
        <div class="cap-kind-row">${e}</div>
        <div class="cap-limit-row">
          <button type="button" class="icon-btn" id="cap-limit-down" ${s.capLimit<=1||s.capBusy?"disabled":""}>−</button>
          <span>${s.capLimit}</span>
          <button type="button" class="icon-btn" id="cap-limit-up" ${s.capLimit>=it||s.capBusy?"disabled":""}>+</button>
        </div>
        ${n}
      </div>
    </div>
  </div>`}function vs(){return s.showArtifacts?`<div class="overlay settings-overlay" id="artifacts-overlay">
    <div class="overlay-panel settings-panel wide-panel">
      <header class="settings-header">
        <h2>产物库</h2>
        <button type="button" class="icon-btn" id="artifacts-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <p class="muted">workspace 内 AI 产出（不含 imports/、tmp/）</p>
        <ul class="artifact-list">${s.artifacts.map(t=>{const a=st(t.workspaceRelativePath)?`<button type="button" class="link-btn" data-art-preview="1" data-art-session="${l(t.sessionId)}" data-art-path="${l(t.workspaceRelativePath)}">预览</button>`:"",i=s.activeSessionId?`<button type="button" class="link-btn" data-art-insert="1" data-art-session="${l(t.sessionId)}" data-art-path="${l(t.workspaceRelativePath)}">插入当前会话</button>`:"";return`<li><strong>${l(t.fileName)}</strong>
        <span class="muted"> · ${l(t.sessionId.slice(0,8))} · ${Math.round(t.sizeBytes/1024)}KB</span>
        <div class="artifact-actions">${a} ${i}</div></li>`}).join("")||"<li class='muted'>暂无产物</li>"}</ul>
      </div>
    </div>
  </div>`:""}function bs(){if(!s.preview)return"";const e=$t(s.preview.sessionId,s.preview.path);return`<div class="overlay preview-overlay" id="preview-overlay">
    <div class="preview-panel">
      <div class="preview-toolbar">
        <span>${l(s.preview.path)}</span>
        <button type="button" class="icon-btn" id="preview-close">✕</button>
      </div>
      <iframe class="preview-frame" src="${e}" title="preview"></iframe>
    </div>
  </div>`}function hs(){if(!s.showMcp)return"";const e=s.mcpServers.map((n,a)=>{const i=JSON.stringify(n.headers||{});return`<div class="mcp-card" data-mcp-index="${a}">
        <div class="field-row">
          <div class="field"><label>名称</label><input class="settings-control mcp-name" value="${l(n.name)}" /></div>
          <label class="mcp-enable"><input type="checkbox" class="mcp-enabled" ${n.enabled?"checked":""}/> 启用</label>
        </div>
        <div class="field"><label>URL</label><input class="settings-control mcp-url" value="${l(n.url)}" /></div>
        <div class="field"><label>Headers (JSON)</label><input class="settings-control mcp-headers" value="${l(i)}" /></div>
        <p class="settings-hint">${n.toolCount?`${n.toolCount} 个工具`:"保存后尝试索引工具"}</p>
        <button type="button" class="link-btn mcp-remove" data-mcp-index="${a}">删除</button>
      </div>`}).join(""),t=s.config;return`<div class="overlay settings-overlay" id="mcp-overlay">
    <div class="overlay-panel settings-panel">
      <header class="settings-header">
        <h2>MCP 与联网搜索</h2>
        <button type="button" class="icon-btn" id="mcp-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <p class="muted">添加服务器后，助手用 find_caps 查找接口，再在脚本里调用 $mcp.名称.工具。修改后将自动保存并刷新索引。</p>
        ${s.mcpStatus?`<p class="settings-hint">${l(s.mcpStatus)}</p>`:""}
        <div id="mcp-list">${e}</div>
        <button type="button" class="link-btn" id="mcp-add">添加服务器</button>
        <section class="settings-section">
          <h3>联网搜索（Tavily）</h3>
          <div class="field">
            <label for="mcp-web-key">Web Search API Key</label>
            <input id="mcp-web-key" class="settings-control" type="password" autocomplete="off"
              value="${l(s.mcpWebSearchApiKey)}"
              placeholder="${t!=null&&t.hasWebSearch?"已配置，留空不修改":"tvly-…"}" />
          </div>
          <div class="field">
            <label for="mcp-web-base">Tavily Base URL</label>
            <input id="mcp-web-base" class="settings-control" value="${l(s.mcpWebSearchBaseUrl)}" />
          </div>
        </section>
      </div>
    </div>
  </div>`}function ys(){if(!s.showSystemPrompt)return"";const e=s.systemPromptTools.map(n=>{const a=n.description?`<p class="muted">${l(n.description)}</p>`:"",i=n.parametersSchema?`<pre class="prompt-schema">${l(n.parametersSchema)}</pre>`:"";return`<details class="prompt-tool">
        <summary>${l(n.name)}</summary>
        ${a}${i}
      </details>`}).join(""),t=s.systemPromptBusy?'<p class="muted">正在读取系统提示词…</p>':[s.systemPromptError?`<p class="error-inline">${l(s.systemPromptError)}</p>`:"",s.systemPromptText?`<div class="prompt-body">${_(s.systemPromptText,null)}</div>`:s.systemPromptError?"":'<p class="muted">当前会话尚未装入系统提示词。</p>',s.systemPromptTools.length?`<h3 class="prompt-tools-title">工具 ${s.systemPromptTools.length}</h3>${e}`:""].join("");return`<div class="overlay settings-overlay" id="system-prompt-overlay">
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
  </div>`}function ws(){return s.showTools?`<div class="overlay" id="tools-overlay">
    <div class="overlay-panel usage-panel">
      <h2>Agent 工具</h2>
      <ul class="tools-list">${s.toolsList.map(t=>`<li><strong>${l(t.name)}</strong><span class="muted">${l(t.description)}</span></li>`).join("")||"<li class='muted'>无</li>"}</ul>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="tools-close">关闭</button>
      </div>
    </div>
  </div>`:""}function $s(){if(!s.showAbout)return"";const e=s.aboutInfo,t=s.aboutLoading||!e?'<p class="muted">加载中…</p>':(()=>{const n=e.versionCode>0?`版本 ${l(e.versionName)}（${e.versionCode}）`:`版本 ${l(e.versionName)}`,a=e.dependencies.map(i=>`<div class="about-row"><div class="about-label">${l(i.name)}</div><div class="about-value">${l(i.license)}</div></div>`).join("");return`<div class="about-brand">${l(e.appName)}</div>
          <div class="about-version">${n}</div>
          <div class="about-row"><div class="about-label">${l(e.authorLabel)}</div><div class="about-value">${l(e.authorValue)}</div></div>
          <div class="about-row"><div class="about-label">${l(e.websiteLabel)}</div><a class="about-link" href="${Xe(e.websiteUrl)}" target="_blank" rel="noopener noreferrer">${l(e.websiteUrl)}</a></div>
          <div class="about-row"><div class="about-label">${l(e.sourceLabel)}</div><a class="about-link" href="${Xe(e.sourceUrl)}" target="_blank" rel="noopener noreferrer">${l(e.sourceUrl)}</a></div>
          <div class="about-row"><div class="about-label">${l(e.licenseLabel)}</div><div class="about-value">${l(e.licenseValue)}</div></div>
          <h3 class="about-deps-title">${l(e.depsTitle)}</h3>
          <p class="muted about-deps-notice">${l(e.depsNotice)}</p>
          ${a}`})();return`<div class="overlay settings-overlay" id="about-overlay">
    <div class="overlay-panel settings-panel">
      <header class="settings-header">
        <h2>${l((e==null?void 0:e.title)??"关于")}</h2>
        <button type="button" class="icon-btn" id="about-close" title="关闭">✕</button>
      </header>
      <div class="settings-body about-scroll">${t}</div>
    </div>
  </div>`}function Xe(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#39;")}async function Ss(){r({showAbout:!0,aboutLoading:!0,aboutInfo:null,moreMenuOpen:!1});try{const e=await It();r({aboutInfo:e,aboutLoading:!1})}catch(e){r({showAbout:!1,aboutLoading:!1,error:e instanceof Error?e.message:String(e)})}}function Es(){return s.moreMenuOpen?`<div class="more-menu" role="menu" id="more-menu">
    <button type="button" class="more-menu-item" data-more-action="settings">模型设置</button>
    <button type="button" class="more-menu-item" data-more-action="mcp">MCP 与联网搜索</button>
    <button type="button" class="more-menu-item" data-more-action="capabilities">能力检索</button>
    <button type="button" class="more-menu-item" data-more-action="system-prompt">系统提示词</button>
    <button type="button" class="more-menu-item" data-more-action="brief">会话简报</button>
    <button type="button" class="more-menu-item" data-more-action="diagnostics">诊断包</button>
    <button type="button" class="more-menu-item" data-more-action="failed">失败工具调用</button>
    <button type="button" class="more-menu-item" data-more-action="about">关于</button>
  </div>`:""}function ks(){if(!s.showFailedLogs)return"";const e=s.failedLogsScopeSession?"当前会话":"全部会话",t=s.failedLogs.map(n=>`<li class="failed-log-item">
          <div><strong>${l(n.toolName||"tool")}</strong>
            <span class="muted"> · ${l(n.toolCallId)}</span></div>
          <div class="muted small">run ${l(n.runId)} · session ${l(n.sessionId)}</div>
          <div>${l(n.errorMessage||"—")}</div>
        </li>`).join("");return`<div class="overlay" id="failed-logs-overlay">
    <div class="overlay-panel usage-panel wide-panel">
      <h2>失败工具调用</h2>
      <p class="muted">范围：${l(e)} · 对齐 <code>./agent1 logs failed</code></p>
      <ul class="failed-log-list">${t||"<li class='muted'>（无失败记录）</li>"}</ul>
      <div class="overlay-actions">
        <label class="checkbox-row"><input type="checkbox" id="failed-scope-session" ${s.failedLogsScopeSession?"checked":""}/> 仅当前会话</label>
        <button type="button" id="failed-refresh">刷新</button>
        <button type="button" class="primary-btn" id="failed-close">关闭</button>
      </div>
    </div>
  </div>`}function Is(){return!s.usagePanelOpen||!s.usage?"":`<div class="overlay" id="usage-overlay">
    <div class="overlay-panel usage-panel">
      <h2>累计用量</h2>
      <div class="usage-detail">${Xt(s.usage)}</div>
      <p class="muted">来自 agentRoot/logs/events.jsonl</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="usage-close">关闭</button>
      </div>
    </div>
  </div>`}function Ts(){if(!s.showSettings||!s.settingsDraft)return"";const e=s.settingsDraft,t=s.config,n=K.map(d=>`<button type="button" class="provider-chip ${d.id===e.providerId?"active":""}" data-provider="${d.id}">${l(d.label)}</button>`).join(""),a=s.modelOptions.map(d=>{const f=d.modelId===e.model?"selected":"";return`<option value="${l(d.modelId)}" ${f}>${l(d.title)} · ${l(d.subtitle)}</option>`}).join(""),i=e.model&&!s.modelOptions.some(d=>d.modelId===e.model)?`<option value="${l(e.model)}" selected>${l(e.model)}（当前）</option>`:"",c=s.modelsLoading?"正在加载模型列表…":s.modelsStatus||(s.modelOptions.length?`共 ${s.modelOptions.length} 个模型`:"");return`<div class="overlay settings-overlay" id="settings-overlay">
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
              placeholder="${l(t!=null&&t.hasApiKey?`已配置 ${t.apiKeyHint||""}`:"粘贴 Key，留空则不修改")}" />
          </div>
          <div class="field">
            <label for="cfg-base">Base URL</label>
            <input id="cfg-base" class="settings-control" value="${l(e.baseUrl)}" />
          </div>
          <div class="field">
            <div class="field-label-row">
              <label for="cfg-model">模型</label>
              <button type="button" class="link-btn" id="cfg-refresh-models" ${s.modelsLoading?"disabled":""}>从网络刷新</button>
            </div>
            <select id="cfg-model" class="settings-control settings-select">${i}${a}</select>
            <p class="settings-hint">${l(c)}</p>
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
  </div>`}function ct(){var w;const e=s.sessions.map(g=>{const $=g.sessionId===s.activeSessionId?"active":"",P=g.title||g.sessionId.slice(0,8);return`<div class="session-item ${$}">
        <button type="button" class="session-title" data-use="${g.sessionId}">${l(P)}</button>
        <button type="button" class="session-delete" data-del="${g.sessionId}" title="删除">✕</button>
      </div>`}).join(""),t=Ot(s.lines,s.isRunning,s.streamingText,s.streamingReasoning,!!s.pendingAsk),n=s.lines.map(g=>us(g,s.activeSessionId)).join(""),a=t?'<button type="button" class="new-topic-btn" id="btn-new-topic">开始新话题</button>':"",i=s.lastRunTokenSummary&&!s.isRunning?`<div class="run-token-summary ${s.lastRunTokenSummary.failed?"failed":""}">${l(Jt(s.lastRunTokenSummary))}</div>`:"",c=s.lines.length===0&&!s.streamingText&&!s.isRunning?'<div class="landing"><h2>Agent1</h2><p>生产力助手 · 与 Android 端同款纸本风格。左侧管理会话，下方输入开始对话。</p></div>':n+i+a+ps()+ms(),d=Gt(s.usage),f=s.isRunning||!!s.pendingAsk,b=s.pendingAsk?"请在上方卡片回答":s.isRunning?"生成中…":"输入消息，Enter 发送，Shift+Enter 换行";rt.innerHTML=`
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
        <span class="usage-headline">${l(d.headline)}</span>
        <span class="usage-detail-line">${l(d.detail)}</span>
      </button>
    </aside>
    <section class="main" id="app-main">
      <div class="topbar">
        <button type="button" class="icon-btn" id="btn-drawer" title="会话">☰</button>
        <h1>对话</h1>
        <div class="topbar-actions">
          <button type="button" class="icon-btn" id="btn-artifacts" title="产物库">📁</button>
          <div class="more-menu-anchor">
            <button type="button" class="icon-btn" id="btn-more" title="更多" aria-haspopup="menu" aria-expanded="${s.moreMenuOpen}">⋮</button>
            ${Es()}
          </div>
        </div>
      </div>
      ${s.toast?`<div class="toast-banner">${l(s.toast)}</div>`:""}
      ${s.error?`<div class="error-banner">${l(s.error)}</div>`:""}
      <div class="chat-scroll" id="chat-scroll">${c}</div>
      <div class="composer-wrap">
        ${s.config&&!s.config.hasApiKey?'<div class="inline-setup">尚未配置 API Key。<button type="button" id="btn-setup">打开设置</button></div>':""}
        ${s.attachments.length?`<div class="attachment-chips">${s.attachments.map(g=>`<span class="chip">${l(g.fileName)} <button type="button" data-rm-attach="${l(g.workspaceRelativePath)}">×</button></span>`).join("")}</div>`:""}
        <div class="composer">
          <input type="file" id="file-input" class="hidden-file" multiple />
          <button type="button" class="icon-btn" id="btn-attach" title="添加附件" ${f?"disabled":""}>📎</button>
          <textarea id="composer" ${f?"disabled":""} placeholder="${b}">${l(s.composer)}</textarea>
          ${s.isRunning?'<button type="button" class="danger-btn" id="btn-stop">停止</button>':`<button type="button" class="primary-btn" id="btn-send" ${f?"disabled":""}>发送</button>`}
        </div>
        <div class="model-chip">模型 ${l(((w=s.config)==null?void 0:w.model)||"—")} ·
          <button type="button" id="btn-model">更改</button></div>
      </div>
    </section>
    ${Ts()}
    ${hs()}
    ${ys()}
    ${ws()}
    ${Is()}
    ${ks()}
    ${$s()}
    ${gs()}
    ${vs()}
    ${bs()}
  </div>`,Ls();const v=document.getElementById("chat-scroll");v&&(v.scrollTop=v.scrollHeight)}function Ls(){var i,c,d,f,b,v,w,g,$,P,X,V,Y,Z,ee,te,se,ne,ae,oe,ie,re,le,ce,de,ue,pe,me,fe,ge,ve,be,he,ye,we,$e,Se,Ee,ke,Ie,Te,Le,Pe,Ae,Re,xe,Be,Ce,Me,Oe,Ue;(i=document.getElementById("btn-new"))==null||i.addEventListener("click",()=>void We()),(c=document.getElementById("btn-new-topic"))==null||c.addEventListener("click",()=>void We()),(d=document.getElementById("btn-drawer"))==null||d.addEventListener("click",o=>{o.stopPropagation();const u=!s.drawerOpen;r({drawerOpen:u}),u&&L()}),(f=document.getElementById("app-main"))==null||f.addEventListener("click",()=>{s.drawerOpen&&r({drawerOpen:!1})}),(b=document.getElementById("usage-footer"))==null||b.addEventListener("click",()=>{L().then(()=>r({usagePanelOpen:!0}))}),(v=document.getElementById("usage-close"))==null||v.addEventListener("click",()=>r({usagePanelOpen:!1})),(w=document.getElementById("usage-overlay"))==null||w.addEventListener("click",o=>{o.target===o.currentTarget&&r({usagePanelOpen:!1})}),(g=document.getElementById("failed-close"))==null||g.addEventListener("click",()=>r({showFailedLogs:!1})),($=document.getElementById("failed-refresh"))==null||$.addEventListener("click",()=>void q()),(P=document.getElementById("failed-scope-session"))==null||P.addEventListener("change",o=>{const u=o.target.checked;r({failedLogsScopeSession:u}),q()}),(X=document.getElementById("failed-logs-overlay"))==null||X.addEventListener("click",o=>{o.target===o.currentTarget&&r({showFailedLogs:!1})}),(V=document.getElementById("btn-more"))==null||V.addEventListener("click",o=>{o.stopPropagation(),r({moreMenuOpen:!s.moreMenuOpen})}),(Y=document.getElementById("more-menu"))==null||Y.addEventListener("click",o=>o.stopPropagation()),document.querySelectorAll("[data-more-action]").forEach(o=>{o.addEventListener("click",()=>{const u=o.dataset.moreAction;switch(Ge(),u){case"settings":N();break;case"mcp":Je();break;case"capabilities":os();break;case"system-prompt":ts();break;case"brief":rs().catch(p=>r({error:p instanceof Error?p.message:String(p)}));break;case"diagnostics":gt().then(()=>S("诊断包已下载")).catch(p=>r({error:p instanceof Error?p.message:String(p)}));break;case"failed":Zt();break;case"about":Ss();break}})}),s.moreMenuOpen&&window.setTimeout(()=>{const o=()=>{Ge(),document.removeEventListener("click",o)};document.addEventListener("click",o)},0),(Z=document.getElementById("about-close"))==null||Z.addEventListener("click",()=>r({showAbout:!1,aboutInfo:null,aboutLoading:!1})),(ee=document.getElementById("about-overlay"))==null||ee.addEventListener("click",o=>{o.target===o.currentTarget&&r({showAbout:!1,aboutInfo:null,aboutLoading:!1})}),(te=document.getElementById("btn-setup"))==null||te.addEventListener("click",()=>void N()),(se=document.getElementById("btn-model"))==null||se.addEventListener("click",()=>void N()),(ne=document.getElementById("btn-artifacts"))==null||ne.addEventListener("click",async()=>{const o=await vt();r({artifacts:o,showArtifacts:!0})}),(ae=document.getElementById("artifacts-close"))==null||ae.addEventListener("click",()=>r({showArtifacts:!1})),(oe=document.getElementById("capabilities-close"))==null||oe.addEventListener("click",()=>r({showCapabilities:!1})),(ie=document.getElementById("cap-search-btn"))==null||ie.addEventListener("click",()=>void is()),document.querySelectorAll(".cap-kind-chip").forEach(o=>{o.addEventListener("click",()=>{const u=o.dataset.capKind;if(!u)return;const p=new Set(s.capSelectedKinds);p.has(u)?p.delete(u):p.add(u),r({capSelectedKinds:[...p]})})}),(re=document.getElementById("cap-limit-down"))==null||re.addEventListener("click",()=>{s.capLimit>1&&r({capLimit:s.capLimit-1})}),(le=document.getElementById("cap-limit-up"))==null||le.addEventListener("click",()=>{s.capLimit<it&&r({capLimit:s.capLimit+1})}),document.querySelectorAll("[data-art-preview]").forEach(o=>{o.addEventListener("click",()=>{const u=o.dataset.artSession,p=o.dataset.artPath;u&&p&&r({preview:{sessionId:u,path:p},showArtifacts:!1})})}),document.querySelectorAll("[data-art-insert]").forEach(o=>{o.addEventListener("click",async()=>{const u=o.dataset.artSession,p=o.dataset.artPath,h=s.activeSessionId;if(!(!h||!u||!p))try{await bt(h,u,p),await B(h),S("已复制到 imports/")}catch(k){r({error:k instanceof Error?k.message:String(k)})}})}),(ce=document.getElementById("preview-close"))==null||ce.addEventListener("click",()=>r({preview:null})),(de=document.getElementById("preview-overlay"))==null||de.addEventListener("click",o=>{o.target===o.currentTarget&&r({preview:null})});const e=document.getElementById("file-input");(ue=document.getElementById("btn-attach"))==null||ue.addEventListener("click",()=>e==null?void 0:e.click()),e==null||e.addEventListener("change",async()=>{var u;const o=s.activeSessionId;if(!(!o||!((u=e.files)!=null&&u.length)))try{for(const p of Array.from(e.files))await yt(o,p);await B(o),e.value=""}catch(p){r({error:p instanceof Error?p.message:String(p)})}}),document.querySelectorAll("[data-rm-attach]").forEach(o=>{o.addEventListener("click",async()=>{const u=s.activeSessionId,p=o.dataset.rmAttach;!u||!p||(await wt(u,p),await B(u))})}),document.querySelectorAll("[data-use]").forEach(o=>{o.addEventListener("click",async()=>{const u=o.dataset.use;if(s.isRunning){r({error:"请先停止当前 Run 再切换会话"});return}await j(u),await T(u),await E()})}),document.querySelectorAll("[data-del]").forEach(o=>{o.addEventListener("click",async u=>{u.stopPropagation();const p=o.dataset.del;if(confirm("删除该会话？")&&(await kt(p),await E(),s.activeSessionId===p))if(s.sessions[0])await j(s.sessions[0].sessionId),await T(s.sessions[0].sessionId);else{const h=await W();await E(),await T(h.sessionId)}})});const t=document.getElementById("composer");t==null||t.addEventListener("input",()=>{s={...s,composer:t.value}}),t==null||t.addEventListener("keydown",o=>{o.key==="Enter"&&!o.shiftKey&&(o.preventDefault(),H())}),(pe=document.getElementById("btn-send"))==null||pe.addEventListener("click",()=>void H()),(me=document.getElementById("btn-stop"))==null||me.addEventListener("click",async()=>{await At()}),document.querySelectorAll("[data-ask-opt]").forEach(o=>{o.addEventListener("click",()=>{const u=o.dataset.askId,p=o.dataset.askOpt;r({askAnswers:{...s.askAnswers,[u]:p}})})}),document.querySelectorAll("[data-ask-input]").forEach(o=>{o.addEventListener("input",()=>{const u=o.dataset.askInput;s={...s,askAnswers:{...s.askAnswers,[u]:o.value}}})}),(fe=document.getElementById("ask-submit"))==null||fe.addEventListener("click",()=>void ds());const n=()=>r({showSettings:!1,settingsDraft:null,modelsStatus:null,modelsLoading:!1});(ge=document.getElementById("cfg-close"))==null||ge.addEventListener("click",n),(ve=document.getElementById("settings-overlay"))==null||ve.addEventListener("click",o=>{o.target===o.currentTarget&&n()}),document.querySelectorAll("[data-provider]").forEach(o=>{o.addEventListener("click",()=>{var p;const u=o.dataset.provider;u&&u!==((p=s.settingsDraft)==null?void 0:p.providerId)&&es(u)})});const a=o=>{s.settingsDraft&&r({settingsDraft:{...s.settingsDraft,...o}})};(be=document.getElementById("cfg-model"))==null||be.addEventListener("change",o=>{a({model:o.target.value})}),(he=document.getElementById("cfg-ctx-turns"))==null||he.addEventListener("change",o=>{a({maxContextTurns:parseInt(o.target.value,10)||0})}),(ye=document.getElementById("cfg-ctx-msgs"))==null||ye.addEventListener("change",o=>{a({maxContextMessages:parseInt(o.target.value,10)||0})}),(we=document.getElementById("cfg-run-turns"))==null||we.addEventListener("change",o=>{a({maxTurnsPerRun:parseInt(o.target.value,10)||0})}),($e=document.getElementById("cfg-tool-calls"))==null||$e.addEventListener("change",o=>{a({maxToolCallsPerRun:parseInt(o.target.value,10)||0})}),(Se=document.getElementById("cfg-open-mcp"))==null||Se.addEventListener("click",()=>void Je()),(Ee=document.getElementById("mcp-close"))==null||Ee.addEventListener("click",()=>r({showMcp:!1})),(ke=document.getElementById("mcp-web-key"))==null||ke.addEventListener("input",()=>ze()),(Ie=document.getElementById("mcp-web-base"))==null||Ie.addEventListener("input",()=>ze()),(Te=document.getElementById("mcp-list"))==null||Te.addEventListener("input",()=>R()),(Le=document.getElementById("mcp-list"))==null||Le.addEventListener("change",()=>R()),(Pe=document.getElementById("system-prompt-close"))==null||Pe.addEventListener("click",()=>r({showSystemPrompt:!1,systemPromptError:null})),(Ae=document.getElementById("system-prompt-overlay"))==null||Ae.addEventListener("click",o=>{o.target===o.currentTarget&&r({showSystemPrompt:!1,systemPromptError:null})}),(Re=document.getElementById("system-prompt-copy"))==null||Re.addEventListener("click",async()=>{if(s.systemPromptText)try{await navigator.clipboard.writeText(s.systemPromptText),S("已复制系统提示词")}catch(o){r({error:o instanceof Error?o.message:String(o)})}}),(xe=document.getElementById("mcp-add"))==null||xe.addEventListener("click",()=>{r({mcpServers:[...s.mcpServers,{name:"server",url:"https://",enabled:!0,headers:{}}]}),R()}),document.querySelectorAll(".mcp-remove").forEach(o=>{o.addEventListener("click",()=>{const u=Number(o.dataset.mcpIndex);r({mcpServers:s.mcpServers.filter((p,h)=>h!==u)}),R()})}),document.querySelectorAll(".file-link").forEach(o=>{o.addEventListener("click",()=>{const u=o.dataset.filePath,p=s.activeSessionId;!u||!p||(st(u)?r({preview:{sessionId:p,path:u}}):S(u))})}),(Be=document.getElementById("cfg-show-tools"))==null||Be.addEventListener("click",async()=>{try{const{tools:o}=await mt();r({showTools:!0,toolsList:o})}catch(o){r({error:o instanceof Error?o.message:String(o)})}}),(Ce=document.getElementById("tools-close"))==null||Ce.addEventListener("click",()=>r({showTools:!1})),(Me=document.getElementById("tools-overlay"))==null||Me.addEventListener("click",o=>{o.target===o.currentTarget&&r({showTools:!1})}),(Oe=document.getElementById("cfg-refresh-models"))==null||Oe.addEventListener("click",()=>{const o=s.settingsDraft;o&&Q(o,!0)}),(Ue=document.getElementById("cfg-save"))==null||Ue.addEventListener("click",async()=>{const o=s.settingsDraft;if(!o)return;const u=document.getElementById("cfg-key").value,p=document.getElementById("cfg-base").value,h=document.getElementById("cfg-model").value,k=parseInt(document.getElementById("cfg-ctx-turns").value,10),Ne=parseInt(document.getElementById("cfg-ctx-msgs").value,10),je=parseInt(document.getElementById("cfg-run-turns").value,10),De=parseInt(document.getElementById("cfg-tool-calls").value,10);try{const I=await Ye({apiKey:u||void 0,baseUrl:p||void 0,model:h||void 0,providerId:o.providerId,maxContextTurns:Number.isFinite(k)?k:0,maxContextMessages:Number.isFinite(Ne)?Ne:0,maxTurnsPerRun:Number.isFinite(je)?je:0,maxToolCallsPerRun:Number.isFinite(De)?De:0});r({config:I,showSettings:!1,settingsDraft:null,modelsStatus:null,error:null}),S("设置已保存")}catch(I){r({error:I instanceof Error?I.message:String(I)})}})}ct();ls().catch(e=>{r({error:e instanceof Error?e.message:String(e)})});window.addEventListener("beforeunload",()=>x==null?void 0:x());
