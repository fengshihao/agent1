package com.agent1.javaagent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToolCallIdsTest {

    @Test
    void normalize_rejectsNullLiteralAndBlank() {
        String a = ToolCallIds.normalize("null");
        String b = ToolCallIds.normalize("NULL");
        String c = ToolCallIds.normalize("");
        String d = ToolCallIds.normalize(null);
        assertTrue(a.startsWith("tool_call_"));
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(b, d);
    }

    @Test
    void normalize_keepsValidId() {
        assertEquals("call_abc", ToolCallIds.normalize("call_abc"));
        assertEquals("call_abc", ToolCallIds.normalize("  call_abc  "));
    }
}
