package com.agent1.javaagent.cli.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class MemoryToolTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void writeThenSearchThenRead_shouldWork() throws Exception {
        var db = Files.createTempFile("agent-memory-tool-", ".sqlite");
        MemoryTool tool = new MemoryTool(db);
        CancellationToken token = new CancellationToken();

        ObjectNode write = MAPPER.createObjectNode();
        write.put("action", "write");
        write.put("id", "m1");
        write.put("type", "txt");
        write.put("keywords", "java,memory");
        write.put("summary", "程序员背景信息");
        write.put("content", "这是一条测试记忆");
        ToolExecutionResult w = tool.execute("1", write, token, u -> {
        });
        assertTrue(w.getText().contains("写入成功"), w.getText());

        ObjectNode search = MAPPER.createObjectNode();
        search.put("action", "search");
        search.put("query", "java & memory");
        search.put("limit", 100);
        ToolExecutionResult s = tool.execute("2", search, token, u -> {
        });
        assertTrue(s.getText().contains("m1"), s.getText());
        assertTrue(s.getText().contains("程序员背景信息"), s.getText());
        assertTrue(s.getText().contains("最多 20 条"), s.getText());

        ObjectNode spacedSearch = MAPPER.createObjectNode();
        spacedSearch.put("action", "search");
        spacedSearch.put("query", "java memory");
        ToolExecutionResult s2 = tool.execute("2b", spacedSearch, token, u -> {
        });
        assertTrue(s2.getText().contains("m1"), s2.getText());

        ObjectNode read = MAPPER.createObjectNode();
        read.put("action", "read");
        read.put("id", "m1");
        ToolExecutionResult r = tool.execute("3", read, token, u -> {
        });
        assertTrue(r.getText().contains("这是一条测试记忆"), r.getText());
        assertTrue(r.getText().contains("summary: 程序员背景信息"), r.getText());
    }
}
