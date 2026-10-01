package com.agent1.javaagent.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.mcp.McpServerRecord;
import com.agent1.javaagent.mcp.McpServersFile;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.agent.CapabilitySearchTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CapabilitySearchViewTest {

    @TempDir
    Path temp;

    @Test
    void modelTextMatchesCapabilitySearchTool() throws Exception {
        Path agentRoot = temp.resolve("root");
        AgentHomeBootstrap.ensure(agentRoot);
        String query = "markdown word docx";
        CapabilitySearchView.Result view = CapabilitySearchView.search(
            agentRoot,
            "android",
            agentRoot,
            query,
            List.of(),
            8
        );

        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("query", query);
        params.put("limit", 8);
        ToolExecutionResult tool = new CapabilitySearchTool(agentRoot, "android", agentRoot)
            .execute("t", params, new CancellationToken(), update -> {
            });

        assertEquals(tool.getText(), view.modelText());
        assertFalse(view.visible().isEmpty());
        assertEquals("android", view.hostPlatform());
        assertEquals(8, view.limit());
    }

    @Test
    void desktopHidesAndroidCapsButStillListsThem() {
        Path agentRoot = temp.resolve("desktop");
        AgentHomeBootstrap.ensure(agentRoot);
        CapabilitySearchView.Result view = CapabilitySearchView.search(
            agentRoot,
            "desktop",
            agentRoot,
            "android.share 微信",
            List.of("caps"),
            8
        );

        assertFalse(view.modelText().contains("android.share.send"));
        assertTrue(view.hiddenByPlatform().stream().anyMatch(hit ->
            "caps.android.share.send".equals(hit.id())
                && CapabilitySearchView.PLATFORM_HIDDEN.equals(hit.availability())
        ));
    }

    @Test
    void disabledMcpIsSeparateFromModelText() {
        Path agentRoot = temp.resolve("mcp");
        AgentHomeBootstrap.ensure(agentRoot);
        McpServersFile.save(agentRoot, List.of(
            new McpServerRecord(
                "npm_tools",
                "https://example.com/mcp",
                Map.of(),
                false,
                "npm packages",
                0,
                ""
            )
        ));

        CapabilitySearchView.Result view = CapabilitySearchView.search(
            agentRoot,
            "android",
            agentRoot,
            "npm",
            List.of(),
            8
        );

        assertTrue(view.disabledMcp().stream().anyMatch(server -> "npm_tools".equals(server.name())));
        assertFalse(view.modelText().contains("npm_tools"));

        CapabilitySearchView.Result skillsOnly = CapabilitySearchView.search(
            agentRoot,
            "android",
            agentRoot,
            "npm",
            List.of("skill"),
            8
        );
        assertTrue(skillsOnly.disabledMcp().isEmpty());
    }

    @Test
    void listedButUnusableMcpStaysInModelText() {
        Path agentRoot = temp.resolve("down");
        AgentHomeBootstrap.ensure(agentRoot);
        CapabilityIndexStore.replaceKind(agentRoot, "mcp", List.of(
            new CapabilityRecord(
                "mcp:down/",
                "mcp",
                "down",
                "https://example.com 暂时列不出工具。",
                "down mcp",
                "any",
                "$mcp.down.<tool>",
                "",
                "",
                "",
                "mcp",
                1.0
            )
        ));

        CapabilitySearchView.Result view = CapabilitySearchView.search(
            agentRoot,
            "android",
            agentRoot,
            "down",
            List.of("mcp"),
            8
        );

        assertTrue(view.modelText().contains("down"));
        assertTrue(view.visible().stream().anyMatch(hit ->
            "mcp:down/".equals(hit.id())
                && CapabilitySearchView.LISTED_BUT_UNUSABLE.equals(hit.availability())
        ));
    }
}
