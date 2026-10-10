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
 * 在 weizhi {@code webview_exec} 外包一层：换成一份模型可见说明，
 * 并注入 {@code writeFile(path, data)}，把 Base64 图片解码后写入工作区沙箱。
 */
public final class WebViewExecHost {

    static final String MARKER = "【webview_exec】";
    static final String ENVELOPE_KEY = "__webviewWrites";

    private static final String WEB_RUNTIME =
        "code 跑在标准 Web 环境，只有浏览器 Web API（document、window、fetch 等）。"
            + "不是 Node、不是 QuickJS，没有 fs/require。"
            + "也可从 run_js 里 await $tools.webview_exec({...}) 调用，与本工具相同。"
            + "读工作区文件用 input_path（全局 input 是 Uint8Array）；写回用 writeFile(相对路径, 数据)。\n"
            + "code 在函数中执行，顶层 return 才是结果；异步写成 return (async () => { ... })()。"
            + "图片传纯 Base64 或 data URL（解码为字节），文本按 UTF-8 写入。\n"
            + "短文本在 resultPreview。直接 return 的图片或超过 64KB 的结果写入 tmp/webview_exec/<id>.b64"
            + "（output_path 可改），文件是返回值原文，回执只给路径和字节数。"
            + "返回 null 或 undefined 不写文件。timeout_ms 默认 60000，上限 600000。";

    /** 模型看到的工具说明。上游原文里叠过的失败案例不再拼接。 */
    public static final String DESCRIPTION =
        MARKER + " 无头浏览器（本机 Chromium）。" + WEB_RUNTIME;

    /** Android 侧：同一套协议，底层是系统 WebView。 */
    public static final String DESCRIPTION_ANDROID =
        MARKER + " Android 系统 WebView 的包装。" + WEB_RUNTIME;

    public static final String CODE_PARAM_DESCRIPTION =
        "在函数中执行。顶层 return 返回值；异步写成 return (async () => { ... })()。"
            + "标准 Web API，没有 Node 的 fs/require。写工作区文件用 writeFile(相对路径, 数据)，放在该 Promise 内。";

    public static final String OUTPUT_PATH_DESCRIPTION =
        "可选。覆盖默认路径 tmp/webview_exec/<id>.b64，内容是返回值的 UTF-8 文本。"
            + "不传时，图片或超过 64KB 的结果仍会自动落盘。";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_WRITE_BYTES = 20 * 1024 * 1024;
    /** Base64 比解码后大约 4/3，读信封时放宽。 */
    private static final int MAX_ENVELOPE_BYTES = 28 * 1024 * 1024;

    private WebViewExecHost() {
    }

    public static String augmentDescription(String description) {
        return augmentDescription(description, false);
    }

    public static String augmentDescription(String description, boolean androidHost) {
        String target = androidHost ? DESCRIPTION_ANDROID : DESCRIPTION;
        if (description != null && description.equals(target)) {
            return description;
        }
        return target;
    }

    public static JsonNode augmentParameters(JsonNode parameters) {
        if (parameters == null || !parameters.isObject()) {
            return parameters;
        }
        ObjectNode copy = parameters.deepCopy();
        JsonNode properties = copy.get("properties");
        if (properties != null && properties.isObject()) {
            if (properties.get("code") instanceof ObjectNode code) {
                code.put("description", CODE_PARAM_DESCRIPTION);
            }
            if (properties.get("output_path") instanceof ObjectNode outputPath) {
                outputPath.put("description", OUTPUT_PATH_DESCRIPTION);
            }
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
        JsonNode preview = receipt.get("resultPreview");
        if (preview != null && preview.isObject() && preview.has(ENVELOPE_KEY)) {
            return preview.toString();
        }
        String previewText = preview == null || preview.isNull() ? "" : preview.asText("");
        if (previewText.contains(ENVELOPE_KEY)) {
            return previewText;
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
