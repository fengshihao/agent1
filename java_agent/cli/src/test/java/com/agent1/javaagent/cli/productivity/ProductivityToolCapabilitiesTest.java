package com.agent1.javaagent.cli.productivity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProductivityToolCapabilitiesTest {

    @Test
    void summaryMentionsMcpWhenWeizhiOnClasspath() {
        if (!ProductivityToolCapabilities.weizhiToolsOnClasspath()) {
            return;
        }
        String summary = ProductivityToolCapabilities.summaryForCli(true);
        assertTrue(summary.contains("MCP"));
        assertTrue(summary.contains("WebView") || summary.contains("无 WebView"));
    }

    @Test
    void detailIncludesMcpConfigHint() {
        if (!ProductivityToolCapabilities.weizhiToolsOnClasspath()) {
            return;
        }
        assertTrue(
            ProductivityToolCapabilities.detailBullets(true).stream()
                .anyMatch(line -> line.contains("mcp_servers.json"))
        );
    }

    @Test
    void withoutWeizhiClasspathStillListsWorkspaceTools() {
        String summary = ProductivityToolCapabilities.summaryForCli(false);
        assertTrue(summary.contains("chat_history") || summary.contains("read_file"));
        if (!ProductivityToolCapabilities.weizhiToolsOnClasspath()) {
            assertFalse(summary.contains("execute_script"));
        }
    }
}
