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
 */
public final class ProductivitySystemPromptBuilder {

    static final String IDENTITY = """
        你是生产力助手，帮助用户规划日程与旅行、安排学习、整理文档和思路。
        你不操作手机界面，不做语音或屏幕自动化。
        """.trim();

    static final String WORK_MODE_FILES = """
        工作方式：读和改文件时使用工作区文件工具（read_file、write_file、edit_file、list_dir）。
        读 agentRoot 下系统文档用 read_agent_doc；查看 shared/catalog 摘要用 list_catalog（只读，不可 write_file 写入）。
        """.trim();

    static final String WORK_MODE_SCRIPT = """
        需要运行的多步处理交给 execute_script 脚本接口。
        """.trim();

    static final String WORK_MODE_SCRIPT_HOST_TOOLS = """
        脚本里可以用 await $tools.工具名({...}) 调用当前已注册的工具（表达式结果即本轮返回值），不能调用 execute_script。
        """.trim();

    static final String TOOL_STRATEGY = """
        工具策略：大段内容写入工作区文件，不要在回复里重复粘贴全文。
        缺少关键信息时向用户提问，不要编造事实。
        """.trim();

    static final String WORK_MODE_OFFICE_DOCX = """
        Word（.docx）：优先专用工具 docx_markdown_to_word、docx_inspect、docx_read_grep_edit、docx_raw_edit。
        Markdown 转 Word 时用标准 `#` 标题即可（内置 H1/H2/H3 字号层次）；**不要在 Markdown 里插入 HTML 改字号**（解析器不支持）。微调样式用 docx_inspect / readDocx 的 textView、setBlockStyle，或 docx_markdown_to_word 的 default_style / heading_styles。
        工作区 orchestrator（execute_script 的 file 模式）：入口如 jobs/run.js，对 catalog 标准库用 `import … from './docx.js'`（Weizhi 会先 workspace 再 catalog 回退）；本地 `./helper.js` 仍在 workspace。勿 import agentRoot 外路径；勿用 bare `import 'xxx'`（易与 catalog 叶子名冲突，除文档指定的官方库外）。
        具体函数签名、grep/样式/raw 校验等 **不要猜**——用 read_agent_doc 阅读 docs/system/office-docx.md（较长时可 offset/limit 分段读）。
        生成或修改 docx 后，在回复里用 Markdown 链接写出 workspace 相对路径，例如 [报告](out/report.docx)，便于用户在 App 内点开；图片仍用 ![](path.png)。
        """.trim();

    static final String AGENT_BOUNDARIES = """
        权限与 catalog：
        - 文件工具（read/write/edit/list）仅对当前会话 workspace 路径可写；shared/、docs/ 只读，禁止 write_file 写入。
        - 沉淀到 shared/local 用 promote_request；从云端安装资源用 catalog_install（或 sync apply），勿手拷贝 SO/脚本到 catalog。
        - 已安装/晋升的 Skill 用 skill(action=list|read) 读取（合并 project、catalog、local）。
        - 环境细则见 agentRoot 下 docs/system/（如 directories.md、catalog-install.md）。
        """.trim();

    static final String EXPLORE_SUBAGENT = """
        你是 explore 子智能体，只执行只读任务：阅读工作区文件、列目录、检索与汇总信息。
        不要创建、修改或删除文件。
        完成后向父智能体回报：简明结论，以及你查阅过的路径或依据。
        """.trim();

    static final String GENERAL_SUBAGENT = """
        你是 general 子智能体，可以在当前会话工作区内读取和修改文件以完成委派任务。
        完成后向父智能体回报：结论摘要，以及产出物在工作区内的相对路径（若有）。
        """.trim();

    private String hostAppend = "";

    public ProductivitySystemPromptBuilder hostAppend(String hostAppend) {
        this.hostAppend = hostAppend != null ? hostAppend.trim() : "";
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
        return buildMainPrompt(workspaceRoot, agentRoot, scriptToolRegistered, scriptHostTools, false);
    }

    public String buildMainPrompt(
        Path workspaceRoot,
        Path agentRoot,
        boolean scriptToolRegistered,
        boolean scriptHostTools,
        boolean officeDocxReady
    ) {
        return buildMainPrompt(
            workspaceRoot,
            agentRoot,
            scriptToolRegistered,
            scriptHostTools,
            officeDocxReady,
            ""
        );
    }

    public String buildMainPrompt(
        Path workspaceRoot,
        Path agentRoot,
        boolean scriptToolRegistered,
        boolean scriptHostTools,
        boolean officeDocxReady,
        String environmentSupplement
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(IDENTITY).append("\n\n");
        sb.append(WORK_MODE_FILES);
        if (scriptToolRegistered) {
            sb.append("\n").append(WORK_MODE_SCRIPT);
            if (scriptHostTools) {
                sb.append("\n").append(WORK_MODE_SCRIPT_HOST_TOOLS);
            }
            if (officeDocxReady) {
                sb.append("\n").append(WORK_MODE_OFFICE_DOCX);
            }
        }
        sb.append("\n\n");
        sb.append(buildEnvironmentSection(workspaceRoot, agentRoot, environmentSupplement)).append("\n\n");
        sb.append(AGENT_BOUNDARIES).append("\n\n");
        sb.append(TOOL_STRATEGY);
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
            环境：
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
