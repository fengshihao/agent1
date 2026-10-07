/**
 * Agent1 官网文案。语言记在 localStorage `agent1-lang`。
 * 简体中文 / English。
 */
(function (global) {
  var STORAGE = "agent1-lang";
  var LANGS = [
    { id: "zh", name: "简体中文", html: "zh-CN" },
    { id: "en", name: "English", html: "en" },
  ];

  var T = {
    zh: {
      skip: "跳到正文",
      navAria: "主导航",
      brandAria: "Agent1",
      navDevice: "设备",
      navEngine: "引擎",
      navDiscover: "发现",
      navIntegrate: "集成",
      navDebug: "调试",
      navDocs: "贡献",
      navGitHub: "GitHub",
      langAria: "界面语言",
      title: "Agent1 — 给低端 Android 设备的开箱即用智能体 SDK",
      description:
        "Agent1 是为低端 Android 设备设计的智能体 SDK，APK 约 3MB。内置微型 JS 引擎和基础 API，AI 可以持续写代码、自己进化。能力用检索发现，数十万条也不会撑进上下文。",
      kicker: "Android SDK · APK 约 3MB",
      headline: "给低端设备的开箱即用智能体。",
      lede: "现在只支持 Android，参考 APK 约 3MB。里面有微型 JS 引擎和已经接好的基础 API，AI 可以持续写代码、自己进化。能力用检索发现，数十万条也不会撑进上下文。",
      ctaEngine: "微型 JS 引擎",
      ctaDiscover: "能力发现",
      ctaIntegrate: "一句话集成",
      stat1: "约 3MB",
      stat2: "目前只支持这一端",
      stat3: "数十万",
      stat3b: "能力不进上下文",
      engineTitle: "内置微型 JS 引擎。基础 API 已经接好。",
      engineBody:
        "设备上跑的是 QuickJS。文件、工具桥和平台能力都已经接到脚本里，模型不用把每一步都拆成一次工具调用。它在会话工作区里写 JavaScript，用 run_js 跑；走通的流程收成 Skill，下一次还能找到。代码留在设备上，能力越积越多，这就是自我进化。",
      engineFact1: "QuickJS，经 run_js 在设备上执行",
      engineFact2: "基础 API 已接通：工作区文件、脚本内工具桥、平台能力",
      engineFact3: "写好的流程可以沉淀，留给以后的自己",
      discoverTitle: "能力发现按数十万条来准备，上下文里只留下几条。",
      discoverBody:
        "Skill、脚本、平台 API 和 MCP 工具进同一套本地索引，不写进系统提示。模型要做事时先 find_caps：回来的是短摘要和调用入口，一次只有少数几条，正文仍留在索引外面。所以目录可以长到数十万条，提示词不会跟着变厚。",
      discoverFact1: "一个入口：find_caps",
      discoverFact2: "命中只带短摘要，手册不进上下文",
      discoverFact3: "索引在设备本地，按数十万条能力来准备",
      deviceTitle: "为低端设备做的，打开就能用。",
      deviceBody:
        "这是一套智能体 SDK，不是要你从聊天框开始搭。会话、工作区沙箱、工具循环和事件日志都在里面。目前只支持 Android，参考实现打出来的 APK 约 3MB，为的是内存和存储都紧的手机。",
      deviceFact1: "目前只支持 Android",
      deviceFact2: "APK 约 3MB",
      deviceFact3: "参考 App 里就是完整的生产力助手，模型 Key 在应用里配置",
      integrateTitle: "仓库按 AI 来设计。一句话，集成进你的 App。",
      integrateBody:
        "契约、目录和接入方式写给编码智能体读。AGENTS.md 规定它能改什么，android_agent 演示 ProductivityAgentHost 怎么接到界面上。把下面这一句发给 Cursor，它会自己克隆、阅读，并把 SDK 接进你的 Android 工程。",
      integratePrompt:
        "把开源项目 Agent1（https://github.com/fengshihao/agent1）集成进我的 Android App。请阅读 README、AGENTS.md，以及 android_agent 里 ProductivityAgentHost 的接法，用 java-agent-core 接到我现有的界面上。改完告诉我要加哪些依赖、模型 Key 放在哪里。",
      debugTitle: "日志和文档也按 AI 来设计，它能自己查问题。",
      debugBody:
        "每一次模型请求、工具调用、用量和这次运行为什么结束，都写进 logs/events.jsonl。会话还有 transcript 和单次 run 的状态。这些文件旁边有目录说明。把失败的日志交给编码智能体，它可以对着文档把原因找出来，再改代码。",
      debugFact1: " — 模型、工具、用量、结束原因",
      debugFact2: " — 开发时直接看失败的工具调用",
      debugFact3: " — 每个文件是干什么的",
      debugPrompt:
        "这次运行失败了。请读 logs/events.jsonl，并用 ./agent1 logs failed 的结果对照 java_agent/doc/runtime-data-layout.md。自己定位原因，改代码，再用单测验证。",
      aiTitle: "用 AI 成为贡献者",
      aiBody: "先复制下面这段发给 Cursor，让它准备好环境；再说你想改什么。克隆、读契约、跑单测都交给它。",
      setupPrompt:
        "帮我准备开源项目 Agent1（https://github.com/fengshihao/agent1）的贡献环境：请你自己克隆仓库、读 AGENTS.md 和 docs/ai/START.md，安装 JDK 17 后运行 ./java_agent/gradlew -p java_agent :core:test :cli:test。准备好后告诉我，我再说想贡献什么。",
      copy: "复制",
      copied: "已复制",
      aiMore: "贡献两步说明",
      aiFull: "给 AI 的完整提示",
      footMit: "MIT · ",
      footNote: "开源。目前支持 Android。",
      footDocs: "贡献",
      footFeedback: "反馈",
      docsTitle: "开始贡献 · Agent1",
      docsDescription: "复制一句给 AI，让它准备 Agent1 的贡献环境；再说你想改什么。",
      docsH1: "两步就够",
      docsIntro: "不用先读完仓库。先让 AI 把 JDK 和单测跑通，再用一句话说你要改的那一件事。",
      docsStep1: "1. 复制发给 AI，准备环境",
      docsStep1Body: "打开 Cursor（或别的编码智能体），点复制，粘贴发给它：",
      docsHint1: "环境就绪后，用一句话告诉它你要做什么。一次只做一件事。",
      taskPrompt:
        "我要贡献：〈一件事〉。按 AGENTS.md 与 docs/ai/CHECKLIST.md 修改；新增或改动 Java 行为必须带 JUnit 单测；改完必须 ./scripts/ci-local.sh fast 通过，再按 docs/ai/PR_PLAYBOOK.md 开 PR。若改 Android 组装或大量 Android 代码，再跑 ./scripts/ci-local.sh full。",
      docsStep2: "2. 验收 AI 有没有做对",
      docsStep2Body: "让 AI 跑，或你在仓库目录运行：",
      docsHint2: "Java 行为变更必须有 JUnit。动到 Android 组装时，把 fast 换成 full。",
      docsLiTest: " — 核心与 CLI 单测",
      docsLiFast: " — 与 CI 对齐的快速门禁",
      docsLiSite: " — 本地打开本官网",
      docsFine: "给 AI 的细则（人一般不用点）",
      docsBack: "← 回首页",
    },
    en: {
      skip: "Skip to content",
      navAria: "Primary",
      brandAria: "Agent1",
      navDevice: "Device",
      navEngine: "Engine",
      navDiscover: "Discover",
      navIntegrate: "Integrate",
      navDebug: "Debug",
      navDocs: "Contribute",
      navGitHub: "GitHub",
      langAria: "Language",
      title: "Agent1 — an out-of-the-box agent SDK for low-end Android devices",
      description:
        "Agent1 is an agent SDK for low-end Android devices, with an APK of about 3MB. A micro JS engine and the base APIs are built in, so an AI can keep writing code and evolve. Capabilities are found by search, so hundreds of thousands of them never enter the context.",
      kicker: "Android SDK · APK ~3MB",
      headline: "An agent that runs on low-end devices, ready to ship.",
      lede: "Android only for now, and the reference APK is about 3MB. A micro JS engine and the base APIs are already inside, so an AI can keep writing code and evolve. Capabilities are discovered by search, so hundreds of thousands of them never enter the context.",
      ctaEngine: "Micro JS engine",
      ctaDiscover: "Capability search",
      ctaIntegrate: "Integrate in one sentence",
      stat1: "~3MB",
      stat2: "the only host today",
      stat3: "100k+",
      stat3b: "capabilities stay out of context",
      engineTitle: "A micro JS engine is built in. The base APIs are already wired.",
      engineBody:
        "QuickJS runs on the device. Files, the tool bridge, and platform capabilities are already callable from script, so the model does not turn every step into its own tool call. It writes JavaScript in the session workspace and runs it with run_js. A flow that works can be kept as a skill and found again later. The code stays on device, and the set of skills grows. That is the self-evolution.",
      engineFact1: "QuickJS, executed on device through run_js",
      engineFact2: "Base APIs are connected: workspace files, the in-script tool bridge, and platform capabilities",
      engineFact3: "A working flow can be kept for the next session",
      discoverTitle: "Discovery is built for hundreds of thousands of capabilities. The context only keeps a few.",
      discoverBody:
        "Skills, scripts, platform APIs, and MCP tools share one local index. They are not written into the system prompt. When the model needs something, it calls find_caps and gets a short summary plus an entry point, a handful of hits at a time. The full text stays outside the index result. The catalog can grow to hundreds of thousands of entries without thickening the prompt.",
      discoverFact1: "One entry point: find_caps",
      discoverFact2: "A hit is a short summary. Manuals stay out of the context",
      discoverFact3: "The index lives on device and is built for hundreds of thousands of capabilities",
      deviceTitle: "Built for low-end devices. Ready when you open it.",
      deviceBody:
        "This is an agent SDK, not a chat box you have to assemble. Sessions, a workspace sandbox, the tool loop, and the event log are already in it. Android is the only host today. The reference APK is about 3MB, so it fits phones that are short on memory and storage.",
      deviceFact1: "Android only, for now",
      deviceFact2: "APK about 3MB",
      deviceFact3: "The reference app is a full productivity assistant. The model key stays in the app.",
      integrateTitle: "The repo is designed for AI. One sentence integrates it into your app.",
      integrateBody:
        "The contract, the layout, and the embedding path are written for a coding agent. AGENTS.md says what it may change. android_agent shows how ProductivityAgentHost meets a UI. Paste the sentence below into Cursor. It clones, reads, and wires the SDK into your Android project.",
      integratePrompt:
        "Integrate the open-source project Agent1 (https://github.com/fengshihao/agent1) into my Android app. Read the README, AGENTS.md, and how ProductivityAgentHost is embedded in android_agent. Connect java-agent-core to my existing UI. When done, tell me which dependencies to add and where the model key goes.",
      debugTitle: "The logs and the docs are designed for AI. It can debug itself.",
      debugBody:
        "Every model request, tool call, token usage, and the reason a run ended is written to logs/events.jsonl. A session also keeps a transcript and per-run state. A layout document sits next to those files. Hand a failed log to a coding agent and it can match the events to the docs, find the cause, and change the code.",
      debugFact1: " — model, tools, usage, and why the run ended",
      debugFact2: " — failed tool calls while you are developing",
      debugFact3: " — what each file is for",
      debugPrompt:
        "This run failed. Read logs/events.jsonl and the output of ./agent1 logs failed, then compare them with java_agent/doc/runtime-data-layout.md. Find the cause yourself, change the code, and verify with unit tests.",
      aiTitle: "Contribute with an AI",
      aiBody: "Paste this into Cursor so it can clone the repo and run tests. Then say what you want changed.",
      setupPrompt:
        "Set up contribution environment for Agent1 (https://github.com/fengshihao/agent1): clone the repo, read AGENTS.md and docs/ai/START.md, use JDK 17, run ./java_agent/gradlew -p java_agent :core:test :cli:test. Tell me when ready for my task.",
      copy: "Copy",
      copied: "Copied",
      aiMore: "Two-step guide",
      aiFull: "Full prompt for agents",
      footMit: "MIT · ",
      footNote: "Open source. Android today.",
      footDocs: "Contribute",
      footFeedback: "Feedback",
      docsTitle: "Contribute · Agent1",
      docsDescription: "Paste one prompt so an AI can set up Agent1, then say what you want changed.",
      docsH1: "Two steps",
      docsIntro: "You do not need to read the whole repo first. Let an AI install JDK 17 and run the tests, then describe one change.",
      docsStep1: "1. Paste this so an AI can set up",
      docsStep1Body: "Open Cursor or another coding agent, copy the prompt, and send it:",
      docsHint1: "When the environment is ready, describe one change. One thing per pull request.",
      taskPrompt:
        "I want to contribute: 〈one scoped change〉. Follow AGENTS.md and docs/ai/CHECKLIST.md; add/update JUnit tests for Java changes; run ./scripts/ci-local.sh fast before opening a PR per docs/ai/PR_PLAYBOOK.md.",
      docsStep2: "2. Check the result",
      docsStep2Body: "Ask the AI to run these, or run them yourself from the repo root:",
      docsHint2: "Java behavior changes need JUnit tests. If the Android build changed, run ci-local.sh full instead of fast.",
      docsLiTest: " — core and CLI tests",
      docsLiFast: " — the same fast gate as CI",
      docsLiSite: " — open this site locally",
      docsFine: "The contract for agents (people can skip this)",
      docsBack: "← Home",
    },
  };

  function detect() {
    try {
      var saved = localStorage.getItem(STORAGE);
      if (saved && T[saved]) return saved;
    } catch (e) {}
    var nav = (navigator.language || "zh").toLowerCase();
    return nav.indexOf("zh") === 0 ? "zh" : "en";
  }

  var current = detect();

  function t(key) {
    var pack = T[current] || T.zh;
    return pack[key] != null ? pack[key] : (T.zh[key] || "");
  }

  function apply(lang) {
    if (!T[lang]) lang = "zh";
    current = lang;
    try { localStorage.setItem(STORAGE, lang); } catch (e) {}
    var meta = LANGS.filter(function (l) { return l.id === lang; })[0];
    document.documentElement.lang = meta ? meta.html : "zh-CN";
    var pack = T[lang];
    document.querySelectorAll("[data-i18n]").forEach(function (el) {
      var key = el.getAttribute("data-i18n");
      if (pack[key] != null) el.textContent = pack[key];
    });
    document.querySelectorAll("[data-i18n-aria]").forEach(function (el) {
      var key = el.getAttribute("data-i18n-aria");
      if (pack[key] != null) el.setAttribute("aria-label", pack[key]);
    });
    var titleKey = document.documentElement.getAttribute("data-i18n-title") || "title";
    if (pack[titleKey]) document.title = pack[titleKey];
    var descKey = document.documentElement.getAttribute("data-i18n-desc") || "description";
    var desc = document.querySelector('meta[name="description"]');
    if (desc && pack[descKey]) desc.setAttribute("content", pack[descKey]);
    var select = document.getElementById("siteLang");
    if (select && select.value !== lang) select.value = lang;
  }

  function fillSelect() {
    var select = document.getElementById("siteLang");
    if (!select) return;
    select.textContent = "";
    LANGS.forEach(function (l) {
      var opt = document.createElement("option");
      opt.value = l.id;
      opt.textContent = l.name;
      select.appendChild(opt);
    });
    select.value = current;
    select.addEventListener("change", function () {
      apply(select.value);
    });
  }

  function wireCopy() {
    document.querySelectorAll("[data-copy]").forEach(function (btn) {
      btn.addEventListener("click", async function () {
        var id = btn.getAttribute("data-copy");
        var node = id ? document.getElementById(id) : null;
        var text = node ? node.textContent : "";
        try {
          await navigator.clipboard.writeText(text);
        } catch (e) {
          var ta = document.createElement("textarea");
          ta.value = text;
          document.body.appendChild(ta);
          ta.select();
          document.execCommand("copy");
          ta.remove();
        }
        btn.textContent = t("copied");
        btn.classList.add("is-done");
        setTimeout(function () {
          btn.textContent = t("copy");
          btn.classList.remove("is-done");
        }, 1400);
      });
    });
  }

  function boot() {
    fillSelect();
    apply(current);
    wireCopy();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }

  global.Agent1SiteI18n = { t: t, apply: apply };
})(window);
