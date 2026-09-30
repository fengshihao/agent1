package com.agent1.javaagent.coach;

import com.agent1.javaagent.script.ScriptEvalFrame;
import com.agent1.javaagent.script.ScriptFailureFormatter;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;

/** 方案 A：在 tool result 末尾追加 {@code [coach]} 提示（H1 + script.fail_repeat）。 */
public final class ProductivityCoach {

    private static final String OUTSIDE_MARKER = "路径超出工作区范围";

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
                "仅当前会话 workspace 可写。读 docs 用 read_file/grep（docs/system/...）；catalog 摘要 list_catalog；"
                    + "改 shared 用 promote_request / catalog_install，勿 write_file 越界。";
        } else if ("write_file".equals(toolName) && !isError && parameters != null
            && !parameters.path("path").asText("").replace('\\', '/').contains("staging/")) {
            String content = parameters.path("content").asText("");
            if (content.length() >= largeWriteBytes) {
                hookId = "file.large_write";
                advice =
                    "单次写入较大：内容应留在 workspace 文件，回复只摘要；"
                        + "若要复用可整理到 workspace/staging/ 再 promote_request。";
            }
        } else if ("catalog_sync_status".equals(toolName) && text != null) {
            int pending = parsePendingCount(text);
            if (pending > 0) {
                hookId = "catalog.pending";
                advice =
                    "远程 catalog 有 " + pending + " 条待安装；可用 catalog_install 或 agent1 sync apply，"
                        + "勿 write_file 写入 shared/catalog。";
            }
        } else if ("write_file".equals(toolName) && !isError && parameters != null) {
            String path = parameters.path("path").asText("");
            if (path.replace('\\', '/').contains("staging/")) {
                hookId = "staging.ready";
                advice =
                    "staging 已有内容；确认 SKILL.md 或脚本就绪后调用 promote_request 沉淀到 shared/local。";
            }
        } else if ("execute_script".equals(toolName) && parameters != null) {
            String file = parameters.path("file").asText("").trim();
            String code = parameters.path("code").asText("");
            if (ScriptFailureFormatter.looksLikeFailureJson(text) || isScriptFailureLegacy(text)) {
                if (CatalogMissingNativeHints.looksLikeMissingNative(text)) {
                    String plugin = CatalogMissingNativeHints.resolvePluginName(code, text);
                    hookId = "catalog.missing_native";
                    advice = CatalogMissingNativeHints.adviceFor(plugin);
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
                                + "可读 docs/system/tools-and-quickjs.md。";
                    }
                }
            } else if (file.isEmpty() && !code.isBlank()) {
                int lines = countLines(code);
                int bytes = code.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (lines > inlineLongLines || bytes > inlineLongBytes) {
                    hookId = "script.inline_long";
                    advice =
                        "inline 脚本过长：请 write_file 到 workspace/*.js，"
                            + "再用 execute_script 的 file 参数执行，便于行号与调试。";
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

    private static int parsePendingCount(String text) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("pending:")) {
                try {
                    return Integer.parseInt(trimmed.substring("pending:".length()).trim());
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
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
}
