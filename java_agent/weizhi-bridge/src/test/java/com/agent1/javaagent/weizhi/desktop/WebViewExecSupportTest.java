package com.agent1.javaagent.weizhi.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.weizhi.agent.sandbox.WorkspaceSandbox;
import com.weizhi.agent.tool.Tool;
import com.weizhi.agent.tool.ToolParam;
import com.weizhi.agent.web.WebViewTask;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebViewExecSupportTest {

    @Test
    void nullReturnWithOutputPathDoesNotWriteFile(@TempDir Path workspace) throws Exception {
        Path existing = workspace.resolve("out.png");
        Files.writeString(existing, "PNG");

        String json = render(workspace, "out.png", "{\"result\":null}");
        JsonObject receipt = JsonParser.parseString(json).getAsJsonObject();
        assertFalse(receipt.get("ok").getAsBoolean());
        assertTrue(receipt.get("error").getAsString().contains("没有可落盘的返回值"));
        assertFalse(json.contains("outputBytes"));
        assertEquals("PNG", Files.readString(existing));
    }

    @Test
    void missingReturnWithOutputPathDoesNotWriteFile(@TempDir Path workspace) throws Exception {
        String json = render(workspace, "puppy.png", "{}");
        JsonObject receipt = JsonParser.parseString(json).getAsJsonObject();
        assertFalse(receipt.get("ok").getAsBoolean());
        assertFalse(Files.exists(workspace.resolve("puppy.png")));
    }

    @Test
    void stringNullIsSpillableAndTypedString(@TempDir Path workspace) throws Exception {
        String json = render(workspace, "out.png", "{\"result\":\"null\"}");
        JsonObject receipt = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(receipt.get("ok").getAsBoolean());
        assertEquals("string", receipt.get("resultType").getAsString());
        assertEquals("null", receipt.get("resultPreview").getAsString());
        assertEquals(4, receipt.get("outputBytes").getAsInt());
        assertEquals("null", Files.readString(workspace.resolve("out.png")));
    }

    @Test
    void nullWithoutOutputPathIsOkWithResultTypeNull(@TempDir Path workspace) {
        String json = render(workspace, null, "{\"result\":null}");
        JsonObject receipt = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(receipt.get("ok").getAsBoolean());
        assertEquals("null", receipt.get("resultType").getAsString());
        assertEquals("null", receipt.get("resultPreview").getAsString());
        assertFalse(receipt.has("outputPath"));
    }

    @Test
    void falsyValuesStillSpill(@TempDir Path workspace) throws Exception {
        JsonObject zero = JsonParser.parseString(render(workspace, "n.txt", "{\"result\":0}")).getAsJsonObject();
        assertEquals("number", zero.get("resultType").getAsString());
        assertEquals("0", Files.readString(workspace.resolve("n.txt")));

        JsonObject no = JsonParser.parseString(render(workspace, "b.txt", "{\"result\":false}")).getAsJsonObject();
        assertEquals("boolean", no.get("resultType").getAsString());
        assertEquals("false", Files.readString(workspace.resolve("b.txt")));

        JsonObject empty = JsonParser.parseString(render(workspace, "s.txt", "{\"result\":\"\"}")).getAsJsonObject();
        assertEquals("string", empty.get("resultType").getAsString());
        assertEquals(0, empty.get("outputBytes").getAsInt());
        assertEquals("", Files.readString(workspace.resolve("s.txt")));
    }

    @Test
    void objectAndArrayResultTypes() {
        WebViewSpillResult object = WebViewSpillResult.parse("{\"result\":{\"a\":1}}");
        assertEquals("object", object.resultType);
        assertEquals("{\"a\":1}", object.text);

        WebViewSpillResult array = WebViewSpillResult.parse("{\"result\":[1,\"null\"]}");
        assertEquals("array", array.resultType);
        assertTrue(new String(array.spillUtf8, StandardCharsets.UTF_8).contains("\"null\""));
    }

    @Test
    void toolDescriptionStatesUtf8TextContract() throws Exception {
        Method method = DesktopWebViewExecTool.class.getMethod(
            "webviewExec",
            String.class,
            String.class,
            String.class,
            String.class,
            String.class
        );
        String description = method.getAnnotation(Tool.class).description();
        assertTrue(description.contains("UTF-8"));
        assertTrue(description.contains("resultType"));
        assertTrue(description.contains("不会把文本 null 写入文件"));

        ToolParam output = method.getParameters()[3].getAnnotation(ToolParam.class);
        assertEquals("output_path", output.name());
        assertTrue(output.description().contains("UTF-8"));
        assertTrue(output.description().contains("Base64"));
        assertTrue(output.description().contains("不会写入文本 null"));
    }

    private static String render(Path workspace, String outputRel, String payload) {
        WorkspaceSandbox sandbox = new WorkspaceSandbox(workspace);
        WebViewTask task = new WebViewTask("return 1;", null, null, outputRel, 1000L);
        CdpWebViewRuntime.WebViewRuntimeOutcome outcome = new CdpWebViewRuntime.WebViewRuntimeOutcome(
            true,
            payload,
            null,
            List.of(),
            12L
        );
        return WebViewExecSupport.renderOk(task, sandbox, outcome);
    }
}
