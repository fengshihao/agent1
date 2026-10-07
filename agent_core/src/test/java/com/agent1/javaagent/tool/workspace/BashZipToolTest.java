package com.agent1.javaagent.tool.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.anno.AnnotatedTools;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BashZipToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void tokenizeQuotes() {
        assertEquals(3, BashTool.tokenize("ls -la \"my file.txt\"").length);
        assertEquals("my file.txt", BashTool.tokenize("ls -la \"my file.txt\"")[2]);
        assertEquals("hello world", BashTool.tokenize("echo 'hello world'")[1]);
    }

    @Test
    void largeCatDoesNotDeadlock(@TempDir Path workspace) throws Exception {
        StringBuilder content = new StringBuilder(80_000);
        for (int i = 0; i < 8000; i++) {
            content.append("0123456789");
        }
        Files.writeString(workspace.resolve("big.txt"), content.toString());
        var tool = AnnotatedTools.from(new BashTool(new WorkspaceSandbox(workspace))).get(0);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("command", "cat big.txt");
        String out = tool.execute("b", params, new CancellationToken(), update -> { }).getText();
        assertFalse(out.startsWith("Error:"));
        assertTrue(out.contains("<truncated: output exceeded 50000 bytes>"));
    }

    @Test
    void zipRoundTrip(@TempDir Path workspace) throws Exception {
        Path src = workspace.resolve("pack");
        Files.createDirectories(src);
        Files.writeString(src.resolve("a.txt"), "rocket");
        Path zip = workspace.resolve("out.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("a.txt"));
            zos.write("rocket".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        var tool = AnnotatedTools.from(new ZipTools(new WorkspaceSandbox(workspace))).stream()
            .filter(item -> "zip_extract".equals(item.name()))
            .findFirst()
            .orElseThrow();
        ObjectNode params = MAPPER.createObjectNode();
        params.put("file", "out.zip");
        params.put("dest", "unpacked");
        String text = tool.execute("z", params, new CancellationToken(), update -> { }).getText();
        assertTrue(text.contains("Extracted"));
        assertEquals("rocket", Files.readString(workspace.resolve("unpacked/a.txt")));
    }
}
