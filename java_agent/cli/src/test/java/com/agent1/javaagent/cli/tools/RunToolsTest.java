package com.agent1.javaagent.cli.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class RunToolsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void runBashTool_shouldExecuteCommand() {
        RunBashTool tool = new RunBashTool();
        ObjectNode params = MAPPER.createObjectNode().put("command", "echo hello");
        String out = tool.execute("1", params, new CancellationToken(), update -> { }).getText();
        assertTrue(out.contains("hello"));
    }

    @Test
    void runPythonTool_shouldExecuteInlineScript() {
        RunPythonTool tool = new RunPythonTool();
        ObjectNode params = MAPPER.createObjectNode().put("script", "print('ok')");
        String out = tool.execute("1", params, new CancellationToken(), update -> { }).getText();
        assertTrue(out.contains("ok"));
    }

    @Test
    void skillTool_shouldInstallAndReadFromLocalDirectory() throws IOException {
        Path workspace = Files.createTempDirectory("install-skill-workspace");
        Path sourceRoot = workspace.resolve("skill-source");
        Path sourceSkillDir = sourceRoot.resolve("weather-query");
        Files.createDirectories(sourceSkillDir);
        Files.writeString(
            sourceSkillDir.resolve("SKILL.md"),
            """
            ---
            name: weather-query
            description: 查询天气
            ---

            test body
            """,
            StandardCharsets.UTF_8
        );

        try {
            SkillTool tool = new SkillTool(workspace);
            ObjectNode installParams = MAPPER.createObjectNode()
                .put("action", "install")
                .put("source", "skill-source")
                .put("skill_name", "weather-query");
            String installOut = tool.execute("1", installParams, new CancellationToken(), update -> { }).getText();
            assertTrue(installOut.contains("安装成功"));
            assertTrue(Files.exists(workspace.resolve(".claude/skills/weather-query/SKILL.md")));

            ObjectNode readParams = MAPPER.createObjectNode()
                .put("action", "read")
                .put("skill_name", "weather-query");
            String readOut = tool.execute("2", readParams, new CancellationToken(), update -> { }).getText();
            assertTrue(readOut.contains("skill: weather-query"));
            assertTrue(readOut.contains("test body"));

            ObjectNode uninstallParams = MAPPER.createObjectNode()
                .put("action", "uninstall")
                .put("skill_name", "weather-query");
            String uninstallOut = tool.execute("3", uninstallParams, new CancellationToken(), update -> { }).getText();
            assertTrue(uninstallOut.contains("卸载成功"));
            assertTrue(!Files.exists(workspace.resolve(".claude/skills/weather-query")));
        } finally {
            deleteRecursively(workspace);
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
