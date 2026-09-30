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

/** read_file / list_dir 对 docs/system 只读路径（grep/glob 见 weizhi-bridge 单测）。 */
class AgentDocsReadToolsTest {

    @TempDir
    Path temp;

    private WorkspaceSandbox sandbox;

    @BeforeEach
    void setUp() {
        Path agentRoot = temp.resolve("agentRoot");
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
}
