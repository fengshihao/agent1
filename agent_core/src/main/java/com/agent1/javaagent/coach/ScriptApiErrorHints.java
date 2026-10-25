package com.agent1.javaagent.coach;

import com.agent1.javaagent.script.ScriptFailureFormatter;

/** run_js 调用 Package / 宿主 API 失败时，提示用 find_caps 核对签名（非语法类错误）。 */
public final class ScriptApiErrorHints {

    private ScriptApiErrorHints() {
    }

    public static boolean looksLikeApiOrPackageError(String toolResultText) {
        if (toolResultText == null || toolResultText.isBlank()) {
            return false;
        }
        String lower = toolResultText.toLowerCase();
        if (lower.contains("unexpected token")
            || lower.contains("syntaxerror")
            || lower.contains("referenceerror")
            || lower.contains("return not in a function")
            || lower.contains("require is not defined")
            || lower.contains("工具执行超时")
            || lower.contains("cancelled")
            || lower.contains("cancel")) {
            return false;
        }
        if (CatalogMissingNativeHints.looksLikeMissingNative(toolResultText)) {
            return false;
        }
        if (ScriptFailureFormatter.looksLikeFailureJson(toolResultText)) {
            return lower.contains("bad argument")
                || lower.contains("unsupported:")
                || lower.contains("buildjob")
                || lower.contains("weizhi:")
                || lower.contains("ensurenative");
        }
        return lower.contains("bad argument:")
            || lower.contains("unsupported:")
            || lower.contains("buildjob:");
    }

    public static String advice() {
        return "脚本里调用的 API 报错时，先用 find_caps 检索相关能力（query 写清包名或任务关键词），"
            + "按返回条目里的调用示例改脚本，不要凭记忆猜字段或参数名。";
    }
}
