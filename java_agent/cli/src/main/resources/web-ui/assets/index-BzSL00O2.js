(function(){const t=document.createElement("link").relList;if(t&&t.supports&&t.supports("modulepreload"))return;for(const i of document.querySelectorAll('link[rel="modulepreload"]'))o(i);new MutationObserver(i=>{for(const l of i)if(l.type==="childList")for(const d of l.addedNodes)d.tagName==="LINK"&&d.rel==="modulepreload"&&o(d)}).observe(document,{childList:!0,subtree:!0});function s(i){const l={};return i.integrity&&(l.integrity=i.integrity),i.referrerPolicy&&(l.referrerPolicy=i.referrerPolicy),i.crossOrigin==="use-credentials"?l.credentials="include":i.crossOrigin==="anonymous"?l.credentials="omit":l.credentials="same-origin",l}function o(i){if(i.ep)return;i.ep=!0;const l=s(i);fetch(i.href,l)}})();async function f(e){const t=await e.json();if(!e.ok)throw new Error(t.error||e.statusText);return t}async function ht(){return f(await fetch("/api/config"))}async function wt(e){return f(await fetch("/api/config",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(e)}))}async function Dt(){return f(await fetch("/api/usage"))}async function Nt(e,t=!1){const s=new URLSearchParams({provider:e});return t&&s.set("refresh","1"),f(await fetch(`/api/models?${s}`))}async function Et(){return f(await fetch("/api/mcp/servers"))}async function jt(e){return f(await fetch("/api/mcp/servers",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({servers:e})}))}async function _t(){return f(await fetch("/api/tools"))}async function qt(e){return f(await fetch(`/api/sessions/${encodeURIComponent(e)}/system-prompt`))}async function Ft(){var l,d;const e=await fetch("/api/diagnostics/export");if(!e.ok)throw new Error("诊断包导出失败");const t=await e.blob(),s=((d=(l=e.headers.get("Content-Disposition"))==null?void 0:l.match(/filename="([^"]+)"/))==null?void 0:d[1])??`agent1-diag-${Date.now()}.zip`,o=URL.createObjectURL(t),i=document.createElement("a");i.href=o,i.download=s,i.click(),URL.revokeObjectURL(o)}async function Kt(){return(await f(await fetch("/api/artifacts"))).artifacts}async function Ht(e,t,s){return f(await fetch(`/api/sessions/${encodeURIComponent(e)}/artifacts/import`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({sourceSessionId:t,workspaceRelativePath:s})}))}async function Jt(e){return(await f(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`))).files}async function Wt(e,t){const s=new FormData;return s.append("file",t),f(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`,{method:"POST",body:s}))}async function Gt(e,t){await f(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments/${encodeURIComponent(t)}`,{method:"DELETE"}))}function zt(e,t){const s=t.split("/").map(o=>encodeURIComponent(o)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${s}`}function Xt(e,t){const s=t.split("/").map(o=>encodeURIComponent(o)).join("/");return`/api/sessions/${encodeURIComponent(e)}/preview/${s}`}async function Qt(e,t){const s=new URLSearchParams({path:t});return f(await fetch(`/api/sessions/${encodeURIComponent(e)}/preview-meta?${s}`))}async function et(e,t,s){return(await f(await fetch("/api/html-inspect/compose",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({relativePath:e,elementJson:t,problem:s})}))).text||""}async function Vt(e,t,s){const o=new URLSearchParams;return o.set("q",e),o.set("limit",String(s)),t.length&&o.set("kinds",t.join(",")),f(await fetch(`/api/capabilities?${o.toString()}`))}async function Yt(){return f(await fetch("/api/sessions"))}async function Q(){return f(await fetch("/api/sessions",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"}))}async function V(e){await f(await fetch(`/api/sessions/${encodeURIComponent(e)}/use`,{method:"POST",body:"{}"}))}async function Zt(e){await f(await fetch(`/api/sessions/${encodeURIComponent(e)}`,{method:"DELETE"}))}async function en(){return f(await fetch("/api/about"))}async function tn(e){return f(await fetch(`/api/sessions/${encodeURIComponent(e)}/brief`))}async function nn(e){const t=e?`?sessionId=${encodeURIComponent(e)}`:"";return(await f(await fetch(`/api/logs/failed${t}`))).items}async function St(e){return(await f(await fetch(`/api/sessions/${encodeURIComponent(e)}/transcript`))).messages}async function sn(e){return(await f(await fetch(`/api/sessions/${encodeURIComponent(e)}/todos`))).todos??[]}async function $t(e,t){await f(await fetch(`/api/sessions/${encodeURIComponent(e)}/messages`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({text:t})}))}async function on(){await f(await fetch("/api/run/abort",{method:"POST",body:"{}"}))}function an(e){const t=new EventSource("/api/events");return t.onmessage=s=>{try{e(JSON.parse(s.data))}catch{}},()=>t.close()}const rn="ask_user",cn=["等待用户","waiting","ask_user"];function ln(e){try{const s=(JSON.parse(e.argumentsJson||"{}").questions||[]).map((o,i)=>({id:o.id||`q${i}`,prompt:o.prompt||o.question||"",options:o.options,allowFreeText:o.allowFreeText??o.allow_free_text??!0})).filter(o=>o.prompt);return s.length===0?null:{questions:s}}catch{return null}}function It(e){if(!e.length)return null;for(let t=e.length-1;t>=0;t--){const s=e[t];if(s.role==="user")return null;if(s.role==="assistant"){for(const o of s.toolCalls||[])if(o.name===rn)return ln(o)}if(s.role==="toolResult"){const o=s.content||"";if(!cn.some(l=>o.toLowerCase().includes(l.toLowerCase())))return null}}return null}function dn(e,t){return e.questions.map(o=>{const i=(t[o.id]||"").trim();return`${o.prompt}
→ ${i||"(未答)"}`}).join(`

`)}function un(e,t,s,o,i){if(t||i||s.trim()||o.trim())return!1;const l=pn(e);return(l==null?void 0:l.role)==="assistant"}function pn(e){return e.filter(t=>t.role==="user"||t.role==="assistant").at(-1)}function mn(){return{sessions:[],activeSessionId:null,config:null,lines:[],streamingText:"",streamingReasoning:"",tools:[],sessionTodos:[],isRunning:!1,pendingAsk:null,askAnswers:{},error:null,showSettings:!1,drawerOpen:!0,composer:"",usage:null,usagePanelOpen:!1,toast:null,attachments:[],artifacts:[],showArtifacts:!1,preview:null,previewReloadKey:0,previewIsDeck:!1,previewInspectEnabled:!1,previewSelectedElementJson:null,previewOpinion:"",showCapabilities:!1,capQuery:"",capSelectedKinds:[],capLimit:8,capBusy:!1,capError:null,capSearched:!1,capModelText:"",capHostPlatform:"",capHiddenHits:[],capDisabledMcp:[],modelOptions:[],settingsDraft:null,modelsLoading:!1,modelsStatus:null,showTools:!1,toolsList:[],lastRunTokenSummary:null,showMcp:!1,mcpServers:[],mcpStatus:null,showFailedLogs:!1,failedLogs:[],failedLogsScopeSession:!0,moreMenuOpen:!1,showAbout:!1,aboutLoading:!1,aboutInfo:null,showSystemPrompt:!1,systemPromptBusy:!1,systemPromptError:null,systemPromptText:"",systemPromptTools:[],mcpWebSearchBaseUrl:"https://api.tavily.com",mcpWebSearchApiKey:"",pendingDeleteSessionId:null}}function kt(e){const t=e.split("/").pop()??e,s=t.lastIndexOf(".");return s>=0?t.slice(s+1).toLowerCase():""}function Tt(e){const t=kt(e);return t==="html"||t==="htm"}const fn=new Set(["html","htm","svg","png","jpg","jpeg","gif","webp"]);function Lt(e){return fn.has(kt(e))}function vn(e){return e.isRunning||e.streamingText.trim()||e.streamingReasoning.trim()||e.tools.length>0||e.pendingAsk?!1:!e.lines.some(t=>t.role==="user"||t.role==="assistant")}const tt="往返轮次已达上限";function nt(e){return e.includes("单轮模型与工具往返超过上限")||e.includes("单轮工具调用次数超过上限")||e.includes("工具调用次数超过上限")||e.includes("insufficient tool messages")}function gn(e){return e.trim().startsWith("错误:")}function bn(e,t,s){if(t||s)return!1;let o=-1;if(e.forEach((l,d)=>{const v=l.role==="user"&&l.content.includes(tt),m=l.role==="assistant"&&gn(l.content);(v||m||nt(l.content))&&(o=d)}),o<0)return!1;let i=!1;for(let l=o+1;l<e.length;l+=1){const d=e[l];if(d.role==="user"&&!d.content.includes(tt)&&(i=!0),i&&d.role==="assistant"&&d.content.trim()&&!nt(d.content))return!1}return!0}function Pt(e){const t=[];return e.forEach((s,o)=>{var i;if(s.role==="user"){t.push({id:`u-${o}`,role:"user",content:s.content});return}if(s.role==="assistant"){(i=s.content)!=null&&i.trim()&&t.push({id:`a-${o}`,role:"assistant",content:s.content});for(const l of s.toolCalls||[])t.push({id:`t-${l.id}`,role:"tool",content:l.argsPreview||l.argumentsJson||"",toolName:l.name});return}if(s.role==="toolResult"){t.push({id:`tr-${o}`,role:"tool",content:s.content,toolName:"result",error:!!s.error});return}s.role}),t}const yn=new Set(["png","jpg","jpeg","webp","gif","svg"]);function J(e){var s;const t=((s=e.split(".").pop())==null?void 0:s.toLowerCase())??"";return yn.has(t)}function At(e){const t=/!\[[^\]]*]\(([^)]+)\)/g,s=[];let o;for(;(o=t.exec(e))!==null;){const i=o[1].trim();i&&!i.startsWith("http://")&&!i.startsWith("https://")&&s.push(Y(i))}return s}function Y(e){return e.replace(/^\.\//,"").replace(/^workspace\//,"")}function hn(e){const t=[],s=/```[\w-]*\n?([\s\S]*?)```/g;let o=0,i;for(;(i=s.exec(e))!==null;)i.index>o&&t.push({kind:"text",text:e.slice(o,i.index)}),t.push({kind:"code",text:i[1]??""}),o=i.index+i[0].length;return o<e.length&&t.push({kind:"text",text:e.slice(o)}),t.length?t:[{kind:"text",text:e}]}function W(e,t){return e?hn(e).map(s=>s.kind==="code"?`<pre class="bubble-code"><code>${S(s.text.trimEnd())}</code></pre>`:wn(s.text,t)).join(""):""}function wn(e,t){const s=/!?\[([^\]]*)]\(([^)]+)\)/g;let o="",i=0,l;for(;(l=s.exec(e))!==null;){o+=st(e.slice(i,l.index));const d=l[1],v=l[2].trim(),m=l[0].startsWith("!");if(t&&!v.startsWith("http://")&&!v.startsWith("https://")){const h=Y(v);m&&J(h)?o+=`<figure class="bubble-image"><img src="${Rt(t,h)}" alt="${G(h)}" loading="lazy"/></figure>`:m?o+=S(l[0]):o+=`<button type="button" class="file-link" data-file-path="${G(h)}">${S(d||h)}</button>`}else o+=S(l[0]);i=l.index+l[0].length}return o+=st(e.slice(i)),o}function st(e){if(!e)return"";const t=e.split(`
`);let s="",o=!1;for(const i of t){const l=i.trimEnd(),d=l.match(/^[-—]\s+(.*)$/);if(d){o||(s+='<ul class="bubble-list">',o=!0),s+=`<li>${ot(d[1])}</li>`;continue}o&&(s+="</ul>",o=!1),l===""?s+='<div class="bubble-gap"></div>':s+=`<p class="bubble-p">${ot(l)}</p>`}return o&&(s+="</ul>"),s}function ot(e){const t=e.split(/`([^`\n]+)`/);let s="";for(let o=0;o<t.length;o++)o%2===0?s+=En(t[o]):s+=`<code class="bubble-inline-code">${S(t[o])}</code>`;return s}function En(e){const t=/\*\*(.+?)\*\*/g;let s="",o=0,i;for(;(i=t.exec(e))!==null;)s+=S(e.slice(o,i.index)),s+=`<strong>${S(i[1])}</strong>`,o=i.index+i[0].length;return s+=S(e.slice(o)),s}function Rt(e,t){const s=t.split("/").map(o=>encodeURIComponent(o)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${s}`}function S(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}function G(e){return S(e).replaceAll("'","&#39;")}function Sn(e){const t=At(e).filter(J),s=new Set,o=[];for(const d of t)s.has(d)||(s.add(d),o.push(d));const i=/(?:imports|out|tmp|staging)[/][\w./-]+\.(?:png|jpe?g|webp|gif)/gi;let l;for(;(l=i.exec(e))!==null;){const d=Y(l[0]);J(d)&&!s.has(d)&&(s.add(d),o.push(d))}return o}function at(e,t){const s=new Set(At(e));return Sn(e).filter(o=>!s.has(o)).map(o=>`<figure class="bubble-image"><img src="${Rt(t,o)}" alt="${G(o)}" loading="lazy"/></figure>`).join("")}let j=0,_=0,Z=0;function $n(){j=0,_=0,Z=0}function In(e){j+=Number(e.inputTokens)||0,_+=Number(e.outputTokens)||0;const t=e.cachedTokens;t!=null&&(Z+=Number(t)||0)}function it(e){const t=j>0||_>0;return!e&&!t?null:{inputTokens:j,outputTokens:_,cachedTokens:Z,failed:e}}function kn(e){const t=[`输入 ${rt(e.inputTokens)}`,`输出 ${rt(e.outputTokens)}`];if(e.inputTokens>0){const s=Math.min(100,Math.round(e.cachedTokens*100/e.inputTokens));t.push(`缓存命中 ${s}%`)}return e.failed&&t.push("未完成"),t.join(" · ")}function rt(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,s=Math.trunc(t);return t===s?`${s}k`:`${t.toFixed(1)}k`}const z=[{id:"dashscope",label:"通义",baseUrl:"https://dashscope.aliyuncs.com/compatible-mode/v1",defaultModel:"qwen3.7-flash"},{id:"deepseek",label:"DeepSeek",baseUrl:"https://api.deepseek.com",defaultModel:"deepseek-flash"},{id:"zhipu_coding",label:"智谱 Coding",baseUrl:"https://open.bigmodel.cn/api/coding/paas/v4",defaultModel:"glm-5.3"}];function Tn(e){const t=e.toLowerCase();return t.includes("deepseek.com")?"deepseek":t.includes("bigmodel.cn")?"zhipu_coding":"dashscope"}function ee(e){return z.find(t=>t.id===e)??z[0]}function C(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,s=Math.trunc(t);return t===s?`${s}k`:`${t.toFixed(1)}k`}function Ln(e){if(!e||!e.hasUsage)return{headline:"累计用量",detail:"暂无 usage 记录"};const t=`约 ${C(e.totalTokens)} tokens`,s=e.cacheHitPercent,o=s!=null?`输入 ${C(e.inputTokens)} · 缓存 ${s}%`:`输入 ${C(e.inputTokens)} · 输出 ${C(e.outputTokens)}`;return{headline:t,detail:o}}function Pn(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;")}function An(e){const t=[`输入 ${e.inputTokens.toLocaleString()}`,`输出 ${e.outputTokens.toLocaleString()}`,`缓存 ${e.cachedTokens.toLocaleString()}`,`usage 事件 ${e.usageEvents}`];let s="";return e.byModel.length&&(s="<ul>"+e.byModel.map(o=>`<li><strong>${Pn(o.modelId||"（未知模型）")}</strong>：入 ${o.inputTokens.toLocaleString()} / 出 ${o.outputTokens.toLocaleString()}</li>`).join("")+"</ul>"),`<p>${t.join(" · ")}</p>${s}`}const Rn=[{id:"caps",label:"Caps"},{id:"builtin",label:"内置"},{id:"weizhi_package",label:"官方包"},{id:"catalog_script",label:"catalog 脚本"},{id:"catalog_lib",label:"catalog 库"},{id:"native",label:"native"},{id:"skill",label:"Skill"},{id:"mcp",label:"MCP"},{id:"bridge_tool",label:"桥接"},{id:"agent_tool",label:"外层工具"}],te=20;function xn(e){return e==="listed_but_unusable"?"已索引，当前不可用":e==="platform_hidden"?"当前平台不可用":"可用"}const Bn="agent1-inspect",Cn="agent1-inspect-cmd";function k(e,t){e!=null&&e.contentWindow&&e.contentWindow.postMessage({type:Cn,cmd:t},window.location.origin)}const xt=document.getElementById("app");if(!xt)throw new Error("#app missing");let n=mn(),U=null,q,F,M;const ct={};function Mn(e){if(!e)return"";try{return JSON.parse(e).tag||"element"}catch{return"element"}}function lt(e,t,s){r({preview:{sessionId:e,path:t},previewReloadKey:0,previewIsDeck:!1,previewInspectEnabled:!1,previewSelectedElementJson:null,previewOpinion:"",showArtifacts:!1,...s}),Tt(t)&&Qt(e,t).then(o=>{var i,l;((i=n.preview)==null?void 0:i.sessionId)===e&&((l=n.preview)==null?void 0:l.path)===t&&r({previewIsDeck:o.isDeck})},()=>{})}function K(){const e=document.getElementById("preview-frame");n.previewInspectEnabled&&k(e,"disable"),r({preview:null,previewInspectEnabled:!1,previewSelectedElementJson:null,previewOpinion:"",previewIsDeck:!1})}function On(){n.preview&&(M&&clearTimeout(M),M=setTimeout(()=>{if(M=void 0,!n.preview)return;const e=document.getElementById("preview-frame"),t=n.previewInspectEnabled;r({previewReloadKey:n.previewReloadKey+1,previewSelectedElementJson:null,previewOpinion:""}),window.setTimeout(()=>{k(e,"install"),t&&k(e,"enable")},50)},300))}function Un(){const e=document.getElementById("preview-frame");e&&e.addEventListener("load",()=>{k(e,"install"),n.previewInspectEnabled&&k(e,"enable")})}function dt(e,t){const s=ct[e]??!1;return ct[e]=t,t&&s?" overlay-no-enter":""}function Dn(){return n.showCapabilities||n.showSettings||n.showMcp||n.showArtifacts||n.showSystemPrompt||n.showAbout||n.showTools||n.usagePanelOpen||n.showFailedLogs}function Bt(e){const t=document.getElementById("cap-search-btn");t&&(t.disabled=e,t.textContent=e?"…":"搜索");const s=document.getElementById("cap-limit-down"),o=document.getElementById("cap-limit-up");s&&(s.disabled=e||n.capLimit<=1),o&&(o.disabled=e||n.capLimit>=te),document.querySelectorAll(".cap-kind-chip").forEach(i=>{i.disabled=e})}function r(e){n={...n,...e},Ut()}function c(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}async function T(){const e=await Yt();r({sessions:e.sessions.slice().sort((t,s)=>s.updatedAt.localeCompare(t.updatedAt)),activeSessionId:e.activeSessionId??n.activeSessionId,isRunning:e.runInProgress})}async function A(){try{const e=await Dt();r({usage:e})}catch{}}async function X(){const e=n.failedLogsScopeSession&&n.activeSessionId?n.activeSessionId:null,t=await nn(e);r({failedLogs:t})}async function Nn(){r({showFailedLogs:!0});try{await X()}catch(e){r({error:e instanceof Error?e.message:String(e)})}}function w(e){r({toast:e}),window.setTimeout(()=>{n.toast===e&&r({toast:null})},2200)}async function ut(){if(n.pendingDeleteSessionId&&r({pendingDeleteSessionId:null}),n.isRunning){r({error:"请先停止当前 Run"});return}if(n.activeSessionId&&vn(n)){w("已是新会话");return}const e=await Q();await T(),await R(e.sessionId),await A(),r({drawerOpen:!1,pendingDeleteSessionId:null})}async function jn(){const e=n.pendingDeleteSessionId;if(!e)return;const t=n.activeSessionId===e;r({pendingDeleteSessionId:null});try{if(await Zt(e),await T(),!t)return;if(n.sessions[0])await V(n.sessions[0].sessionId),await R(n.sessions[0].sessionId);else{const s=await Q();await T(),await R(s.sessionId)}}catch(s){r({error:s instanceof Error?s.message:String(s)})}}function Ct(){const e=n.config,t=(e==null?void 0:e.baseUrl)||"",s=(e==null?void 0:e.providerId)||Tn(t);return{providerId:s,baseUrl:t,model:(e==null?void 0:e.model)||ee(s).defaultModel,maxContextTurns:(e==null?void 0:e.maxContextTurns)??6,maxContextMessages:(e==null?void 0:e.maxContextMessages)??0,maxTurnsPerRun:(e==null?void 0:e.maxTurnsPerRun)??24,maxToolCallsPerRun:(e==null?void 0:e.maxToolCallsPerRun)??48}}async function ne(e,t){var s,o;r({modelsLoading:!0});try{const{models:i,source:l}=await Nt(e.providerId,t);let d=e.model;if(!i.some(v=>v.modelId===d)){const v=ee(e.providerId);d=((s=i.find(m=>m.modelId===v.defaultModel))==null?void 0:s.modelId)??((o=i[0])==null?void 0:o.modelId)??d}r({modelOptions:i,modelsLoading:!1,settingsDraft:{...e,model:d},modelsStatus:t?`已从网络更新 · ${i.length} 个模型`:`共 ${i.length} 个模型`})}catch(i){r({modelsLoading:!1,error:i instanceof Error?i.message:"模型列表加载失败"})}}function _n(e){const t=ee(e),s={...n.settingsDraft??Ct(),providerId:e,baseUrl:t.baseUrl,model:t.defaultModel};r({settingsDraft:s,error:null}),ne(s,!1)}async function H(){const e=Ct();r({showSettings:!0,settingsDraft:e,modelOptions:[],error:null,moreMenuOpen:!1,showMcp:!1,showSystemPrompt:!1}),await ne(e,!1)}async function pt(){try{const[{servers:e},t]=await Promise.all([Et(),ht()]);r({config:t,showMcp:!0,mcpServers:e,mcpStatus:null,showSettings:!1,showSystemPrompt:!1,moreMenuOpen:!1,mcpWebSearchBaseUrl:t.webSearchBaseUrl||"https://api.tavily.com",mcpWebSearchApiKey:""})}catch(e){r({error:e instanceof Error?e.message:String(e)})}}async function qn(){const e=n.activeSessionId;if(!e){r({showSystemPrompt:!0,showSettings:!1,showMcp:!1,systemPromptBusy:!1,systemPromptError:"先打开或新建一个会话，再查看发给模型的系统提示词。",systemPromptText:"",systemPromptTools:[],moreMenuOpen:!1});return}r({showSystemPrompt:!0,showSettings:!1,showMcp:!1,systemPromptBusy:!0,systemPromptError:null,moreMenuOpen:!1});try{const t=await qt(e);r({showSystemPrompt:!0,systemPromptBusy:!1,systemPromptError:null,systemPromptText:t.prompt||"",systemPromptTools:t.tools||[]})}catch(t){r({showSystemPrompt:!0,systemPromptBusy:!1,systemPromptError:t instanceof Error?t.message:String(t)})}}function Fn(){const e=document.querySelectorAll(".mcp-card"),t=[];return e.forEach((s,o)=>{var h,E,$,g;const i=n.mcpServers[o],l=((h=s.querySelector(".mcp-name"))==null?void 0:h.value.trim())||"",d=((E=s.querySelector(".mcp-url"))==null?void 0:E.value.trim())||"",v=(($=s.querySelector(".mcp-enabled"))==null?void 0:$.checked)??!0;let m={};try{m=JSON.parse(((g=s.querySelector(".mcp-headers"))==null?void 0:g.value)||"{}")}catch{}t.push({name:l,url:d,enabled:v,headers:m,toolCount:i==null?void 0:i.toolCount,lastListedAt:i==null?void 0:i.lastListedAt})}),t}function O(){n.showMcp&&(q&&clearTimeout(q),q=setTimeout(()=>void Kn(),700))}function mt(){n.showMcp&&(F&&clearTimeout(F),F=setTimeout(()=>void Hn(),700))}async function Kn(){if(!n.showMcp)return;const e=Fn();try{const t=await jt(e),{servers:s}=await Et(),o=t.warnings.length?` · ${t.warnings.join("; ")}`:"";r({mcpServers:s,mcpStatus:`已自动保存 · 索引 ${t.toolCount} 个 MCP 工具${o}`})}catch(t){r({error:t instanceof Error?t.message:String(t)})}}async function Hn(){var i,l;const e=document.getElementById("mcp-web-key"),t=document.getElementById("mcp-web-base"),s=((i=e==null?void 0:e.value)==null?void 0:i.trim())||void 0,o=((l=t==null?void 0:t.value)==null?void 0:l.trim())||void 0;try{const d=await wt({webSearchApiKey:s,webSearchBaseUrl:o});r({config:d,mcpWebSearchApiKey:(e==null?void 0:e.value)??"",mcpWebSearchBaseUrl:d.webSearchBaseUrl||o||"https://api.tavily.com",mcpStatus:d.hasWebSearch?"已自动保存 · 联网搜索已配置":"已自动保存 · 尚未配置 Key（web_search 不会注册）"})}catch(d){r({error:d instanceof Error?d.message:String(d)})}}function Jn(){r({showCapabilities:!0,showArtifacts:!1,moreMenuOpen:!1,capError:null})}function Mt(){if(!n.capSearched)return"";const e=n.capHostPlatform?`（平台 ${c(n.capHostPlatform)}）`:"",t=[n.capHiddenHits.length?`<div class="cap-results-tail">
            <h3 class="cap-section-title">当前平台搜不到</h3>
            <p class="muted">索引里有这些条目，但宿主平台过滤后不会出现在上面的正文里。</p>
            ${n.capHiddenHits.map(ns).join("")}
          </div>`:"",n.capDisabledMcp.length?`<div class="cap-results-tail">
            <h3 class="cap-section-title">未启用的 MCP</h3>
            <p class="muted">未启用的服务器不会写入能力索引，模型的 find_caps 也找不到。</p>
            ${n.capDisabledMcp.map(s=>`<div class="cap-hit-card"><strong>${c(s.name)}</strong><div class="muted small">${c(s.url)}</div><div>${c(s.description)}</div></div>`).join("")}
          </div>`:""].join("");return`<div class="cap-results">
        ${n.capError?`<p class="error-inline">${c(n.capError)}</p>`:""}
        ${n.capError?"":`<div class="cap-model-block">
            <h3 class="cap-section-title">模型看到的正文${e}</h3>
            <pre class="cap-result">${c(n.capModelText||"（空）")}</pre>
          </div>`}
        ${t}
      </div>`}function Wn(){var o;const e=document.querySelector("#capabilities-overlay .cap-panel-body");if(!e)return;(o=e.querySelector(".cap-results"))==null||o.remove();const t=Mt();if(!t)return;const s=e.querySelector(".cap-panel-toolbar");s?s.insertAdjacentHTML("afterend",t):e.insertAdjacentHTML("beforeend",t)}function ft(e){n={...n,...e,capBusy:!1},Bt(!1);const t=document.querySelector("#capabilities-overlay .cap-limit-row span");t&&(t.textContent=String(n.capLimit)),Wn()}async function vt(){const e=document.getElementById("cap-query"),t=((e==null?void 0:e.value)??n.capQuery).trim();if(!(!t||n.capBusy)){n={...n,capQuery:t,capBusy:!0,capError:null},Bt(!0);try{const s=await Vt(t,n.capSelectedKinds,n.capLimit);ft({capSearched:!0,capError:null,capModelText:s.modelText,capHostPlatform:s.hostPlatform,capLimit:s.limit,capHiddenHits:s.hiddenByPlatform,capDisabledMcp:s.disabledMcp})}catch(s){ft({capSearched:!0,capError:s instanceof Error?s.message:String(s),capModelText:"",capHostPlatform:"",capHiddenHits:[],capDisabledMcp:[]})}}}async function Gn(){const e=n.activeSessionId;if(!e)return;const{text:t}=await tn(e);await navigator.clipboard.writeText(t),w("简报已复制到剪贴板")}function gt(){n.moreMenuOpen&&r({moreMenuOpen:!1})}async function Ot(e){try{const t=await sn(e);r({sessionTodos:t})}catch{r({sessionTodos:[]})}}async function D(e){try{const t=await Jt(e);r({attachments:t})}catch{r({attachments:[]})}}async function R(e){const t=await St(e),s=It(t);r({activeSessionId:e,lines:Pt(t),pendingAsk:s,askAnswers:{},streamingText:"",streamingReasoning:"",tools:[],error:null}),await D(e),await Ot(e)}async function zn(){const[e]=await Promise.all([ht(),T()]);r({config:e});let t=n.activeSessionId;t||(n.sessions.length===0?(t=(await Q()).sessionId,await T()):(t=n.sessions[0].sessionId,await V(t))),await R(t),await A(),U=an(Xn)}function Xn(e){var t;if(e.sessionId&&n.activeSessionId&&e.sessionId!==n.activeSessionId){const s=(t=n.preview)==null?void 0:t.sessionId;if(!s||e.sessionId!==s)return}switch(e.type){case"AGENT_START":$n(),r({isRunning:!0,streamingText:"",streamingReasoning:"",tools:[],error:null,lastRunTokenSummary:null});break;case"USAGE":In(e.payload);break;case"MESSAGE_UPDATE":r({streamingText:n.streamingText+String(e.payload.delta||""),isRunning:!0});break;case"REASONING_UPDATE":r({streamingReasoning:n.streamingReasoning+String(e.payload.delta||""),isRunning:!0});break;case"MESSAGE_RESET":r({streamingText:"",streamingReasoning:""});break;case"TOOL_EXECUTION_START":{const s=e.payload.toolCall,o={id:s.id||`tool-${Date.now()}`,toolCallId:s.id||"",toolName:s.name||"tool",argsPreview:s.argsPreview||"",running:!0};r({tools:[...n.tools,o],isRunning:!0});break}case"TOOL_EXECUTION_END":{const s=String(e.payload.toolCallId||""),o=n.tools.find(m=>m.toolCallId===s),i=e.payload.todoList,l=Array.isArray(i)?{sessionTodos:i.filter(m=>!!m&&typeof m=="object").map(m=>({id:String(m.id??""),content:String(m.content??""),status:String(m.status??"")})).filter(m=>m.id&&m.content)}:{},d=n.tools.map(m=>m.toolCallId===s?{...m,running:!1,error:!!(e.payload.error||e.payload.toolError),resultSummary:String(e.payload.resultText||"")}:m),v=!!e.payload.stopRunWaitingUser;r({tools:d,isRunning:!v&&n.isRunning,...l}),n.preview&&o&&(o.toolName==="write_file"||o.toolName==="edit_file")&&On();break}case"AGENT_ERROR":r({error:String(e.payload.message||"运行出错"),isRunning:!1,lastRunTokenSummary:it(!0)}),bt();break;case"AGENT_END":r({isRunning:!1,lastRunTokenSummary:it(!1)}),e.sessionId&&Ot(e.sessionId),bt();break}}async function bt(){if(!n.activeSessionId)return;const e=n.lastRunTokenSummary;try{const t=await St(n.activeSessionId);r({lines:Pt(t),pendingAsk:It(t),streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,lastRunTokenSummary:e}),await T(),await A()}catch(t){r({error:t instanceof Error?t.message:String(t)})}}async function N(e){var i;const t=(e??n.composer).trim();if(!t||!n.activeSessionId||n.isRunning||n.pendingAsk)return;if(!((i=n.config)!=null&&i.hasApiKey)){r({showSettings:!0,error:"请先配置 API Key"});return}const s={id:`local-${Date.now()}`,role:"user",content:t};r({lines:[...n.lines,s],composer:e==null?"":n.composer,isRunning:!0,streamingText:"",tools:[],error:null,lastRunTokenSummary:null});let o=t;if(n.attachments.length>0){const l=n.attachments.map(d=>`- ${d.workspaceRelativePath}`);o+=`

[用户附件]
${l.join(`
`)}`}try{await $t(n.activeSessionId,o)}catch(l){r({isRunning:!1,error:l instanceof Error?l.message:String(l)})}}async function Qn(){if(!n.pendingAsk||!n.activeSessionId)return;const e=dn(n.pendingAsk,n.askAnswers);r({pendingAsk:null,composer:e}),await N()}function Vn(e){switch(e){case"in_progress":return"进行中";case"completed":return"已完成";case"pending":default:return"待办"}}function Yn(){return n.sessionTodos.length?`<div class="todo-panel" aria-label="任务清单"><div class="todo-panel-head">任务清单</div><ul class="todo-list">${n.sessionTodos.map(t=>`<li class="todo-item ${t.status==="completed"?"done":t.status==="in_progress"?"active":""}"><span class="todo-status">${c(Vn(t.status))}</span><span class="todo-text">${c(t.content)}</span></li>`).join("")}</ul></div>`:""}function Zn(e,t){if(e.role==="tool"){const o=t?W(e.content,t)+at(e.content,t):c(e.content);return`<details class="tool-card"><summary>⚙ ${c(e.toolName||"tool")}</summary>
      <div class="tool-row ${e.error?"err":""}">${o}</div></details>`}const s=t?W(e.content,t)+at(e.content,t):c(e.content);return`<div class="bubble ${e.role}"><div class="bubble-text">${s}</div></div>`}function es(){if(!n.tools.length&&!n.streamingText&&!n.streamingReasoning)return"";const e=n.tools.map(s=>{const o=s.running?"进行中…":s.error?"失败":"完成";return`<div class="tool-row ${s.error?"err":""}"><strong>${c(s.toolName)}</strong> · ${o}
        ${s.argsPreview?`<div>${c(s.argsPreview)}</div>`:""}
        ${s.resultSummary?`<div>${c(s.resultSummary)}</div>`:""}
      </div>`}).join("");return`<details class="tool-card" open><summary>思考与执行</summary>${[n.streamingReasoning?`<div class="tool-row">思考：${c(n.streamingReasoning)}</div>`:"",e].join("")}</details>
    ${n.streamingText?`<div class="bubble assistant">${c(n.streamingText)}</div>`:""}`}function ts(){return n.pendingAsk?`<div class="ask-panel"><h3>请回答后继续</h3>${n.pendingAsk.questions.map(t=>{const s=(t.options||[]).map(o=>`<button type="button" class="chip ${n.askAnswers[t.id]===o?"selected":""}" data-ask-id="${c(t.id)}" data-ask-opt="${c(o)}">${c(o)}</button>`).join("");return`<div class="ask-q">
        <label>${c(t.prompt)}</label>
        ${s?`<div class="ask-options">${s}</div>`:""}
        <input data-ask-input="${c(t.id)}" value="${c(n.askAnswers[t.id]||"")}" placeholder="你的回答" />
      </div>`}).join("")}
    <button type="button" class="primary-btn" id="ask-submit">提交回答</button></div>`:""}function ns(e){const t=e.title||e.id;return`<div class="cap-hit-card">
    <div class="cap-hit-title">[${c(e.kind)}] ${c(t)}</div>
    <div class="muted small">${c(xn(e.availability))}</div>
    ${e.summary?`<div>${c(e.summary)}</div>`:""}
  </div>`}function ss(){if(!n.showCapabilities)return dt("capabilities",!1),"";const e=dt("capabilities",!0),t=Rn.map(({id:o,label:i})=>`<button type="button" class="provider-chip cap-kind-chip ${n.capSelectedKinds.includes(o)?"active":""}" data-cap-kind="${o}">${c(i)}</button>`).join(""),s=Mt();return`<div class="overlay settings-overlay${e}" id="capabilities-overlay">
    <div class="overlay-panel settings-panel wide-panel">
      <header class="settings-header">
        <h2>能力检索</h2>
        <button type="button" class="icon-btn" id="capabilities-close" title="关闭">✕</button>
      </header>
      <div class="settings-body cap-panel-body">
        <div class="cap-panel-toolbar">
          <div class="cap-search">
            <input id="cap-query" class="settings-control" placeholder="搜索" value="${c(n.capQuery)}" />
            <button type="button" class="primary-btn" id="cap-search-btn" ${n.capBusy?"disabled":""}>${n.capBusy?"…":"搜索"}</button>
          </div>
          <div class="cap-kind-row">${t}</div>
          <div class="cap-limit-row">
            <button type="button" class="icon-btn" id="cap-limit-down" ${n.capLimit<=1||n.capBusy?"disabled":""}>−</button>
            <span>${n.capLimit}</span>
            <button type="button" class="icon-btn" id="cap-limit-up" ${n.capLimit>=te||n.capBusy?"disabled":""}>+</button>
          </div>
        </div>
        ${s}
      </div>
    </div>
  </div>`}function os(){return n.showArtifacts?`<div class="overlay settings-overlay" id="artifacts-overlay">
    <div class="overlay-panel settings-panel wide-panel">
      <header class="settings-header">
        <h2>产物库</h2>
        <button type="button" class="icon-btn" id="artifacts-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <p class="muted">workspace 内 AI 产出（不含 imports/、tmp/）</p>
        <ul class="artifact-list">${n.artifacts.map(t=>{const o=Lt(t.workspaceRelativePath)?`<button type="button" class="link-btn" data-art-preview="1" data-art-session="${c(t.sessionId)}" data-art-path="${c(t.workspaceRelativePath)}">预览</button>`:"",i=n.activeSessionId?`<button type="button" class="link-btn" data-art-insert="1" data-art-session="${c(t.sessionId)}" data-art-path="${c(t.workspaceRelativePath)}">插入当前会话</button>`:"";return`<li><strong>${c(t.fileName)}</strong>
        <span class="muted"> · ${c(t.sessionId.slice(0,8))} · ${Math.round(t.sizeBytes/1024)}KB</span>
        <div class="artifact-actions">${o} ${i}</div></li>`}).join("")||"<li class='muted'>暂无产物</li>"}</ul>
      </div>
    </div>
  </div>`:""}function as(){if(!n.preview)return"";const e=Tt(n.preview.path),t=e?`${Xt(n.preview.sessionId,n.preview.path)}?v=${n.previewReloadKey}`:`${zt(n.preview.sessionId,n.preview.path)}?v=${n.previewReloadKey}`,s=n.previewIsDeck?'<span class="preview-deck-badge">Deck</span>':"",o=n.previewInspectEnabled?"关闭审查":"审查模式",i=n.previewSelectedElementJson,l=e&&i?`<div class="preview-inspect-bar">
        <p class="preview-inspect-summary">已选中 &lt;${c(Mn(i))}&gt;</p>
        <textarea id="preview-opinion" class="preview-opinion" rows="2" placeholder="描述要修改的问题…">${c(n.previewOpinion)}</textarea>
        <div class="preview-inspect-actions">
          <button type="button" class="link-btn" id="preview-inspect-cancel">取消</button>
          <button type="button" class="link-btn" id="preview-fill-composer">填入输入框</button>
          <button type="button" class="primary-btn" id="preview-send-ai">反馈给 AI</button>
        </div>
      </div>`:"",d=e?`<button type="button" class="link-btn" id="preview-inspect-toggle">${o}</button>`:"",v=e?`<iframe class="preview-frame" id="preview-frame" src="${t}" title="preview"></iframe>`:`<div class="preview-media"><img src="${t}" alt="${c(n.preview.path)}" /></div>`;return`<div class="overlay preview-overlay" id="preview-overlay">
    <div class="preview-panel">
      <div class="preview-toolbar">
        <span class="preview-path">${s}${c(n.preview.path)}</span>
        <div class="preview-toolbar-actions">
          ${d}
          <button type="button" class="icon-btn" id="preview-close" title="关闭">✕</button>
        </div>
      </div>
      ${v}
      ${l}
    </div>
  </div>`}function is(){if(!n.showMcp)return"";const e=n.mcpServers.map((s,o)=>{const i=JSON.stringify(s.headers||{});return`<div class="mcp-card" data-mcp-index="${o}">
        <div class="field-row">
          <div class="field"><label>名称</label><input class="settings-control mcp-name" value="${c(s.name)}" /></div>
          <label class="mcp-enable"><input type="checkbox" class="mcp-enabled" ${s.enabled?"checked":""}/> 启用</label>
        </div>
        <div class="field"><label>URL</label><input class="settings-control mcp-url" value="${c(s.url)}" /></div>
        <div class="field"><label>Headers (JSON)</label><input class="settings-control mcp-headers" value="${c(i)}" /></div>
        <p class="settings-hint">${s.toolCount?`${s.toolCount} 个工具`:"保存后尝试索引工具"}</p>
        <button type="button" class="link-btn mcp-remove" data-mcp-index="${o}">删除</button>
      </div>`}).join(""),t=n.config;return`<div class="overlay settings-overlay" id="mcp-overlay">
    <div class="overlay-panel settings-panel">
      <header class="settings-header">
        <h2>MCP 与联网搜索</h2>
        <button type="button" class="icon-btn" id="mcp-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <p class="muted">添加服务器后，助手用 find_caps 查找接口，再在脚本里调用 $mcp.名称.工具。修改后将自动保存并刷新索引。</p>
        ${n.mcpStatus?`<p class="settings-hint">${c(n.mcpStatus)}</p>`:""}
        <div id="mcp-list">${e}</div>
        <button type="button" class="link-btn" id="mcp-add">添加服务器</button>
        <section class="settings-section">
          <h3>联网搜索（Tavily）</h3>
          <div class="field">
            <label for="mcp-web-key">Web Search API Key</label>
            <input id="mcp-web-key" class="settings-control" type="password" autocomplete="off"
              value="${c(n.mcpWebSearchApiKey)}"
              placeholder="${t!=null&&t.hasWebSearch?"已配置，留空不修改":"tvly-…"}" />
          </div>
          <div class="field">
            <label for="mcp-web-base">Tavily Base URL</label>
            <input id="mcp-web-base" class="settings-control" value="${c(n.mcpWebSearchBaseUrl)}" />
          </div>
        </section>
      </div>
    </div>
  </div>`}function rs(){if(!n.showSystemPrompt)return"";const e=n.systemPromptTools.map(s=>{const o=s.description?`<p class="muted">${c(s.description)}</p>`:"",i=s.parametersSchema?`<pre class="prompt-schema">${c(s.parametersSchema)}</pre>`:"";return`<details class="prompt-tool">
        <summary>${c(s.name)}</summary>
        ${o}${i}
      </details>`}).join(""),t=n.systemPromptBusy?'<p class="muted">正在读取系统提示词…</p>':[n.systemPromptError?`<p class="error-inline">${c(n.systemPromptError)}</p>`:"",n.systemPromptText?`<div class="prompt-body">${W(n.systemPromptText,null)}</div>`:n.systemPromptError?"":'<p class="muted">当前会话尚未装入系统提示词。</p>',n.systemPromptTools.length?`<h3 class="prompt-tools-title">工具 ${n.systemPromptTools.length}</h3>${e}`:""].join("");return`<div class="overlay settings-overlay" id="system-prompt-overlay">
    <div class="overlay-panel settings-panel wide-panel">
      <header class="settings-header">
        <h2>系统提示词</h2>
        <div class="settings-header-actions">
          <button type="button" class="link-btn" id="system-prompt-copy" ${n.systemPromptText?"":"disabled"}>复制</button>
          <button type="button" class="icon-btn" id="system-prompt-close" title="关闭">✕</button>
        </div>
      </header>
      <div class="settings-body prompt-scroll">${t}</div>
    </div>
  </div>`}function cs(){return n.showTools?`<div class="overlay" id="tools-overlay">
    <div class="overlay-panel usage-panel">
      <h2>Agent 工具</h2>
      <ul class="tools-list">${n.toolsList.map(t=>`<li><strong>${c(t.name)}</strong><span class="muted">${c(t.description)}</span></li>`).join("")||"<li class='muted'>无</li>"}</ul>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="tools-close">关闭</button>
      </div>
    </div>
  </div>`:""}function ls(){if(!n.showAbout)return"";const e=n.aboutInfo,t=n.aboutLoading||!e?'<p class="muted">加载中…</p>':(()=>{const s=e.versionCode>0?`版本 ${c(e.versionName)}（${e.versionCode}）`:`版本 ${c(e.versionName)}`,o=e.dependencies.map(i=>`<div class="about-row"><div class="about-label">${c(i.name)}</div><div class="about-value">${c(i.license)}</div></div>`).join("");return`<div class="about-brand">${c(e.appName)}</div>
          <div class="about-version">${s}</div>
          <div class="about-row"><div class="about-label">${c(e.authorLabel)}</div><div class="about-value">${c(e.authorValue)}</div></div>
          <div class="about-row"><div class="about-label">${c(e.websiteLabel)}</div><a class="about-link" href="${yt(e.websiteUrl)}" target="_blank" rel="noopener noreferrer">${c(e.websiteUrl)}</a></div>
          <div class="about-row"><div class="about-label">${c(e.sourceLabel)}</div><a class="about-link" href="${yt(e.sourceUrl)}" target="_blank" rel="noopener noreferrer">${c(e.sourceUrl)}</a></div>
          <div class="about-row"><div class="about-label">${c(e.licenseLabel)}</div><div class="about-value">${c(e.licenseValue)}</div></div>
          <h3 class="about-deps-title">${c(e.depsTitle)}</h3>
          <p class="muted about-deps-notice">${c(e.depsNotice)}</p>
          ${o}`})();return`<div class="overlay settings-overlay" id="about-overlay">
    <div class="overlay-panel settings-panel">
      <header class="settings-header">
        <h2>${c((e==null?void 0:e.title)??"关于")}</h2>
        <button type="button" class="icon-btn" id="about-close" title="关闭">✕</button>
      </header>
      <div class="settings-body about-scroll">${t}</div>
    </div>
  </div>`}function yt(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#39;")}async function ds(){r({showAbout:!0,aboutLoading:!0,aboutInfo:null,moreMenuOpen:!1});try{const e=await en();r({aboutInfo:e,aboutLoading:!1})}catch(e){r({showAbout:!1,aboutLoading:!1,error:e instanceof Error?e.message:String(e)})}}function us(){return n.moreMenuOpen?`<div class="more-menu" role="menu" id="more-menu">
    <button type="button" class="more-menu-item" data-more-action="settings">模型设置</button>
    <button type="button" class="more-menu-item" data-more-action="mcp">MCP 与联网搜索</button>
    <button type="button" class="more-menu-item" data-more-action="capabilities">能力检索</button>
    <button type="button" class="more-menu-item" data-more-action="system-prompt">系统提示词</button>
    <button type="button" class="more-menu-item" data-more-action="brief">会话简报</button>
    <button type="button" class="more-menu-item" data-more-action="diagnostics">诊断包</button>
    <button type="button" class="more-menu-item" data-more-action="failed">失败工具调用</button>
    <button type="button" class="more-menu-item" data-more-action="about">关于</button>
  </div>`:""}function ps(){if(!n.showFailedLogs)return"";const e=n.failedLogsScopeSession?"当前会话":"全部会话",t=n.failedLogs.map(s=>`<li class="failed-log-item">
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
  </div>`}function ms(){return!n.usagePanelOpen||!n.usage?"":`<div class="overlay" id="usage-overlay">
    <div class="overlay-panel usage-panel">
      <h2>累计用量</h2>
      <div class="usage-detail">${An(n.usage)}</div>
      <p class="muted">来自 agentRoot/logs/events.jsonl</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="usage-close">关闭</button>
      </div>
    </div>
  </div>`}function fs(){if(!n.showSettings||!n.settingsDraft)return"";const e=n.settingsDraft,t=n.config,s=z.map(d=>`<button type="button" class="provider-chip ${d.id===e.providerId?"active":""}" data-provider="${d.id}">${c(d.label)}</button>`).join(""),o=n.modelOptions.map(d=>{const v=d.modelId===e.model?"selected":"";return`<option value="${c(d.modelId)}" ${v}>${c(d.title)} · ${c(d.subtitle)}</option>`}).join(""),i=e.model&&!n.modelOptions.some(d=>d.modelId===e.model)?`<option value="${c(e.model)}" selected>${c(e.model)}（当前）</option>`:"",l=n.modelsLoading?"正在加载模型列表…":n.modelsStatus||(n.modelOptions.length?`共 ${n.modelOptions.length} 个模型`:"");return`<div class="overlay settings-overlay" id="settings-overlay">
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
  </div>`}function Ut(){var $;const e=n.sessions.map(g=>{const L=g.sessionId===n.activeSessionId?"active":"",x=g.title||g.sessionId.slice(0,8);return g.sessionId===n.pendingDeleteSessionId?`<div class="session-item ${L} session-item-confirm">
          <span class="session-confirm-text">删除此对话？</span>
          <button type="button" class="session-confirm-cancel" id="delete-session-cancel">取消</button>
          <button type="button" class="session-confirm-ok" id="delete-session-confirm">删除</button>
        </div>`:`<div class="session-item ${L}">
        <button type="button" class="session-open" data-use="${g.sessionId}">
          <span class="session-title">${c(x)}</span>
        </button>
        <button type="button" class="session-delete" data-del="${g.sessionId}" title="删除" aria-label="删除">✕</button>
      </div>`}).join(""),t=un(n.lines,n.isRunning,n.streamingText,n.streamingReasoning,!!n.pendingAsk),s=n.lines.map(g=>Zn(g,n.activeSessionId)).join(""),o=t?'<button type="button" class="new-topic-btn" id="btn-new-topic">开始新话题</button>':"",i=n.lastRunTokenSummary&&!n.isRunning?`<div class="run-token-summary ${n.lastRunTokenSummary.failed?"failed":""}">${c(kn(n.lastRunTokenSummary))}</div>`:"",l=n.lines.length===0&&!n.streamingText&&!n.isRunning?'<div class="landing"><h2>Agent1</h2><p>生产力助手 · 与 Android 端同款纸本风格。左侧管理会话，下方输入开始对话。</p></div>':`${Yn()}${s}${i}${o}${es()}${ts()}`,d=Ln(n.usage),v=bn(n.lines,n.isRunning,!!n.pendingAsk),m=n.isRunning||!!n.pendingAsk,h=n.pendingAsk?"请在上方卡片回答":n.isRunning?"生成中…":"输入消息，Enter 发送，Shift+Enter 换行";xt.innerHTML=`
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
        <span class="usage-headline">${c(d.headline)}</span>
        <span class="usage-detail-line">${c(d.detail)}</span>
      </button>
    </aside>
    <section class="main" id="app-main">
      <div class="topbar">
        <button type="button" class="icon-btn" id="btn-drawer" title="会话">☰</button>
        <h1>对话</h1>
        <div class="topbar-actions">
          <button type="button" class="icon-btn" id="btn-artifacts" title="产物库">📁</button>
          <div class="more-menu-anchor">
            <button type="button" class="icon-btn" id="btn-more" title="更多" aria-haspopup="menu" aria-expanded="${n.moreMenuOpen}">⋮</button>
            ${us()}
          </div>
        </div>
      </div>
      ${n.toast?`<div class="toast-banner">${c(n.toast)}</div>`:""}
      ${n.error?`<div class="error-banner">${c(n.error)}</div>`:""}
      <div class="chat-scroll" id="chat-scroll">${l}</div>
      <div class="composer-wrap">
        ${n.config&&!n.config.hasApiKey?'<div class="inline-setup">尚未配置 API Key。<button type="button" id="btn-setup">打开设置</button></div>':""}
        ${n.attachments.length?`<div class="attachment-chips">${n.attachments.map(g=>`<span class="chip">${c(g.fileName)} <button type="button" data-rm-attach="${c(g.workspaceRelativePath)}">×</button></span>`).join("")}</div>`:""}
        ${v?'<div class="continue-bar"><p>任务还没做完，点继续接着做完</p><button type="button" class="primary-btn" id="btn-continue">继续</button></div>':""}
        <div class="composer">
          <input type="file" id="file-input" class="hidden-file" multiple />
          <button type="button" class="icon-btn" id="btn-attach" title="添加附件" ${m?"disabled":""}>📎</button>
          <textarea id="composer" ${m?"disabled":""} placeholder="${h}">${c(n.composer)}</textarea>
          ${n.isRunning?'<button type="button" class="danger-btn" id="btn-stop">停止</button>':`<button type="button" class="primary-btn" id="btn-send" ${m?"disabled":""}>发送</button>`}
        </div>
        <div class="model-chip">模型 ${c((($=n.config)==null?void 0:$.model)||"—")} ·
          <button type="button" id="btn-model">更改</button></div>
      </div>
    </section>
    ${fs()}
    ${is()}
    ${rs()}
    ${cs()}
    ${ms()}
    ${ps()}
    ${ls()}
    ${ss()}
    ${os()}
    ${as()}
  </div>`,vs();const E=document.getElementById("chat-scroll");E&&!Dn()&&(E.scrollTop=E.scrollHeight)}function vs(){var i,l,d,v,m,h,E,$,g,L,x,se,oe,ae,ie,re,ce,le,de,ue,pe,me,fe,ve,ge,be,ye,he,we,Ee,Se,$e,Ie,ke,Te,Le,Pe,Ae,Re,xe,Be,Ce,Me,Oe,Ue,De,Ne,je,_e,qe,Fe,Ke,He,Je,We,Ge,ze,Xe,Qe,Ve,Ye;(i=document.getElementById("btn-new"))==null||i.addEventListener("click",()=>void ut()),(l=document.getElementById("btn-new-topic"))==null||l.addEventListener("click",()=>void ut()),(d=document.getElementById("btn-drawer"))==null||d.addEventListener("click",a=>{a.stopPropagation();const u=!n.drawerOpen;r({drawerOpen:u}),u&&A()}),(v=document.getElementById("app-main"))==null||v.addEventListener("click",a=>{if(!n.drawerOpen)return;const u=a.target;u!=null&&u.closest("button, a, input, textarea, select, label")||r({drawerOpen:!1})}),(m=document.getElementById("usage-footer"))==null||m.addEventListener("click",()=>{A().then(()=>r({usagePanelOpen:!0}))}),(h=document.getElementById("usage-close"))==null||h.addEventListener("click",()=>r({usagePanelOpen:!1})),(E=document.getElementById("usage-overlay"))==null||E.addEventListener("click",a=>{a.target===a.currentTarget&&r({usagePanelOpen:!1})}),($=document.getElementById("failed-close"))==null||$.addEventListener("click",()=>r({showFailedLogs:!1})),(g=document.getElementById("failed-refresh"))==null||g.addEventListener("click",()=>void X()),(L=document.getElementById("failed-scope-session"))==null||L.addEventListener("change",a=>{const u=a.target.checked;r({failedLogsScopeSession:u}),X()}),(x=document.getElementById("failed-logs-overlay"))==null||x.addEventListener("click",a=>{a.target===a.currentTarget&&r({showFailedLogs:!1})}),(se=document.getElementById("btn-more"))==null||se.addEventListener("click",a=>{a.stopPropagation(),r({moreMenuOpen:!n.moreMenuOpen})}),(oe=document.getElementById("more-menu"))==null||oe.addEventListener("click",a=>a.stopPropagation()),document.querySelectorAll("[data-more-action]").forEach(a=>{a.addEventListener("click",()=>{const u=a.dataset.moreAction;switch(gt(),u){case"settings":H();break;case"mcp":pt();break;case"capabilities":Jn();break;case"system-prompt":qn();break;case"brief":Gn().catch(p=>r({error:p instanceof Error?p.message:String(p)}));break;case"diagnostics":Ft().then(()=>w("诊断包已下载")).catch(p=>r({error:p instanceof Error?p.message:String(p)}));break;case"failed":Nn();break;case"about":ds();break}})}),n.moreMenuOpen&&window.setTimeout(()=>{if(!n.moreMenuOpen)return;const a=()=>{document.removeEventListener("click",a),gt()};document.addEventListener("click",a)},0),(ae=document.getElementById("capabilities-overlay"))==null||ae.addEventListener("click",a=>{const u=a.currentTarget.querySelector(".overlay-panel");u&&u.contains(a.target)&&a.stopPropagation()}),(ie=document.getElementById("about-close"))==null||ie.addEventListener("click",()=>r({showAbout:!1,aboutInfo:null,aboutLoading:!1})),(re=document.getElementById("about-overlay"))==null||re.addEventListener("click",a=>{a.target===a.currentTarget&&r({showAbout:!1,aboutInfo:null,aboutLoading:!1})}),(ce=document.getElementById("btn-setup"))==null||ce.addEventListener("click",()=>void H()),(le=document.getElementById("btn-model"))==null||le.addEventListener("click",()=>void H()),(de=document.getElementById("btn-artifacts"))==null||de.addEventListener("click",async()=>{const a=await Kt();r({artifacts:a,showArtifacts:!0})}),(ue=document.getElementById("artifacts-close"))==null||ue.addEventListener("click",()=>r({showArtifacts:!1})),(pe=document.getElementById("capabilities-close"))==null||pe.addEventListener("click",()=>r({showCapabilities:!1})),(me=document.getElementById("cap-search-btn"))==null||me.addEventListener("click",a=>{a.preventDefault(),a.stopPropagation(),vt()}),(fe=document.getElementById("cap-query"))==null||fe.addEventListener("keydown",a=>{a.key==="Enter"&&(a.preventDefault(),vt())}),document.querySelectorAll(".cap-kind-chip").forEach(a=>{a.addEventListener("click",()=>{const u=a.dataset.capKind;if(!u)return;const p=new Set(n.capSelectedKinds);p.has(u)?p.delete(u):p.add(u),r({capSelectedKinds:[...p]})})}),(ve=document.getElementById("cap-limit-down"))==null||ve.addEventListener("click",()=>{n.capLimit>1&&r({capLimit:n.capLimit-1})}),(ge=document.getElementById("cap-limit-up"))==null||ge.addEventListener("click",()=>{n.capLimit<te&&r({capLimit:n.capLimit+1})}),document.querySelectorAll("[data-art-preview]").forEach(a=>{a.addEventListener("click",()=>{const u=a.dataset.artSession,p=a.dataset.artPath;u&&p&&lt(u,p)})}),document.querySelectorAll("[data-art-insert]").forEach(a=>{a.addEventListener("click",async()=>{const u=a.dataset.artSession,p=a.dataset.artPath,b=n.activeSessionId;if(!(!b||!u||!p))try{await Ht(b,u,p),await D(b),w("已复制到 imports/")}catch(y){r({error:y instanceof Error?y.message:String(y)})}})}),Un(),(be=document.getElementById("preview-close"))==null||be.addEventListener("click",()=>K()),(ye=document.getElementById("preview-overlay"))==null||ye.addEventListener("click",a=>{a.target===a.currentTarget&&K()}),(he=document.getElementById("preview-inspect-toggle"))==null||he.addEventListener("click",()=>{const a=document.getElementById("preview-frame"),u=!n.previewInspectEnabled;r({previewInspectEnabled:u,previewSelectedElementJson:null,previewOpinion:""}),k(a,u?"enable":"disable")}),(we=document.getElementById("preview-inspect-cancel"))==null||we.addEventListener("click",()=>{const a=document.getElementById("preview-frame");k(a,"clear"),r({previewSelectedElementJson:null,previewOpinion:""})}),(Ee=document.getElementById("preview-fill-composer"))==null||Ee.addEventListener("click",async()=>{var b;const a=n.preview,u=n.previewSelectedElementJson;if(!a||!u)return;const p=((b=document.getElementById("preview-opinion"))==null?void 0:b.value)??n.previewOpinion;try{const y=await et(a.path,u,p);r({composer:y}),K(),w("已填入输入框")}catch(y){r({error:y instanceof Error?y.message:String(y)})}}),(Se=document.getElementById("preview-send-ai"))==null||Se.addEventListener("click",async()=>{var y;const a=n.preview,u=n.previewSelectedElementJson,p=a.sessionId;if(!u)return;if(n.isRunning){r({error:"请先停止当前 Run"});return}const b=((y=document.getElementById("preview-opinion"))==null?void 0:y.value)??n.previewOpinion;if(!b.trim()){w("请先描述问题");return}try{const I=await et(a.path,u,b);await $t(p,I);const B=document.getElementById("preview-frame");k(B,"clear"),r({previewSelectedElementJson:null,previewOpinion:"",isRunning:!0,streamingText:"",tools:[]}),w("已发送给 AI，修改后页面将自动刷新")}catch(I){r({error:I instanceof Error?I.message:String(I)})}}),($e=document.getElementById("preview-opinion"))==null||$e.addEventListener("input",a=>{r({previewOpinion:a.target.value})});const e=document.getElementById("file-input");(Ie=document.getElementById("btn-attach"))==null||Ie.addEventListener("click",()=>e==null?void 0:e.click()),e==null||e.addEventListener("change",async()=>{var u;const a=n.activeSessionId;if(!(!a||!((u=e.files)!=null&&u.length)))try{for(const p of Array.from(e.files))await Wt(a,p);await D(a),e.value=""}catch(p){r({error:p instanceof Error?p.message:String(p)})}}),document.querySelectorAll("[data-rm-attach]").forEach(a=>{a.addEventListener("click",async()=>{const u=n.activeSessionId,p=a.dataset.rmAttach;!u||!p||(await Gt(u,p),await D(u))})}),document.querySelectorAll("[data-use]").forEach(a=>{a.addEventListener("click",async()=>{const u=a.dataset.use;if(n.pendingDeleteSessionId&&r({pendingDeleteSessionId:null}),u!==n.activeSessionId){if(n.isRunning){r({error:"请先停止当前 Run 再切换会话"});return}await V(u),await R(u),await T()}})}),document.querySelectorAll("[data-del]").forEach(a=>{a.addEventListener("click",u=>{u.stopPropagation();const p=a.dataset.del;p&&r({pendingDeleteSessionId:p})})}),(ke=document.getElementById("delete-session-cancel"))==null||ke.addEventListener("click",a=>{a.stopPropagation(),r({pendingDeleteSessionId:null})}),(Te=document.getElementById("delete-session-confirm"))==null||Te.addEventListener("click",a=>{a.stopPropagation(),jn()});const t=document.getElementById("composer");t==null||t.addEventListener("input",()=>{n={...n,composer:t.value}}),t==null||t.addEventListener("keydown",a=>{a.key==="Enter"&&!a.shiftKey&&(a.preventDefault(),N())}),(Le=document.getElementById("btn-send"))==null||Le.addEventListener("click",()=>void N()),(Pe=document.getElementById("btn-continue"))==null||Pe.addEventListener("click",()=>void N("继续")),(Ae=document.getElementById("btn-stop"))==null||Ae.addEventListener("click",async()=>{await on()}),document.querySelectorAll("[data-ask-opt]").forEach(a=>{a.addEventListener("click",()=>{const u=a.dataset.askId,p=a.dataset.askOpt;r({askAnswers:{...n.askAnswers,[u]:p}})})}),document.querySelectorAll("[data-ask-input]").forEach(a=>{a.addEventListener("input",()=>{const u=a.dataset.askInput;n={...n,askAnswers:{...n.askAnswers,[u]:a.value}}})}),(Re=document.getElementById("ask-submit"))==null||Re.addEventListener("click",()=>void Qn());const s=()=>r({showSettings:!1,settingsDraft:null,modelsStatus:null,modelsLoading:!1});(xe=document.getElementById("cfg-close"))==null||xe.addEventListener("click",s),(Be=document.getElementById("settings-overlay"))==null||Be.addEventListener("click",a=>{a.target===a.currentTarget&&s()}),document.querySelectorAll("[data-provider]").forEach(a=>{a.addEventListener("click",()=>{var p;const u=a.dataset.provider;u&&u!==((p=n.settingsDraft)==null?void 0:p.providerId)&&_n(u)})});const o=a=>{n.settingsDraft&&r({settingsDraft:{...n.settingsDraft,...a}})};(Ce=document.getElementById("cfg-model"))==null||Ce.addEventListener("change",a=>{o({model:a.target.value})}),(Me=document.getElementById("cfg-ctx-turns"))==null||Me.addEventListener("change",a=>{o({maxContextTurns:parseInt(a.target.value,10)||0})}),(Oe=document.getElementById("cfg-ctx-msgs"))==null||Oe.addEventListener("change",a=>{o({maxContextMessages:parseInt(a.target.value,10)||0})}),(Ue=document.getElementById("cfg-run-turns"))==null||Ue.addEventListener("change",a=>{o({maxTurnsPerRun:parseInt(a.target.value,10)||0})}),(De=document.getElementById("cfg-tool-calls"))==null||De.addEventListener("change",a=>{o({maxToolCallsPerRun:parseInt(a.target.value,10)||0})}),(Ne=document.getElementById("cfg-open-mcp"))==null||Ne.addEventListener("click",()=>void pt()),(je=document.getElementById("mcp-close"))==null||je.addEventListener("click",()=>r({showMcp:!1})),(_e=document.getElementById("mcp-web-key"))==null||_e.addEventListener("input",()=>mt()),(qe=document.getElementById("mcp-web-base"))==null||qe.addEventListener("input",()=>mt()),(Fe=document.getElementById("mcp-list"))==null||Fe.addEventListener("input",()=>O()),(Ke=document.getElementById("mcp-list"))==null||Ke.addEventListener("change",()=>O()),(He=document.getElementById("system-prompt-close"))==null||He.addEventListener("click",()=>r({showSystemPrompt:!1,systemPromptError:null})),(Je=document.getElementById("system-prompt-overlay"))==null||Je.addEventListener("click",a=>{a.target===a.currentTarget&&r({showSystemPrompt:!1,systemPromptError:null})}),(We=document.getElementById("system-prompt-copy"))==null||We.addEventListener("click",async()=>{if(n.systemPromptText)try{await navigator.clipboard.writeText(n.systemPromptText),w("已复制系统提示词")}catch(a){r({error:a instanceof Error?a.message:String(a)})}}),(Ge=document.getElementById("mcp-add"))==null||Ge.addEventListener("click",()=>{r({mcpServers:[...n.mcpServers,{name:"server",url:"https://",enabled:!0,headers:{}}]}),O()}),document.querySelectorAll(".mcp-remove").forEach(a=>{a.addEventListener("click",()=>{const u=Number(a.dataset.mcpIndex);r({mcpServers:n.mcpServers.filter((p,b)=>b!==u)}),O()})}),document.querySelectorAll(".file-link").forEach(a=>{a.addEventListener("click",u=>{u.stopPropagation();const p=a.dataset.filePath,b=n.activeSessionId;if(!p||!b){w("无法打开文件");return}Lt(p)?lt(b,p):w(p)})}),(ze=document.getElementById("cfg-show-tools"))==null||ze.addEventListener("click",async()=>{try{const{tools:a}=await _t();r({showTools:!0,toolsList:a})}catch(a){r({error:a instanceof Error?a.message:String(a)})}}),(Xe=document.getElementById("tools-close"))==null||Xe.addEventListener("click",()=>r({showTools:!1})),(Qe=document.getElementById("tools-overlay"))==null||Qe.addEventListener("click",a=>{a.target===a.currentTarget&&r({showTools:!1})}),(Ve=document.getElementById("cfg-refresh-models"))==null||Ve.addEventListener("click",()=>{const a=n.settingsDraft;a&&ne(a,!0)}),(Ye=document.getElementById("cfg-save"))==null||Ye.addEventListener("click",async()=>{const a=n.settingsDraft;if(!a)return;const u=document.getElementById("cfg-key").value,p=document.getElementById("cfg-base").value,b=document.getElementById("cfg-model").value,y=parseInt(document.getElementById("cfg-ctx-turns").value,10),I=parseInt(document.getElementById("cfg-ctx-msgs").value,10),B=parseInt(document.getElementById("cfg-run-turns").value,10),Ze=parseInt(document.getElementById("cfg-tool-calls").value,10);try{const P=await wt({apiKey:u||void 0,baseUrl:p||void 0,model:b||void 0,providerId:a.providerId,maxContextTurns:Number.isFinite(y)?y:0,maxContextMessages:Number.isFinite(I)?I:0,maxTurnsPerRun:Number.isFinite(B)?B:0,maxToolCallsPerRun:Number.isFinite(Ze)?Ze:0});r({config:P,showSettings:!1,settingsDraft:null,modelsStatus:null,error:null}),w("设置已保存")}catch(P){r({error:P instanceof Error?P.message:String(P)})}})}window.addEventListener("message",e=>{if(e.origin!==window.location.origin||!e.data||e.data.type!==Bn||!n.preview||!n.previewInspectEnabled)return;const t=typeof e.data.payload=="string"?e.data.payload:null;t&&r({previewSelectedElementJson:t,previewOpinion:""})});Ut();zn().catch(e=>{r({error:e instanceof Error?e.message:String(e)})});window.addEventListener("beforeunload",()=>U==null?void 0:U());
