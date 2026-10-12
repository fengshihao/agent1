package com.agent1.javaagent.prompt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductivitySystemPromptBuilderTest {

    @TempDir
    Path temp;

    @Test
    void mentionsWebSearchOnlyWhenEnabled() {
        String off = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), false);
        assertFalse(off.contains("web_search"));
        String on = new ProductivitySystemPromptBuilder()
            .webSearch(true)
            .buildMainPrompt(temp.resolve("ws"), false);
        assertTrue(on.contains("web_search"));
        assertTrue(on.contains("Tavily"));
    }

    @Test
    void promptHasFiveTopLevelSections() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), true);
        assertTrue(prompt.contains("# 你的角色和用户背景"));
        assertTrue(prompt.contains("# 任务和工作流程"));
        assertTrue(prompt.contains("# 当前环境状态"));
        assertTrue(prompt.contains("# 主要工具"));
    }

    @Test
    void mentionsAskUserAndPlainLanguageForUsers() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), false);
        assertTrue(prompt.contains("ask_user"));
        assertTrue(prompt.contains("对用户"));
        assertTrue(prompt.contains("各行各业从业者或学生"));
        assertTrue(prompt.contains("除非用户表现出相关专业理解力"));
        assertTrue(prompt.contains("[学习大纲](大模型7天学习大纲.docx)"));
    }

    @Test
    void referencesOnlyActuallyRegisteredToolNames() {
        // 与 ProductivityAgentHost.buildTools 注册名逐一核对：
        // AskUserTool.TOOL_NAME / CapabilitySearchTool.TOOL_NAME / ExecuteScriptTool / WebSearchTool.TOOL_NAME
        String prompt = new ProductivitySystemPromptBuilder()
            .webSearch(true)
            .buildMainPrompt(temp.resolve("ws"), true);
        assertTrue(prompt.contains("ask_user"));
        assertTrue(prompt.contains("find_caps"));
        assertTrue(prompt.contains("run_js"));
        assertTrue(prompt.contains("web_search"));
        assertFalse(prompt.contains("ask-user"));
        assertFalse(prompt.contains("findCaps"));
        assertFalse(prompt.contains("runJS"));
    }

    @Test
    void mentionsMcpCallFormWhenScriptHostToolsEnabled() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), null, true, true);
        assertTrue(prompt.contains("find_caps"));
        assertTrue(prompt.contains("webview_exec"));
        assertTrue(prompt.contains("$tools.webview_exec"));
        assertTrue(prompt.contains("run_js"));
        assertTrue(prompt.contains("一段 run_js"));
        assertTrue(prompt.contains("不要在外层单独调 webview_exec"));
        assertTrue(prompt.contains("宿主系统能力"));
        assertFalse(prompt.contains("不操作手机界面"));
        assertFalse(prompt.contains("svgToImage"));
        assertFalse(prompt.contains("toDataURL"));
        assertFalse(prompt.contains("页面和画布"));
        assertFalse(prompt.contains("workspace/dog.svg"));
    }

    @Test
    void warnsAgainstWorkspacePathPrefix() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), false);
        assertTrue(prompt.contains("workspace/ 前缀"));
    }

    @Test
    void mentionsJsFirstAndCapabilitySearchWhenScriptEnabled() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), true);
        assertTrue(prompt.contains("run_js"));
        assertTrue(prompt.contains("find_caps"));
        assertTrue(prompt.contains("一次性写代码到文件"));
        assertTrue(prompt.contains("先写结构化源"));
        assertTrue(prompt.contains("web_lib"));
        assertFalse(prompt.contains("webview_exec"));
        assertFalse(prompt.contains("bash"));
        assertFalse(prompt.contains("docx_markdown_to_word"));
    }

    @Test
    void describesProductionFlowWithDirectAndJsPaths() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), true);
        assertTrue(prompt.contains("run_js"));
        assertTrue(prompt.contains("不逐步执行"));
        assertTrue(prompt.contains("markdown 总结"));
        assertTrue(prompt.contains("下一步建议"));
        assertFalse(prompt.contains("直接生产"));
        assertFalse(prompt.contains("编程生产"));
        assertFalse(prompt.contains("docx_"));
    }

    @Test
    void statesWhatIsForbiddenAndHowDirectoriesAreLaidOut() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), false);
        assertTrue(prompt.contains("不能 npm"));
        assertTrue(prompt.contains("shared/"));
        assertTrue(prompt.contains("调用示例"));
        assertFalse(prompt.contains("docs/"));
        assertTrue(prompt.contains("# 任务和工作流程"));
        assertFalse(prompt.contains("QuickJS"));
        assertFalse(prompt.contains("Tavily"));
    }

    @Test
    void statesProgrammingAgentIdentity() {
        String withoutScript = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), false);
        assertTrue(withoutScript.contains("编程型生产力智能体"));
        assertTrue(withoutScript.contains("不是纯聊天助手"));

        String withScript = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), true);
        assertTrue(withScript.contains("编程型生产力智能体"));
        assertTrue(withScript.contains("一段 run_js 串行完成"));
        assertTrue(withScript.contains("失败就改这一段"));
        assertTrue(withScript.contains("不要改成外层逐个工具补步骤"));
        assertFalse(withScript.contains("优先用"));
    }

    @Test
    void runLimitsAppearInEnvironmentWhenSet() {
        String prompt = new ProductivitySystemPromptBuilder()
            .runLimits(24, 48)
            .buildMainPrompt(temp.resolve("ws"), true);
        assertTrue(prompt.contains("模型↔工具往返最多 24 轮"));
        assertTrue(prompt.contains("工具执行最多 48 次"));
        assertTrue(prompt.contains("模型配置"));
    }

    @Test
    void mentionsCapabilitySearchWithoutScriptEngine() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), false);
        assertTrue(prompt.contains("find_caps"));
        assertTrue(prompt.contains("编程型生产力智能体"));
        assertFalse(prompt.contains("run_js"));
    }

    @Test
    void omitsExecuteScriptWhenScriptToolNotRegistered() {
        Path workspace = temp.resolve("ws");
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(workspace, false);

        assertFalse(prompt.contains("run_js"));
        assertTrue(prompt.contains("read_file"));
        assertTrue(prompt.contains("read_url"));
    }

    @Test
    void includesExecuteScriptWhenScriptToolRegistered() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), true);

        assertTrue(prompt.contains("run_js"));
    }

    @Test
    void environmentKeepsTimezoneLocaleButNotDate() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), true);
        assertTrue(prompt.contains("- 时区："));
        assertTrue(prompt.contains("语言："));
        assertFalse(prompt.contains("- 日期："));
        // 每条用户消息附加时间戳，系统提示词只说明其含义
        assertTrue(prompt.contains("用户消息末尾的（时间：…）"));
    }

    @Test
    void environmentUsesProviderLinesWhenGiven() {
        HostEnvironmentProvider provider = new HostEnvironmentProvider() {
            @Override
            public String platformLine() {
                return "Android 14 内核 5.15.0-android14";
            }

            @Override
            public String jsRuntimeLine() {
                return "QuickJS 扩展，非 Node；不能 npm 或 require";
            }

            @Override
            public String memoryLine() {
                return "总内存 8192MB，可用 1024MB";
            }

            @Override
            public List<String> directoryLines() {
                return List.of("workspace/：本会话工作区（可写）", "shared/：公共能力库（只读）");
            }
        };
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(
                temp.resolve("ws"),
                temp.resolve("ar"),
                true,
                false,
                "",
                provider
            );
        assertTrue(prompt.contains("平台：Android 14 内核 5.15.0-android14"));
        assertTrue(prompt.contains("JS 运行环境：QuickJS 扩展，非 Node"));
        assertTrue(prompt.contains("内存：总内存 8192MB，可用 1024MB"));
        assertTrue(prompt.contains("- workspace/：本会话工作区（可写）"));
        assertTrue(prompt.contains("- shared/：公共能力库（只读）"));
    }

    @Test
    void environmentFallsBackToDesktopProvider() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), null, true, false, "", null);
        assertTrue(prompt.contains("- 平台："));
        assertTrue(prompt.contains("内核"));
        assertTrue(prompt.contains("QuickJS 扩展，非 Node"));
        assertTrue(prompt.contains("相对工作区根"));
        assertTrue(prompt.contains("shared/"));
    }

    @Test
    void workspacePathAppearsInEnvironmentSection() throws Exception {
        Path workspace = temp.resolve("my-session").toAbsolutePath().normalize();
        java.nio.file.Files.createDirectories(workspace);

        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(workspace, false);

        assertFalse(prompt.contains(workspace.toString()));
        assertTrue(prompt.contains("相对工作区"));
    }

    @Test
    void includesCatalogPendingWhenSyncFileExists() throws Exception {
        Path workspace = temp.resolve("ws");
        Path agentRoot = temp.resolve("agentRoot");
        java.nio.file.Files.createDirectories(agentRoot.resolve("sync"));
        java.nio.file.Files.writeString(
            agentRoot.resolve("sync/pending.json"),
            "{\"items\":[{\"id\":\"script.a\"}]}"
        );
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(workspace, agentRoot, false, false);
        assertTrue(prompt.contains("catalog pending"));
    }

    @Test
    void frameworkStatesHowToCreateSkillWithoutSearching() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), false);
        assertFalse(prompt.contains("skill-creator"));
        assertFalse(prompt.contains("skill(action=read"));
        assertTrue(prompt.contains("find_caps"));
        String promote = new com.agent1.javaagent.tool.agent.PromoteRequestTool(
            temp.resolve("agentRoot"),
            temp.resolve("ws")
        ).description();
        assertTrue(promote.contains("skill-creator"));
        assertTrue(promote.contains("staging/skills/<name>/SKILL.md"));
        assertTrue(promote.contains("name"));
        assertTrue(promote.contains("description"));
    }

    @Test
    void includesAgentBoundariesAndAgentRoot() throws Exception {
        Path workspace = temp.resolve("ws");
        Path agentRoot = temp.resolve("agentRoot");
        java.nio.file.Files.createDirectories(workspace);
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(workspace, agentRoot, false, false);
        assertTrue(prompt.contains("shared/"));
        assertFalse(prompt.contains("docs/system/"));
        assertFalse(prompt.contains(agentRoot.toString()));
        assertFalse(prompt.contains(workspace.toString()));
    }

    @Test
    void hostAppendIsIncludedWhenSet() {
        String prompt = new ProductivitySystemPromptBuilder()
            .hostAppend("语音入口：请简短口语化回复。")
            .buildMainPrompt(temp.resolve("ws"), false);

        assertTrue(prompt.contains("语音入口"));
    }

    @Test
    void doesNotEmbedOfficeOrWebViewRecipes() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), temp.resolve("ar"), true, true, "");
        assertFalse(prompt.contains("docx_markdown_to_word"));
        assertFalse(prompt.contains("office-docx.md"));
        assertFalse(prompt.contains("jsdelivr"));
        assertFalse(prompt.contains("markmap"));
        assertFalse(prompt.contains("toDataURL"));
        assertTrue(prompt.contains("先写结构化源"));
        assertTrue(prompt.contains("find_caps"));
        assertTrue(prompt.contains("调用示例"));
        assertTrue(prompt.contains("run_js"));
    }

    @Test
    void explorePromptDoesNotMentionWriteFile() {
        String prompt = new ProductivitySystemPromptBuilder().buildExploreSubagentPrompt();
        assertFalse(prompt.contains("write_file"));
        assertTrue(prompt.contains("只读"));
        assertTrue(prompt.contains("find_caps"));
        assertTrue(prompt.contains("不必对每个关键词各搜一轮"));
    }

    @Test
    void mainPromptBatchCapabilitySearchGuidance() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), true);
        assertTrue(prompt.contains("find_caps"));
        assertTrue(prompt.contains("现成接口或同类脚本"));
        assertTrue(prompt.contains("query 写全"));
        assertTrue(prompt.contains("不要 glob、list_dir 或 catalog_sync"));
        assertTrue(prompt.contains("edit_file"));
        assertTrue(prompt.contains("外层 read_file"));
        assertTrue(prompt.contains("须在 workspace 根下"));
        assertFalse(prompt.contains("20 行或 1000 字符"));
        assertFalse(prompt.contains("limit（最多 20）"));
        assertFalse(prompt.contains("不确定有什么能力时"));
        assertFalse(prompt.contains("稳定行号"));
    }

    @Test
    void environmentSupplementAppearsInEnvironmentSection() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(
                temp.resolve("ws"),
                temp.resolve("ar"),
                false,
                false,
                "- 本会话用户提供的可访问文件（read_file 相对路径）：\n  - imports/a.pdf"
            );
        assertTrue(prompt.contains("imports/a.pdf"));
        assertTrue(prompt.contains("可访问文件"));
    }
}