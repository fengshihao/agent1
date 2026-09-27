package com.agent1.javaagent.coach;

import com.agent1.javaagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;

/** 方案 A：在 tool result 末尾追加 {@code [coach]} 提示（H1 钩子）。 */
public final class ProductivityCoach {

    private static final String OUTSIDE_MARKER = "路径超出工作区范围";

    private final int largeWriteBytes;
    private final int inlineLongLines;
    private final int inlineLongBytes;

    public ProductivityCoach() {
        this(AgentCoachConfig.defaults());
    }

    public ProductivityCoach(AgentCoachConfig config) {
        this(config.largeWriteBytes(), config.inlineLongLines(), config.inlineLongBytes());
    }

    ProductivityCoach(int largeWriteBytes, int inlineLongLines, int inlineLongBytes) {
        this.largeWriteBytes = largeWriteBytes;
        this.inlineLongLines = inlineLongLines;
        this.inlineLongBytes = inlineLongBytes;
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
                "仅当前会话 workspace 可写。读 shared/docs 用 read_agent_doc / list_catalog；"
                    + "改 shared 用 promote_request / catalog_install，勿 write_file 越界。";
        } else if ("write_file".equals(toolName) && !isError && parameters != null) {
            String content = parameters.path("content").asText("");
            if (content.length() >= largeWriteBytes) {
                hookId = "file.large_write";
                advice =
                    "单次写入较大：内容应留在 workspace 文件，回复只摘要；"
                        + "若要复用可整理到 workspace/staging/ 再 promote_request。";
            }
        } else if ("execute_script".equals(toolName) && parameters != null) {
            String file = parameters.path("file").asText("").trim();
            String code = parameters.path("code").asText("");
            if (file.isEmpty() && !code.isBlank()) {
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
