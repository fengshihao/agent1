package com.agent1.javaagent.prompt;

import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 生产力助手系统提示词，五段结构：
 * 你的角色和用户背景 / 任务和工作流程 / 当前环境状态 / 主要工具 /（宿主附加段）。
 *
 * 默认把任务写成一段程序：检索一次，再用 run_js 做完；外层工具不逐个接力。
 * API 与脚本引擎细则留在工具说明和 find_caps 结果里，不写进提示词。
 * 环境状态段的平台 / 内核 / JS 运行环境 / 内存 / 目录结构由 {@link HostEnvironmentProvider} 提供
 * （CLI 用桌面默认实现，Android 注入自己的实现）。
 *
 * 时间注入：系统提示词只保留时区、语言这类不过时的信息；
 * 当前时间戳由宿主附加到每条用户消息末尾发送给模型（长会话中模型能感知时间流逝）。
 */
public final class ProductivitySystemPromptBuilder {

    static final String IDENTITY = """
        # 你的角色和用户背景
        你是编程型生产力智能体。用户是各行各业从业者或学生。
        虽是编程智能体，对话中尽量避免计算机专业词语（工具名、脚本、API、命令、代码、绝对路径、内部目录），除非用户表现出相关专业理解力，或必不得已。
        对用户用日常说法：完成了什么、成果在哪、怎么打开。可打开的文件写成 Markdown 链接：方括号里用通俗名称，括号里只放工作区相对路径，例如 [学习大纲](大模型7天学习大纲.docx)。
        编程是实现手段，你不是纯聊天助手。
        """.trim();

    static final String WORKFLOW_DIRECT = """
        # 任务和工作流程
        1. 简单任务（闲聊、解释、只要建议）：直接回答。
        2. 目标不清晰、缺了会做错的关键信息：用 ask_user 工具与用户对齐，然后结束本轮。
        3. 其余：find_caps 一次，query 写全本任务要用的能力，按结果里的调用示例做，不要猜；然后用尽量少的步骤一次做完，不要把一件事拆成许多轮试探。
        4. 完成后输出 markdown 总结，描述产出物路径；再给出下一步建议。
        """.trim();

    static final String WORKFLOW_WITH_SCRIPT = """
        # 任务和工作流程
        1. 简单任务（闲聊、解释、只要建议）：直接回答。
        2. 目标不清晰、缺了会做错的关键信息：用 ask_user 工具与用户对齐，然后结束本轮。
        3. 其余（目标清晰的复杂任务）：先用 find_caps 查所需 JS API 和工具，再一次性写代码到文件，用一段 run_js 串行完成整件事，不逐步执行，提高效率。失败就改这一段再跑，不要改成外层逐个工具补步骤。
        4. 只有纯文本、且只动一个已知文件时，才用一次 read_file、write_file 或 edit_file，不必写程序。
        5. 完成后输出 markdown 总结，描述产出物路径；再给出下一步建议。
        """.trim();

    /**
     * 文档与可视化的路由。库 URL 和薄壳示例留在 find_caps（web_lib / docx / pptx），不写进提示词。
     */
    static final String RENDER_PIPELINE = """
        文档和可视化先写结构化源（Markdown、Mermaid 或 JSON），再 find_caps 查 web_lib、docx、pptx。script 和 link 只用结果里的稳定 URL，不要自造 CDN，不要手写大段 HTML 或 CSS。写入 .html 后宿主会自动检查页面，失败按回执改。
        """.trim();

    /** 仅 scriptHostTools（Weizhi $tools 桥）时追加到工作流程段。 */
    static final String WEBVIEW_FROM_SCRIPT = """
        浏览器放进同一段脚本：await $tools.webview_exec({...})，返回值已是回执对象。不要 JSON.parse，也不要再 JSON.stringify。不要在外层单独调 webview_exec。
        """.trim();

    static final String TOOLS_DIRECT = """
        # 主要工具
        - find_caps：检索可用能力/API。query 写全本任务要用的能力，按结果里的调用示例做，不要猜。
        - read_file / write_file / edit_file / list_dir：工作区相对路径（不要 workspace/ 前缀）。辅助：read_url、chat_history。
        """.trim();

    static final String TOOLS_WITH_SCRIPT = """
        # 主要工具
        - run_js：执行 JS 脚本。宿主系统能力（平台 API）只在脚本里，没有对应的外层工具；$tools、工作区读写、import 已有脚本在同一段里 await。路径：fs 与 workspace 内 import 可用相对或绝对路径，须在 workspace 根下（引擎归一化）；catalog 用 from "文件名.js"（不要 ./）；./ 只表示与当前脚本同目录的 workspace 文件；编排入口用 file（如 jobs/run.js）；平台 Caps 的 path 仅相对路径。不要 glob、list_dir 或 catalog_sync 去找脚本库。
        - find_caps：检索可用能力/API。query 写全本任务要用的能力，先看有没有现成接口或同类脚本；按结果里的调用示例写，不要猜函数。
        - 外层 read_file、write_file、edit_file 只用工作区相对路径查看和改脚本，不要 workspace/ 前缀。
        """.trim();

    static final String WEB_SEARCH_LINE = "- web_search：联网搜索（Tavily）。不要编造检索结果。";

    static final String EXPLORE_SUBAGENT = """
        你是 explore 子智能体，只执行只读任务：阅读工作区文件、列目录、find_caps、read_file 文档与汇总信息。
        不要创建、修改或删除文件。
        父智能体让你调研能力时：用 find_caps，query 里并列任务所需关键词。按结果里的调用示例回报。不必对每个关键词各搜一轮。
        完成后向父智能体回报：简明结论，以及条目清单（kind、title、调用示例）；Skill 可摘要要点，勿贴无关长文。
        """.trim();

    static final String GENERAL_SUBAGENT = """
        你是 general 子智能体（编程型）：在当前会话工作区内用一段脚本完成委派任务，不要逐步调用工具。
        完成后向父智能体回报：结论摘要，以及产出物在工作区内的相对路径（若有）。
        """.trim();

    private String hostAppend = "";
    private boolean webSearchEnabled;
    private int maxTurnsPerRun;
    private int maxToolCallsPerRun;

    public ProductivitySystemPromptBuilder hostAppend(String hostAppend) {
        this.hostAppend = hostAppend != null ? hostAppend.trim() : "";
        return this;
    }

    public ProductivitySystemPromptBuilder webSearch(boolean enabled) {
        this.webSearchEnabled = enabled;
        return this;
    }

    /** 注入单 Run 预算，提醒模型把轮次留给「检索一次、跑一段程序、按报错改程序」。 */
    public ProductivitySystemPromptBuilder runLimits(int maxTurnsPerRun, int maxToolCallsPerRun) {
        this.maxTurnsPerRun = maxTurnsPerRun;
        this.maxToolCallsPerRun = maxToolCallsPerRun;
        return this;
    }

    public String buildMainPrompt(Path workspaceRoot, boolean scriptToolRegistered) {
        return buildMainPrompt(workspaceRoot, null, scriptToolRegistered, false);
    }

    public String buildMainPrompt(
        Path workspaceRoot,
        boolean scriptToolRegistered,
        boolean scriptHostTools
    ) {
        return buildMainPrompt(workspaceRoot, null, scriptToolRegistered, scriptHostTools);
    }

    public String buildMainPrompt(
        Path workspaceRoot,
        Path agentRoot,
        boolean scriptToolRegistered,
        boolean scriptHostTools
    ) {
        return buildMainPrompt(workspaceRoot, agentRoot, scriptToolRegistered, scriptHostTools, "");
    }

    public String buildMainPrompt(
        Path workspaceRoot,
        Path agentRoot,
        boolean scriptToolRegistered,
        boolean scriptHostTools,
        String environmentSupplement
    ) {
        return buildMainPrompt(
            workspaceRoot,
            agentRoot,
            scriptToolRegistered,
            scriptHostTools,
            environmentSupplement,
            null
        );
    }

    /** 完整入口；{@code environment} 为 null 时用桌面默认实现。 */
    public String buildMainPrompt(
        Path workspaceRoot,
        Path agentRoot,
        boolean scriptToolRegistered,
        boolean scriptHostTools,
        String environmentSupplement,
        HostEnvironmentProvider environment
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(IDENTITY).append("\n\n");
        String workflow = scriptToolRegistered ? WORKFLOW_WITH_SCRIPT : WORKFLOW_DIRECT;
        if (scriptToolRegistered && scriptHostTools) {
            workflow = workflow + "\n" + WEBVIEW_FROM_SCRIPT;
        }
        sb.append(workflow).append("\n").append(RENDER_PIPELINE).append("\n\n");
        sb.append(buildEnvironmentSection(
            workspaceRoot,
            agentRoot,
            environmentSupplement,
            maxTurnsPerRun,
            maxToolCallsPerRun,
            scriptToolRegistered,
            environment
        ));
        sb.append("\n\n").append(scriptToolRegistered ? TOOLS_WITH_SCRIPT : TOOLS_DIRECT);
        if (webSearchEnabled) {
            sb.append("\n").append(WEB_SEARCH_LINE);
        }
        if (!hostAppend.isEmpty()) {
            sb.append("\n\n").append(hostAppend);
        }
        return sb.toString().trim();
    }

    public String buildExploreSubagentPrompt() {
        return EXPLORE_SUBAGENT;
    }

    public String buildGeneralSubagentPrompt() {
        return GENERAL_SUBAGENT;
    }

    static String buildEnvironmentSection(
        Path workspaceRoot,
        Path agentRoot,
        String environmentSupplement,
        int maxTurnsPerRun,
        int maxToolCallsPerRun,
        boolean scriptToolRegistered,
        HostEnvironmentProvider environment
    ) {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("workspaceRoot required");
        }
        HostEnvironmentProvider env = environment == null
            ? DesktopEnvironmentProvider.INSTANCE
            : environment;
        List<String> lines = new ArrayList<>();
        lines.add("- 时区：" + timezoneLine() + "  语言：" + Locale.getDefault().toLanguageTag());
        String platform = env.platformLine();
        if (!platform.isBlank()) {
            lines.add("- 平台：" + platform);
        }
        String jsRuntime = env.jsRuntimeLine();
        lines.add(scriptToolRegistered
            ? "- JS 运行环境：" + (jsRuntime.isBlank() ? "QuickJS 扩展，非 Node" : jsRuntime)
            : "- JS 运行环境：未启用脚本引擎；没有 Node，不能 npm 或 require");
        String memory = env.memoryLine();
        if (!memory.isBlank()) {
            lines.add("- 内存：" + memory);
        }
        lines.add("- 用户消息末尾的（时间：…）是该消息发出时间；系统提示词不含日期");
        for (String dir : env.directoryLines()) {
            if (dir != null && !dir.isBlank()) {
                lines.add("- " + dir);
            }
        }
        if (maxTurnsPerRun > 0 && maxToolCallsPerRun > 0) {
            lines.add("- 本轮预算：模型↔工具往返最多 "
                + maxTurnsPerRun
                + " 轮，工具执行最多 "
                + maxToolCallsPerRun
                + " 次（可在宿主「模型配置」高级参数调节）。留给按任务和工作流程做完，不要花在逐步试探上。");
        }
        String catalogLine = agentRoot == null ? "" : catalogPendingSummary(agentRoot);
        if (!catalogLine.isBlank()) {
            lines.add(catalogLine);
        }
        if (environmentSupplement != null && !environmentSupplement.isBlank()) {
            lines.add(environmentSupplement.trim());
        }
        return "# 当前环境状态\n" + String.join("\n", lines);
    }

    /** 如「Asia/Shanghai（UTC+08:00）」。 */
    static String timezoneLine() {
        ZoneId zone = ZoneId.systemDefault();
        String offset = zone.getRules().getOffset(Instant.now()).getId();
        return zone.getId() + "（UTC" + offset + "）";
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static String catalogPendingSummary(Path agentRoot) {
        Path pendingFile = agentRoot.resolve("sync/pending.json");
        if (!Files.isRegularFile(pendingFile)) {
            return "";
        }
        try {
            JsonNode root = MAPPER.readTree(PathIo.readString(pendingFile));
            int count = root.path("items").isArray() ? root.path("items").size() : 0;
            if (count <= 0) {
                return "";
            }
            return "- catalog pending（上次 sync check）：" + count + " 条（catalog_sync_status / sync apply）";
        } catch (IOException ignored) {
            return "";
        }
    }
}