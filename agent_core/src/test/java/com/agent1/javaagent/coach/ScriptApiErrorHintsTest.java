package com.agent1.javaagent.coach;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScriptApiErrorHintsTest {

    @Test
    void detectsPackageApiErrors() {
        assertTrue(ScriptApiErrorHints.looksLikeApiOrPackageError(
            "{\"ok\":false,\"message\":\"Error: bad argument: buildJob: overlapping clips\"}"
        ));
        assertFalse(ScriptApiErrorHints.looksLikeApiOrPackageError("require is not defined"));
        assertFalse(ScriptApiErrorHints.looksLikeApiOrPackageError(
            "Error: unsupported: $tools.image_search (not in js exposed whitelist)"
        ));
        assertFalse(ScriptApiErrorHints.looksLikeApiOrPackageError("Error: unknown MCP server: gaode"));
        assertFalse(ScriptApiErrorHints.looksLikeApiOrPackageError("工具执行超时"));
    }
}
