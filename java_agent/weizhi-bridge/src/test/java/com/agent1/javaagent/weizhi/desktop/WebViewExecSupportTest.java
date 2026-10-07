package com.agent1.javaagent.weizhi.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.weizhi.WebViewExecHost;
import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.agent1.javaagent.tool.anno.Tool;
import com.agent1.javaagent.tool.anno.ToolParam;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebViewExecSupportTest {

    private static final Gson GSON = new Gson();

    private static final String TINY_PNG_B64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @Test
    void nullReturnWithOutputPathDoesNotWriteFile(@TempDir Path workspace) throws Exception {
        Path existing = workspace.resolve("out.png");
        Files.writeString(existing, "PNG");

        String json = render(workspace, "out.png", "{\"result\":null}");
        JsonObject receipt = JsonParser.parseString(json).getAsJsonObject();
        assertFalse(receipt.get("ok").getAsBoolean());
        assertTrue(receipt.get("error").getAsString().contains("没有可落盘的返回值"));
        assertFalse(receipt.has("outputPath"));
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
    void nullWithoutOutputPathIsErrorAndNoTmpDir(@TempDir Path workspace) {
        String json = render(workspace, null, "{\"result\":null}");
        JsonObject receipt = JsonParser.parseString(json).getAsJsonObject();
        assertFalse(receipt.get("ok").getAsBoolean());
        assertTrue(receipt.get("error").getAsString().contains("没有可落盘的返回值"));
        assertFalse(Files.exists(workspace.resolve("tmp")));
    }

    @Test
    void smallPngBase64SpillsWithoutPayloadInPreview(@TempDir Path workspace) throws Exception {
        String json = render(workspace, null, jsonString(TINY_PNG_B64));
        JsonObject receipt = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(receipt.get("ok").getAsBoolean());
        assertEquals("string", receipt.get("resultType").getAsString());
        String rel = receipt.get("outputPath").getAsString();
        assertTrue(rel.startsWith("tmp/webview_exec/"));
        assertTrue(rel.endsWith(".b64"));
        assertEquals(TINY_PNG_B64.length(), receipt.get("outputBytes").getAsInt());
        assertEquals(TINY_PNG_B64, Files.readString(workspace.resolve(rel)));
        assertEquals(WebViewExecSupport.SPILL_PREVIEW, receipt.get("resultPreview").getAsString());
        assertFalse(receipt.get("resultPreview").getAsString().contains("iVBORw0KGgo"));
    }

    @Test
    void explicitOutputPathOverridesTempFile(@TempDir Path workspace) throws Exception {
        String json = render(workspace, "puppy.png", jsonString(TINY_PNG_B64));
        JsonObject receipt = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(receipt.get("ok").getAsBoolean());
        assertEquals("puppy.png", receipt.get("outputPath").getAsString());
        assertEquals(TINY_PNG_B64, Files.readString(workspace.resolve("puppy.png")));
        assertEquals(WebViewExecSupport.SPILL_PREVIEW, receipt.get("resultPreview").getAsString());
        assertFalse(Files.exists(workspace.resolve("tmp")));
    }

    @Test
    void otherImagePrefixesSpill(@TempDir Path workspace) throws Exception {
        assertSpillsImage(workspace, "/9j/small-jpeg");
        assertSpillsImage(workspace, "R0lGODlhAQAB");
        assertSpillsImage(workspace, "UklGRgAAA");
    }

    @Test
    void stringNullWithExplicitPathStillSpills(@TempDir Path workspace) throws Exception {
        String json = render(workspace, "out.png", "{\"result\":\"null\"}");
        JsonObject receipt = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(receipt.get("ok").getAsBoolean());
        assertEquals("string", receipt.get("resultType").getAsString());
        assertEquals(WebViewExecSupport.SPILL_PREVIEW, receipt.get("resultPreview").getAsString());
        assertEquals(4, receipt.get("outputBytes").getAsInt());
        assertEquals("null", Files.readString(workspace.resolve("out.png")));
    }

    @Test
    void stringNullWithoutOutputPathStaysInline(@TempDir Path workspace) {
        String json = render(workspace, null, "{\"result\":\"null\"}");
        JsonObject receipt = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(receipt.get("ok").getAsBoolean());
        assertEquals("string", receipt.get("resultType").getAsString());
        assertEquals("null", receipt.get("resultPreview").getAsString());
        assertFalse(receipt.has("outputPath"));
    }

    @Test
    void smallNonImageStaysInline(@TempDir Path workspace) {
        String json = render(workspace, null, "{\"result\":\"hello\"}");
        JsonObject receipt = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(receipt.get("ok").getAsBoolean());
        assertEquals("hello", receipt.get("resultPreview").getAsString());
        assertFalse(receipt.has("outputPath"));
    }

    @Test
    void smallFalsyNonImageStaysInlineWithoutPath(@TempDir Path workspace) {
        JsonObject zero = JsonParser.parseString(render(workspace, null, "{\"result\":0}")).getAsJsonObject();
        assertEquals("number", zero.get("resultType").getAsString());
        assertEquals("0", zero.get("resultPreview").getAsString());
        assertFalse(zero.has("outputPath"));
        assertFalse(Files.exists(workspace.resolve("tmp")));

        JsonObject no = JsonParser.parseString(render(workspace, null, "{\"result\":false}")).getAsJsonObject();
        assertEquals("boolean", no.get("resultType").getAsString());
        assertEquals("false", no.get("resultPreview").getAsString());
        assertFalse(no.has("outputPath"));
    }

    @Test
    void over64KbSpillsAndPreviewOmitsPayload(@TempDir Path workspace) throws Exception {
        String body = repeat('a', WebViewExecSupport.AUTO_SPILL_BYTES + 1);
        String json = render(workspace, null, jsonString(body));
        JsonObject receipt = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(receipt.get("ok").getAsBoolean());
        String rel = receipt.get("outputPath").getAsString();
        assertTrue(rel.startsWith("tmp/webview_exec/"));
        assertEquals(body, Files.readString(workspace.resolve(rel)));
        assertEquals(WebViewExecSupport.SPILL_PREVIEW, receipt.get("resultPreview").getAsString());
        assertFalse(receipt.get("resultPreview").getAsString().contains("aaa"));
    }

    @Test
    void exactly64KbNonImageStaysInline(@TempDir Path workspace) {
        String body = repeat('b', WebViewExecSupport.AUTO_SPILL_BYTES);
        String json = render(workspace, null, jsonString(body));
        JsonObject receipt = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(receipt.get("ok").getAsBoolean());
        assertFalse(receipt.has("outputPath"));
        assertEquals(body, receipt.get("resultPreview").getAsString());
    }

    @Test
    void falsyValuesSpillWhenOutputPathGiven(@TempDir Path workspace) throws Exception {
        JsonObject zero = JsonParser.parseString(render(workspace, "n.txt", "{\"result\":0}")).getAsJsonObject();
        assertEquals("number", zero.get("resultType").getAsString());
        assertEquals(WebViewExecSupport.SPILL_PREVIEW, zero.get("resultPreview").getAsString());
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
    void toolDescriptionDocumentsAutoSpill() throws Exception {
        Method method = DesktopWebViewExecTool.class.getMethod(
            "webviewExec",
            String.class,
            String.class,
            String.class,
            String.class,
            String.class
        );
        String description = method.getAnnotation(Tool.class).description();
        assertEquals(WebViewExecHost.DESCRIPTION, description);
        assertTrue(description.contains("tmp/webview_exec/"));
        assertTrue(description.contains("output_path"));

        ToolParam output = method.getParameters()[3].getAnnotation(ToolParam.class);
        assertEquals("output_path", output.name());
        assertFalse(output.required());
        assertEquals(WebViewExecHost.OUTPUT_PATH_DESCRIPTION, output.description());
    }

    private static void assertSpillsImage(Path workspace, String b64) throws Exception {
        String json = render(workspace, null, jsonString(b64));
        JsonObject receipt = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(receipt.get("ok").getAsBoolean());
        assertTrue(receipt.get("outputPath").getAsString().startsWith("tmp/webview_exec/"));
        assertEquals(WebViewExecSupport.SPILL_PREVIEW, receipt.get("resultPreview").getAsString());
        assertEquals(b64, Files.readString(workspace.resolve(receipt.get("outputPath").getAsString())));
    }

    private static String jsonString(String value) {
        return "{\"result\":" + GSON.toJson(value) + "}";
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static String render(Path workspace, String outputRel, String payload) {
        WorkspaceSandbox sandbox = new WorkspaceSandbox(workspace);
        CdpWebViewRuntime.WebViewRuntimeOutcome outcome = new CdpWebViewRuntime.WebViewRuntimeOutcome(
            true,
            payload,
            null,
            List.of(),
            12L
        );
        String explicit = outputRel != null && !outputRel.isEmpty() ? outputRel : null;
        return WebViewExecSupport.renderOk(sandbox, outcome, explicit);
    }
}
