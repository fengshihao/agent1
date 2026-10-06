package com.agent1.javaagent.tool.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.mcp.McpCapabilitySync;
import com.agent1.javaagent.mcp.McpListedTool;
import com.agent1.javaagent.mcp.McpServerRecord;
import com.agent1.javaagent.mcp.McpServersFile;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CapabilitySearchToolTest {

    @TempDir
    Path temp;

    @Test
    void modelToolIgnoresKindsFilter() throws Exception {
        Path agentRoot = temp.resolve("agentRootKinds");
        AgentHomeBootstrap.ensure(agentRoot);
        CapabilitySearchTool tool = new CapabilitySearchTool(agentRoot, "android");
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("query", "markdown word docx");
        params.putArray("kinds").add("skill").add("builtin");

        ToolExecutionResult result = tool.execute("t-kinds", params, new CancellationToken(), u -> {
        });

        assertTrue(result.getText().contains("markdownToDocx"));
        assertTrue(result.getText().contains("catalog_script"));
    }

    @Test
    void kindFilterAppliesWhenEnabled() throws Exception {
        Path agentRoot = temp.resolve("agentRootKindsFilter");
        AgentHomeBootstrap.ensure(agentRoot);
        CapabilitySearchTool tool = new CapabilitySearchTool(agentRoot, "android", null, true);
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("query", "markdown word docx");
        params.putArray("kinds").add("skill").add("builtin");

        ToolExecutionResult result = tool.execute("t-kinds-filter", params, new CancellationToken(), u -> {
        });

        assertTrue(result.getText().startsWith("未找到匹配"));
    }

    @Test
    void searchReturnsDocxHint() throws Exception {
        Path agentRoot = temp.resolve("agentRoot");
        CapabilitySearchTool tool = new CapabilitySearchTool(agentRoot);
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("query", "markdown word docx");

        ToolExecutionResult result = tool.execute("t1", params, new CancellationToken(), u -> {
        });

        assertTrue(result.getText().contains("docx"));
        assertTrue(result.getDetails() != null && result.getDetails().has("hits"));
    }

    @Test
    void hostPlatformFiltersAndroidCapsOnDesktop() throws Exception {
        Path agentRoot = temp.resolve("agentRoot2");
        AgentHomeBootstrap.ensure(agentRoot);
        CapabilitySearchTool desktop = new CapabilitySearchTool(agentRoot, "desktop");
        CapabilitySearchTool android = new CapabilitySearchTool(agentRoot, "android");
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("query", "android.share 微信");

        ToolExecutionResult onDesktop = desktop.execute("t2", params, new CancellationToken(), u -> {
        });
        ToolExecutionResult onAndroid = android.execute("t3", params, new CancellationToken(), u -> {
        });

        assertFalse(onDesktop.getText().contains("android.share.send"));
        assertTrue(onAndroid.getText().contains("android.share.send"));
    }

    @Test
    void androidSearchFindsIntentViewCall() throws Exception {
        Path agentRoot = temp.resolve("agentRootIntent");
        AgentHomeBootstrap.ensure(agentRoot);
        CapabilitySearchTool android = new CapabilitySearchTool(agentRoot, "android");
        CapabilitySearchTool desktop = new CapabilitySearchTool(agentRoot, "desktop");
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("query", "打开 Word docx");

        ToolExecutionResult onAndroid = android.execute("t-intent", params, new CancellationToken(), u -> {
        });
        ToolExecutionResult onDesktop = desktop.execute("t-intent-desk", params, new CancellationToken(), u -> {
        });

        assertTrue(onAndroid.getText().contains("android.intent.start"));
        assertTrue(onAndroid.getText().contains("action: \"view\""));
        assertTrue(onAndroid.getText().contains("panel"));
        assertFalse(onAndroid.getText().contains("office-docx.md"));
        assertFalse(onDesktop.getText().contains("android.intent.start"));
    }

    @Test
    void longDocIsNotInlined() throws Exception {
        Path agentRoot = temp.resolve("agentRootLongDoc");
        AgentHomeBootstrap.ensure(agentRoot);
        CapabilitySearchTool tool = new CapabilitySearchTool(agentRoot, "android");
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("query", "markdown word docx");

        ToolExecutionResult result = tool.execute("t-long", params, new CancellationToken(), u -> {
        });

        assertTrue(result.getText().contains("markdownToDocx"));
        assertTrue(result.getText().contains("inputPath"));
        assertTrue(result.getText().contains("from \"docx.js\""));
        assertFalse(result.getText().contains("from \"./docx.js\""));
        assertTrue(result.getText().contains("不是 workspace 文件"));
        assertFalse(result.getText().contains("office-docx.md"));
        assertFalse(result.getText().contains("不必再 read_file"));
    }

    @Test
    void searchLoadsSkillCreatorBody() throws Exception {
        Path agentRoot = temp.resolve("agentRoot3");
        AgentHomeBootstrap.ensure(agentRoot);
        CapabilitySearchTool tool = new CapabilitySearchTool(agentRoot, "android");
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("query", "skill creator");

        ToolExecutionResult result = tool.execute("t4", params, new CancellationToken(), u -> {
        });

        assertTrue(result.getText().contains("skill-creator loaded"));
        assertTrue(result.getText().contains("source: bundled"));
        assertTrue(result.getText().contains("promote_request"));
        assertFalse(result.getText().contains("skill(action=read"));
    }

    @Test
    void searchLoadsLocalSkillByName() throws Exception {
        Path agentRoot = temp.resolve("agentRoot4");
        AgentHomeBootstrap.ensure(agentRoot);
        Path skillDir = agentRoot.resolve("shared/local/skills/uc09-skill");
        Files.createDirectories(skillDir);
        PathIo.writeString(skillDir.resolve("SKILL.md"), "---\nname: uc09-skill\n---\n# UC09 body\n");

        CapabilitySearchTool tool = new CapabilitySearchTool(agentRoot, "android");
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("query", "uc09-skill");
        ToolExecutionResult result = tool.execute("t5", params, new CancellationToken(), u -> {
        });

        assertTrue(result.getText().contains("UC09 body"));
        assertTrue(result.getText().contains("source: local"));
    }

    @Test
    void searchShowsParamsOnlyForFirstTwoMcpHits() {
        Path agentRoot = temp.resolve("agentRoot5");
        McpServersFile.save(agentRoot, List.of(new McpServerRecord(
            "gaode",
            "https://example.com/mcp",
            Map.of(),
            true,
            "",
            0,
            ""
        )));
        McpCapabilitySync.ensureIndexed(agentRoot, server -> List.of(
            new McpListedTool("maps_geo", "地址转坐标", schema("address")),
            new McpListedTool("maps_weather", "查天气", schema("city")),
            new McpListedTool("maps_around", "周边搜", schema("radiusOnly"))
        ), true);

        CapabilitySearchTool tool = new CapabilitySearchTool(agentRoot);
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("query", "maps");
        params.put("limit", 8);
        ToolExecutionResult result = tool.execute("t6", params, new CancellationToken(), u -> {
        });

        String text = result.getText();
        assertTrue(text.contains("maps_geo"));
        assertTrue(text.contains("maps_weather"));
        assertTrue(text.contains("maps_around"));
        assertTrue(count(text, "参数:") == 2, text);
        assertTrue(text.contains("radiusOnly"));
        assertTrue(text.contains("address"));
        assertFalse(text.contains("city"));
    }

    private static String schema(String field) {
        return "{\"type\":\"object\",\"properties\":{\""
            + field
            + "\":{\"type\":\"string\",\"description\":\"d\"}},\"required\":[\""
            + field
            + "\"]}";
    }

    private static int count(String text, String needle) {
        int n = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) {
                return n;
            }
            n++;
            from = at + needle.length();
        }
    }
}
