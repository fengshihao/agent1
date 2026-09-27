package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeizhiWorkspaceToolsTest {

    @Test
    void grepSearchesSessionWorkspace(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("note.txt"), "hello-weizhi\n");
        List<AgentTool> tools = WeizhiWorkspaceTools.create(new WorkspaceSandbox(workspace));
        AgentTool grep = tools.stream().filter(tool -> "grep".equals(tool.name())).findFirst().orElseThrow();
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("pattern", "hello-weizhi");
        String text = grep.execute("g1", params, new CancellationToken(), update -> {
            // 测试不收集进度。
        }).getText();
        assertTrue(text.contains("note.txt"));
        assertTrue(tools.stream().anyMatch(tool -> "bash".equals(tool.name())));
        assertTrue(tools.stream().anyMatch(tool -> "zip_extract".equals(tool.name())));
        assertTrue(tools.stream().anyMatch(tool -> "load_skill_through_path".equals(tool.name())));
    }

    @Test
    void registersMcpMetaToolsWhenAgentRootProvided(@TempDir Path workspace, @TempDir Path agentRoot)
        throws Exception {
        Files.writeString(
            agentRoot.resolve("mcp_servers.json"),
            """
                {"version":2,"servers":[{"name":"demo","url":"https://example.com/mcp","enabled":true}]}
                """
        );
        List<AgentTool> tools = WeizhiWorkspaceTools.create(
            new WorkspaceSandbox(workspace),
            agentRoot,
            workspace
        );
        assertTrue(tools.stream().anyMatch(tool -> "mcp_list_servers".equals(tool.name())));
        assertTrue(tools.stream().anyMatch(tool -> "mcp_call_tool".equals(tool.name())));
    }
}
