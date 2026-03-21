package com.agent1.javaagent.memory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MemorySqliteCatalogTest {

    @Test
    void buildSection_nonexistentFile_mentionsNotCreated() {
        String s = MemorySqliteCatalog.buildSection(Path.of("/nonexistent/agent-memory-test.db"));
        assertTrue(s.contains("尚未") || s.contains("尚未创建"), s);
    }

    @Test
    void buildSection_null_returnsEmpty() {
        assertTrue(MemorySqliteCatalog.buildSection(null).isEmpty());
    }
}
