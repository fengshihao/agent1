package com.agent1.javaagent.weizhi.desktop;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;

/**
 * 桌面 CDP 回执解析，语义对齐 weizhi {@code com.weizhi.agent.web.WebViewResult}
 * （该类为包内可见，JVM 侧不能直接链接）。
 *
 * <p>JSON {@code null}（脚本 {@code return null}，或没有 return、async 函数 resolve 成
 * {@code undefined} 后由 bridge 收成 null）没有可落盘的字节。字符串 {@code "null"} 是
 * {@code string}，两者靠 {@link #resultType} 区分。
 */
final class WebViewSpillResult {

    private static final Gson GSON = new Gson();

    /** {@code null|string|number|boolean|object|array}；解析失败时为 null。 */
    final String resultType;
    /** 预览原文。JSON null 的预览是文本 {@code null}，与字符串 {@code "null"} 的预览相同。 */
    final String text;
    /** 写入 output_path 的 UTF-8；JSON null 时为 null，调用方不得落盘。 */
    final byte[] spillUtf8;
    final boolean unserializable;
    /** 非 null 表示 payload 无法解析。 */
    final String parseError;

    private WebViewSpillResult(
        String resultType,
        String text,
        byte[] spillUtf8,
        boolean unserializable,
        String parseError
    ) {
        this.resultType = resultType;
        this.text = text;
        this.spillUtf8 = spillUtf8;
        this.unserializable = unserializable;
        this.parseError = parseError;
    }

    static WebViewSpillResult parse(String payloadJson) {
        try {
            JsonElement root = JsonParser.parseString(payloadJson);
            if (root == null || !root.isJsonObject()) {
                return error("payload 不是 JSON 对象");
            }
            JsonObject payload = root.getAsJsonObject();
            boolean unserializable = flag(payload, "unserializable");
            if (!payload.has("result") || payload.get("result").isJsonNull()) {
                return new WebViewSpillResult("null", "null", null, unserializable, null);
            }
            JsonElement result = payload.get("result");
            if (result.isJsonPrimitive()) {
                JsonPrimitive primitive = result.getAsJsonPrimitive();
                if (primitive.isString()) {
                    String value = primitive.getAsString();
                    return new WebViewSpillResult("string", value, utf8(value), unserializable, null);
                }
                if (primitive.isBoolean()) {
                    return typed("boolean", GSON.toJson(result), unserializable);
                }
                if (primitive.isNumber()) {
                    return typed("number", GSON.toJson(result), unserializable);
                }
            }
            if (result.isJsonArray()) {
                return typed("array", GSON.toJson(result), unserializable);
            }
            if (result.isJsonObject()) {
                return typed("object", GSON.toJson(result), unserializable);
            }
            return error("无法识别的结果类型");
        } catch (RuntimeException e) {
            return error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static WebViewSpillResult typed(String type, String jsonText, boolean unserializable) {
        return new WebViewSpillResult(type, jsonText, utf8(jsonText), unserializable, null);
    }

    private static WebViewSpillResult error(String message) {
        return new WebViewSpillResult(null, "", null, false, message);
    }

    private static boolean flag(JsonObject payload, String name) {
        if (!payload.has(name) || !payload.get(name).isJsonPrimitive()) {
            return false;
        }
        JsonPrimitive primitive = payload.get(name).getAsJsonPrimitive();
        return primitive.isBoolean() && primitive.getAsBoolean();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
