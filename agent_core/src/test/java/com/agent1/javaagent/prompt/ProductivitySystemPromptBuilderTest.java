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
    void omitsExecuteScriptWhenScriptToolNotRegistered() {
        Path workspace = temp.resolve("ws");
        String prompt = new ProductivitySystemPromptBuilder()
            .buildMainPrompt(workspace, false);

        assertFalse(prompt.contains("execute_script"));
        assertTrue(prompt.contains("read_file"));
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
    void hostAppendIsIncludedWhenSet() {
        String prompt = new ProductivitySystemPromptBuilder()
            .hostAppend("语音入口：请简短口语化回复。")
            .buildMainPrompt(temp.resolve("ws"), false);

        assertTrue(prompt.contains("语音入口"));
    }

    @Test
    void explorePromptDoesNotMentionWriteFile() {
        String prompt = new ProductivitySystemPromptBuilder().buildExploreSubagentPrompt();
        assertFalse(prompt.contains("write_file"));
        assertTrue(prompt.contains("只读"));
    }
}
