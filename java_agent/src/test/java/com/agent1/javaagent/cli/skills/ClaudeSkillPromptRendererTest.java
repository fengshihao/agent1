package com.agent1.javaagent.cli.skills;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClaudeSkillPromptRendererTest {
    @Test
    void render_shouldReplaceArgumentsAndSkillDir() {
        ClaudeSkill skill = new ClaudeSkill(
            "demo",
            "desc",
            "Task: $ARGUMENTS; first=$0; second=$ARGUMENTS[1]; dir=${CLAUDE_SKILL_DIR}",
            Path.of("/tmp/.claude/skills/demo/SKILL.md"),
            Map.of("name", "demo")
        );

        String rendered = ClaudeSkillPromptRenderer.render(skill, "hello world");

        assertTrue(rendered.contains("Task: hello world"));
        assertTrue(rendered.contains("first=hello"));
        assertTrue(rendered.contains("second=world"));
        assertTrue(rendered.contains("dir=/tmp/.claude/skills/demo"));
    }

    @Test
    void render_shouldAppendArgumentsWhenPlaceholderMissing() {
        ClaudeSkill skill = new ClaudeSkill(
            "demo",
            "desc",
            "Do something",
            Path.of("/tmp/.claude/skills/demo/SKILL.md"),
            Map.of()
        );

        String rendered = ClaudeSkillPromptRenderer.render(skill, "arg1 arg2");

        assertTrue(rendered.startsWith("Do something"));
        assertTrue(rendered.contains("ARGUMENTS: arg1 arg2"));
    }

    @Test
    void render_shouldHandleMissingIndexedArgumentAsEmpty() {
        ClaudeSkill skill = new ClaudeSkill(
            "demo",
            "desc",
            "first=$0 third=$2",
            Path.of("/tmp/.claude/skills/demo/SKILL.md"),
            Map.of()
        );

        String rendered = ClaudeSkillPromptRenderer.render(skill, "one two");

        assertTrue(rendered.startsWith("first=one third="));
        assertTrue(rendered.contains("ARGUMENTS: one two"));
    }
}
