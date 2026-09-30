package com.agent1.javaagent.coach;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从 execute_script 失败与源码中识别 ensureNative 缺插件场景（5.8 catalog.missing_native）。 */
public final class CatalogMissingNativeHints {

    private static final Pattern ENSURE_NATIVE_CODE =
        Pattern.compile("ensureNative\\(\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern NOT_IN_CATALOG =
        Pattern.compile("unsupported:\\s*native\\s*\"([^\"]+)\"\\s*\\(not in catalog\\)", Pattern.CASE_INSENSITIVE);

    private CatalogMissingNativeHints() {
    }

    public static boolean looksLikeMissingNative(String toolResultText) {
        if (toolResultText == null || toolResultText.isBlank()) {
            return false;
        }
        String lower = toolResultText.toLowerCase();
        return lower.contains("not in catalog")
            || lower.contains("plugin dir not set")
            || lower.contains("ensurenative needs host native");
    }

    public static String resolvePluginName(String inlineCode, String toolResultText) {
        if (toolResultText != null) {
            Matcher fromMsg = NOT_IN_CATALOG.matcher(toolResultText);
            if (fromMsg.find()) {
                return fromMsg.group(1).trim();
            }
        }
        if (inlineCode != null && !inlineCode.isBlank()) {
            Matcher fromCode = ENSURE_NATIVE_CODE.matcher(inlineCode);
            if (fromCode.find()) {
                return fromCode.group(1).trim();
            }
        }
        return "";
    }

    public static String adviceFor(String pluginName) {
        String idHint = pluginName.isBlank() ? "对应 catalog id" : "catalog 中 native." + pluginName + ".* 或 capabilities 里的 id";
        return "脚本需要 native 插件"
            + (pluginName.isBlank() ? "" : " \"" + pluginName + "\"")
            + "，本地尚未安装或自动安装失败。生产力路径会在 ensureNative 失败时尝试 catalog sync；"
            + "若仍失败请 read_file docs/system/catalog-install.md、查 docs/capabilities/，"
            + "用 catalog_install 安装 " + idHint + "。"
            + "脚本内使用 await host.ensureNative(\""
            + (pluginName.isBlank() ? "插件名" : pluginName)
            + "\")。";
    }
}
