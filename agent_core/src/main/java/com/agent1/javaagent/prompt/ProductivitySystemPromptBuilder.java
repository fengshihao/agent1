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
        你是生产力助手，用工作区和脚本完成文档、日程、整理和计算。
        你不操作手机界面，不做语音或屏幕自动化。
        """.trim();

    static final String HOW_TO_DIRECT_ONLY = """
        生产流程：
        1. 弄清目标和交付物（要落在 workspace 里的哪些文件）。缺关键事实或选择时，调用 ask_user 并暂停 Run；不要用长段正文代替 ask_user，也不要在 ask_user 同一轮继续调用其他工具或先写完整交付物。用户下一条消息将开启新的 Run。
        2. 不熟悉的能力、Skill、catalog 脚本或 API 时，先 capability_search。命中 skill 时正文已经附在结果里；有 doc_path 再 read_file 那一篇。不要通读 docs/system，不要臆造工具名。
        3. 直接生产：只改一两个文件、单次转换、或专用工具一次能完成 → 用 read_file、write_file、edit_file、list_dir，以及 read_url 等外层工具。工具 path 相对当前会话 workspace 根，不要再加 workspace/ 前缀（写 dog.svg 而非 workspace/dog.svg）。多步任务拆成多次工具调用。
        4. 完成后在回复里给出 workspace 相对路径；大段内容写入文件，不要把全文贴回对话。可复用的 Skill 先放 workspace/staging，再 promote_request。
        """.trim();

    static final String HOW_TO_WITH_SCRIPT = """
        生产流程：
        1. 弄清目标和交付物（要落在 workspace 里的哪些文件）。缺关键事实或选择时，调用 ask_user 并暂停 Run；不要用长段正文代替 ask_user，也不要在 ask_user 同一轮继续调用其他工具或先写完整交付物。用户下一条消息将开启新的 Run。
        2. 不熟悉的能力、Skill、catalog 脚本或 API 时，先 capability_search。命中 skill 时正文已经附在结果里；有 doc_path 再 read_file 那一篇。不要通读 docs/system，不要臆造工具名。
        3. 按任务选交付方式：
           直接生产：只改一两个文件、单次转换、或专用工具一次能完成 → 用 read_file、write_file、edit_file、list_dir，以及 docx_*、read_url、webview_exec 等外层工具。path 相对 workspace 根，不要再加 workspace/ 前缀。
           编程生产：多步流水线、要组合 Caps / $tools / MCP、要用 catalog 模块、或同一逻辑要跑多轮 → 在 workspace 写 JS 程序（入口如 jobs/run.js，可拆 helper），用 execute_script 的 file 模式执行；产出仍写入 workspace。
        4. 完成后在回复里给出 workspace 相对路径；大段内容写入文件，不要把全文贴回对话。可复用的脚本或 Skill 先放 workspace/staging，再 promote_request。
        """.trim();

    static final String CAN_AND_CANNOT = """
        能用：当前 workspace 里的文件，以及下面「主要工具」里已经注册的工具。capability_search 搜到的 Skill 和文档可以读。
        不能用：
        - 没有 Node.js。不能 npm、npx、yarn、pnpm，不能 node_modules，不能 require('包名')，也不能 bare import '包名'。需要的库只来自 workspace 里的文件，或已经安装在 catalog 里的脚本。
        - 不能用文件工具写 workspace 以外。shared/、docs/system、docs/capabilities 只读。
        - 不能编造工具列表里没有的外层工具。android.* 这类平台对象只在脚本里，不是外层工具。
        - 用户未确认的事实不要编造。
        """.trim();

    static final String DIRECTORY = """
        目录（agentRoot）：
        sessions/<sessionId>/workspace/  唯一可写。文件、脚本和产出都放这里。
        shared/catalog/                  云端资源，只读。安装用 catalog_install，不要手拷文件进去。
        shared/local/                    已晋升的 Skill 和脚本，只读。新增走 workspace/staging 再 promote_request。
        docs/system/                     手册，只读。按 capability_search 的 doc_path 阅读。
        docs/capabilities/               能力索引，只读。
        logs/events.jsonl                审计，不要改。
        """.trim();

    static final String JS_ENV = """
        JS 环境是 QuickJS（execute_script），不是 Node，也不是浏览器。没有 document、window、DOM。fs 与 path 相对 workspace。
        编程智能体：能跑的步骤放进 QuickJS，外层工具只编排。多步任务先写 workspace 内的 .js（如 jobs/run.js），再用 execute_script 的 file 模式执行。
        模块写 import … from './叶子名.js'：先找 workspace，再回退 catalog。不要 import agentRoot 外面的路径。
        要 DOM、Canvas 或按 HTML 排版时，只用工具列表里已有的 webview_exec。不要在 QuickJS 里假装有浏览器，也不要为此装包。纯计算和文本留在 QuickJS。
        平台对象（如 android.files、android.share）和 host.ensureNative 只在脚本里调用，名称以 capability_search 的结果为准。
        """.trim();

    static final String JS_WEBVIEW = """
        webview_exec：读工作区文件用 input_path（脚本内全局 input 为 Uint8Array），不要用 fetch('相对路径')。code 在函数里执行，必须顶层 return；异步写成 return (async () => { ... })()。只写 (async () => {})()，或把 return 放在 img.onload 里，完成值是 undefined，会报没有可落盘的返回值。
        画图可以不 return：writeFile('route.png', canvas.toDataURL('image/png').split(',')[1])。宿主把纯 Base64 解码成图片字节写入工作区。writeFile 要放在脚本等待的 Promise 里。
        若仍 return 纯 PNG/JPEG Base64，可省略 output_path，运行时落盘 tmp/webview_exec/*.b64（文件内容是 Base64 文本）。output_path 写入的也是 UTF-8 文本，不是二进制图片。
        SVG 转 PNG 或 JPG 不要手写 canvas：execute_script 的 file 脚本里 import { svgToImage } from './svg-raster.js'，传入 svgPath、width、height（高度也可写 length；正方形可只给 size）、format（png 或 jpg）。函数写出二进制图片。参数见 docs/system/svg-raster.md，或 capability_search「svg png」。
        """.trim();

    static final String JS_HOST_TOOLS = """
        脚本里可以用 await $tools.工具名({...}) 调用当前已注册的工具（表达式结果即本轮返回值），不能调用 execute_script。
        MCP 接口不出现在外层工具参数里。capability_search 命中后，在脚本里 await $mcp.<server>.<tool>({...})。
        """.trim();

    static final String WORK_MODE_OFFICE_DOCX = """
        Word（.docx）：优先专用工具 docx_markdown_to_word、docx_inspect、docx_read_grep_edit、docx_raw_edit。
        Markdown 转 Word 时用标准 `#` 标题即可（内置 H1/H2/H3 字号层次）；**不要在 Markdown 里插入 HTML 改字号**（解析器不支持）。微调样式用 docx_inspect / readDocx 的 textView、setBlockStyle，或 docx_markdown_to_word 的 default_style / heading_styles。
        工作区 orchestrator（execute_script 的 file 模式）：入口如 jobs/run.js，对 catalog 标准库用 `import … from './docx.js'`（Weizhi 会先 workspace 再 catalog 回退）；本地 `./helper.js` 仍在 workspace。勿 import agentRoot 外路径；勿用 bare `import 'xxx'`（易与 catalog 叶子名冲突，除文档指定的官方库外）。
        具体函数签名、样式/raw 校验等 **不要猜**——用 read_file 阅读 docs/system/office-docx.md（较长时分段 offset/limit；可用 grep/glob 在 docs/system 内查找）。
        生成或修改 docx 后，在回复里用 Markdown 链接写出 workspace 相对路径，例如 [报告](out/report.docx)，便于用户在 App 内点开；图片仍用 ![](path.png)。
        """.trim();

    static final String AGENT_BOUNDARIES = """
        晋升与安装（按这里做，不必先搜框架文档）：
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
        sb.append(scriptToolRegistered ? HOW_TO_WITH_SCRIPT : HOW_TO_DIRECT_ONLY).append("\n\n");
        sb.append(CAN_AND_CANNOT).append("\n\n");
        sb.append(DIRECTORY).append("\n\n");
        sb.append(buildEnvironmentSection(workspaceRoot, agentRoot, environmentSupplement)).append("\n\n");
        sb.append(AGENT_BOUNDARIES).append("\n\n");
        if (scriptToolRegistered) {
            sb.append(JS_ENV).append("\n\n");
            if (scriptHostTools) {
                sb.append(JS_WEBVIEW).append("\n\n");
                sb.append(JS_HOST_TOOLS).append("\n\n");
            }
            if (officeDocxReady) {
                sb.append(WORK_MODE_OFFICE_DOCX).append("\n\n");
            }
        }
        sb.append(toolIndex(webSearchEnabled, scriptToolRegistered, scriptHostTools, officeDocxReady));
        if (!hostAppend.isEmpty()) {
            sb.append("\n\n").append(hostAppend);
        }
        return sb.toString().trim();
    }

    private static String toolIndex(
        boolean webSearchEnabled,
        boolean scriptToolRegistered,
        boolean scriptHostTools,
        boolean officeDocxReady
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("主要工具：\n");
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
        sb.append("- list_catalog：shared/catalog 摘要，只读。\n");
        sb.append("- catalog_sync_status、catalog_install：查看并安装云端资源。\n");
        sb.append("- promote_request：把 staging 里的 Skill 或脚本晋升到 shared/local/。\n");
        sb.append("- capability_search：查 Skill、MCP、Caps、catalog 脚本和文档指针。\n");
        sb.append("- ask_user：缺少关键信息时提问并暂停。\n");
        sb.append("- chat_history、list_sessions：当前对话历史和会话列表。\n");
        if (scriptToolRegistered) {
            sb.append("- execute_script：在 workspace 里跑 QuickJS。优先 file，不要贴大段 inline code。\n");
        }
        if (scriptHostTools) {
            sb.append("- webview_exec：DOM/canvas/SVG。code 必须顶层 return；或 writeFile(路径, Base64) 把图片写入工作区。\n");
            sb.append("- SVG→PNG/JPG：execute_script 内 import { svgToImage } from './svg-raster.js'。\n");
            sb.append("- 脚本内 $tools.工具名：调用已注册工具。$mcp.<server>.<tool>：调用检索到的 MCP。\n");
        }
        if (officeDocxReady && scriptToolRegistered) {
            sb.append("- docx_markdown_to_word、docx_inspect、docx_read_grep_edit、docx_raw_edit：Word。\n");
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
