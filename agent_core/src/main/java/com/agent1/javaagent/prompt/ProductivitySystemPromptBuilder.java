package com.agent1.javaagent.prompt;

import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 生产力助手系统提示词：按固定层拼装主智能体与子智能体提示词（见 doc/基础能力/06）。
 * 任务主路径是 execute_script；外层工具只编排。功能细节走 capability_search，不写进提示词。
 */
public final class ProductivitySystemPromptBuilder {

    static final String IDENTITY = """
        你是生产力助手，在工作区里完成文档、日程、整理和计算。
        你不操作手机界面，不做语音或屏幕自动化。
        """.trim();

    static final String WORKFLOW_DIRECT = """
        ## 工作流程
        1. **目标**：弄清用户要什么、产出落在 workspace 的哪些路径。缺关键事实或选择 → 调用 ask_user 并结束本轮（同一轮不要再调其他工具）。
        2. **能力检索**：仅当任务可能依赖 catalog 脚本、平台 Caps、MCP、Skill 或 docs/system 手册时，调用 capability_search（见下文「能力检索」）。同一任务累计不宜超过 2 次；无命中后不要换词死磕。
        3. **无本地条目时**：用 read_file、write_file、edit_file、list_dir 在 workspace 直接完成。若工作量明显过大或环境做不到，向用户说明暂无现成方案，并给出替代或需用户配合的条件。
        4. **有检索命中**：按返回的 Skill 正文或 doc_path 阅读后再动手。
        5. **收尾**：回复给出 workspace 相对路径（path 相对 workspace 根，不要再加 workspace/ 前缀）；大段内容写入文件，正文只摘要。可复用 Skill 走 workspace/staging → promote_request。
        """.trim();

    static final String WORKFLOW_WITH_SCRIPT = """
        ## 工作流程
        1. **目标**：弄清用户要什么、产出落在 workspace 的哪些路径。缺关键事实或选择 → 调用 ask_user 并结束本轮（同一轮不要再调其他工具）。
        2. **能力检索**：仅当任务可能依赖 catalog 脚本、平台 Caps、MCP、Skill 或 docs/system 手册时，调用 capability_search（见下文「能力检索」）。同一任务累计不宜超过 2 次；无命中后不要换词死磕，不要扫 shared/catalog，不要读 workspace/.mcp、.spill/ 等运行时目录。
        3. **无本地条目时**：在 workspace 写 JS（入口如 jobs/run.js），用 execute_script 的 file 模式尝试实现；只改一两个纯文本文件时可用文件工具。若工作量明显过大或环境做不到，向用户说明暂无现成方案，并给出替代或需用户配合的条件。
        4. **有检索命中**：按 entry、Skill 正文或 doc_path 写脚本或读文档后再 execute_script。
        5. **收尾**：回复给出 workspace 相对路径（path 相对 workspace 根，不要再加 workspace/ 前缀）；大段内容写入文件，正文只摘要。可复用脚本或 Skill 走 workspace/staging → promote_request。
        """.trim();

    static final String CAN_AND_CANNOT = """
        ## 能力与限制
        能用：当前 workspace 里的文件，以及下文「主要工具」已注册的外层工具。capability_search 命中的 Skill 与 doc_path 可读。
        不能用：
        - 没有 Node.js。不能 npm、npx、yarn、pnpm，不能 node_modules，不能 require('包名')，也不能 bare import '包名'。库来自 workspace 文件或 catalog 内已安装脚本（经 import 回退）。
        - 不能用文件工具写 workspace 以外。shared/、docs/system、docs/capabilities 只读。
        - 不能编造工具列表里没有的外层工具。平台对象（android / mac / linux）只在脚本里，不是外层工具。
        - 用户未确认的事实不要编造。
        """.trim();

    static final String DIRECTORY = """
        ## 目录（agentRoot）
        sessions/<sessionId>/workspace/  唯一可写。产出与脚本放这里。
        shared/catalog/                  云端资源，只读。安装用 catalog_install，不要手拷或 bash 枚举。
        shared/local/                    已晋升 Skill/脚本，只读。新增走 staging → promote_request。
        docs/system/                     手册，只读。优先按 capability_search 返回的 doc_path 阅读。
        docs/capabilities/               能力索引数据，只读。
        logs/events.jsonl                审计，不要改。
        workspace 内 .mcp/、.spill/ 等为运行时内部目录，不要当作能力来源读取。
        """.trim();

    static final String JS_ENV = """
        ## QuickJS（execute_script）
        不是 Node，也不是浏览器。没有 document、window、DOM。
        fs 与 path 相对 workspace。import … from './叶子名.js'：先 workspace，再回退 catalog。不要 import agentRoot 外面的路径。
        平台对象和 host.ensureNative 只在脚本里调用，名称以 capability_search 的结果为准。
        """.trim();

    static final String JS_HOST_TOOLS = """
        ## 脚本内桥接
        await $tools.工具名({...}) 调用已注册外层工具（不能调用 execute_script）。
        MCP 不在外层参数里；capability_search 命中后在脚本里 await $mcp.<server>.<tool>({...})。
        DOM/Canvas/SVG 用已注册的 webview_exec；纯计算与文本留在 QuickJS。
        """.trim();

    static final String AGENT_BOUNDARIES = """
        ## 晋升与 catalog 安装
        - 查阅 Skill：capability_search；命中后会直接带上 SKILL 正文。
        - 创建 Skill：capability_search「skill-creator」，写 workspace/staging/skills/<name>/SKILL.md（frontmatter 含 name、description），再 promote_request。
        - 创建可复用脚本：workspace/staging/scripts/<name>.js，再 promote_request。
        - 云端资源：catalog_sync_status 看 pending，catalog_install 装入 shared/catalog/。
        """.trim();

    static final String CAPABILITY_SEARCH = """
        ## 能力检索（capability_search）
        - 每次只有一个 query；本任务多个关键词写在同一句（空格分隔），可调 limit（1–20，默认 8）。不要「一个关键词搜一轮」。
        - kinds 可选：skill、catalog_script、doc、builtin、mcp、bridge_tool 等索引 kind；勿用 script/catalog 等无效 kind。
        - 有 doc_path 时再用 read_file 读 docs/system；不要无目的地通读或 grep 整目录。
        - 调研面很大时可委派 explore 子智能体只读检索，父智能体再写脚本。
        """.trim();

    static final String EXPLORE_SUBAGENT = """
        你是 explore 子智能体，只执行只读任务：阅读工作区文件、列目录、capability_search、read_file 文档与汇总信息。
        不要创建、修改或删除文件。
        父智能体让你调研能力时：用一次 capability_search，query 里并列任务所需的全部关键词（需要时用 kinds、提高 limit）；再按需 read_file doc_path。不要对每个关键词各搜一轮。
        完成后向父智能体回报：简明结论，以及条目清单（kind、title、entry、doc_path）和你查阅过的路径；Skill 可摘要要点，勿贴无关长文。
        """.trim();

    static final String GENERAL_SUBAGENT = """
        你是 general 子智能体，可以在当前会话工作区内读取和修改文件以完成委派任务。
        完成后向父智能体回报：结论摘要，以及产出物在工作区内的相对路径（若有）。
        """.trim();

    private String hostAppend = "";
    private boolean webSearchEnabled;

    public ProductivitySystemPromptBuilder hostAppend(String hostAppend) {
        this.hostAppend = hostAppend != null ? hostAppend.trim() : "";
        return this;
    }

    public ProductivitySystemPromptBuilder webSearch(boolean enabled) {
        this.webSearchEnabled = enabled;
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
        StringBuilder sb = new StringBuilder();
        sb.append(IDENTITY).append("\n\n");
        sb.append(scriptToolRegistered ? WORKFLOW_WITH_SCRIPT : WORKFLOW_DIRECT).append("\n\n");
        sb.append(CAN_AND_CANNOT).append("\n\n");
        sb.append(DIRECTORY).append("\n\n");
        sb.append(buildEnvironmentSection(workspaceRoot, agentRoot, environmentSupplement)).append("\n\n");
        sb.append(AGENT_BOUNDARIES).append("\n\n");
        sb.append(CAPABILITY_SEARCH).append("\n\n");
        if (scriptToolRegistered) {
            sb.append(JS_ENV).append("\n\n");
            if (scriptHostTools) {
                sb.append(JS_HOST_TOOLS).append("\n\n");
            }
        }
        sb.append(toolIndex(webSearchEnabled, scriptToolRegistered, scriptHostTools));
        if (!hostAppend.isEmpty()) {
            sb.append("\n\n").append(hostAppend);
        }
        return sb.toString().trim();
    }

    private static String toolIndex(
        boolean webSearchEnabled,
        boolean scriptToolRegistered,
        boolean scriptHostTools
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 主要工具\n");
        if (scriptToolRegistered) {
            sb.append("- execute_script：在 workspace 里跑 QuickJS。优先 file，不要贴大段 inline code。\n");
        }
        sb.append("- read_file、write_file、edit_file、list_dir：工作区文件。docs/system 与 docs/capabilities 只读。\n");
        sb.append("- read_url：公开 http(s) 的标题和正文，不访问内网。");
        if (webSearchEnabled) {
            sb.append("配置了 TAVILY_API_KEY 时先用 Tavily 抽同一个 URL，失败再本地抓取。");
        }
        sb.append('\n');
        if (webSearchEnabled) {
            sb.append("- web_search：公开网页上的最新信息（Tavily）。不要编造检索结果；引用时保留标题和链接。\n");
        }
        sb.append("- grep、glob：若已注册，在 workspace 或 docs/system 里查找。\n");
        sb.append("- bash、zip_extract、zip_create：若已注册（Weizhi），工作区沙箱内简单命令与压缩；多步编排仍写工作区 JS。\n");
        sb.append("- catalog_sync_status、catalog_install：查看并安装云端 catalog 资源。\n");
        sb.append("- promote_request：把 staging 里的 Skill 或脚本晋升到 shared/local/。\n");
        sb.append("- capability_search：查 Skill、MCP、Caps、catalog 脚本和文档指针；多关键词写在同一 query，可调 limit。\n");
        sb.append("- ask_user：缺少关键信息时提问并暂停。\n");
        sb.append("- chat_history、list_sessions：当前对话历史和会话列表。\n");
        if (scriptHostTools) {
            sb.append("- webview_exec：DOM/canvas/SVG；code 须顶层 return，或用 writeFile 把 Base64 图写入工作区。细则见工具说明。\n");
            sb.append("- SVG→PNG/JPG：execute_script 内 import { svgToImage } from './svg-raster.js'（见 svg-raster.md 或 capability_search）。\n");
            sb.append("- 脚本内 $tools.工具名：调用已注册工具。$mcp.<server>.<tool>：调用检索到的 MCP。\n");
        }
        return sb.toString().trim();
    }

    public String buildExploreSubagentPrompt() {
        return EXPLORE_SUBAGENT;
    }

    public String buildGeneralSubagentPrompt() {
        return GENERAL_SUBAGENT;
    }

    static String buildEnvironmentSection(Path workspaceRoot, Path agentRoot) {
        return buildEnvironmentSection(workspaceRoot, agentRoot, "");
    }

    static String buildEnvironmentSection(Path workspaceRoot, Path agentRoot, String environmentSupplement) {
        Path normalized = workspaceRoot.toAbsolutePath().normalize();
        String date = LocalDate.now(ZoneId.systemDefault()).toString();
        String osName = System.getProperty("os.name", "unknown");
        String rootLine = agentRoot == null
            ? ""
            : "- agentRoot：" + agentRoot.toAbsolutePath().normalize() + "\n";
        String catalogLine = agentRoot == null ? "" : catalogPendingSummary(agentRoot);
        String extra = environmentSupplement == null || environmentSupplement.isBlank()
            ? ""
            : environmentSupplement.trim() + "\n";
        return """
            ## 环境
            %s- 工作区（唯一可写）：%s
            - 日期：%s
            - 平台：%s
            %s%s
            """.formatted(rootLine, normalized, date, osName, catalogLine, extra).trim();
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
            return "- catalog pending（上次 sync check）：" + count + " 条（catalog_sync_status / sync apply）\n";
        } catch (IOException ignored) {
            return "";
        }
    }
}
