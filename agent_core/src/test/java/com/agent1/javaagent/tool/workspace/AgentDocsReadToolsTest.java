package com.agent1.javaagent.tool.workspace;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentDocsReadToolsTest {

    @TempDir
    Path temp;

    private WorkspaceSandbox sandbox;
    private Path agentRoot;

    @BeforeEach
    void setUp() {
        agentRoot = temp.resolve("agentRoot");
        AgentHomeBootstrap.ensure(agentRoot);
        sandbox = new WorkspaceSandbox(temp.resolve("workspace"), agentRoot);
    }

    @Test
    void readFileReadsSystemDoc() throws Exception {
        ReadFileTool read = new ReadFileTool(sandbox);
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("path", "docs/system/directories.md");
        params.put("limit", 5);
        ToolExecutionResult result = read.execute("r1", params, new CancellationToken(), u -> {
        });
        assertTrue(result.getText().contains("PATH: docs/system/directories.md"));
    }

    @Test
    void grepFindsInDocsSystem() throws Exception {
        GrepTool grep = new GrepTool(sandbox);
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("pattern", "agentRoot");
        params.put("path", "docs/system");
        ToolExecutionResult result = grep.execute("g1", params, new CancellationToken(), u -> {
        });
        assertTrue(result.getText().contains("docs/system/"));
    }

    @Test
    void globListsMarkdownUnderDocs() throws Exception {
        GlobTool glob = new GlobTool(sandbox);
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("pattern", "*.md");
        params.put("path", "docs/system");
        ToolExecutionResult result = glob.execute("gl1", params, new CancellationToken(), u -> {
        });
        assertTrue(result.getText().contains("docs/system/"));
        assertTrue(result.getText().contains(".md"));
    }
}
