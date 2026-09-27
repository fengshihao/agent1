package com.agent1.javaagent.coach;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.agent1.javaagent.coach.AgentCoachConfig;
import org.junit.jupiter.api.Test;

class ProductivityCoachTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void outsideAttemptAppendsCoach() {
        ProductivityCoach coach = new ProductivityCoach();
        ToolExecutionResult in = ToolExecutionResult.text("错误：路径超出工作区范围: ../shared/foo");
        ToolExecutionResult out = coach.maybeAugment("write_file", MAPPER.createObjectNode(), in, true);
        assertTrue(out.getText().contains("[coach] path.outside_attempt"));
    }

    @Test
    void largeWriteAtThreshold() {
        ProductivityCoach coach = new ProductivityCoach(10, 80, 8192);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("content", "0123456789");
        ToolExecutionResult in = ToolExecutionResult.text("已写入: big.txt (10 chars)");
        ToolExecutionResult out = coach.maybeAugment("write_file", params, in, false);
        assertTrue(out.getText().contains("[coach] file.large_write"));
    }

    @Test
    void largeWriteBelowThresholdNoCoach() {
        ProductivityCoach coach = new ProductivityCoach(10, 80, 8192);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("content", "123456789");
        ToolExecutionResult in = ToolExecutionResult.text("ok");
        ToolExecutionResult out = coach.maybeAugment("write_file", params, in, false);
        assertFalse(out.getText().contains("[coach]"));
    }

    @Test
    void inlineLongScriptCoach() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode params = MAPPER.createObjectNode();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 81; i++) {
            code.append("x\n");
        }
        params.put("code", code.toString());
        ToolExecutionResult in = ToolExecutionResult.text("ok");
        ToolExecutionResult out = coach.maybeAugment("execute_script", params, in, false);
        assertTrue(out.getText().contains("[coach] script.inline_long"));
    }

    @Test
    void scriptFailRepeatCoachAfterThreshold() {
        AgentCoachConfig config = new AgentCoachConfig(true, 65_536, 80, 8192, 2);
        ProductivityCoach coach = new ProductivityCoach(config);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("code", "bad()");
        ToolExecutionResult fail = ToolExecutionResult.text("{\"ok\":false,\"message\":\"SyntaxError at line 2\"}");
        coach.maybeAugment("execute_script", params, fail, false);
        ToolExecutionResult second = coach.maybeAugment("execute_script", params, fail, false);
        assertTrue(second.getText().contains("[coach] script.fail_repeat"));
    }

    @Test
    void catalogPendingCoach() {
        ProductivityCoach coach = new ProductivityCoach();
        ToolExecutionResult in = ToolExecutionResult.text("manifest: x\npending: 2\n- a");
        ToolExecutionResult out = coach.maybeAugment("catalog_sync_status", MAPPER.createObjectNode(), in, false);
        assertTrue(out.getText().contains("[coach] catalog.pending"));
    }

    @Test
    void stagingReadyCoach() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode params = MAPPER.createObjectNode();
        params.put("path", "staging/skills/foo/SKILL.md");
        params.put("content", "---\nname: foo\n---\n");
        ToolExecutionResult in = ToolExecutionResult.text("ok");
        ToolExecutionResult out = coach.maybeAugment("write_file", params, in, false);
        assertTrue(out.getText().contains("[coach] staging.ready"));
    }

    @Test
    void missingNativeCoachOnEnsureNativeFailure() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode params = MAPPER.createObjectNode();
        params.put("code", "await host.ensureNative('echo_math');");
        ToolExecutionResult fail = ToolExecutionResult.text(
            "{\"ok\":false,\"message\":\"unsupported: native \\\"echo_math\\\" (not in catalog)\"}"
        );
        ToolExecutionResult out = coach.maybeAugment("execute_script", params, fail, false);
        assertTrue(out.getText().contains("[coach] catalog.missing_native"));
        assertTrue(out.getText().contains("echo_math"));
        assertTrue(out.getText().contains("catalog_install"));
    }

    @Test
    void fileModeScriptSkipsInlineCoach() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode params = MAPPER.createObjectNode();
        params.put("file", "scripts/a.js");
        params.put("code", "ignored");
        ToolExecutionResult in = ToolExecutionResult.text("ok");
        ToolExecutionResult out = coach.maybeAugment("execute_script", params, in, false);
        assertFalse(out.getText().contains("[coach] script.inline_long"));
    }
}
