package com.agent1.javaagent.coach;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.script.InlineScriptSpill;
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
        assertTrue(out.getText().contains("edit_file"));
    }

    @Test
    void spilledInlineDoesNotRepeatCoach() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode params = MAPPER.createObjectNode();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 81; i++) {
            code.append("x\n");
        }
        params.put("code", code.toString());
        ToolExecutionResult in = ToolExecutionResult.text(
            "\"ok\"" + InlineScriptSpill.notice("jobs/inline-abc.js")
        );
        ToolExecutionResult out = coach.maybeAugment("execute_script", params, in, false);
        assertFalse(out.getText().contains("[coach] script.inline_long"));
        assertTrue(out.getText().contains("jobs/inline-abc.js"));
    }

    @Test
    void topLevelReturnGetsUsageCoachImmediately() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode params = MAPPER.createObjectNode();
        params.put("file", "jobs/geo.js");
        ToolExecutionResult fail = ToolExecutionResult.text(
            "{\"ok\":false,\"message\":\"SyntaxError: return not in a function\"}"
        );
        ToolExecutionResult out = coach.maybeAugment("execute_script", params, fail, false);
        assertTrue(out.getText().contains("[coach] script.no_top_return"));
        assertTrue(out.getText().contains("最后一条表达式"));
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
    void webviewNullReturnCoach() {
        ProductivityCoach coach = new ProductivityCoach();
        ToolExecutionResult in = ToolExecutionResult.text(
            "{\"ok\":false,\"error\":\"没有可落盘的返回值:脚本返回了 null 或 undefined。不会写入文件。\"}"
        );
        ToolExecutionResult out = coach.maybeAugment("webview_exec", MAPPER.createObjectNode(), in, true);
        assertTrue(out.getText().contains("[coach] webview.null_return"));
        assertTrue(out.getText().contains("return (async () =>"));
        assertTrue(out.getText().contains("没有可落盘的返回值"));
    }

    @Test
    void webviewAsyncSyntaxCoach() {
        ProductivityCoach coach = new ProductivityCoach();
        ToolExecutionResult in = ToolExecutionResult.text(
            "{\"ok\":false,\"error\":\"SyntaxError: await is only valid in async functions\"}"
        );
        ToolExecutionResult out = coach.maybeAugment("webview_exec", MAPPER.createObjectNode(), in, true);
        assertTrue(out.getText().contains("[coach] webview.async_syntax"));
    }

    @Test
    void webviewTinyOutputCoach() {
        ProductivityCoach coach = new ProductivityCoach();
        ToolExecutionResult in = ToolExecutionResult.text("已生成图片 · workspace/dog.png · 4 字节 · 90 ms");
        ToolExecutionResult out = coach.maybeAugment("webview_exec", MAPPER.createObjectNode(), in, false);
        assertTrue(out.getText().contains("[coach] webview.tiny_output"));
    }

    @Test
    void webviewNodeFsErrorCoach() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode params = MAPPER.createObjectNode();
        params.put("code", "const fs = await import('fs');");
        ToolExecutionResult in = ToolExecutionResult.text(
            "{\"ok\":false,\"error\":\"Failed to resolve module specifier 'fs'\\nTypeError: Failed to resolve module specifier 'fs'\"}"
        );
        ToolExecutionResult out = coach.maybeAugment("webview_exec", params, in, true);
        assertTrue(out.getText().contains("[coach] webview.not_web_api"));
        assertTrue(out.getText().contains("标准 WebView"));
        assertTrue(out.getText().contains("Web API"));
        assertTrue(out.getText().contains("没有 Node 的 fs"));
    }

    @Test
    void webviewNodeFsInCodeCoachEvenIfErrorGeneric() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode params = MAPPER.createObjectNode();
        params.put("code", "fs.readFileSync('kite.svg', 'utf8')");
        ToolExecutionResult in = ToolExecutionResult.text("{\"ok\":false,\"error\":\"TypeError: fs is not a function\"}");
        ToolExecutionResult out = coach.maybeAugment("webview_exec", params, in, true);
        assertTrue(out.getText().contains("[coach] webview.not_web_api"));
    }

    @Test
    void webviewGenericErrorDoesNotTriggerNodeApiCoach() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode params = MAPPER.createObjectNode();
        params.put("code", "return document.body.innerHTML;");
        ToolExecutionResult in = ToolExecutionResult.text("{\"ok\":false,\"error\":\"TypeError: Cannot read properties of null\"}");
        ToolExecutionResult out = coach.maybeAugment("webview_exec", params, in, true);
        assertFalse(out.getText().contains("[coach] webview.not_web_api"));
    }

    @Test
    void executeScriptSvgFileCoach() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode params = MAPPER.createObjectNode();
        params.put("file", "dog.svg");
        ToolExecutionResult fail = ToolExecutionResult.text(
            "{\"ok\":false,\"message\":\"SyntaxError: unexpected token in expression: '<'\"}"
        );
        ToolExecutionResult out = coach.maybeAugment("execute_script", params, fail, false);
        assertTrue(out.getText().contains("[coach] script.wrong_file_type"));
    }

    @Test
    void capabilitySearchLimitCoachAfterThirdCall() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode params = MAPPER.createObjectNode();
        params.put("query", "foo");
        ToolExecutionResult hit = ToolExecutionResult.text("capability_search: 1 条\n- [doc] x");
        coach.maybeAugment("capability_search", params, hit, false);
        coach.maybeAugment("capability_search", params, hit, false);
        ToolExecutionResult third = coach.maybeAugment("capability_search", params, hit, false);
        assertTrue(third.getText().contains("[coach] capability.search_limit"));
    }

    @Test
    void capabilitySearchLimitCoachAfterThreeEmpty() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode params = MAPPER.createObjectNode();
        params.put("query", "foo");
        ToolExecutionResult empty = ToolExecutionResult.text("未找到匹配「foo」的能力条目。");
        ToolExecutionResult first = coach.maybeAugment("capability_search", params, empty, false);
        assertFalse(first.getText().contains("[coach]"));
        ToolExecutionResult second = coach.maybeAugment("capability_search", params, empty, false);
        assertFalse(second.getText().contains("[coach]"));
        ToolExecutionResult third = coach.maybeAugment("capability_search", params, empty, false);
        assertTrue(third.getText().contains("[coach] capability.search_limit"));
    }

    @Test
    void bashWhichBeforeCapabilitySearchCoach() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode params = MAPPER.createObjectNode();
        params.put("command", "which pandoc");
        ToolExecutionResult in = ToolExecutionResult.text("Error: command exited with code 1");
        ToolExecutionResult out = coach.maybeAugment("bash", params, in, false);
        assertTrue(out.getText().contains("[coach] bash.host_tool_probe"));
        assertTrue(out.getText().contains("capability_search"));
    }

    @Test
    void bashWhichNoCoachAfterCapabilitySearch() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode searchParams = MAPPER.createObjectNode();
        searchParams.put("query", "docx");
        coach.maybeAugment(
            "capability_search",
            searchParams,
            ToolExecutionResult.text("capability_search: 1 条\n- [catalog_script] x"),
            false
        );
        ObjectNode bashParams = MAPPER.createObjectNode();
        bashParams.put("command", "which pandoc");
        ToolExecutionResult out = coach.maybeAugment(
            "bash",
            bashParams,
            ToolExecutionResult.text("Error: command exited with code 1"),
            false
        );
        assertFalse(out.getText().contains("[coach] bash.host_tool_probe"));
    }

    @Test
    void bashLsNoHostToolProbeCoach() {
        ProductivityCoach coach = new ProductivityCoach();
        ObjectNode params = MAPPER.createObjectNode();
        params.put("command", "ls -l");
        ToolExecutionResult out = coach.maybeAugment(
            "bash",
            params,
            ToolExecutionResult.text("ok"),
            false
        );
        assertFalse(out.getText().contains("[coach] bash.host_tool_probe"));
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
