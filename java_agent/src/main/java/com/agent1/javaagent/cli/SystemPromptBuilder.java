package com.agent1.javaagent.cli;

import com.agent1.javaagent.cli.skills.ClaudeSkill;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 系统提示词构建器，负责组装发送给大模型的系统提示词。
 * 包含基础提示词、可用 skill 列表和运行时环境信息。
 */
public final class SystemPromptBuilder {

    private String basePrompt = "你是一个有帮助的 AI 助手，可以执行 bash 命令、Python 脚本，以及 skill 管理。"
        + "当用户需要执行命令或脚本时，使用 run_bash 或 run_python 工具。"
        + "当用户用自然语言表达 skill 相关意图（例如找、安装、读取、卸载）时，自动使用 skill 工具，不要求用户提供函数参数。"
        + "请使用 Markdown 格式回复。";

    private final List<ClaudeSkill> availableSkills = new ArrayList<>();
    private Path memoryDatabasePath;

    public SystemPromptBuilder() {
    }

    /**
     * SQLite file for long-term memory (run_bash + sqlite3). If null, the memory section is omitted.
     */
    public SystemPromptBuilder memoryDatabasePath(Path path) {
        this.memoryDatabasePath = path;
        return this;
    }

    /**
     * 设置基础系统提示词（不包含 skill 列表和环境信息）。
     */
    public SystemPromptBuilder basePrompt(String basePrompt) {
        this.basePrompt = basePrompt != null ? basePrompt : "";
        return this;
    }

    /**
     * 添加可用的 skill。
     */
    public SystemPromptBuilder addSkill(ClaudeSkill skill) {
        if (skill != null) {
            this.availableSkills.add(skill);
        }
        return this;
    }

    /**
     * 添加所有可用的 skills。
     */
    public SystemPromptBuilder addAllSkills(Iterable<ClaudeSkill> skills) {
        if (skills != null) {
            for (ClaudeSkill skill : skills) {
                if (skill != null) {
                    this.availableSkills.add(skill);
                }
            }
        }
        return this;
    }

    /**
     * 从 skill Map 中添加所有 skills（保留 Map 的顺序）。
     */
    public SystemPromptBuilder addAllSkillsFromMap(Map<String, ClaudeSkill> skillsByName) {
        if (skillsByName != null) {
            for (ClaudeSkill skill : skillsByName.values()) {
                if (skill != null) {
                    this.availableSkills.add(skill);
                }
            }
        }
        return this;
    }

    /**
     * 构建最终的系统提示词。
     */
    public String build() {
        StringBuilder sb = new StringBuilder();

        // 基础提示词
        sb.append(basePrompt);

        // 运行时环境信息
        sb.append(buildRuntimeEnvSection());

        // 可用 skill 列表
        sb.append(buildSkillListSection());

        sb.append(buildMemorySection());

        return sb.toString();
    }

    /**
     * 构建运行时环境信息部分。
     */
    private String buildRuntimeEnvSection() {
        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "unknown");
        String shellHint = firstNonBlank(System.getenv("SHELL"), System.getenv("ComSpec"), "unknown");
        String cwd = Path.of(".").toAbsolutePath().normalize().toString();

        String shellPreference;
        String osLower = osName.toLowerCase(Locale.ROOT);
        if (osLower.contains("mac")) {
            shellPreference = "当前是 macOS，优先使用 zsh 语法命令。";
        } else if (osLower.contains("win")) {
            shellPreference = "当前是 Windows，优先 bash；不可用时回退 PowerShell 或 cmd。";
        } else {
            shellPreference = "当前是 Linux/Unix，优先使用 bash 命令。";
        }

        return "\n\n当前运行环境信息如下，请据此选择命令和代码写法："
            + "OS=" + osName + " " + osVersion
            + "，Shell=" + shellHint
            + "，CWD=" + cwd + "。"
            + shellPreference;
    }

    /**
     * 构建可用 skill 列表部分。
     */
    private String buildSkillListSection() {
        if (availableSkills.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== 可用 Skills ===\n");
        sb.append("你可以自主判断是否使用某个 skill。");
        sb.append("如果决定使用 skill，请先调用 skill 工具执行 read(skill_name)，再按读取内容执行。\n");
        sb.append("skill(action=read) 的返回文本会自动进入对话历史，这就是 skill 内容进入上下文的方式。\n");
        sb.append("用户会使用自然语言表达意图（如“找一个查询天气的skill”“安装查询天气的skill”“卸载xxskill”），你应将其自动映射为 skill(action=search/install/read/uninstall) 的工具调用。\n");
        sb.append("在读取 skill 内容之前，不要假设自己已经知道该 skill 的完整规则。\n");
        sb.append("注意：当用户发起一个新的具体请求（即使同一会话里你之前读过该 skill），在首次调用业务工具前仍需重新 read 一次对应 skill。\n\n");
        sb.append("执行流程（必须遵循）：\n");
        sb.append("1) 先识别用户意图：search / install / read / uninstall。\n");
        sb.append("2) 必要时先 search，再 install/read。\n");
        sb.append("3) 执行任务前，用 read(skill_name) 加载规则；若参数不足，按 skill 要求补充信息。\n\n");
        sb.append("可用 skill 列表：\n");

        for (ClaudeSkill skill : availableSkills) {
            sb.append("- ").append(skill.getName());
            if (!skill.getDescription().isEmpty()) {
                sb.append(": ").append(skill.getDescription());
            }
            sb.append("\n");
        }

        sb.append("\n示例：当用户问天气时，先 read 对应 weather skill 的 SKILL.md，再决定是否调用 run_bash。");

        return sb.toString();
    }

    private String buildMemorySection() {
        if (memoryDatabasePath == null) {
            return "";
        }
        Path abs = memoryDatabasePath.toAbsolutePath().normalize();
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        String relativeDisplay = abs.toString();
        try {
            Path rel = cwd.relativize(abs);
            if (!rel.startsWith("..")) {
                relativeDisplay = rel.toString();
            }
        } catch (Exception ignored) {
        }
        String env = firstNonBlank(System.getenv("AGENT1_MEMORY_DB"));
        String envLine = env.isEmpty()
            ? "默认路径见下；也可设置环境变量 AGENT1_MEMORY_DB 指向其它 .sqlite 文件。"
            : "当前由环境变量 AGENT1_MEMORY_DB 指向该文件。";

        return "\n\n=== 长期记忆（SQLite）===\n"
            + "你可以在本地 SQLite 中持久化跨会话的信息（偏好、结论、项目事实等）。"
            + "系统提示里会附带「记忆库结构」快照（每条用户消息后的首次模型请求更新一次）；表内数据需你通过工具自行查询。\n"
            + envLine
            + "\n- 绝对路径（供 sqlite3 使用）：" + abs
            + "\n- 相对当前工作目录：" + relativeDisplay
            + "\n- 读写方式：仅使用 **run_bash** 执行 `sqlite3`；示例："
            + "`sqlite3 \"" + abs.toString().replace("\"", "\\\"") + "\" \"SELECT 1 LIMIT 1;\"`。"
            + "路径含空格时必须为路径加引号；不要用交互式点命令，优先一条 SQL 字符串。\n"
            + "何时读：回答不确定、缺少上下文、需要延续历史结论时先 SELECT（带 LIMIT）。\n"
            + "何时写：在即将结束本轮、不再调用工具并回复用户之前，判断是否有值得长期保留的信息；若有则先 sqlite3 写入，再回复。避免无意义刷屏。\n"
            + "若本机没有 sqlite3 可执行文件，请如实说明，不要假装已写入。\n";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
