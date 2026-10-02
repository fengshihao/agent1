package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebViewExecHostTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TINY_PNG_B64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @TempDir
    Path workspace;

    @Test
    void descriptionAndCodeParamLeadWithReturnAndWriteFile() {
        String description = WebViewExecHost.augmentDescription("原说明");
        assertTrue(description.startsWith(WebViewExecHost.MARKER));
        assertTrue(description.indexOf("必须顶层 return") < description.indexOf("原说明"));
        assertTrue(description.contains("writeFile"));
        assertEquals(description, WebViewExecHost.augmentDescription(description));

        ObjectNode schema = MAPPER.createObjectNode();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("code").put("description", "旧说明");
        String codeDescription = WebViewExecHost.augmentParameters(schema)
            .path("properties").path("code").path("description").asText();
        assertTrue(codeDescription.contains("必须写顶层 return"));
        assertTrue(codeDescription.contains("writeFile"));
    }

    @Test
    void rewriteWrapsUserCodeAsReturnedPromise() {
        String wrapped = WebViewExecHost.rewriteCode("writeFile(\"a.png\", \"iVBOR\")");
        assertTrue(wrapped.startsWith("return (async () => {"));
        assertTrue(wrapped.contains("function writeFile"));
        assertTrue(wrapped.contains("await eval(__code)"));
        assertTrue(wrapped.contains("\"writeFile(\\\"a.png\\\", \\\"iVBOR\\\")\""));
    }

    @Test
    void blankCodeIsNotRewritten() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("code", "  ");
        assertEquals("  ", WebViewExecHost.rewriteArgs(args).path("code").asText());
    }

    @Test
    void materializeDecodesImageBase64IntoSandbox(@TempDir Path dir) throws Exception {
        WorkspaceSandbox sandbox = new WorkspaceSandbox(dir);
        String envelope = envelope("maps/route.png", "data:image/png;base64," + TINY_PNG_B64);
        String raw = receiptPreview(envelope);
        String out = WebViewExecHost.materialize(sandbox, raw);
        var parsed = MAPPER.readTree(out);
        assertTrue(parsed.path("ok").asBoolean());
        assertEquals("maps/route.png", parsed.path("outputPath").asText());
        assertEquals("已写入 maps/route.png", parsed.path("resultPreview").asText());
        assertFalse(out.contains(TINY_PNG_B64));
        byte[] png = Files.readAllBytes(dir.resolve("maps/route.png"));
        assertEquals("PNG", new String(png, 1, 3, StandardCharsets.US_ASCII));
        assertEquals(Base64.getDecoder().decode(TINY_PNG_B64).length, png.length);
    }

    @Test
    void materializeReadsSpilledEnvelopeAndDeletesSpill(@TempDir Path dir) throws Exception {
        WorkspaceSandbox sandbox = new WorkspaceSandbox(dir);
        String rel = "tmp/webview_exec/wv-1.b64";
        Path spill = dir.resolve(rel);
        Files.createDirectories(spill.getParent());
        Files.writeString(spill, envelope("route.png", TINY_PNG_B64));
        String raw = """
            {"ok":true,"outputPath":"tmp/webview_exec/wv-1.b64","resultPreview":"完整结果已写入 outputPath","elapsedMs":12}
            """;
        String out = WebViewExecHost.materialize(sandbox, raw);
        var parsed = MAPPER.readTree(out);
        assertEquals("route.png", parsed.path("outputPath").asText());
        assertEquals(12, parsed.path("elapsedMs").asInt());
        assertFalse(Files.exists(spill));
        assertTrue(Files.isRegularFile(dir.resolve("route.png")));
    }

    @Test
    void svgTextIsWrittenAsUtf8(@TempDir Path dir) throws Exception {
        WorkspaceSandbox sandbox = new WorkspaceSandbox(dir);
        String svg = "<svg xmlns=\\\"http://www.w3.org/2000/svg\\\"></svg>";
        String out = WebViewExecHost.materialize(sandbox, receiptPreview(envelope("map.svg", svg)));
        assertTrue(MAPPER.readTree(out).path("ok").asBoolean());
        assertTrue(Files.readString(dir.resolve("map.svg")).startsWith("<svg"));
    }

    @Test
    void pathEscapeDoesNotWrite(@TempDir Path dir) throws Exception {
        WorkspaceSandbox sandbox = new WorkspaceSandbox(dir);
        String out = WebViewExecHost.materialize(sandbox, receiptPreview(envelope("../x.png", TINY_PNG_B64)));
        var parsed = MAPPER.readTree(out);
        assertFalse(parsed.path("ok").asBoolean());
        assertTrue(parsed.path("error").asText().contains("writeFile"));
        assertEquals(0, Files.list(dir).count());
    }

    @Test
    void receiptWithoutEnvelopeStaysUntouched() {
        String raw = "{\"ok\":true,\"resultPreview\":\"hello\",\"resultType\":\"string\"}";
        assertEquals(raw, WebViewExecHost.materialize(new WorkspaceSandbox(workspace), raw));
    }

    private static String envelope(String path, String data) throws Exception {
        ObjectNode env = MAPPER.createObjectNode();
        var writes = env.putArray(WebViewExecHost.ENVELOPE_KEY);
        ObjectNode one = writes.addObject();
        one.put("path", path);
        one.put("data", data);
        return MAPPER.writeValueAsString(env);
    }

    private static String receiptPreview(String envelope) throws Exception {
        ObjectNode receipt = MAPPER.createObjectNode();
        receipt.put("ok", true);
        receipt.put("resultType", "string");
        receipt.put("resultPreview", envelope);
        return MAPPER.writeValueAsString(receipt);
    }
}
