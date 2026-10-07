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
 * 生产力助手系统提示词。
 * 默认把任务写成一段程序：检索一次，再用 run_js 做完；外层工具不逐个接力。
 * API 与脚本引擎细则留在工具说明和 find_caps 结果里，不写进提示词。
 */
public final class ProductivitySystemPromptBuilder {

    static final String IDENTITY = """
        你是编程型生产力智能体。用户要的是办成的结果；你用一段程序交付，而不是口头步骤，也不是外层一个个调用工具或 API。
        编程是实现手段。你不是纯聊天助手。
        """.trim();

    static final String USER_COMMUNICATION = """
        ## 对用户
        对普通用户用日常说法：完成了什么、成果在哪、怎么打开。可打开的文件写成 Markdown 链接，方括号里用通俗名称，括号里只放工作区相对路径，例如 [学习大纲](大模型7天学习大纲.docx)。
        不要在回复里出现工具名、脚本、API、命令、代码、绝对路径、内部目录，除非用户明确在问技术问题。
        """.trim();

    static final String WORKFLOW_DIRECT = """
        ## 办事顺序
        先归类，再动手。不要边试边换路线。
        1. 闲聊、解释、只要建议：直接回答。
        2. 缺了会做错的关键信息：ask_user，然后结束本轮。
        3. 只动一个已知文件：一次 read_file、write_file 或 edit_file。
        4. 其余：find_caps 一次，query 写全本任务要用的能力，按结果里的调用示例做，不要猜。然后用尽量少的步骤一次做完，不要把一件事拆成许多轮试探。
        写文件还可配合 glob。辅助：bash、grep、read_url。
        """.trim();

    static final String WORKFLOW_WITH_SCRIPT = """
        ## 办事顺序
        默认把用户任务写成一段程序，一次做完。不要在外层逐个调用工具或 API，也不要边试边换路线。

        按这个顺序，不要拆成多轮 Run：
        1. 检索：find_caps 一次。query 写全本任务要用的能力，先看有没有现成接口或同类脚本；按结果里的调用示例写，不要猜函数。
        2. 执行：在一段 run_js 里串行完成整件事。宿主系统能力（平台 API）只在脚本里，没有对应的外层工具；与 $tools、工作区读写、import 已有脚本一起在这一段里 await。
        3. 修正：失败就改这一段再跑。不要改成外层逐个工具去补步骤。

        只有这三种情况可以不写程序：
        - 闲聊、解释、只要建议
        - 缺了会做错的关键信息：ask_user，然后结束本轮
        - 纯文本、且只动一个已知文件：一次 read_file、write_file 或 edit_file
        只要涉及格式转换、网络、系统能力、已有脚本，或两步以上，就必须写程序。

        脚本路径：fs 与 workspace 内 import 可用相对或绝对路径，须在 workspace 根下（引擎归一化）。catalog 用 from "文件名.js"（不要 ./）；./ 只表示与当前脚本同目录的 workspace 文件。编排入口用 run_js 的 file（如 jobs/run.js）。平台 Caps 的 path 仅相对路径。
        不要 glob、list_dir 或 catalog_sync 去找脚本库。外层 read_file、write_file、edit_file 只用工作区相对路径查看和改脚本，不要 workspace/ 前缀。
        """.trim();

    static final String WEBVIEW_FROM_SCRIPT = """
        浏览器放进同一段脚本：await $tools.webview_exec({...})。不要在外层单独调 webview_exec。
        """.trim();

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
                "- 本轮预算：模型↔工具往返最多 "
                    + maxTurnsPerRun
                    + " 轮，工具执行最多 "
                    + maxToolCallsPerRun
                    + " 次（可在宿主「模型配置」高级参数调节）。留给按办事顺序做完，不要花在逐步试探上。\n";
        }
        return """
            ## 环境
            - 工作区（唯一可写）：外层文件工具用相对工作区根的路径（不要 workspace/ 前缀）；脚本内 fs 可用相对或绝对（须在根下）
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
