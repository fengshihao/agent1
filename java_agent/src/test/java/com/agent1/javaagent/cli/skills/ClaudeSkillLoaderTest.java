package com.agent1.javaagent.cli.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class ClaudeSkillLoaderTest {
    @Test
    void loadFromSkillsRoot_shouldParseStandardSkill() throws IOException {
        Path root = Files.createTempDirectory("skills-test");
        try {
            Path skillFile = root.resolve("my-skill").resolve("SKILL.md");
            Files.createDirectories(skillFile.getParent());
            Files.writeString(
                skillFile,
                """
                ---
                name: test-skill
                description: test description
                ---

                body line
                """,
                StandardCharsets.UTF_8
            );

            ClaudeSkillLoader loader = new ClaudeSkillLoader();
            ClaudeSkillLoader.SkillLoadResult result = loader.loadFromSkillsRoot(root);

            assertEquals(1, result.skills().size());
            assertTrue(result.warnings().isEmpty());
            assertEquals("test-skill", result.skills().get(0).getName());
            assertEquals("test description", result.skills().get(0).getDescription());
            assertTrue(result.skills().get(0).getContent().contains("body line"));
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    void loadFromSkillsRoot_shouldFallbackToDirectoryNameWhenNameMissing() throws IOException {
        Path root = Files.createTempDirectory("skills-test");
        try {
            Path skillFile = root.resolve("fallback-name").resolve("SKILL.md");
            Files.createDirectories(skillFile.getParent());
            Files.writeString(
                skillFile,
                """
                ---
                description: fallback description
                ---

                content
                """,
                StandardCharsets.UTF_8
            );

            ClaudeSkillLoader loader = new ClaudeSkillLoader();
            ClaudeSkillLoader.SkillLoadResult result = loader.loadFromSkillsRoot(root);

            assertEquals(1, result.skills().size());
            assertEquals("fallback-name", result.skills().get(0).getName());
            assertEquals("fallback description", result.skills().get(0).getDescription());
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    void loadFromSkillsRoot_shouldLoadRawBodyWhenNoFrontmatter() throws IOException {
        Path root = Files.createTempDirectory("skills-test");
        try {
            Path skillFile = root.resolve("plain-skill").resolve("SKILL.md");
            Files.createDirectories(skillFile.getParent());
            Files.writeString(
                skillFile,
                "plain content without frontmatter",
                StandardCharsets.UTF_8
            );

            ClaudeSkillLoader loader = new ClaudeSkillLoader();
            ClaudeSkillLoader.SkillLoadResult result = loader.loadFromSkillsRoot(root);

            assertEquals(1, result.skills().size());
            assertEquals("plain-skill", result.skills().get(0).getName());
            assertEquals("", result.skills().get(0).getDescription());
            assertEquals("plain content without frontmatter", result.skills().get(0).getContent());
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    void loadFromSkillsRoot_shouldContinueWhenSingleSkillParseFails() throws IOException {
        Path root = Files.createTempDirectory("skills-test");
        try {
            Path broken = root.resolve("broken").resolve("SKILL.md");
            Files.createDirectories(broken.getParent());
            Files.writeString(
                broken,
                """
                ---
                name: broken
                description: not closed
                """,
                StandardCharsets.UTF_8
            );

            Path valid = root.resolve("valid").resolve("SKILL.md");
            Files.createDirectories(valid.getParent());
            Files.writeString(
                valid,
                """
                ---
                name: valid
                description: ok
                ---

                ok
                """,
                StandardCharsets.UTF_8
            );

            ClaudeSkillLoader loader = new ClaudeSkillLoader();
            ClaudeSkillLoader.SkillLoadResult result = loader.loadFromSkillsRoot(root);

            assertEquals(1, result.skills().size());
            assertEquals("valid", result.skills().get(0).getName());
            assertEquals(1, result.warnings().size());
            assertTrue(result.warnings().get(0).contains("broken"));
        } finally {
            deleteRecursively(root);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup for temp dirs in tests
                }
            });
        }
    }
}
