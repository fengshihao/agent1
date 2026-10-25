(function(){const t=document.createElement("link").relList;if(t&&t.supports&&t.supports("modulepreload"))return;for(const i of document.querySelectorAll('link[rel="modulepreload"]'))a(i);new MutationObserver(i=>{for(const c of i)if(c.type==="childList")for(const d of c.addedNodes)d.tagName==="LINK"&&d.rel==="modulepreload"&&a(d)}).observe(document,{childList:!0,subtree:!0});function s(i){const c={};return i.integrity&&(c.integrity=i.integrity),i.referrerPolicy&&(c.referrerPolicy=i.referrerPolicy),i.crossOrigin==="use-credentials"?c.credentials="include":i.crossOrigin==="anonymous"?c.credentials="omit":c.credentials="same-origin",c}function a(i){if(i.ep)return;i.ep=!0;const c=s(i);fetch(i.href,c)}})();async function m(e){const t=await e.json();if(!e.ok)throw new Error(t.error||e.statusText);return t}async function ht(){return m(await fetch("/api/config"))}async function wt(e){return m(await fetch("/api/config",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(e)}))}async function Ut(){return m(await fetch("/api/usage"))}async function Dt(e,t=!1){const s=new URLSearchParams({provider:e});return t&&s.set("refresh","1"),m(await fetch(`/api/models?${s}`))}async function Et(){return m(await fetch("/api/mcp/servers"))}async function Nt(e){return m(await fetch("/api/mcp/servers",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({servers:e})}))}async function jt(){return m(await fetch("/api/tools"))}async function _t(e){return m(await fetch(`/api/sessions/${encodeURIComponent(e)}/system-prompt`))}async function qt(){var c,d;const e=await fetch("/api/diagnostics/export");if(!e.ok)throw new Error("诊断包导出失败");const t=await e.blob(),s=((d=(c=e.headers.get("Content-Disposition"))==null?void 0:c.match(/filename="([^"]+)"/))==null?void 0:d[1])??`agent1-diag-${Date.now()}.zip`,a=URL.createObjectURL(t),i=document.createElement("a");i.href=a,i.download=s,i.click(),URL.revokeObjectURL(a)}async function Ft(){return(await m(await fetch("/api/artifacts"))).artifacts}async function Kt(e,t,s){return m(await fetch(`/api/sessions/${encodeURIComponent(e)}/artifacts/import`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({sourceSessionId:t,workspaceRelativePath:s})}))}async function Ht(e){return(await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`))).files}async function Jt(e,t){const s=new FormData;return s.append("file",t),m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments`,{method:"POST",body:s}))}async function Wt(e,t){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/attachments/${encodeURIComponent(t)}`,{method:"DELETE"}))}function Gt(e,t){const s=t.split("/").map(a=>encodeURIComponent(a)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${s}`}function zt(e,t){const s=t.split("/").map(a=>encodeURIComponent(a)).join("/");return`/api/sessions/${encodeURIComponent(e)}/preview/${s}`}async function Xt(e,t){const s=new URLSearchParams({path:t});return m(await fetch(`/api/sessions/${encodeURIComponent(e)}/preview-meta?${s}`))}async function et(e,t,s){return(await m(await fetch("/api/html-inspect/compose",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({relativePath:e,elementJson:t,problem:s})}))).text||""}async function Qt(e,t,s){const a=new URLSearchParams;return a.set("q",e),a.set("limit",String(s)),t.length&&a.set("kinds",t.join(",")),m(await fetch(`/api/capabilities?${a.toString()}`))}async function Vt(){return m(await fetch("/api/sessions"))}async function Q(){return m(await fetch("/api/sessions",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"}))}async function V(e){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/use`,{method:"POST",body:"{}"}))}async function Yt(e){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}`,{method:"DELETE"}))}async function Zt(){return m(await fetch("/api/about"))}async function en(e){return m(await fetch(`/api/sessions/${encodeURIComponent(e)}/brief`))}async function tn(e){const t=e?`?sessionId=${encodeURIComponent(e)}`:"";return(await m(await fetch(`/api/logs/failed${t}`))).items}async function St(e){return(await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/transcript`))).messages}async function $t(e,t){await m(await fetch(`/api/sessions/${encodeURIComponent(e)}/messages`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({text:t})}))}async function nn(){await m(await fetch("/api/run/abort",{method:"POST",body:"{}"}))}function sn(e){const t=new EventSource("/api/events");return t.onmessage=s=>{try{e(JSON.parse(s.data))}catch{}},()=>t.close()}const an="ask_user",on=["等待用户","waiting","ask_user"];function rn(e){try{const s=(JSON.parse(e.argumentsJson||"{}").questions||[]).map((a,i)=>({id:a.id||`q${i}`,prompt:a.prompt||a.question||"",options:a.options,allowFreeText:a.allowFreeText??a.allow_free_text??!0})).filter(a=>a.prompt);return s.length===0?null:{questions:s}}catch{return null}}function kt(e){if(!e.length)return null;for(let t=e.length-1;t>=0;t--){const s=e[t];if(s.role==="user")return null;if(s.role==="assistant"){for(const a of s.toolCalls||[])if(a.name===an)return rn(a)}if(s.role==="toolResult"){const a=s.content||"";if(!on.some(c=>a.toLowerCase().includes(c.toLowerCase())))return null}}return null}function ln(e,t){return e.questions.map(a=>{const i=(t[a.id]||"").trim();return`${a.prompt}
→ ${i||"(未答)"}`}).join(`

`)}function cn(e,t,s,a,i){if(t||i||s.trim()||a.trim())return!1;const c=dn(e);return(c==null?void 0:c.role)==="assistant"}function dn(e){return e.filter(t=>t.role==="user"||t.role==="assistant").at(-1)}function un(){return{sessions:[],activeSessionId:null,config:null,lines:[],streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,pendingAsk:null,askAnswers:{},error:null,showSettings:!1,drawerOpen:!0,composer:"",usage:null,usagePanelOpen:!1,toast:null,attachments:[],artifacts:[],showArtifacts:!1,preview:null,previewReloadKey:0,previewIsDeck:!1,previewInspectEnabled:!1,previewSelectedElementJson:null,previewOpinion:"",showCapabilities:!1,capQuery:"",capSelectedKinds:[],capLimit:8,capBusy:!1,capError:null,capSearched:!1,capModelText:"",capHostPlatform:"",capHiddenHits:[],capDisabledMcp:[],modelOptions:[],settingsDraft:null,modelsLoading:!1,modelsStatus:null,showTools:!1,toolsList:[],lastRunTokenSummary:null,showMcp:!1,mcpServers:[],mcpStatus:null,showFailedLogs:!1,failedLogs:[],failedLogsScopeSession:!0,moreMenuOpen:!1,showAbout:!1,aboutLoading:!1,aboutInfo:null,showSystemPrompt:!1,systemPromptBusy:!1,systemPromptError:null,systemPromptText:"",systemPromptTools:[],mcpWebSearchBaseUrl:"https://api.tavily.com",mcpWebSearchApiKey:"",pendingDeleteSessionId:null}}function It(e){const t=e.split("/").pop()??e,s=t.lastIndexOf(".");return s>=0?t.slice(s+1).toLowerCase():""}function Tt(e){const t=It(e);return t==="html"||t==="htm"}const pn=new Set(["html","htm","svg","png","jpg","jpeg","gif","webp"]);function Lt(e){return pn.has(It(e))}function mn(e){return e.isRunning||e.streamingText.trim()||e.streamingReasoning.trim()||e.tools.length>0||e.pendingAsk?!1:!e.lines.some(t=>t.role==="user"||t.role==="assistant")}const tt="往返轮次已达上限";function nt(e){return e.includes("单轮模型与工具往返超过上限")||e.includes("单轮工具调用次数超过上限")||e.includes("工具调用次数超过上限")||e.includes("insufficient tool messages")}function fn(e){return e.trim().startsWith("错误:")}function vn(e,t,s){if(t||s)return!1;let a=-1;if(e.forEach((c,d)=>{const f=c.role==="user"&&c.content.includes(tt),g=c.role==="assistant"&&fn(c.content);(f||g||nt(c.content))&&(a=d)}),a<0)return!1;let i=!1;for(let c=a+1;c<e.length;c+=1){const d=e[c];if(d.role==="user"&&!d.content.includes(tt)&&(i=!0),i&&d.role==="assistant"&&d.content.trim()&&!nt(d.content))return!1}return!0}function Pt(e){const t=[];return e.forEach((s,a)=>{var i;if(s.role==="user"){t.push({id:`u-${a}`,role:"user",content:s.content});return}if(s.role==="assistant"){(i=s.content)!=null&&i.trim()&&t.push({id:`a-${a}`,role:"assistant",content:s.content});for(const c of s.toolCalls||[])t.push({id:`t-${c.id}`,role:"tool",content:c.argsPreview||c.argumentsJson||"",toolName:c.name});return}if(s.role==="toolResult"){t.push({id:`tr-${a}`,role:"tool",content:s.content,toolName:"result",error:!!s.error});return}s.role}),t}const gn=new Set(["png","jpg","jpeg","webp","gif","svg"]);function J(e){var s;const t=((s=e.split(".").pop())==null?void 0:s.toLowerCase())??"";return gn.has(t)}function At(e){const t=/!\[[^\]]*]\(([^)]+)\)/g,s=[];let a;for(;(a=t.exec(e))!==null;){const i=a[1].trim();i&&!i.startsWith("http://")&&!i.startsWith("https://")&&s.push(Y(i))}return s}function Y(e){return e.replace(/^\.\//,"").replace(/^workspace\//,"")}function bn(e){const t=[],s=/```[\w-]*\n?([\s\S]*?)```/g;let a=0,i;for(;(i=s.exec(e))!==null;)i.index>a&&t.push({kind:"text",text:e.slice(a,i.index)}),t.push({kind:"code",text:i[1]??""}),a=i.index+i[0].length;return a<e.length&&t.push({kind:"text",text:e.slice(a)}),t.length?t:[{kind:"text",text:e}]}function W(e,t){return e?bn(e).map(s=>s.kind==="code"?`<pre class="bubble-code"><code>${S(s.text.trimEnd())}</code></pre>`:yn(s.text,t)).join(""):""}function yn(e,t){const s=/!?\[([^\]]*)]\(([^)]+)\)/g;let a="",i=0,c;for(;(c=s.exec(e))!==null;){a+=st(e.slice(i,c.index));const d=c[1],f=c[2].trim(),g=c[0].startsWith("!");if(t&&!f.startsWith("http://")&&!f.startsWith("https://")){const h=Y(f);g&&J(h)?a+=`<figure class="bubble-image"><img src="${Rt(t,h)}" alt="${G(h)}" loading="lazy"/></figure>`:g?a+=S(c[0]):a+=`<button type="button" class="file-link" data-file-path="${G(h)}">${S(d||h)}</button>`}else a+=S(c[0]);i=c.index+c[0].length}return a+=st(e.slice(i)),a}function st(e){if(!e)return"";const t=e.split(`
`);let s="",a=!1;for(const i of t){const c=i.trimEnd(),d=c.match(/^[-—]\s+(.*)$/);if(d){a||(s+='<ul class="bubble-list">',a=!0),s+=`<li>${at(d[1])}</li>`;continue}a&&(s+="</ul>",a=!1),c===""?s+='<div class="bubble-gap"></div>':s+=`<p class="bubble-p">${at(c)}</p>`}return a&&(s+="</ul>"),s}function at(e){const t=e.split(/`([^`\n]+)`/);let s="";for(let a=0;a<t.length;a++)a%2===0?s+=hn(t[a]):s+=`<code class="bubble-inline-code">${S(t[a])}</code>`;return s}function hn(e){const t=/\*\*(.+?)\*\*/g;let s="",a=0,i;for(;(i=t.exec(e))!==null;)s+=S(e.slice(a,i.index)),s+=`<strong>${S(i[1])}</strong>`,a=i.index+i[0].length;return s+=S(e.slice(a)),s}function Rt(e,t){const s=t.split("/").map(a=>encodeURIComponent(a)).join("/");return`/api/sessions/${encodeURIComponent(e)}/files/${s}`}function S(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}function G(e){return S(e).replaceAll("'","&#39;")}function wn(e){const t=At(e).filter(J),s=new Set,a=[];for(const d of t)s.has(d)||(s.add(d),a.push(d));const i=/(?:imports|out|tmp|staging)[/][\w./-]+\.(?:png|jpe?g|webp|gif)/gi;let c;for(;(c=i.exec(e))!==null;){const d=Y(c[0]);J(d)&&!s.has(d)&&(s.add(d),a.push(d))}return a}function ot(e,t){const s=new Set(At(e));return wn(e).filter(a=>!s.has(a)).map(a=>`<figure class="bubble-image"><img src="${Rt(t,a)}" alt="${G(a)}" loading="lazy"/></figure>`).join("")}let j=0,_=0,Z=0;function En(){j=0,_=0,Z=0}function Sn(e){j+=Number(e.inputTokens)||0,_+=Number(e.outputTokens)||0;const t=e.cachedTokens;t!=null&&(Z+=Number(t)||0)}function it(e){const t=j>0||_>0;return!e&&!t?null:{inputTokens:j,outputTokens:_,cachedTokens:Z,failed:e}}function $n(e){const t=[`输入 ${rt(e.inputTokens)}`,`输出 ${rt(e.outputTokens)}`];if(e.inputTokens>0){const s=Math.min(100,Math.round(e.cachedTokens*100/e.inputTokens));t.push(`缓存命中 ${s}%`)}return e.failed&&t.push("未完成"),t.join(" · ")}function rt(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,s=Math.trunc(t);return t===s?`${s}k`:`${t.toFixed(1)}k`}const z=[{id:"dashscope",label:"通义",baseUrl:"https://dashscope.aliyuncs.com/compatible-mode/v1",defaultModel:"qwen3.7-flash"},{id:"deepseek",label:"DeepSeek",baseUrl:"https://api.deepseek.com",defaultModel:"deepseek-flash"},{id:"zhipu_coding",label:"智谱 Coding",baseUrl:"https://open.bigmodel.cn/api/coding/paas/v4",defaultModel:"glm-5.3"}];function kn(e){const t=e.toLowerCase();return t.includes("deepseek.com")?"deepseek":t.includes("bigmodel.cn")?"zhipu_coding":"dashscope"}function ee(e){return z.find(t=>t.id===e)??z[0]}function C(e){if(e<1e3)return String(e);const t=Math.round(e/100)/10,s=Math.trunc(t);return t===s?`${s}k`:`${t.toFixed(1)}k`}function In(e){if(!e||!e.hasUsage)return{headline:"累计用量",detail:"暂无 usage 记录"};const t=`约 ${C(e.totalTokens)} tokens`,s=e.cacheHitPercent,a=s!=null?`输入 ${C(e.inputTokens)} · 缓存 ${s}%`:`输入 ${C(e.inputTokens)} · 输出 ${C(e.outputTokens)}`;return{headline:t,detail:a}}function Tn(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;")}function Ln(e){const t=[`输入 ${e.inputTokens.toLocaleString()}`,`输出 ${e.outputTokens.toLocaleString()}`,`缓存 ${e.cachedTokens.toLocaleString()}`,`usage 事件 ${e.usageEvents}`];let s="";return e.byModel.length&&(s="<ul>"+e.byModel.map(a=>`<li><strong>${Tn(a.modelId||"（未知模型）")}</strong>：入 ${a.inputTokens.toLocaleString()} / 出 ${a.outputTokens.toLocaleString()}</li>`).join("")+"</ul>"),`<p>${t.join(" · ")}</p>${s}`}const Pn=[{id:"caps",label:"Caps"},{id:"builtin",label:"内置"},{id:"weizhi_package",label:"官方包"},{id:"catalog_script",label:"catalog 脚本"},{id:"catalog_lib",label:"catalog 库"},{id:"native",label:"native"},{id:"skill",label:"Skill"},{id:"mcp",label:"MCP"},{id:"bridge_tool",label:"桥接"},{id:"agent_tool",label:"外层工具"}],te=20;function An(e){return e==="listed_but_unusable"?"已索引，当前不可用":e==="platform_hidden"?"当前平台不可用":"可用"}const Rn="agent1-inspect",xn="agent1-inspect-cmd";function I(e,t){e!=null&&e.contentWindow&&e.contentWindow.postMessage({type:xn,cmd:t},window.location.origin)}const xt=document.getElementById("app");if(!xt)throw new Error("#app missing");let n=un(),U=null,q,F,M;const lt={};function Bn(e){if(!e)return"";try{return JSON.parse(e).tag||"element"}catch{return"element"}}function ct(e,t,s){r({preview:{sessionId:e,path:t},previewReloadKey:0,previewIsDeck:!1,previewInspectEnabled:!1,previewSelectedElementJson:null,previewOpinion:"",showArtifacts:!1,...s}),Tt(t)&&Xt(e,t).then(a=>{var i,c;((i=n.preview)==null?void 0:i.sessionId)===e&&((c=n.preview)==null?void 0:c.path)===t&&r({previewIsDeck:a.isDeck})},()=>{})}function K(){const e=document.getElementById("preview-frame");n.previewInspectEnabled&&I(e,"disable"),r({preview:null,previewInspectEnabled:!1,previewSelectedElementJson:null,previewOpinion:"",previewIsDeck:!1})}function Cn(){n.preview&&(M&&clearTimeout(M),M=setTimeout(()=>{if(M=void 0,!n.preview)return;const e=document.getElementById("preview-frame"),t=n.previewInspectEnabled;r({previewReloadKey:n.previewReloadKey+1,previewSelectedElementJson:null,previewOpinion:""}),window.setTimeout(()=>{I(e,"install"),t&&I(e,"enable")},50)},300))}function Mn(){const e=document.getElementById("preview-frame");e&&e.addEventListener("load",()=>{I(e,"install"),n.previewInspectEnabled&&I(e,"enable")})}function dt(e,t){const s=lt[e]??!1;return lt[e]=t,t&&s?" overlay-no-enter":""}function On(){return n.showCapabilities||n.showSettings||n.showMcp||n.showArtifacts||n.showSystemPrompt||n.showAbout||n.showTools||n.usagePanelOpen||n.showFailedLogs}function Bt(e){const t=document.getElementById("cap-search-btn");t&&(t.disabled=e,t.textContent=e?"…":"搜索");const s=document.getElementById("cap-limit-down"),a=document.getElementById("cap-limit-up");s&&(s.disabled=e||n.capLimit<=1),a&&(a.disabled=e||n.capLimit>=te),document.querySelectorAll(".cap-kind-chip").forEach(i=>{i.disabled=e})}function r(e){n={...n,...e},Ot()}function l(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;")}async function T(){const e=await Vt();r({sessions:e.sessions.slice().sort((t,s)=>s.updatedAt.localeCompare(t.updatedAt)),activeSessionId:e.activeSessionId??n.activeSessionId,isRunning:e.runInProgress})}async function A(){try{const e=await Ut();r({usage:e})}catch{}}async function X(){const e=n.failedLogsScopeSession&&n.activeSessionId?n.activeSessionId:null,t=await tn(e);r({failedLogs:t})}async function Un(){r({showFailedLogs:!0});try{await X()}catch(e){r({error:e instanceof Error?e.message:String(e)})}}function w(e){r({toast:e}),window.setTimeout(()=>{n.toast===e&&r({toast:null})},2200)}async function ut(){if(n.pendingDeleteSessionId&&r({pendingDeleteSessionId:null}),n.isRunning){r({error:"请先停止当前 Run"});return}if(n.activeSessionId&&mn(n)){w("已是新会话");return}const e=await Q();await T(),await R(e.sessionId),await A(),r({drawerOpen:!1,pendingDeleteSessionId:null})}async function Dn(){const e=n.pendingDeleteSessionId;if(!e)return;const t=n.activeSessionId===e;r({pendingDeleteSessionId:null});try{if(await Yt(e),await T(),!t)return;if(n.sessions[0])await V(n.sessions[0].sessionId),await R(n.sessions[0].sessionId);else{const s=await Q();await T(),await R(s.sessionId)}}catch(s){r({error:s instanceof Error?s.message:String(s)})}}function Ct(){const e=n.config,t=(e==null?void 0:e.baseUrl)||"",s=(e==null?void 0:e.providerId)||kn(t);return{providerId:s,baseUrl:t,model:(e==null?void 0:e.model)||ee(s).defaultModel,maxContextTurns:(e==null?void 0:e.maxContextTurns)??6,maxContextMessages:(e==null?void 0:e.maxContextMessages)??0,maxTurnsPerRun:(e==null?void 0:e.maxTurnsPerRun)??24,maxToolCallsPerRun:(e==null?void 0:e.maxToolCallsPerRun)??48}}async function ne(e,t){var s,a;r({modelsLoading:!0});try{const{models:i,source:c}=await Dt(e.providerId,t);let d=e.model;if(!i.some(f=>f.modelId===d)){const f=ee(e.providerId);d=((s=i.find(g=>g.modelId===f.defaultModel))==null?void 0:s.modelId)??((a=i[0])==null?void 0:a.modelId)??d}r({modelOptions:i,modelsLoading:!1,settingsDraft:{...e,model:d},modelsStatus:t?`已从网络更新 · ${i.length} 个模型`:`共 ${i.length} 个模型`})}catch(i){r({modelsLoading:!1,error:i instanceof Error?i.message:"模型列表加载失败"})}}function Nn(e){const t=ee(e),s={...n.settingsDraft??Ct(),providerId:e,baseUrl:t.baseUrl,model:t.defaultModel};r({settingsDraft:s,error:null}),ne(s,!1)}async function H(){const e=Ct();r({showSettings:!0,settingsDraft:e,modelOptions:[],error:null,moreMenuOpen:!1,showMcp:!1,showSystemPrompt:!1}),await ne(e,!1)}async function pt(){try{const[{servers:e},t]=await Promise.all([Et(),ht()]);r({config:t,showMcp:!0,mcpServers:e,mcpStatus:null,showSettings:!1,showSystemPrompt:!1,moreMenuOpen:!1,mcpWebSearchBaseUrl:t.webSearchBaseUrl||"https://api.tavily.com",mcpWebSearchApiKey:""})}catch(e){r({error:e instanceof Error?e.message:String(e)})}}async function jn(){const e=n.activeSessionId;if(!e){r({showSystemPrompt:!0,showSettings:!1,showMcp:!1,systemPromptBusy:!1,systemPromptError:"先打开或新建一个会话，再查看发给模型的系统提示词。",systemPromptText:"",systemPromptTools:[],moreMenuOpen:!1});return}r({showSystemPrompt:!0,showSettings:!1,showMcp:!1,systemPromptBusy:!0,systemPromptError:null,moreMenuOpen:!1});try{const t=await _t(e);r({showSystemPrompt:!0,systemPromptBusy:!1,systemPromptError:null,systemPromptText:t.prompt||"",systemPromptTools:t.tools||[]})}catch(t){r({showSystemPrompt:!0,systemPromptBusy:!1,systemPromptError:t instanceof Error?t.message:String(t)})}}function _n(){const e=document.querySelectorAll(".mcp-card"),t=[];return e.forEach((s,a)=>{var h,E,$,v;const i=n.mcpServers[a],c=((h=s.querySelector(".mcp-name"))==null?void 0:h.value.trim())||"",d=((E=s.querySelector(".mcp-url"))==null?void 0:E.value.trim())||"",f=(($=s.querySelector(".mcp-enabled"))==null?void 0:$.checked)??!0;let g={};try{g=JSON.parse(((v=s.querySelector(".mcp-headers"))==null?void 0:v.value)||"{}")}catch{}t.push({name:c,url:d,enabled:f,headers:g,toolCount:i==null?void 0:i.toolCount,lastListedAt:i==null?void 0:i.lastListedAt})}),t}function O(){n.showMcp&&(q&&clearTimeout(q),q=setTimeout(()=>void qn(),700))}function mt(){n.showMcp&&(F&&clearTimeout(F),F=setTimeout(()=>void Fn(),700))}async function qn(){if(!n.showMcp)return;const e=_n();try{const t=await Nt(e),{servers:s}=await Et(),a=t.warnings.length?` · ${t.warnings.join("; ")}`:"";r({mcpServers:s,mcpStatus:`已自动保存 · 索引 ${t.toolCount} 个 MCP 工具${a}`})}catch(t){r({error:t instanceof Error?t.message:String(t)})}}async function Fn(){var i,c;const e=document.getElementById("mcp-web-key"),t=document.getElementById("mcp-web-base"),s=((i=e==null?void 0:e.value)==null?void 0:i.trim())||void 0,a=((c=t==null?void 0:t.value)==null?void 0:c.trim())||void 0;try{const d=await wt({webSearchApiKey:s,webSearchBaseUrl:a});r({config:d,mcpWebSearchApiKey:(e==null?void 0:e.value)??"",mcpWebSearchBaseUrl:d.webSearchBaseUrl||a||"https://api.tavily.com",mcpStatus:d.hasWebSearch?"已自动保存 · 联网搜索已配置":"已自动保存 · 尚未配置 Key（web_search 不会注册）"})}catch(d){r({error:d instanceof Error?d.message:String(d)})}}function Kn(){r({showCapabilities:!0,showArtifacts:!1,moreMenuOpen:!1,capError:null})}function Mt(){if(!n.capSearched)return"";const e=n.capHostPlatform?`（平台 ${l(n.capHostPlatform)}）`:"",t=[n.capHiddenHits.length?`<div class="cap-results-tail">
            <h3 class="cap-section-title">当前平台搜不到</h3>
            <p class="muted">索引里有这些条目，但宿主平台过滤后不会出现在上面的正文里。</p>
            ${n.capHiddenHits.map(Yn).join("")}
          </div>`:"",n.capDisabledMcp.length?`<div class="cap-results-tail">
            <h3 class="cap-section-title">未启用的 MCP</h3>
            <p class="muted">未启用的服务器不会写入能力索引，模型的 find_caps 也找不到。</p>
            ${n.capDisabledMcp.map(s=>`<div class="cap-hit-card"><strong>${l(s.name)}</strong><div class="muted small">${l(s.url)}</div><div>${l(s.description)}</div></div>`).join("")}
          </div>`:""].join("");return`<div class="cap-results">
        ${n.capError?`<p class="error-inline">${l(n.capError)}</p>`:""}
        ${n.capError?"":`<div class="cap-model-block">
            <h3 class="cap-section-title">模型看到的正文${e}</h3>
            <pre class="cap-result">${l(n.capModelText||"（空）")}</pre>
          </div>`}
        ${t}
      </div>`}function Hn(){var a;const e=document.querySelector("#capabilities-overlay .cap-panel-body");if(!e)return;(a=e.querySelector(".cap-results"))==null||a.remove();const t=Mt();if(!t)return;const s=e.querySelector(".cap-panel-toolbar");s?s.insertAdjacentHTML("afterend",t):e.insertAdjacentHTML("beforeend",t)}function ft(e){n={...n,...e,capBusy:!1},Bt(!1);const t=document.querySelector("#capabilities-overlay .cap-limit-row span");t&&(t.textContent=String(n.capLimit)),Hn()}async function vt(){const e=document.getElementById("cap-query"),t=((e==null?void 0:e.value)??n.capQuery).trim();if(!(!t||n.capBusy)){n={...n,capQuery:t,capBusy:!0,capError:null},Bt(!0);try{const s=await Qt(t,n.capSelectedKinds,n.capLimit);ft({capSearched:!0,capError:null,capModelText:s.modelText,capHostPlatform:s.hostPlatform,capLimit:s.limit,capHiddenHits:s.hiddenByPlatform,capDisabledMcp:s.disabledMcp})}catch(s){ft({capSearched:!0,capError:s instanceof Error?s.message:String(s),capModelText:"",capHostPlatform:"",capHiddenHits:[],capDisabledMcp:[]})}}}async function Jn(){const e=n.activeSessionId;if(!e)return;const{text:t}=await en(e);await navigator.clipboard.writeText(t),w("简报已复制到剪贴板")}function gt(){n.moreMenuOpen&&r({moreMenuOpen:!1})}async function D(e){try{const t=await Ht(e);r({attachments:t})}catch{r({attachments:[]})}}async function R(e){const t=await St(e),s=kt(t);r({activeSessionId:e,lines:Pt(t),pendingAsk:s,askAnswers:{},streamingText:"",streamingReasoning:"",tools:[],error:null}),await D(e)}async function Wn(){const[e]=await Promise.all([ht(),T()]);r({config:e});let t=n.activeSessionId;t||(n.sessions.length===0?(t=(await Q()).sessionId,await T()):(t=n.sessions[0].sessionId,await V(t))),await R(t),await A(),U=sn(Gn)}function Gn(e){var t;if(e.sessionId&&n.activeSessionId&&e.sessionId!==n.activeSessionId){const s=(t=n.preview)==null?void 0:t.sessionId;if(!s||e.sessionId!==s)return}switch(e.type){case"AGENT_START":En(),r({isRunning:!0,streamingText:"",streamingReasoning:"",tools:[],error:null,lastRunTokenSummary:null});break;case"USAGE":Sn(e.payload);break;case"MESSAGE_UPDATE":r({streamingText:n.streamingText+String(e.payload.delta||""),isRunning:!0});break;case"REASONING_UPDATE":r({streamingReasoning:n.streamingReasoning+String(e.payload.delta||""),isRunning:!0});break;case"MESSAGE_RESET":r({streamingText:"",streamingReasoning:""});break;case"TOOL_EXECUTION_START":{const s=e.payload.toolCall,a={id:s.id||`tool-${Date.now()}`,toolCallId:s.id||"",toolName:s.name||"tool",argsPreview:s.argsPreview||"",running:!0};r({tools:[...n.tools,a],isRunning:!0});break}case"TOOL_EXECUTION_END":{const s=String(e.payload.toolCallId||""),a=n.tools.find(d=>d.toolCallId===s),i=n.tools.map(d=>d.toolCallId===s?{...d,running:!1,error:!!(e.payload.error||e.payload.toolError),resultSummary:String(e.payload.resultText||"")}:d),c=!!e.payload.stopRunWaitingUser;r({tools:i,isRunning:!c&&n.isRunning}),n.preview&&a&&(a.toolName==="write_file"||a.toolName==="edit_file")&&Cn();break}case"AGENT_ERROR":r({error:String(e.payload.message||"运行出错"),isRunning:!1,lastRunTokenSummary:it(!0)}),bt();break;case"AGENT_END":r({isRunning:!1,lastRunTokenSummary:it(!1)}),bt();break}}async function bt(){if(!n.activeSessionId)return;const e=n.lastRunTokenSummary;try{const t=await St(n.activeSessionId);r({lines:Pt(t),pendingAsk:kt(t),streamingText:"",streamingReasoning:"",tools:[],isRunning:!1,lastRunTokenSummary:e}),await T(),await A()}catch(t){r({error:t instanceof Error?t.message:String(t)})}}async function N(e){var i;const t=(e??n.composer).trim();if(!t||!n.activeSessionId||n.isRunning||n.pendingAsk)return;if(!((i=n.config)!=null&&i.hasApiKey)){r({showSettings:!0,error:"请先配置 API Key"});return}const s={id:`local-${Date.now()}`,role:"user",content:t};r({lines:[...n.lines,s],composer:e==null?"":n.composer,isRunning:!0,streamingText:"",tools:[],error:null,lastRunTokenSummary:null});let a=t;if(n.attachments.length>0){const c=n.attachments.map(d=>`- ${d.workspaceRelativePath}`);a+=`

[用户附件]
${c.join(`
`)}`}try{await $t(n.activeSessionId,a)}catch(c){r({isRunning:!1,error:c instanceof Error?c.message:String(c)})}}async function zn(){if(!n.pendingAsk||!n.activeSessionId)return;const e=ln(n.pendingAsk,n.askAnswers);r({pendingAsk:null,composer:e}),await N()}function Xn(e,t){if(e.role==="tool"){const a=t?W(e.content,t)+ot(e.content,t):l(e.content);return`<details class="tool-card"><summary>⚙ ${l(e.toolName||"tool")}</summary>
      <div class="tool-row ${e.error?"err":""}">${a}</div></details>`}const s=t?W(e.content,t)+ot(e.content,t):l(e.content);return`<div class="bubble ${e.role}"><div class="bubble-text">${s}</div></div>`}function Qn(){if(!n.tools.length&&!n.streamingText&&!n.streamingReasoning)return"";const e=n.tools.map(s=>{const a=s.running?"进行中…":s.error?"失败":"完成";return`<div class="tool-row ${s.error?"err":""}"><strong>${l(s.toolName)}</strong> · ${a}
        ${s.argsPreview?`<div>${l(s.argsPreview)}</div>`:""}
        ${s.resultSummary?`<div>${l(s.resultSummary)}</div>`:""}
      </div>`}).join("");return`<details class="tool-card" open><summary>思考与执行</summary>${[n.streamingReasoning?`<div class="tool-row">思考：${l(n.streamingReasoning)}</div>`:"",e].join("")}</details>
    ${n.streamingText?`<div class="bubble assistant">${l(n.streamingText)}</div>`:""}`}function Vn(){return n.pendingAsk?`<div class="ask-panel"><h3>请回答后继续</h3>${n.pendingAsk.questions.map(t=>{const s=(t.options||[]).map(a=>`<button type="button" class="chip ${n.askAnswers[t.id]===a?"selected":""}" data-ask-id="${l(t.id)}" data-ask-opt="${l(a)}">${l(a)}</button>`).join("");return`<div class="ask-q">
        <label>${l(t.prompt)}</label>
        ${s?`<div class="ask-options">${s}</div>`:""}
        <input data-ask-input="${l(t.id)}" value="${l(n.askAnswers[t.id]||"")}" placeholder="你的回答" />
      </div>`}).join("")}
    <button type="button" class="primary-btn" id="ask-submit">提交回答</button></div>`:""}function Yn(e){const t=e.title||e.id;return`<div class="cap-hit-card">
    <div class="cap-hit-title">[${l(e.kind)}] ${l(t)}</div>
    <div class="muted small">${l(An(e.availability))}</div>
    ${e.summary?`<div>${l(e.summary)}</div>`:""}
  </div>`}function Zn(){if(!n.showCapabilities)return dt("capabilities",!1),"";const e=dt("capabilities",!0),t=Pn.map(({id:a,label:i})=>`<button type="button" class="provider-chip cap-kind-chip ${n.capSelectedKinds.includes(a)?"active":""}" data-cap-kind="${a}">${l(i)}</button>`).join(""),s=Mt();return`<div class="overlay settings-overlay${e}" id="capabilities-overlay">
    <div class="overlay-panel settings-panel wide-panel">
      <header class="settings-header">
        <h2>能力检索</h2>
        <button type="button" class="icon-btn" id="capabilities-close" title="关闭">✕</button>
      </header>
      <div class="settings-body cap-panel-body">
        <div class="cap-panel-toolbar">
          <div class="cap-search">
            <input id="cap-query" class="settings-control" placeholder="搜索" value="${l(n.capQuery)}" />
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
  </div>`}function es(){return n.showArtifacts?`<div class="overlay settings-overlay" id="artifacts-overlay">
    <div class="overlay-panel settings-panel wide-panel">
      <header class="settings-header">
        <h2>产物库</h2>
        <button type="button" class="icon-btn" id="artifacts-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <p class="muted">workspace 内 AI 产出（不含 imports/、tmp/）</p>
        <ul class="artifact-list">${n.artifacts.map(t=>{const a=Lt(t.workspaceRelativePath)?`<button type="button" class="link-btn" data-art-preview="1" data-art-session="${l(t.sessionId)}" data-art-path="${l(t.workspaceRelativePath)}">预览</button>`:"",i=n.activeSessionId?`<button type="button" class="link-btn" data-art-insert="1" data-art-session="${l(t.sessionId)}" data-art-path="${l(t.workspaceRelativePath)}">插入当前会话</button>`:"";return`<li><strong>${l(t.fileName)}</strong>
        <span class="muted"> · ${l(t.sessionId.slice(0,8))} · ${Math.round(t.sizeBytes/1024)}KB</span>
        <div class="artifact-actions">${a} ${i}</div></li>`}).join("")||"<li class='muted'>暂无产物</li>"}</ul>
      </div>
    </div>
  </div>`:""}function ts(){if(!n.preview)return"";const e=Tt(n.preview.path),t=e?`${zt(n.preview.sessionId,n.preview.path)}?v=${n.previewReloadKey}`:`${Gt(n.preview.sessionId,n.preview.path)}?v=${n.previewReloadKey}`,s=n.previewIsDeck?'<span class="preview-deck-badge">Deck</span>':"",a=n.previewInspectEnabled?"关闭审查":"审查模式",i=n.previewSelectedElementJson,c=e&&i?`<div class="preview-inspect-bar">
        <p class="preview-inspect-summary">已选中 &lt;${l(Bn(i))}&gt;</p>
        <textarea id="preview-opinion" class="preview-opinion" rows="2" placeholder="描述要修改的问题…">${l(n.previewOpinion)}</textarea>
        <div class="preview-inspect-actions">
          <button type="button" class="link-btn" id="preview-inspect-cancel">取消</button>
          <button type="button" class="link-btn" id="preview-fill-composer">填入输入框</button>
          <button type="button" class="primary-btn" id="preview-send-ai">反馈给 AI</button>
        </div>
      </div>`:"",d=e?`<button type="button" class="link-btn" id="preview-inspect-toggle">${a}</button>`:"",f=e?`<iframe class="preview-frame" id="preview-frame" src="${t}" title="preview"></iframe>`:`<div class="preview-media"><img src="${t}" alt="${l(n.preview.path)}" /></div>`;return`<div class="overlay preview-overlay" id="preview-overlay">
    <div class="preview-panel">
      <div class="preview-toolbar">
        <span class="preview-path">${s}${l(n.preview.path)}</span>
        <div class="preview-toolbar-actions">
          ${d}
          <button type="button" class="icon-btn" id="preview-close" title="关闭">✕</button>
        </div>
      </div>
      ${f}
      ${c}
    </div>
  </div>`}function ns(){if(!n.showMcp)return"";const e=n.mcpServers.map((s,a)=>{const i=JSON.stringify(s.headers||{});return`<div class="mcp-card" data-mcp-index="${a}">
        <div class="field-row">
          <div class="field"><label>名称</label><input class="settings-control mcp-name" value="${l(s.name)}" /></div>
          <label class="mcp-enable"><input type="checkbox" class="mcp-enabled" ${s.enabled?"checked":""}/> 启用</label>
        </div>
        <div class="field"><label>URL</label><input class="settings-control mcp-url" value="${l(s.url)}" /></div>
        <div class="field"><label>Headers (JSON)</label><input class="settings-control mcp-headers" value="${l(i)}" /></div>
        <p class="settings-hint">${s.toolCount?`${s.toolCount} 个工具`:"保存后尝试索引工具"}</p>
        <button type="button" class="link-btn mcp-remove" data-mcp-index="${a}">删除</button>
      </div>`}).join(""),t=n.config;return`<div class="overlay settings-overlay" id="mcp-overlay">
    <div class="overlay-panel settings-panel">
      <header class="settings-header">
        <h2>MCP 与联网搜索</h2>
        <button type="button" class="icon-btn" id="mcp-close" title="关闭">✕</button>
      </header>
      <div class="settings-body">
        <p class="muted">添加服务器后，助手用 find_caps 查找接口，再在脚本里调用 $mcp.名称.工具。修改后将自动保存并刷新索引。</p>
        ${n.mcpStatus?`<p class="settings-hint">${l(n.mcpStatus)}</p>`:""}
        <div id="mcp-list">${e}</div>
        <button type="button" class="link-btn" id="mcp-add">添加服务器</button>
        <section class="settings-section">
          <h3>联网搜索（Tavily）</h3>
          <div class="field">
            <label for="mcp-web-key">Web Search API Key</label>
            <input id="mcp-web-key" class="settings-control" type="password" autocomplete="off"
              value="${l(n.mcpWebSearchApiKey)}"
              placeholder="${t!=null&&t.hasWebSearch?"已配置，留空不修改":"tvly-…"}" />
          </div>
          <div class="field">
            <label for="mcp-web-base">Tavily Base URL</label>
            <input id="mcp-web-base" class="settings-control" value="${l(n.mcpWebSearchBaseUrl)}" />
          </div>
        </section>
      </div>
    </div>
  </div>`}function ss(){if(!n.showSystemPrompt)return"";const e=n.systemPromptTools.map(s=>{const a=s.description?`<p class="muted">${l(s.description)}</p>`:"",i=s.parametersSchema?`<pre class="prompt-schema">${l(s.parametersSchema)}</pre>`:"";return`<details class="prompt-tool">
        <summary>${l(s.name)}</summary>
        ${a}${i}
      </details>`}).join(""),t=n.systemPromptBusy?'<p class="muted">正在读取系统提示词…</p>':[n.systemPromptError?`<p class="error-inline">${l(n.systemPromptError)}</p>`:"",n.systemPromptText?`<div class="prompt-body">${W(n.systemPromptText,null)}</div>`:n.systemPromptError?"":'<p class="muted">当前会话尚未装入系统提示词。</p>',n.systemPromptTools.length?`<h3 class="prompt-tools-title">工具 ${n.systemPromptTools.length}</h3>${e}`:""].join("");return`<div class="overlay settings-overlay" id="system-prompt-overlay">
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
  </div>`}function as(){return n.showTools?`<div class="overlay" id="tools-overlay">
    <div class="overlay-panel usage-panel">
      <h2>Agent 工具</h2>
      <ul class="tools-list">${n.toolsList.map(t=>`<li><strong>${l(t.name)}</strong><span class="muted">${l(t.description)}</span></li>`).join("")||"<li class='muted'>无</li>"}</ul>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="tools-close">关闭</button>
      </div>
    </div>
  </div>`:""}function os(){if(!n.showAbout)return"";const e=n.aboutInfo,t=n.aboutLoading||!e?'<p class="muted">加载中…</p>':(()=>{const s=e.versionCode>0?`版本 ${l(e.versionName)}（${e.versionCode}）`:`版本 ${l(e.versionName)}`,a=e.dependencies.map(i=>`<div class="about-row"><div class="about-label">${l(i.name)}</div><div class="about-value">${l(i.license)}</div></div>`).join("");return`<div class="about-brand">${l(e.appName)}</div>
          <div class="about-version">${s}</div>
          <div class="about-row"><div class="about-label">${l(e.authorLabel)}</div><div class="about-value">${l(e.authorValue)}</div></div>
          <div class="about-row"><div class="about-label">${l(e.websiteLabel)}</div><a class="about-link" href="${yt(e.websiteUrl)}" target="_blank" rel="noopener noreferrer">${l(e.websiteUrl)}</a></div>
          <div class="about-row"><div class="about-label">${l(e.sourceLabel)}</div><a class="about-link" href="${yt(e.sourceUrl)}" target="_blank" rel="noopener noreferrer">${l(e.sourceUrl)}</a></div>
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
  </div>`}function yt(e){return e.replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#39;")}async function is(){r({showAbout:!0,aboutLoading:!0,aboutInfo:null,moreMenuOpen:!1});try{const e=await Zt();r({aboutInfo:e,aboutLoading:!1})}catch(e){r({showAbout:!1,aboutLoading:!1,error:e instanceof Error?e.message:String(e)})}}function rs(){return n.moreMenuOpen?`<div class="more-menu" role="menu" id="more-menu">
    <button type="button" class="more-menu-item" data-more-action="settings">模型设置</button>
    <button type="button" class="more-menu-item" data-more-action="mcp">MCP 与联网搜索</button>
    <button type="button" class="more-menu-item" data-more-action="capabilities">能力检索</button>
    <button type="button" class="more-menu-item" data-more-action="system-prompt">系统提示词</button>
    <button type="button" class="more-menu-item" data-more-action="brief">会话简报</button>
    <button type="button" class="more-menu-item" data-more-action="diagnostics">诊断包</button>
    <button type="button" class="more-menu-item" data-more-action="failed">失败工具调用</button>
    <button type="button" class="more-menu-item" data-more-action="about">关于</button>
  </div>`:""}function ls(){if(!n.showFailedLogs)return"";const e=n.failedLogsScopeSession?"当前会话":"全部会话",t=n.failedLogs.map(s=>`<li class="failed-log-item">
          <div><strong>${l(s.toolName||"tool")}</strong>
            <span class="muted"> · ${l(s.toolCallId)}</span></div>
          <div class="muted small">run ${l(s.runId)} · session ${l(s.sessionId)}</div>
          <div>${l(s.errorMessage||"—")}</div>
        </li>`).join("");return`<div class="overlay" id="failed-logs-overlay">
    <div class="overlay-panel usage-panel wide-panel">
      <h2>失败工具调用</h2>
      <p class="muted">范围：${l(e)} · 对齐 <code>./agent1 logs failed</code></p>
      <ul class="failed-log-list">${t||"<li class='muted'>（无失败记录）</li>"}</ul>
      <div class="overlay-actions">
        <label class="checkbox-row"><input type="checkbox" id="failed-scope-session" ${n.failedLogsScopeSession?"checked":""}/> 仅当前会话</label>
        <button type="button" id="failed-refresh">刷新</button>
        <button type="button" class="primary-btn" id="failed-close">关闭</button>
      </div>
    </div>
  </div>`}function cs(){return!n.usagePanelOpen||!n.usage?"":`<div class="overlay" id="usage-overlay">
    <div class="overlay-panel usage-panel">
      <h2>累计用量</h2>
      <div class="usage-detail">${Ln(n.usage)}</div>
      <p class="muted">来自 agentRoot/logs/events.jsonl</p>
      <div class="overlay-actions">
        <button type="button" class="primary-btn" id="usage-close">关闭</button>
      </div>
    </div>
  </div>`}function ds(){if(!n.showSettings||!n.settingsDraft)return"";const e=n.settingsDraft,t=n.config,s=z.map(d=>`<button type="button" class="provider-chip ${d.id===e.providerId?"active":""}" data-provider="${d.id}">${l(d.label)}</button>`).join(""),a=n.modelOptions.map(d=>{const f=d.modelId===e.model?"selected":"";return`<option value="${l(d.modelId)}" ${f}>${l(d.title)} · ${l(d.subtitle)}</option>`}).join(""),i=e.model&&!n.modelOptions.some(d=>d.modelId===e.model)?`<option value="${l(e.model)}" selected>${l(e.model)}（当前）</option>`:"",c=n.modelsLoading?"正在加载模型列表…":n.modelsStatus||(n.modelOptions.length?`共 ${n.modelOptions.length} 个模型`:"");return`<div class="overlay settings-overlay" id="settings-overlay">
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
              placeholder="${l(t!=null&&t.hasApiKey?`已配置 ${t.apiKeyHint||""}`:"粘贴 Key，留空则不修改")}" />
          </div>
          <div class="field">
            <label for="cfg-base">Base URL</label>
            <input id="cfg-base" class="settings-control" value="${l(e.baseUrl)}" />
          </div>
          <div class="field">
            <div class="field-label-row">
              <label for="cfg-model">模型</label>
              <button type="button" class="link-btn" id="cfg-refresh-models" ${n.modelsLoading?"disabled":""}>从网络刷新</button>
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
  </div>`}function Ot(){var $;const e=n.sessions.map(v=>{const L=v.sessionId===n.activeSessionId?"active":"",x=v.title||v.sessionId.slice(0,8);return v.sessionId===n.pendingDeleteSessionId?`<div class="session-item ${L} session-item-confirm">
          <span class="session-confirm-text">删除此对话？</span>
          <button type="button" class="session-confirm-cancel" id="delete-session-cancel">取消</button>
          <button type="button" class="session-confirm-ok" id="delete-session-confirm">删除</button>
        </div>`:`<div class="session-item ${L}">
        <button type="button" class="session-open" data-use="${v.sessionId}">
          <span class="session-title">${l(x)}</span>
        </button>
        <button type="button" class="session-delete" data-del="${v.sessionId}" title="删除" aria-label="删除">✕</button>
      </div>`}).join(""),t=cn(n.lines,n.isRunning,n.streamingText,n.streamingReasoning,!!n.pendingAsk),s=n.lines.map(v=>Xn(v,n.activeSessionId)).join(""),a=t?'<button type="button" class="new-topic-btn" id="btn-new-topic">开始新话题</button>':"",i=n.lastRunTokenSummary&&!n.isRunning?`<div class="run-token-summary ${n.lastRunTokenSummary.failed?"failed":""}">${l($n(n.lastRunTokenSummary))}</div>`:"",c=n.lines.length===0&&!n.streamingText&&!n.isRunning?'<div class="landing"><h2>Agent1</h2><p>生产力助手 · 与 Android 端同款纸本风格。左侧管理会话，下方输入开始对话。</p></div>':s+i+a+Qn()+Vn(),d=In(n.usage),f=vn(n.lines,n.isRunning,!!n.pendingAsk),g=n.isRunning||!!n.pendingAsk,h=n.pendingAsk?"请在上方卡片回答":n.isRunning?"生成中…":"输入消息，Enter 发送，Shift+Enter 换行";xt.innerHTML=`
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
            <button type="button" class="icon-btn" id="btn-more" title="更多" aria-haspopup="menu" aria-expanded="${n.moreMenuOpen}">⋮</button>
            ${rs()}
          </div>
        </div>
      </div>
      ${n.toast?`<div class="toast-banner">${l(n.toast)}</div>`:""}
      ${n.error?`<div class="error-banner">${l(n.error)}</div>`:""}
      <div class="chat-scroll" id="chat-scroll">${c}</div>
      <div class="composer-wrap">
        ${n.config&&!n.config.hasApiKey?'<div class="inline-setup">尚未配置 API Key。<button type="button" id="btn-setup">打开设置</button></div>':""}
        ${n.attachments.length?`<div class="attachment-chips">${n.attachments.map(v=>`<span class="chip">${l(v.fileName)} <button type="button" data-rm-attach="${l(v.workspaceRelativePath)}">×</button></span>`).join("")}</div>`:""}
        ${f?'<div class="continue-bar"><p>任务还没做完，点继续接着做完</p><button type="button" class="primary-btn" id="btn-continue">继续</button></div>':""}
        <div class="composer">
          <input type="file" id="file-input" class="hidden-file" multiple />
          <button type="button" class="icon-btn" id="btn-attach" title="添加附件" ${g?"disabled":""}>📎</button>
          <textarea id="composer" ${g?"disabled":""} placeholder="${h}">${l(n.composer)}</textarea>
          ${n.isRunning?'<button type="button" class="danger-btn" id="btn-stop">停止</button>':`<button type="button" class="primary-btn" id="btn-send" ${g?"disabled":""}>发送</button>`}
        </div>
        <div class="model-chip">模型 ${l((($=n.config)==null?void 0:$.model)||"—")} ·
          <button type="button" id="btn-model">更改</button></div>
      </div>
    </section>
    ${ds()}
    ${ns()}
    ${ss()}
    ${as()}
    ${cs()}
    ${ls()}
    ${os()}
    ${Zn()}
    ${es()}
    ${ts()}
  </div>`,us();const E=document.getElementById("chat-scroll");E&&!On()&&(E.scrollTop=E.scrollHeight)}function us(){var i,c,d,f,g,h,E,$,v,L,x,se,ae,oe,ie,re,le,ce,de,ue,pe,me,fe,ve,ge,be,ye,he,we,Ee,Se,$e,ke,Ie,Te,Le,Pe,Ae,Re,xe,Be,Ce,Me,Oe,Ue,De,Ne,je,_e,qe,Fe,Ke,He,Je,We,Ge,ze,Xe,Qe,Ve,Ye;(i=document.getElementById("btn-new"))==null||i.addEventListener("click",()=>void ut()),(c=document.getElementById("btn-new-topic"))==null||c.addEventListener("click",()=>void ut()),(d=document.getElementById("btn-drawer"))==null||d.addEventListener("click",o=>{o.stopPropagation();const u=!n.drawerOpen;r({drawerOpen:u}),u&&A()}),(f=document.getElementById("app-main"))==null||f.addEventListener("click",o=>{if(!n.drawerOpen)return;const u=o.target;u!=null&&u.closest("button, a, input, textarea, select, label")||r({drawerOpen:!1})}),(g=document.getElementById("usage-footer"))==null||g.addEventListener("click",()=>{A().then(()=>r({usagePanelOpen:!0}))}),(h=document.getElementById("usage-close"))==null||h.addEventListener("click",()=>r({usagePanelOpen:!1})),(E=document.getElementById("usage-overlay"))==null||E.addEventListener("click",o=>{o.target===o.currentTarget&&r({usagePanelOpen:!1})}),($=document.getElementById("failed-close"))==null||$.addEventListener("click",()=>r({showFailedLogs:!1})),(v=document.getElementById("failed-refresh"))==null||v.addEventListener("click",()=>void X()),(L=document.getElementById("failed-scope-session"))==null||L.addEventListener("change",o=>{const u=o.target.checked;r({failedLogsScopeSession:u}),X()}),(x=document.getElementById("failed-logs-overlay"))==null||x.addEventListener("click",o=>{o.target===o.currentTarget&&r({showFailedLogs:!1})}),(se=document.getElementById("btn-more"))==null||se.addEventListener("click",o=>{o.stopPropagation(),r({moreMenuOpen:!n.moreMenuOpen})}),(ae=document.getElementById("more-menu"))==null||ae.addEventListener("click",o=>o.stopPropagation()),document.querySelectorAll("[data-more-action]").forEach(o=>{o.addEventListener("click",()=>{const u=o.dataset.moreAction;switch(gt(),u){case"settings":H();break;case"mcp":pt();break;case"capabilities":Kn();break;case"system-prompt":jn();break;case"brief":Jn().catch(p=>r({error:p instanceof Error?p.message:String(p)}));break;case"diagnostics":qt().then(()=>w("诊断包已下载")).catch(p=>r({error:p instanceof Error?p.message:String(p)}));break;case"failed":Un();break;case"about":is();break}})}),n.moreMenuOpen&&window.setTimeout(()=>{if(!n.moreMenuOpen)return;const o=()=>{document.removeEventListener("click",o),gt()};document.addEventListener("click",o)},0),(oe=document.getElementById("capabilities-overlay"))==null||oe.addEventListener("click",o=>{const u=o.currentTarget.querySelector(".overlay-panel");u&&u.contains(o.target)&&o.stopPropagation()}),(ie=document.getElementById("about-close"))==null||ie.addEventListener("click",()=>r({showAbout:!1,aboutInfo:null,aboutLoading:!1})),(re=document.getElementById("about-overlay"))==null||re.addEventListener("click",o=>{o.target===o.currentTarget&&r({showAbout:!1,aboutInfo:null,aboutLoading:!1})}),(le=document.getElementById("btn-setup"))==null||le.addEventListener("click",()=>void H()),(ce=document.getElementById("btn-model"))==null||ce.addEventListener("click",()=>void H()),(de=document.getElementById("btn-artifacts"))==null||de.addEventListener("click",async()=>{const o=await Ft();r({artifacts:o,showArtifacts:!0})}),(ue=document.getElementById("artifacts-close"))==null||ue.addEventListener("click",()=>r({showArtifacts:!1})),(pe=document.getElementById("capabilities-close"))==null||pe.addEventListener("click",()=>r({showCapabilities:!1})),(me=document.getElementById("cap-search-btn"))==null||me.addEventListener("click",o=>{o.preventDefault(),o.stopPropagation(),vt()}),(fe=document.getElementById("cap-query"))==null||fe.addEventListener("keydown",o=>{o.key==="Enter"&&(o.preventDefault(),vt())}),document.querySelectorAll(".cap-kind-chip").forEach(o=>{o.addEventListener("click",()=>{const u=o.dataset.capKind;if(!u)return;const p=new Set(n.capSelectedKinds);p.has(u)?p.delete(u):p.add(u),r({capSelectedKinds:[...p]})})}),(ve=document.getElementById("cap-limit-down"))==null||ve.addEventListener("click",()=>{n.capLimit>1&&r({capLimit:n.capLimit-1})}),(ge=document.getElementById("cap-limit-up"))==null||ge.addEventListener("click",()=>{n.capLimit<te&&r({capLimit:n.capLimit+1})}),document.querySelectorAll("[data-art-preview]").forEach(o=>{o.addEventListener("click",()=>{const u=o.dataset.artSession,p=o.dataset.artPath;u&&p&&ct(u,p)})}),document.querySelectorAll("[data-art-insert]").forEach(o=>{o.addEventListener("click",async()=>{const u=o.dataset.artSession,p=o.dataset.artPath,b=n.activeSessionId;if(!(!b||!u||!p))try{await Kt(b,u,p),await D(b),w("已复制到 imports/")}catch(y){r({error:y instanceof Error?y.message:String(y)})}})}),Mn(),(be=document.getElementById("preview-close"))==null||be.addEventListener("click",()=>K()),(ye=document.getElementById("preview-overlay"))==null||ye.addEventListener("click",o=>{o.target===o.currentTarget&&K()}),(he=document.getElementById("preview-inspect-toggle"))==null||he.addEventListener("click",()=>{const o=document.getElementById("preview-frame"),u=!n.previewInspectEnabled;r({previewInspectEnabled:u,previewSelectedElementJson:null,previewOpinion:""}),I(o,u?"enable":"disable")}),(we=document.getElementById("preview-inspect-cancel"))==null||we.addEventListener("click",()=>{const o=document.getElementById("preview-frame");I(o,"clear"),r({previewSelectedElementJson:null,previewOpinion:""})}),(Ee=document.getElementById("preview-fill-composer"))==null||Ee.addEventListener("click",async()=>{var b;const o=n.preview,u=n.previewSelectedElementJson;if(!o||!u)return;const p=((b=document.getElementById("preview-opinion"))==null?void 0:b.value)??n.previewOpinion;try{const y=await et(o.path,u,p);r({composer:y}),K(),w("已填入输入框")}catch(y){r({error:y instanceof Error?y.message:String(y)})}}),(Se=document.getElementById("preview-send-ai"))==null||Se.addEventListener("click",async()=>{var y;const o=n.preview,u=n.previewSelectedElementJson,p=o.sessionId;if(!u)return;if(n.isRunning){r({error:"请先停止当前 Run"});return}const b=((y=document.getElementById("preview-opinion"))==null?void 0:y.value)??n.previewOpinion;if(!b.trim()){w("请先描述问题");return}try{const k=await et(o.path,u,b);await $t(p,k);const B=document.getElementById("preview-frame");I(B,"clear"),r({previewSelectedElementJson:null,previewOpinion:"",isRunning:!0,streamingText:"",tools:[]}),w("已发送给 AI，修改后页面将自动刷新")}catch(k){r({error:k instanceof Error?k.message:String(k)})}}),($e=document.getElementById("preview-opinion"))==null||$e.addEventListener("input",o=>{r({previewOpinion:o.target.value})});const e=document.getElementById("file-input");(ke=document.getElementById("btn-attach"))==null||ke.addEventListener("click",()=>e==null?void 0:e.click()),e==null||e.addEventListener("change",async()=>{var u;const o=n.activeSessionId;if(!(!o||!((u=e.files)!=null&&u.length)))try{for(const p of Array.from(e.files))await Jt(o,p);await D(o),e.value=""}catch(p){r({error:p instanceof Error?p.message:String(p)})}}),document.querySelectorAll("[data-rm-attach]").forEach(o=>{o.addEventListener("click",async()=>{const u=n.activeSessionId,p=o.dataset.rmAttach;!u||!p||(await Wt(u,p),await D(u))})}),document.querySelectorAll("[data-use]").forEach(o=>{o.addEventListener("click",async()=>{const u=o.dataset.use;if(n.pendingDeleteSessionId&&r({pendingDeleteSessionId:null}),u!==n.activeSessionId){if(n.isRunning){r({error:"请先停止当前 Run 再切换会话"});return}await V(u),await R(u),await T()}})}),document.querySelectorAll("[data-del]").forEach(o=>{o.addEventListener("click",u=>{u.stopPropagation();const p=o.dataset.del;p&&r({pendingDeleteSessionId:p})})}),(Ie=document.getElementById("delete-session-cancel"))==null||Ie.addEventListener("click",o=>{o.stopPropagation(),r({pendingDeleteSessionId:null})}),(Te=document.getElementById("delete-session-confirm"))==null||Te.addEventListener("click",o=>{o.stopPropagation(),Dn()});const t=document.getElementById("composer");t==null||t.addEventListener("input",()=>{n={...n,composer:t.value}}),t==null||t.addEventListener("keydown",o=>{o.key==="Enter"&&!o.shiftKey&&(o.preventDefault(),N())}),(Le=document.getElementById("btn-send"))==null||Le.addEventListener("click",()=>void N()),(Pe=document.getElementById("btn-continue"))==null||Pe.addEventListener("click",()=>void N("继续")),(Ae=document.getElementById("btn-stop"))==null||Ae.addEventListener("click",async()=>{await nn()}),document.querySelectorAll("[data-ask-opt]").forEach(o=>{o.addEventListener("click",()=>{const u=o.dataset.askId,p=o.dataset.askOpt;r({askAnswers:{...n.askAnswers,[u]:p}})})}),document.querySelectorAll("[data-ask-input]").forEach(o=>{o.addEventListener("input",()=>{const u=o.dataset.askInput;n={...n,askAnswers:{...n.askAnswers,[u]:o.value}}})}),(Re=document.getElementById("ask-submit"))==null||Re.addEventListener("click",()=>void zn());const s=()=>r({showSettings:!1,settingsDraft:null,modelsStatus:null,modelsLoading:!1});(xe=document.getElementById("cfg-close"))==null||xe.addEventListener("click",s),(Be=document.getElementById("settings-overlay"))==null||Be.addEventListener("click",o=>{o.target===o.currentTarget&&s()}),document.querySelectorAll("[data-provider]").forEach(o=>{o.addEventListener("click",()=>{var p;const u=o.dataset.provider;u&&u!==((p=n.settingsDraft)==null?void 0:p.providerId)&&Nn(u)})});const a=o=>{n.settingsDraft&&r({settingsDraft:{...n.settingsDraft,...o}})};(Ce=document.getElementById("cfg-model"))==null||Ce.addEventListener("change",o=>{a({model:o.target.value})}),(Me=document.getElementById("cfg-ctx-turns"))==null||Me.addEventListener("change",o=>{a({maxContextTurns:parseInt(o.target.value,10)||0})}),(Oe=document.getElementById("cfg-ctx-msgs"))==null||Oe.addEventListener("change",o=>{a({maxContextMessages:parseInt(o.target.value,10)||0})}),(Ue=document.getElementById("cfg-run-turns"))==null||Ue.addEventListener("change",o=>{a({maxTurnsPerRun:parseInt(o.target.value,10)||0})}),(De=document.getElementById("cfg-tool-calls"))==null||De.addEventListener("change",o=>{a({maxToolCallsPerRun:parseInt(o.target.value,10)||0})}),(Ne=document.getElementById("cfg-open-mcp"))==null||Ne.addEventListener("click",()=>void pt()),(je=document.getElementById("mcp-close"))==null||je.addEventListener("click",()=>r({showMcp:!1})),(_e=document.getElementById("mcp-web-key"))==null||_e.addEventListener("input",()=>mt()),(qe=document.getElementById("mcp-web-base"))==null||qe.addEventListener("input",()=>mt()),(Fe=document.getElementById("mcp-list"))==null||Fe.addEventListener("input",()=>O()),(Ke=document.getElementById("mcp-list"))==null||Ke.addEventListener("change",()=>O()),(He=document.getElementById("system-prompt-close"))==null||He.addEventListener("click",()=>r({showSystemPrompt:!1,systemPromptError:null})),(Je=document.getElementById("system-prompt-overlay"))==null||Je.addEventListener("click",o=>{o.target===o.currentTarget&&r({showSystemPrompt:!1,systemPromptError:null})}),(We=document.getElementById("system-prompt-copy"))==null||We.addEventListener("click",async()=>{if(n.systemPromptText)try{await navigator.clipboard.writeText(n.systemPromptText),w("已复制系统提示词")}catch(o){r({error:o instanceof Error?o.message:String(o)})}}),(Ge=document.getElementById("mcp-add"))==null||Ge.addEventListener("click",()=>{r({mcpServers:[...n.mcpServers,{name:"server",url:"https://",enabled:!0,headers:{}}]}),O()}),document.querySelectorAll(".mcp-remove").forEach(o=>{o.addEventListener("click",()=>{const u=Number(o.dataset.mcpIndex);r({mcpServers:n.mcpServers.filter((p,b)=>b!==u)}),O()})}),document.querySelectorAll(".file-link").forEach(o=>{o.addEventListener("click",u=>{u.stopPropagation();const p=o.dataset.filePath,b=n.activeSessionId;if(!p||!b){w("无法打开文件");return}Lt(p)?ct(b,p):w(p)})}),(ze=document.getElementById("cfg-show-tools"))==null||ze.addEventListener("click",async()=>{try{const{tools:o}=await jt();r({showTools:!0,toolsList:o})}catch(o){r({error:o instanceof Error?o.message:String(o)})}}),(Xe=document.getElementById("tools-close"))==null||Xe.addEventListener("click",()=>r({showTools:!1})),(Qe=document.getElementById("tools-overlay"))==null||Qe.addEventListener("click",o=>{o.target===o.currentTarget&&r({showTools:!1})}),(Ve=document.getElementById("cfg-refresh-models"))==null||Ve.addEventListener("click",()=>{const o=n.settingsDraft;o&&ne(o,!0)}),(Ye=document.getElementById("cfg-save"))==null||Ye.addEventListener("click",async()=>{const o=n.settingsDraft;if(!o)return;const u=document.getElementById("cfg-key").value,p=document.getElementById("cfg-base").value,b=document.getElementById("cfg-model").value,y=parseInt(document.getElementById("cfg-ctx-turns").value,10),k=parseInt(document.getElementById("cfg-ctx-msgs").value,10),B=parseInt(document.getElementById("cfg-run-turns").value,10),Ze=parseInt(document.getElementById("cfg-tool-calls").value,10);try{const P=await wt({apiKey:u||void 0,baseUrl:p||void 0,model:b||void 0,providerId:o.providerId,maxContextTurns:Number.isFinite(y)?y:0,maxContextMessages:Number.isFinite(k)?k:0,maxTurnsPerRun:Number.isFinite(B)?B:0,maxToolCallsPerRun:Number.isFinite(Ze)?Ze:0});r({config:P,showSettings:!1,settingsDraft:null,modelsStatus:null,error:null}),w("设置已保存")}catch(P){r({error:P instanceof Error?P.message:String(P)})}})}window.addEventListener("message",e=>{if(e.origin!==window.location.origin||!e.data||e.data.type!==Rn||!n.preview||!n.previewInspectEnabled)return;const t=typeof e.data.payload=="string"?e.data.payload:null;t&&r({previewSelectedElementJson:t,previewOpinion:""})});Ot();Wn().catch(e=>{r({error:e instanceof Error?e.message:String(e)})});window.addEventListener("beforeunload",()=>U==null?void 0:U());
