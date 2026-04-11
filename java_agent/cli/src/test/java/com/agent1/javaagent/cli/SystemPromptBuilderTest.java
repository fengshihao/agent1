package com.agent1.javaagent.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.cli.skills.ClaudeSkill;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SystemPromptBuilderTest {

    @Test
    void build_shouldContainRuntimeEnvSection() {
        String prompt = new SystemPromptBuilder().build();
        assertTrue(prompt.contains("OS="));
        assertTrue(prompt.contains("CWD="));
    }

    @Test
    void build_shouldContainSkillListWhenSkillsAdded() {
        ClaudeSkill skill = new ClaudeSkill("weather", "查询天气", "body", Path.of("/tmp/weather/SKILL.md"), Map.of());
        String prompt = new SystemPromptBuilder().addSkill(skill).build();

        assertTrue(prompt.contains("weather"));
        assertTrue(prompt.contains("查询天气"));
        assertTrue(prompt.contains("可用 skill"));
    }

    @Test
    void build_shouldOmitSkillListWhenEmpty() {
        String prompt = new SystemPromptBuilder().build();
        assertFalse(prompt.contains("可用 skill"));
    }

    @Test
    void build_shouldNotContainMemorySection() {
        String prompt = new SystemPromptBuilder().build();
        assertFalse(prompt.contains("长期记忆"));
        assertFalse(prompt.contains("memory"));
        assertFalse(prompt.contains("AGENT1_MEMORY_DB"));
    }
}
