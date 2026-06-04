package com.agent1.javaagent.prompt;

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
        """.trim();

    static final String WORK_MODE_SCRIPT = """
        需要运行的多步处理交给 execute_script 脚本接口。
        """.trim();

    static final String TOOL_STRATEGY = """
        工具策略：大段内容写入工作区文件，不要在回复里重复粘贴全文。
        缺少关键信息时向用户提问，不要编造事实。
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
        StringBuilder sb = new StringBuilder();
        sb.append(IDENTITY).append("\n\n");
        sb.append(WORK_MODE_FILES);
        if (scriptToolRegistered) {
            sb.append("\n").append(WORK_MODE_SCRIPT);
        }
        sb.append("\n\n");
        sb.append(buildEnvironmentSection(workspaceRoot)).append("\n\n");
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

    static String buildEnvironmentSection(Path workspaceRoot) {
        Path normalized = workspaceRoot.toAbsolutePath().normalize();
        String date = LocalDate.now(ZoneId.systemDefault()).toString();
        String osName = System.getProperty("os.name", "unknown");
        return """
            环境：
            - 工作区：%s
            - 日期：%s
            - 平台：%s
            """.formatted(normalized, date, osName).trim();
    }
}
