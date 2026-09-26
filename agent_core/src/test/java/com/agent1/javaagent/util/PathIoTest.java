package com.agent1.javaagent.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathIoTest {

    @TempDir
    Path dir;

    @Test
    void roundTripUtf8IncludingChinese() throws Exception {
        Path file = dir.resolve("meta.json");
        PathIo.writeString(file, "{\"title\":\"新对话\"}", StandardCharsets.UTF_8);
        assertEquals("{\"title\":\"新对话\"}", PathIo.readString(file, StandardCharsets.UTF_8));
        assertEquals("{\"title\":\"新对话\"}", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }
}
