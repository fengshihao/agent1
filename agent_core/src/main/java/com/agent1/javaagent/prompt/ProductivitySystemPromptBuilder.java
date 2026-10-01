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
        从公开 http(s) 链接读取网页标题和正文用 read_url（不访问内网）。配置了 TAVILY_API_KEY 时，read_url 先用 Tavily 抽同一个 URL，失败再本地抓取。
        读 agentRoot 系统文档：read_file / list_dir / grep / glob，路径用 docs/system/... 或 docs/capabilities/...（只读，不可 write_file/edit_file 写入）。
        查看 shared/catalog 摘要用 list_catalog（只读，不可 write_file 写入）。
        """.trim();

    static final String WORK_MODE_SCRIPT = """
        需要运行的多步处理交给 execute_script 脚本接口。
        """.trim();

    static final String WORK_MODE_SCRIPT_HOST_TOOLS = """
        脚本里可以用 await $tools.工具名({...}) 调用当前已注册的工具（表达式结果即本轮返回值），不能调用 execute_script。
        """.trim();

    static final String JS_FIRST = """
        编程智能体：优先用 execute_script（workspace 内 file orchestrator）完成任务；平台能力（android.* / fs / host / catalog 脚本 / $tools）在脚本内调用，不要臆造外层 tool 名。
        """.trim();

    static final String TOOL_STRATEGY = """
        工具策略：大段内容写入工作区文件，不要在回复里重复粘贴全文。
        工作框架已覆盖的路径、权限和晋升步骤直接执行。查阅 Skill 用 capability_search：命中 skill 时正文已经附在结果里。
        平台 API、Caps、catalog 脚本、办公文档等：capability_search 后再 read_file 该条 doc_path。不要为了找框架文档去空搜或通读 docs/system。
        缺少关键信息时调用 ask_user 发起结构化提问并暂停 Run；不要用长段正文代替 ask_user，也不要在 ask_user 同一轮继续调用其他工具或先写完整交付物。
        用户未确认前不要编造事实；用户下一条消息将开启新的 Run。
        """.trim();

    static final String TOOL_STRATEGY_JS_TAIL = "然后写 workspace 内 JS orchestrator，用 execute_script（file 模式）执行。";

    static final String CAPABILITY_MAP = """
        能力大类（细节靠 capability_search + 文档）：沙箱 fs/path；平台 Caps（如 android.files/share，仅脚本内）；host.ensureNative/fetch；catalog 脚本（如 docx.js）；Skill 流程；MCP；$tools 桥（grep/webview 等）。
        """.trim();

    static final String WORK_MODE_OFFICE_DOCX = """
        Word（.docx）：优先专用工具 docx_markdown_to_word、docx_inspect、docx_read_grep_edit、docx_raw_edit。
        Markdown 转 Word 时用标准 `#` 标题即可（内置 H1/H2/H3 字号层次）；**不要在 Markdown 里插入 HTML 改字号**（解析器不支持）。微调样式用 docx_inspect / readDocx 的 textView、setBlockStyle，或 docx_markdown_to_word 的 default_style / heading_styles。
        工作区 orchestrator（execute_script 的 file 模式）：入口如 jobs/run.js，对 catalog 标准库用 `import … from './docx.js'`（Weizhi 会先 workspace 再 catalog 回退）；本地 `./helper.js` 仍在 workspace。勿 import agentRoot 外路径；勿用 bare `import 'xxx'`（易与 catalog 叶子名冲突，除文档指定的官方库外）。
        具体函数签名、样式/raw 校验等 **不要猜**——用 read_file 阅读 docs/system/office-docx.md（较长时分段 offset/limit；可用 grep/glob 在 docs/system 内查找）。
        生成或修改 docx 后，在回复里用 Markdown 链接写出 workspace 相对路径，例如 [报告](out/report.docx)，便于用户在 App 内点开；图片仍用 ![](path.png)。
        """.trim();

    static final String AGENT_BOUNDARIES = """
        工作框架（路径、权限、Skill 与 catalog 已写明，直接执行或直接回答）：
        - 可写范围只有当前会话 workspace。shared/、docs/system、docs/capabilities 只读，禁止 write_file 写入。
        - 查阅 Skill：capability_search。内置、project、shared/catalog、shared/local 里名称或描述对上的技能会直接带上正文。没有 list/read。
        - 创建 Skill：capability_search「skill-creator」，按返回正文写 workspace/staging/skills/<name>/SKILL.md（YAML frontmatter 至少含 name 与 description，name 与目录名一致），再 promote_request 到 shared/local/skills/<name>/。不要把密钥写进文件。
        - 创建可复用脚本：workspace/staging/scripts/<name>.js，可选同名 .meta.json，同样 promote_request 到 shared/local/scripts/。
        - 安装云端资源：catalog_sync_status 查看 pending，catalog_install（或 sync apply）装入 shared/catalog/。不要手拷 SO 或脚本到 catalog。
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
        if (scriptToolRegistered) {
            sb.append(JS_FIRST).append("\n\n");
            sb.append(CAPABILITY_MAP).append("\n\n");
        }
        sb.append(TOOL_STRATEGY);
        if (scriptToolRegistered) {
            sb.append("\n").append(TOOL_STRATEGY_JS_TAIL);
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
