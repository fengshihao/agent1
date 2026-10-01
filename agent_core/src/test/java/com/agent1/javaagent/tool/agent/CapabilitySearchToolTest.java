package com.agent1.javaagent.tool.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CapabilitySearchToolTest {

    @TempDir
    Path temp;

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
}
