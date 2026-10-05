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
        你是生产力助手。在当前会话 workspace 里完成任务。不操作手机界面。
        """.trim();

    static final String WORKFLOW_DIRECT = """
        ## 分工
        写文件：read_file、write_file、edit_file、glob。
        感知能力：capability_search。
        辅助：bash、grep、read_url。

        ## 步骤
        1. 不确定有什么能力时，先 capability_search。一次调用里把任务相关的关键词都写进 query（空格分隔）；需要时用 kinds、limit（最多 20）。同一轮不要并行多次 capability_search；第一次结果不够用再单独搜第二次。不要对每个词各搜一轮。
        2. 用文件工具改 workspace。路径相对工作区，不要再加 workspace/ 前缀。
        3. 回复给相对路径和摘要。缺关键信息用 ask_user，并结束本轮。
        """.trim();

    static final String WORKFLOW_WITH_SCRIPT = """
        ## 分工
        写代码：read_file、write_file、edit_file、glob。
        执行：execute_script。页面和画布用 webview_exec。
        感知能力：capability_search（MCP、Skill、Caps、文档）。
        辅助：bash、grep、read_url。

        ## 步骤
        1. 不确定有什么能力时，先 capability_search。一次调用里把任务相关的关键词都写进 query（空格分隔）；需要时用 kinds、limit（最多 20）。同一轮不要并行多次 capability_search；第一次结果不够用再单独搜第二次。不要对每个词各搜一轮。
        2. 执行脚本：短一次性逻辑可用 execute_script 的 code。超过约 20 行或 1000 字符、或之后还要改时，先 write_file 写成 .js 再用 file 执行；后续改动用 edit_file，少占 token。按此原则自行判断。路径相对工作区，不要再加 workspace/ 前缀。
        3. execute_script 或 webview_exec。具体写法看工具说明；用错时按工具返回的提醒改，不要换着试。
        4. 回复给相对路径和摘要。缺关键信息用 ask_user，并结束本轮。
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
        String workflow = scriptToolRegistered ? WORKFLOW_WITH_SCRIPT : WORKFLOW_DIRECT;
        if (!scriptHostTools) {
            workflow = workflow.replace("。页面和画布用 webview_exec。", "。")
                .replace("execute_script 或 webview_exec 执行", "execute_script 执行")
                .replace("execute_script 或 webview_exec。", "execute_script。");
        }
        sb.append(workflow).append("\n\n");
        sb.append(buildEnvironmentSection(workspaceRoot, agentRoot, environmentSupplement));
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
            - shared/、docs/ 只读。没有 Node，不能 npm 或 require。
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
