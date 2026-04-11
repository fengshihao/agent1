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
import org.junit.jupiter.api.io.TempDir;

class ReadFileToolTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void readFile_shouldReturnFileContent(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("a.txt");
        Files.writeString(file, "line1\nline2\n", StandardCharsets.UTF_8);

        ReadFileTool tool = new ReadFileTool(tempDir);
        ObjectNode params = MAPPER.createObjectNode().put("path", "a.txt");
        String out = tool.execute("1", params, new CancellationToken(), update -> {}).getText();

        assertTrue(out.contains("line1"));
        assertTrue(out.contains("line2"));
    }

    @Test
    void readFile_shouldRejectPathOutsideWorkspace(@TempDir Path tempDir) {
        ReadFileTool tool = new ReadFileTool(tempDir);
        ObjectNode params = MAPPER.createObjectNode().put("path", "../../etc/passwd");
        String out = tool.execute("1", params, new CancellationToken(), update -> {}).getText();

        assertTrue(out.contains("超出工作区"));
    }

    @Test
    void readFile_shouldReportMissingFile(@TempDir Path tempDir) {
        ReadFileTool tool = new ReadFileTool(tempDir);
        ObjectNode params = MAPPER.createObjectNode().put("path", "nonexistent.txt");
        String out = tool.execute("1", params, new CancellationToken(), update -> {}).getText();

        assertTrue(out.contains("文件不存在"));
    }

    @Test
    void readFile_shouldRespectOffsetAndLimit(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("lines.txt");
        Files.writeString(file, "a\nb\nc\nd\ne\n", StandardCharsets.UTF_8);

        ReadFileTool tool = new ReadFileTool(tempDir);
        ObjectNode params = MAPPER.createObjectNode()
            .put("path", "lines.txt")
            .put("offset", 2)
            .put("limit", 2);
        String out = tool.execute("1", params, new CancellationToken(), update -> {}).getText();

        assertTrue(out.contains("b"));
        assertTrue(out.contains("c"));
        assertTrue(out.contains("more lines not shown"));
    }

    @Test
    void readFile_shouldRejectEmptyPath(@TempDir Path tempDir) {
        ReadFileTool tool = new ReadFileTool(tempDir);
        ObjectNode params = MAPPER.createObjectNode().put("path", "");
        String out = tool.execute("1", params, new CancellationToken(), update -> {}).getText();

        assertTrue(out.contains("不能为空"));
    }
}
