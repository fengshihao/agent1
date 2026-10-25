package com.agent1.javaagent.coach;

import com.agent1.javaagent.script.ScriptEvalFrame;
import com.agent1.javaagent.script.ScriptFailureFormatter;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.script.InlineScriptSpill;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 方案 A：在 tool result 末尾追加 {@code [coach]} 提示（H1 + script.fail_repeat）。 */
public final class ProductivityCoach {

    private static final String OUTSIDE_MARKER = "路径超出工作区范围";
    private static final Pattern WEBVIEW_OUTPUT_BYTES =
        Pattern.compile("·\\s*(\\d+)\\s*字节");
    private static final Pattern BASH_WHICH_COMMAND =
        Pattern.compile("(^|\\s)which(\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final int BASH_HOST_TOOL_PROBE_COACH_LIMIT = 2;
    private static final int LIB_HUNT_COACH_LIMIT = 2;
    private static final int WEBVIEW_TINY_IMAGE_BYTES = 256;

    private final int largeWriteBytes;
    private final int inlineLongLines;
    private final int inlineLongBytes;
    private final int scriptFailRepeat;
    private final CoachRunState runState = new CoachRunState();

    public ProductivityCoach() {
        this(AgentCoachConfig.defaults());
    }

    public ProductivityCoach(AgentCoachConfig config) {
        this.largeWriteBytes = config.largeWriteBytes();
        this.inlineLongLines = config.inlineLongLines();
        this.inlineLongBytes = config.inlineLongBytes();
        this.scriptFailRepeat = config.scriptFailRepeat();
    }

    ProductivityCoach(int largeWriteBytes, int inlineLongLines, int inlineLongBytes) {
        this.largeWriteBytes = largeWriteBytes;
        this.inlineLongLines = inlineLongLines;
        this.inlineLongBytes = inlineLongBytes;
        this.scriptFailRepeat = AgentCoachConfig.DEFAULT_SCRIPT_FAIL_REPEAT;
    }

    public void resetRun() {
        runState.clear();
    }

    public ToolExecutionResult maybeAugment(
        String toolName,
        JsonNode parameters,
        ToolExecutionResult result,
        boolean isError
    ) {
        if (result == null || toolName == null) {
            return result;
        }
        String hookId = null;
        String advice = null;

        String text = result.getText();
        if (text != null && text.contains(OUTSIDE_MARKER)) {
            hookId = "path.outside_attempt";
            advice =
                "仅当前会话 workspace 可写。Skill 沉淀用 promote_request。"
                    + "官方 JS 用 import … from \"weizhi:<name>\"，勿 write_file 写入 shared。";
        } else if (("bash".equals(toolName) || "glob".equals(toolName)) && parameters != null) {
            String command = "bash".equals(toolName)
                ? parameters.path("command").asText("")
                : parameters.path("pattern").asText("");
            if ("bash".equals(toolName)
                && looksLikeHostToolWhich(command)
                && !runState.capabilitySearchUsed()
                && runState.recordBashHostToolProbeCoach() <= BASH_HOST_TOOL_PROBE_COACH_LIMIT) {
                hookId = "bash.host_tool_probe";
                advice =
                    "Android/沙箱 bash 通常没有 pandoc、LibreOffice、python3 等主机转换工具，也不要反复 which/find。"
                        + "文档类任务先 find_caps（如 docx、pptx、markdown word、幻灯片），"
                        + "再用 run_js 按结果里的示例调用（如 weizhi:docx 的 markdownToDocx、weizhi:pptx 的 renderPptx）。"
                        + "不要猜 Node 的 require/fs。";
            } else if (looksLikeScriptLibHunt(command)
                && looksLikeNoMatchResult(text)
                && runState.recordLibHuntCoach() <= LIB_HUNT_COACH_LIMIT) {
                // 真机案例：find_caps 已加载 pptx skill 后，模型仍 find/glob 找 pptx.js 文件浪费多轮。
                hookId = "lib.hunt_missing";
                advice =
                    "weizhi:pptx、weizhi:docx、weizhi:svg-raster 等是 run_js 官方 Package，直接 import 调用，例如："
                        + "import { renderPptx } from \"weizhi:pptx\"。"
                        + "需要确认可用能力时用 find_caps。";
            }
        } else if ("find_caps".equals(toolName) && text != null) {
            boolean empty = text.startsWith("未找到匹配");
            int calls = runState.recordCapabilitySearch(empty);
            if (calls > 2 || (empty && runState.capabilitySearchEmptyCalls() >= 3)) {
                hookId = "capability.search_limit";
                advice =
                    "本任务已多次检索本地能力索引且仍无可用条目。请勿继续换关键词搜索、"
                        + "不要尝试枚举 shared/catalog 或读取 workspace/.mcp 等运行时目录。"
                        + "请改为在 workspace 用 read/write/edit 或 run_js 自行实现；"
                        + "若工作量过大或当前环境无法完成，向用户如实说明暂无内置方案，并给出替代做法或需用户配合的条件。";
            }
        } else if ("write_file".equals(toolName) && !isError && parameters != null
            && !parameters.path("path").asText("").replace('\\', '/').contains("staging/")) {
            String path = parameters.path("path").asText("");
            String content = parameters.path("content").asText("");
            // deck 优先于 large_write：大 deck.html 更需要「勿擅自导出」提醒。
            if (looksLikeDeckHtml(path, content)
                && runState.recordDeckHtmlReadyCoach() <= 1) {
                hookId = "deck.html_ready";
                advice =
                    "设计感 PPT 的交付物是 deck.html：回复给用户可打开的预览链接/路径即可，"
                        + "不要立刻 renderHtmlDeck。等用户在预览里点导出或明说「导出」再出 pptx。";
            } else if (content.length() >= largeWriteBytes) {
                hookId = "file.large_write";
                advice =
                    "单次写入较大：内容应留在 workspace 文件，回复只摘要；"
                        + "若要复用可整理到 workspace/staging/ 再 promote_request。";
            }
        } else if ("write_file".equals(toolName) && !isError && parameters != null) {
            String path = parameters.path("path").asText("");
            if (path.replace('\\', '/').contains("staging/")) {
                hookId = "staging.ready";
                advice =
                    "staging 已有内容；确认 SKILL.md 或脚本就绪后调用 promote_request 沉淀到 shared/local。";
            }
        } else if ("webview_exec".equals(toolName) && text != null) {
            WebViewHook hook = webViewHook(text, parameters, true);
            if (hook != null) {
                hookId = hook.id;
                advice = hook.advice;
            }
        } else if ("run_js".equals(toolName) && parameters != null) {
            if (text != null && looksLikeWebViewReceipt(text)) {
                WebViewHook hook = webViewHook(text, parameters, false);
                if (hook != null) {
                    hookId = hook.id;
                    advice = hook.advice;
                }
            }
            String file = parameters.path("file").asText("").trim();
            String code = parameters.path("code").asText("");
            if (hookId == null && (ScriptFailureFormatter.looksLikeFailureJson(text) || isScriptFailureLegacy(text))) {
                if (!file.isEmpty() && looksLikeNonScriptDataFile(file) && text != null
                    && text.contains("unexpected token")) {
                    hookId = "script.wrong_file_type";
                    advice =
                        "run_js 只能运行 .js 脚本，不能把 SVG/图片当 file 执行。"
                            + "读数据用 read_file；SVG 转 PNG/JPG 用 import { svgToImage } from \"weizhi:svg-raster\"。";
                } else if (text != null && text.contains("工具执行超时") && toolName.equals("run_js")) {
                    hookId = "script.timeout";
                    advice =
                        "外层 run_js 被宿主限时打断。多图请同一段脚本里 await $tools.images_download({ items:[{url}], dir, name_prefix })，"
                            + "必要时传 timeout_ms（毫秒，默认约 120000、上限 600000）；超过 20 行请 spill 到 jobs/*.js 后用 file 执行。";
                } else if (text != null && text.contains("return not in a function")) {
                    hookId = "script.no_top_return";
                    advice =
                        "run_js 不要写顶层 return。顶层 await 可以，用最后一条表达式当返回值。"
                            + "例如 const r = await $mcp.服务器.工具({...}); r（$mcp 会解析 JSON 文本，不要 JSON.stringify）";
                } else if (text != null && (text.contains("require is not defined")
                    || text.contains("require(") && text.contains("ReferenceError"))) {
                    hookId = "script.not_node";
                    advice = "这是 QuickJS，不是 Node。不要 require。读写用脚本里的 fs，或外层 read_file / write_file。";
                } else if (CatalogMissingNativeHints.looksLikeMissingNative(text)) {
                    String plugin = CatalogMissingNativeHints.resolvePluginName(code, text);
                    hookId = "catalog.missing_native";
                    advice = CatalogMissingNativeHints.adviceFor(plugin);
                } else if (ScriptApiErrorHints.looksLikeApiOrPackageError(text)
                    && runState.markFindCapsHintAfterApiError()) {
                    hookId = "script.api_error";
                    advice = ScriptApiErrorHints.advice();
                } else {
                    ScriptEvalFrame.SourceKind kind = file.isEmpty()
                        ? ScriptEvalFrame.SourceKind.INLINE
                        : ScriptEvalFrame.SourceKind.FILE;
                    ScriptEvalFrame frame = new ScriptEvalFrame(kind, file, 0, 0, ScriptEvalFrame.countLines(code));
                    String key = frame.scriptKey(code);
                    int failures = runState.recordScriptFailure(key);
                    if (failures >= scriptFailRepeat) {
                        hookId = "script.fail_repeat";
                        advice =
                            "同一脚本已失败 " + failures + " 次。请根据返回 JSON 的 userLine 修改；"
                                + "优先 write_file 到 workspace/*.js 再用 file 执行；"
                                + "调用方式用 find_caps 返回的示例。";
                    }
                }
            } else if (hookId == null && !code.isBlank()
                && (text == null || text.isBlank())
                && code.contains("require(")) {
                hookId = "script.not_node";
                advice =
                    "这是 QuickJS，不是 Node。不要 require。读写用脚本里的全局 fs，或外层 read_file / write_file。";
            } else if (hookId == null && file.isEmpty() && !code.isBlank()
                && (text == null || !text.contains(InlineScriptSpill.MARKER))) {
                int lines = countLines(code);
                int bytes = code.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (lines > inlineLongLines || bytes > inlineLongBytes) {
                    hookId = "script.inline_long";
                    advice =
                        "inline 脚本过长：请 write_file 到 workspace/*.js，"
                            + "再用 run_js 的 file 参数执行；后续改动用 edit_file，少占 token。";
                }
            }
        }

        if (hookId == null) {
            return result;
        }
        String augmented = appendCoach(text, hookId, advice);
        return ToolExecutionResult.text(augmented);
    }

    static String appendCoach(String toolText, String hookId, String advice) {
        String base = toolText == null ? "" : toolText;
        return base + "\n\n---\n[coach] " + hookId + ": " + advice;
    }

    private static boolean isScriptFailureLegacy(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("syntaxerror")
            || lower.contains("referenceerror")
            || lower.contains("typeerror")
            || text.contains("timeout:");
    }

    private static int countLines(String code) {
        if (code.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private record WebViewHook(String id, String advice) {
    }

    /** run_js 回执里出现 webview_exec 的字段时，套用同一套 WebView 提示。不扫描外层脚本源码。 */
    private static boolean looksLikeWebViewReceipt(String text) {
        return text.contains("没有可落盘的返回值")
            || text.contains("resultPreview")
            || text.contains("tmp/webview_exec/");
    }

    private static WebViewHook webViewHook(String text, JsonNode parameters, boolean scanSource) {
        if (scanSource && looksLikeNonWebApiInWebView(text, parameters)) {
            return new WebViewHook("webview.not_web_api", ADVICE_NOT_WEB_API);
        }
        if (!scanSource && looksLikeNodeModuleError(text)) {
            return new WebViewHook("webview.not_web_api", ADVICE_NOT_WEB_API);
        }
        if (text.contains("没有可落盘的返回值")) {
            return new WebViewHook("webview.null_return", ADVICE_NULL_RETURN);
        }
        if (text.contains("await is only valid in async")) {
            return new WebViewHook("webview.async_syntax", ADVICE_ASYNC_SYNTAX);
        }
        if (looksLikeTinyWebViewImage(text)) {
            return new WebViewHook("webview.tiny_output", ADVICE_TINY_OUTPUT);
        }
        return null;
    }

    private static final String ADVICE_NOT_WEB_API =
        "webview_exec 是标准 WebView 控件，只支持 Web API（document、fetch、DOM 等），没有 Node 的 fs/require。"
            + "读工作区文件用 input_path（全局 input 是 Uint8Array）；写回用 writeFile。";
    private static final String ADVICE_NULL_RETURN =
        "脚本被包进函数执行，只有顶层 return 的值会落盘；运行时会等待这个 return 出来的 Promise。"
            + "请写成 return (async () => { ...; return 结果; })()。"
            + "或者不 return，在这个 Promise 里 writeFile('out.png', data)，宿主会把 Base64 图片解码写入工作区。"
            + "不要只写 (async () => {})()，也不要把 return 放在 img.onload 里。";
    private static final String ADVICE_ASYNC_SYNTAX =
        "webview_exec 的 code 需顶层 return 表达式；异步用 return (async () => { ... })()。"
            + "读工作区文件用 input_path，不要用 fetch('相对路径')，也没有 Node 的 fs。";
    private static final String ADVICE_TINY_OUTPUT =
        "输出文件过小，图片可能无效。这是标准 WebView，只支持 Web API。"
            + "同类转换可先 find_caps 看有没有现成脚本；读工作区文件用 input_path，不要用 Node 的 fs。";

    private static boolean looksLikeTinyWebViewImage(String text) {
        String lower = text.toLowerCase();
        if (!lower.contains("png") && !lower.contains(".b64") && !lower.contains("jpg")
            && !lower.contains("jpeg") && !lower.contains("webp")) {
            return false;
        }
        Matcher matcher = WEBVIEW_OUTPUT_BYTES.matcher(text);
        if (!matcher.find()) {
            return false;
        }
        try {
            int bytes = Integer.parseInt(matcher.group(1));
            return bytes > 0 && bytes < WEBVIEW_TINY_IMAGE_BYTES;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean looksLikeNonWebApiInWebView(String text, JsonNode parameters) {
        String code = parameters == null ? "" : parameters.path("code").asText("");
        return looksLikeNodeModuleError(text) || looksLikeNodeApiUsage(code);
    }

    private static boolean looksLikeNodeModuleError(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("failed to resolve module specifier")
            || lower.contains("cannot find module")
            || lower.contains("require is not defined")
            || lower.contains("module is not defined")
            || lower.contains("process is not defined")
            || lower.contains("buffer is not defined");
    }

    private static boolean looksLikeNodeApiUsage(String source) {
        if (source == null || source.isBlank()) {
            return false;
        }
        String lower = source.toLowerCase();
        return lower.contains("import('fs'")
            || lower.contains("import(\"fs\"")
            || lower.contains("import('node:")
            || lower.contains("import(\"node:")
            || lower.contains("from 'fs'")
            || lower.contains("from \"fs\"")
            || lower.contains("from 'node:")
            || lower.contains("from \"node:")
            || lower.contains("require('fs'")
            || lower.contains("require(\"fs\"")
            || lower.contains("require('path'")
            || lower.contains("require(\"path\"")
            || lower.contains("require('child_process'")
            || lower.contains("require(\"child_process\"")
            || lower.contains("node:fs")
            || lower.contains("fs.readfilesync")
            || lower.contains("fs.writefilesync")
            || lower.contains("fs.readfile(")
            || lower.contains("process.cwd")
            || lower.contains("process.env")
            || lower.contains("buffer.from");
    }

    private static boolean looksLikeHostToolWhich(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        return BASH_WHICH_COMMAND.matcher(command.trim()).find();
    }

    /**
     * 写入的 HTML 是否为 html2pptx deck（路径像 deck / 含独立 class slide）。
     * 与 Android {@code DeckHtmlDetector} 对齐意图，JVM 侧轻量判定即可。
     */
    static boolean looksLikeDeckHtml(String path, String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lowerPath = path == null ? "" : path.replace('\\', '/').toLowerCase();
        boolean htmlPath = lowerPath.endsWith(".html") || lowerPath.endsWith(".htm");
        if (!htmlPath && !lowerPath.contains("deck")) {
            return false;
        }
        // class="slide" / class='slide cover'；避开 slideshow 等粘连词。
        return Pattern.compile("(?i)class\\s*=\\s*[\"'][^\"']*\\bslide\\b").matcher(content).find();
    }

    /** 在 bash/glob 里寻找脚本库文件（pptx.js、find -name xxx.js、通配 .js 等）。 */
    private static final java.util.regex.Pattern GLOB_JS =
        java.util.regex.Pattern.compile("\\*[^\\s]*\\.js");

    private static boolean looksLikeScriptLibHunt(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String lower = query.toLowerCase();
        if (!lower.contains(".js")) {
            return false;
        }
        return lower.contains("find")
            || lower.contains("-name")
            || lower.contains("ls")
            || GLOB_JS.matcher(query).find();
    }

    /** bash/glob 结果为「没找到」（空、No matches、No such file、Error 开头）。 */
    private static boolean looksLikeNoMatchResult(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        String lower = trimmed.toLowerCase();
        return lower.contains("no matches")
            || lower.contains("no such file")
            || lower.contains("not found")
            || lower.startsWith("error");
    }

    private static boolean looksLikeNonScriptDataFile(String file) {
        String normalized = file.trim().replace('\\', '/').toLowerCase();
        return normalized.endsWith(".svg")
            || normalized.endsWith(".png")
            || normalized.endsWith(".jpg")
            || normalized.endsWith(".jpeg")
            || normalized.endsWith(".webp")
            || normalized.endsWith(".gif")
            || normalized.endsWith(".md")
            || normalized.endsWith(".txt")
            || normalized.endsWith(".json")
            || normalized.endsWith(".xml")
            || normalized.endsWith(".html");
    }
}
