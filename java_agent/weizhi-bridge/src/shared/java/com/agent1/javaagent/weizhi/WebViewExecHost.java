package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.util.PathIo;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 在 weizhi {@code webview_exec} 外包一层：工具说明在调用前就要求顶层 return，
 * 并注入 {@code writeFile(path, data)}，把 Base64 图片解码后写入工作区沙箱。
 */
public final class WebViewExecHost {

    static final String MARKER = "【webview_exec】";
    static final String ENVELOPE_KEY = "__webviewWrites";

    static final String CODE_PARAM_DESCRIPTION =
        "在函数体里执行。要让宿主拿到结果，必须写顶层 return；异步写成 "
            + "return (async () => { ... })()。只写 (async () => {})()，或把 return 放在 img.onload 里，"
            + "完成值是 undefined，文件不会写入。"
            + "也可以不 return 图片：writeFile('相对路径.png', canvas.toDataURL('image/png').split(',')[1])。"
            + "writeFile 把纯 Base64（或 data URL）解码成图片字节，写入工作区沙箱。文本（如 SVG 原文）按 UTF-8 写入。"
            + "writeFile 要放在脚本等待的 Promise 里面，等它完成后再结束。";

    private static final String DESCRIPTION_PREFIX =
        MARKER + " code 在函数里执行，必须顶层 return；异步用 return (async () => { ... })()。"
            + "画图可以不 return，调用 writeFile(相对路径, 纯Base64) 把图片字节写入工作区沙箱。"
            + "只写 (async () => {})() 会得到 null，不会落盘。\n";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_WRITE_BYTES = 20 * 1024 * 1024;
    /** Base64 比解码后大约 4/3，读信封时放宽。 */
    private static final int MAX_ENVELOPE_BYTES = 28 * 1024 * 1024;

    private WebViewExecHost() {
    }

    public static String augmentDescription(String description) {
        String body = description == null ? "" : description;
        if (body.startsWith(MARKER)) {
            return body;
        }
        return DESCRIPTION_PREFIX + body;
    }

    public static JsonNode augmentParameters(JsonNode parameters) {
        if (parameters == null || !parameters.isObject()) {
            return parameters;
        }
        ObjectNode copy = parameters.deepCopy();
        JsonNode properties = copy.get("properties");
        if (properties != null && properties.isObject() && properties.get("code") instanceof ObjectNode code) {
            code.put("description", CODE_PARAM_DESCRIPTION);
        }
        return copy;
    }

    public static JsonNode rewriteArgs(JsonNode params) {
        if (params == null || !params.isObject()) {
            return params;
        }
        ObjectNode copy = params.deepCopy();
        String code = copy.path("code").asText("");
        if (code.isBlank()) {
            return copy;
        }
        copy.put("code", rewriteCode(code));
        return copy;
    }

    static String rewriteCode(String code) {
        String literal = MAPPER.valueToTree(code).toString();
        return """
            return (async () => {
              var __writes = [];
              function writeFile(path, data) {
                if (path == null || String(path).trim() === '') {
                  throw new Error('writeFile: path required');
                }
                __writes.push({ path: String(path), data: data == null ? '' : String(data) });
              }
              globalThis.writeFile = writeFile;
              var __code = %s;
              var value;
              try {
                value = await eval(__code);
              } catch (e) {
                if (e instanceof SyntaxError) {
                  value = await (new Function('input', 'loadWasm', 'writeFile', '"use strict";\\n' + __code))(input, loadWasm, writeFile);
                } else {
                  throw e;
                }
              }
              if (__writes.length) {
                var __kept = value;
                if (typeof __kept === 'string' && __kept.length > 500) {
                  __kept = null;
                }
                return JSON.stringify({ __webviewWrites: __writes, value: __kept === undefined ? null : __kept });
              }
              return value;
            })();
            """.formatted(literal);
    }

    /**
     * 若回执里带有 writeFile 信封，解码并写入沙箱，再换成不含 Base64 的回执。
     * 没有信封时原样返回。
     */
    public static String materialize(WorkspaceSandbox sandbox, String raw) {
        if (sandbox == null || raw == null || !raw.trim().startsWith("{")) {
            return raw;
        }
        JsonNode receipt;
        try {
            receipt = MAPPER.readTree(raw);
        } catch (IOException e) {
            return raw;
        }
        if (!receipt.path("ok").asBoolean(false) || !receipt.isObject()) {
            return raw;
        }
        String envelopeText = extractEnvelope(sandbox, receipt);
        if (envelopeText == null) {
            return raw;
        }
        JsonNode envelope;
        try {
            envelope = MAPPER.readTree(envelopeText);
        } catch (IOException e) {
            return raw;
        }
        JsonNode writes = envelope.get(ENVELOPE_KEY);
        if (writes == null || !writes.isArray() || writes.isEmpty()) {
            return raw;
        }
        try {
            List<WrittenFile> files = writeAll(sandbox, writes);
            deleteEnvelopeSpill(sandbox, receipt.path("outputPath").asText(""), files);
            return successReceipt((ObjectNode) receipt, files);
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            return errorReceipt(message);
        }
    }

    private static List<WrittenFile> writeAll(WorkspaceSandbox sandbox, JsonNode writes) throws IOException {
        List<WrittenFile> files = new ArrayList<>();
        for (JsonNode write : writes) {
            String rel = write.path("path").asText("").trim();
            if (rel.isEmpty()) {
                throw new IllegalArgumentException("writeFile: path required");
            }
            byte[] bytes = payloadBytes(rel, write.path("data").asText(""));
            if (bytes.length > MAX_WRITE_BYTES) {
                throw new IllegalArgumentException("writeFile 超过 20MB: " + rel);
            }
            Path path = sandbox.resolveWrite(rel);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.write(path, bytes);
            files.add(new WrittenFile(rel.replace('\\', '/'), bytes.length));
        }
        return files;
    }

    static byte[] payloadBytes(String path, String data) {
        String trimmed = data == null ? "" : data.trim();
        String base64 = trimmed;
        int marker = trimmed.indexOf("base64,");
        if (trimmed.startsWith("data:") && marker >= 0) {
            base64 = trimmed.substring(marker + "base64,".length()).trim();
        }
        if (isImageBase64(base64) || (isSvgPath(path) && isProbablyBase64(base64))) {
            try {
                byte[] decoded = Base64.getMimeDecoder().decode(base64);
                if (isImageBase64(base64) || looksLikeSvg(decoded)) {
                    return decoded;
                }
            } catch (IllegalArgumentException e) {
                if (isImageBase64(base64)) {
                    throw new IllegalArgumentException("writeFile Base64 无法解码: " + path);
                }
            }
        }
        return trimmed.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isSvgPath(String path) {
        return path.toLowerCase().endsWith(".svg");
    }

    private static boolean looksLikeSvg(byte[] decoded) {
        String head = new String(decoded, 0, Math.min(decoded.length, 64), StandardCharsets.UTF_8).trim();
        return head.startsWith("<svg") || head.startsWith("<?xml");
    }

    private static boolean isProbablyBase64(String text) {
        if (text.length() < 16 || text.indexOf('<') >= 0) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '+' || c == '/' || c == '=' || c == '-' || c == '_' || Character.isWhitespace(c);
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    static boolean isImageBase64(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.startsWith("iVBORw0KGgo")
            || trimmed.startsWith("/9j/")
            || trimmed.startsWith("R0lGOD")
            || trimmed.startsWith("UklGR");
    }

    private static String extractEnvelope(WorkspaceSandbox sandbox, JsonNode receipt) {
        String preview = receipt.path("resultPreview").asText("");
        if (preview.contains(ENVELOPE_KEY)) {
            return preview;
        }
        String outputPath = receipt.path("outputPath").asText("").trim();
        if (outputPath.isEmpty()) {
            return null;
        }
        try {
            Path file = sandbox.resolveRead(outputPath);
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_ENVELOPE_BYTES) {
                return null;
            }
            String text = PathIo.readString(file);
            if (text.contains(ENVELOPE_KEY)) {
                return text;
            }
        } catch (IOException | SecurityException e) {
            return null;
        }
        return null;
    }

    private static void deleteEnvelopeSpill(WorkspaceSandbox sandbox, String outputPath, List<WrittenFile> files)
        throws IOException {
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        String normalized = outputPath.replace('\\', '/');
        for (WrittenFile file : files) {
            if (file.path.equals(normalized)) {
                return;
            }
        }
        Path spill = sandbox.resolveWrite(normalized);
        if (!Files.isRegularFile(spill)) {
            return;
        }
        String head = PathIo.readString(spill);
        if (head.contains(ENVELOPE_KEY)) {
            Files.deleteIfExists(spill);
        }
    }

    private static String successReceipt(ObjectNode original, List<WrittenFile> files) throws IOException {
        ObjectNode receipt = MAPPER.createObjectNode();
        receipt.put("ok", true);
        if (original.has("elapsedMs")) {
            receipt.put("elapsedMs", original.get("elapsedMs").asLong());
        }
        ArrayNode listed = receipt.putArray("files");
        for (WrittenFile file : files) {
            ObjectNode item = listed.addObject();
            item.put("path", file.path);
            item.put("bytes", file.bytes);
        }
        WrittenFile first = files.get(0);
        receipt.put("outputPath", first.path);
        receipt.put("outputBytes", first.bytes);
        receipt.put("resultType", "file");
        receipt.put("resultPreview", preview(files));
        if (original.has("console")) {
            receipt.set("console", original.get("console"));
        }
        return MAPPER.writeValueAsString(receipt);
    }

    private static String preview(List<WrittenFile> files) {
        StringBuilder text = new StringBuilder("已写入");
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) {
                text.append('、');
            }
            text.append(' ').append(files.get(i).path);
        }
        return text.toString();
    }

    private static String errorReceipt(String message) {
        ObjectNode receipt = MAPPER.createObjectNode();
        receipt.put("ok", false);
        receipt.put("error", "writeFile 失败: " + message);
        try {
            return MAPPER.writeValueAsString(receipt);
        } catch (IOException e) {
            return "{\"ok\":false,\"error\":\"writeFile 失败\"}";
        }
    }

    private record WrittenFile(String path, int bytes) {
    }
}
