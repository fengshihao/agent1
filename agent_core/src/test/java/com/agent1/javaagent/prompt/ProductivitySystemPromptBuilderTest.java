package com.agent1.javaagent.prompt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
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
    void mentionsAskUserInToolStrategy() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), false);
        assertTrue(prompt.contains("ask_user"));
    }

    @Test
    void mentionsMcpCallFormWhenScriptHostToolsEnabled() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), null, true, true);
        assertTrue(prompt.contains("capability_search"));
        assertTrue(prompt.contains("webview_exec"));
        assertTrue(prompt.contains("$tools.webview_exec"));
        assertTrue(prompt.contains("execute_script"));
        assertTrue(prompt.contains("优先用 execute_script"));
        assertFalse(prompt.contains("svgToImage"));
        assertFalse(prompt.contains("toDataURL"));
        assertFalse(prompt.contains("页面和画布"));
        assertFalse(prompt.contains("workspace/dog.svg"));
    }

    @Test
    void warnsAgainstWorkspacePathPrefix() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), false);
        assertTrue(prompt.contains("不要再加 workspace/ 前缀"));
    }

    @Test
    void mentionsJsFirstAndCapabilitySearchWhenScriptEnabled() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), true);
        assertTrue(prompt.contains("execute_script"));
        assertTrue(prompt.contains("capability_search"));
        assertTrue(prompt.contains("写代码"));
        assertFalse(prompt.contains("webview_exec"));
        assertFalse(prompt.contains("docx_markdown_to_word"));
    }

    @Test
    void describesProductionFlowWithDirectAndJsPaths() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), true);
        assertTrue(prompt.contains("execute_script"));
        assertTrue(prompt.contains("写代码"));
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
        assertTrue(prompt.contains("docs/"));
        assertTrue(prompt.contains("分工"));
        assertFalse(prompt.contains("QuickJS"));
        assertFalse(prompt.contains("Tavily"));
    }

    @Test
    void mentionsCapabilitySearchWithoutScriptEngine() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), false);
        assertTrue(prompt.contains("capability_search"));
        assertFalse(prompt.contains("编程智能体"));
    }

    @Test
    void omitsExecuteScriptWhenScriptToolNotRegistered() {
        Path workspace = temp.resolve("ws");
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(workspace, false);

        assertFalse(prompt.contains("execute_script"));
        assertTrue(prompt.contains("read_file"));
        assertTrue(prompt.contains("read_url"));
    }

    @Test
    void includesExecuteScriptWhenScriptToolRegistered() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), true);

        assertTrue(prompt.contains("execute_script"));
    }

    @Test
    void workspacePathAppearsInEnvironmentSection() throws Exception {
        Path workspace = temp.resolve("my-session").toAbsolutePath().normalize();
        java.nio.file.Files.createDirectories(workspace);

        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(workspace, false);

        assertTrue(prompt.contains(workspace.toString()));
        assertTrue(prompt.contains("工作区"));
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
        assertTrue(prompt.contains("capability_search"));
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
        assertTrue(prompt.contains(agentRoot.toString()));
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
        assertFalse(prompt.contains("HTML"));
        assertFalse(prompt.contains("toDataURL"));
        assertTrue(prompt.contains("capability_search"));
        assertTrue(prompt.contains("execute_script"));
    }

    @Test
    void explorePromptDoesNotMentionWriteFile() {
        String prompt = new ProductivitySystemPromptBuilder().buildExploreSubagentPrompt();
        assertFalse(prompt.contains("write_file"));
        assertTrue(prompt.contains("只读"));
        assertTrue(prompt.contains("capability_search"));
        assertTrue(prompt.contains("不必对每个关键词各搜一轮"));
    }

    @Test
    void mainPromptBatchCapabilitySearchGuidance() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), true);
        assertTrue(prompt.contains("capability_search"));
        assertTrue(prompt.contains("现成接口或同类脚本"));
        assertTrue(prompt.contains("不必并行多次"));
        assertTrue(prompt.contains("limit（最多 20）"));
        assertTrue(prompt.contains("execute_script 的 code"));
        assertTrue(prompt.contains("20 行或 1000 字符"));
        assertTrue(prompt.contains("edit_file"));
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
