package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 微智 grep/glob + ReadMount 文档区（Weizhi #14 / Agent1 共用）。 */
class WeizhiDocsGrepGlobTest {

    @TempDir
    Path temp;

    private WorkspaceSandbox agent1Sandbox;
    private List<AgentTool> tools;

    @BeforeEach
    void setUp() {
        Path agentRoot = temp.resolve("agentRoot");
        AgentHomeBootstrap.ensure(agentRoot);
        agent1Sandbox = new WorkspaceSandbox(temp.resolve("workspace"), agentRoot);
        tools = WeizhiWorkspaceTools.create(agent1Sandbox, agentRoot, null);
    }

    @Test
    void grepFindsInDocsSystem() throws Exception {
        AgentTool grep = tools.stream().filter(t -> "grep".equals(t.name())).findFirst().orElseThrow();
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("pattern", "agentRoot");
        params.put("path", "docs/system");
        ToolExecutionResult result = grep.execute("g1", params, new CancellationToken(), u -> {
        });
        assertTrue(result.getText().contains("docs/system/"));
    }

    @Test
    void globListsMarkdownUnderDocs() throws Exception {
        AgentTool glob = tools.stream().filter(t -> "glob".equals(t.name())).findFirst().orElseThrow();
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("pattern", "**/*.md");
        params.put("path", "docs/system");
        ToolExecutionResult result = glob.execute("gl1", params, new CancellationToken(), u -> {
        });
        assertTrue(result.getText().contains("docs/system/"));
        assertTrue(result.getText().contains(".md"));
    }
}
