package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.workspace.ZipTools;
import com.agent1.javaagent.tool.anno.AnnotatedTools;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductivityLlmToolSurfaceTest {

    @TempDir
    Path temp;

    @Test
    void hidesScriptBridgeToolsFromLlmSchema() {
        WorkspaceSandbox sandbox = new WorkspaceSandbox(temp);
        List<AgentTool> all = AnnotatedTools.from(new ZipTools(sandbox));
        List<String> llmNames = ProductivityLlmToolSurface.forLlm(all).stream()
            .map(AgentTool::name)
            .collect(Collectors.toList());
        assertFalse(llmNames.contains("zip_create"));
        assertFalse(llmNames.contains("zip_extract"));
        assertTrue(ProductivityLlmToolSurface.scriptBridgeOnlyNames().contains("zip_create"));
    }
}
