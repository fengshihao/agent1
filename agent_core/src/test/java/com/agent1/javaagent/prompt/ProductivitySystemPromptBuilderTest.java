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
        assertTrue(prompt.contains("$mcp.<server>.<tool>"));
        assertTrue(prompt.contains("capability_search"));
    }

    @Test
    void mentionsJsFirstAndCapabilitySearchWhenScriptEnabled() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), true);
        assertTrue(prompt.contains("execute_script"));
        assertTrue(prompt.contains("capability_search"));
        assertTrue(prompt.contains("编程智能体"));
        assertTrue(prompt.contains("QuickJS"));
        assertTrue(prompt.contains("webview_exec"));
        assertTrue(prompt.contains("没有 document、window、DOM"));
    }

    @Test
    void describesProductionFlowWithDirectAndJsPaths() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), true);
        assertTrue(prompt.contains("生产流程"));
        assertTrue(prompt.contains("直接生产"));
        assertTrue(prompt.contains("编程生产"));
        assertTrue(prompt.contains("jobs/run.js"));
    }

    @Test
    void statesWhatIsForbiddenAndHowDirectoriesAreLaidOut() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), false);
        assertTrue(prompt.contains("不能 npm"));
        assertTrue(prompt.contains("node_modules"));
        assertTrue(prompt.contains("sessions/<sessionId>/workspace/"));
        assertTrue(prompt.contains("shared/catalog/"));
        assertTrue(prompt.contains("docs/system/"));
        assertTrue(prompt.contains("主要工具"));
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
        assertTrue(prompt.contains("skill-creator"));
        assertFalse(prompt.contains("skill(action=read"));
        assertFalse(prompt.contains("skill(action=list"));
        assertTrue(prompt.contains("workspace/staging/skills/<name>/SKILL.md"));
        assertTrue(prompt.contains("promote_request"));
        assertTrue(prompt.contains("name"));
        assertTrue(prompt.contains("description"));
        assertTrue(prompt.contains("capability_search"));
        assertTrue(prompt.contains("doc_path"));
        assertTrue(prompt.contains("命中 skill"));
    }

    @Test
    void includesAgentBoundariesAndAgentRoot() throws Exception {
        Path workspace = temp.resolve("ws");
        Path agentRoot = temp.resolve("agentRoot");
        java.nio.file.Files.createDirectories(workspace);
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(workspace, agentRoot, false, false);
        assertTrue(prompt.contains("shared/"));
        assertTrue(prompt.contains("promote_request"));
        assertTrue(prompt.contains("catalog_install"));
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
    void includesOfficeDocxHintWhenReady() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(temp.resolve("ws"), temp.resolve("ar"), true, false, true);
        assertTrue(prompt.contains("docx_markdown_to_word"));
        assertTrue(prompt.contains("read_file"));
        assertTrue(prompt.contains("docs/system"));
        assertTrue(prompt.contains("office-docx.md"));
        assertTrue(prompt.contains("HTML"));
    }

    @Test
    void explorePromptDoesNotMentionWriteFile() {
        String prompt = new ProductivitySystemPromptBuilder().buildExploreSubagentPrompt();
        assertFalse(prompt.contains("write_file"));
        assertTrue(prompt.contains("只读"));
    }

    @Test
    void environmentSupplementAppearsInEnvironmentSection() {
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(
                temp.resolve("ws"),
                temp.resolve("ar"),
                false,
                false,
                false,
                "- 本会话用户提供的可访问文件（read_file 相对路径）：\n  - imports/a.pdf"
            );
        assertTrue(prompt.contains("imports/a.pdf"));
        assertTrue(prompt.contains("可访问文件"));
    }
}
