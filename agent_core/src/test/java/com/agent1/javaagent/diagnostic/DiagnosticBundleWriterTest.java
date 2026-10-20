package com.agent1.javaagent.diagnostic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticBundleWriterTest {

    @TempDir
    Path temp;

    @Test
    void packsTreeAndText() throws Exception {
        Path tree = temp.resolve("data");
        Files.createDirectories(tree);
        Files.writeString(tree.resolve("a.txt"), "hello", StandardCharsets.UTF_8);
        Path zip = temp.resolve("out.zip");
        new DiagnosticBundleWriter().write(
            new DiagnosticBundleWriter.Request(
                zip,
                List.of(new DiagnosticBundleWriter.NamedDir("agent1", tree)),
                List.of(new DiagnosticBundleWriter.NamedText("readme.txt", "ok"))
            )
        );
        try (ZipFile file = new ZipFile(zip.toFile())) {
            assertTrue(file.getEntry("readme.txt") != null);
            assertTrue(file.getEntry("agent1/a.txt") != null);
        }
    }
}
