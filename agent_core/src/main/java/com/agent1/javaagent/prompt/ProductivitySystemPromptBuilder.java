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
 * 优先 execute_script；细节不写进提示词，编程前用 capability_search 发现 API 与现成脚本。
 */
public final class ProductivitySystemPromptBuilder {

    static final String IDENTITY = """
        你是编程型生产力智能体：在当前会话 workspace 里写代码、改文件、用脚本编排完成任务。
        你不是纯聊天助手；默认用工程化方式交付（脚本、工作区产物、可验证步骤），而不是只给口头步骤。
        服务对象是普通用户；编程与工具是你的实现手段，不是对话主题。
        """.trim();

    static final String USER_COMMUNICATION = """
        ## 对用户说话
        用户大多不是技术人员。回复时用日常、结果导向的语言：说明完成了什么、成果在哪、如何查看或使用，避免展开「怎么做的」技术细节。
        不要在回复里出现工具名、脚本、API、命令行、代码片段、文件路径前缀、内部目录名等实现信息，除非用户明确在问技术问题，或必须给出可复制的操作步骤才能安全完成。
        需要指代产出物时，用通俗名称（如「报告 Word 文档」「整理好的表格」）；确需让用户打开某个文件时，只说文件名或简短说明，不要堆砌路径与工程术语。
        缺关键信息用 ask_user，并结束本轮。
        """.trim();

    static final String WORKFLOW_DIRECT = """
        ## 分工
        写文件：read_file、write_file、edit_file、glob。
        查已有能力：capability_search。
        辅助：bash、grep、read_url。

        ## 怎么做
        动手改文件或调用不熟悉的能力前，用 capability_search 看有没有现成 API 或同类说明。
        命中结果里的调用示例可以直接写进脚本。
        多步骤任务尽量合并为一次可执行方案（脚本或单次编排），少占外层工具轮次。
        用文件工具改 workspace。路径一律相对工作区，不要写绝对路径，也不要加 workspace/ 前缀。
        """.trim();

    static final String WORKFLOW_WITH_SCRIPT = """
        ## 分工
        优先用 execute_script 完成任务（QuickJS：编排、MCP、catalog 脚本、$tools）。
        Weizhi 的平台 API（android.* / mac.* / linux.*）用来把宿主系统能力交给脚本使用。具体函数不要猜，先 capability_search，按结果里的调用示例写。
        写代码：read_file、write_file、edit_file、glob。
        查已有能力：capability_search（API、脚本、MCP、Skill、文档）。
        辅助：bash、grep、read_url。

        ## 怎么做
        动手写脚本或调用不熟悉的 API 前，用 capability_search 看有没有现成接口或同类脚本。一次 query 把相关词写全；需要时用 kinds、limit（最多 20）。同一轮不必并行多次搜。
        命中结果里的调用示例可以直接写进 execute_script。
        多步骤任务：在一段 execute_script 里串行完成（await 平台 API、await $tools.工具名、读写 workspace 文件），不要拆成多轮 Run、也不要外层逐个工具慢慢试。
        短一次性逻辑可用 execute_script 的 code。超过 20 行或 1000 字符时，运行时会把 code 写入 jobs/ 再执行，并在结果里给出路径；之后用 edit_file 改该文件，再用 file。路径相对工作区，不要写绝对路径，也不要加 workspace/ 前缀。
        脚本里 await $tools.工具名({...}) 可调用已注册的外层工具（不能再调 execute_script）。具体写法看各工具说明；用错时按返回提醒改。
        """.trim();

    static final String WEBVIEW_FROM_SCRIPT = """
        webview_exec 已挂到 $tools：在 execute_script 里 await $tools.webview_exec({...}) 即可进入浏览器环境，与外层同名工具相同。
        """.trim();

    static final String EXPLORE_SUBAGENT = """
        你是 explore 子智能体，只执行只读任务：阅读工作区文件、列目录、capability_search、read_file 文档与汇总信息。
        不要创建、修改或删除文件。
        父智能体让你调研能力时：用 capability_search，query 里并列任务所需关键词。按结果里的调用示例回报。不必对每个关键词各搜一轮。
        完成后向父智能体回报：简明结论，以及条目清单（kind、title、调用示例）；Skill 可摘要要点，勿贴无关长文。
        """.trim();

    static final String GENERAL_SUBAGENT = """
        你是 general 子智能体（编程型）：可以在当前会话工作区内读取和修改文件、写脚本片段以完成委派任务。
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

    /** 注入单 Run 工具预算，便于模型在接近上限前改用 execute_script 一次性收尾。 */
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
        StringBuilder sb = new StringBuilder();
        sb.append(IDENTITY).append("\n\n");
        sb.append(USER_COMMUNICATION).append("\n\n");
        String workflow = scriptToolRegistered ? WORKFLOW_WITH_SCRIPT : WORKFLOW_DIRECT;
        if (scriptToolRegistered && scriptHostTools) {
            workflow = workflow + "\n" + WEBVIEW_FROM_SCRIPT;
        }
        sb.append(workflow).append("\n\n");
        sb.append(buildEnvironmentSection(
            workspaceRoot,
            agentRoot,
            environmentSupplement,
            maxTurnsPerRun,
            maxToolCallsPerRun
        ));
        if (webSearchEnabled) {
            sb.append("\n- 公开信息可用 web_search（Tavily）。不要编造检索结果。");
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

    static String buildEnvironmentSection(Path workspaceRoot, Path agentRoot) {
        return buildEnvironmentSection(workspaceRoot, agentRoot, "", 0, 0);
    }

    static String buildEnvironmentSection(Path workspaceRoot, Path agentRoot, String environmentSupplement) {
        return buildEnvironmentSection(workspaceRoot, agentRoot, environmentSupplement, 0, 0);
    }

    static String buildEnvironmentSection(
        Path workspaceRoot,
        Path agentRoot,
        String environmentSupplement,
        int maxTurnsPerRun,
        int maxToolCallsPerRun
    ) {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("workspaceRoot required");
        }
        String date = LocalDate.now(ZoneId.systemDefault()).toString();
        String osName = System.getProperty("os.name", "unknown");
        String catalogLine = agentRoot == null ? "" : catalogPendingSummary(agentRoot);
        String extra = environmentSupplement == null || environmentSupplement.isBlank()
            ? ""
            : environmentSupplement.trim() + "\n";
        String runLimitsLine = "";
        if (maxTurnsPerRun > 0 && maxToolCallsPerRun > 0) {
            runLimitsLine =
                "- 单条用户消息的一轮 Run 预算：模型↔工具往返最多 "
                    + maxTurnsPerRun
                    + " 轮，工具执行最多 "
                    + maxToolCallsPerRun
                    + " 次（可在宿主「模型配置」高级参数调节）。接近上限时优先用 execute_script 一次完成剩余步骤，并给出可继续的结论。\n";
        }
        return """
            ## 环境
            - 工作区（唯一可写）：当前会话 workspace，路径相对工作区根
            - 日期：%s
            - 平台：%s
            %s- shared/ 只读，不能用 write_file 修改。没有 Node，不能 npm 或 require。
            %s%s
            """.formatted(date, osName, runLimitsLine, catalogLine, extra).trim();
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
