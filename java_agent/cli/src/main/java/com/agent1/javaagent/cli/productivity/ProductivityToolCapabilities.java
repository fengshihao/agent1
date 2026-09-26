package com.agent1.javaagent.cli.productivity;

import java.util.ArrayList;
import java.util.List;

/** 当前 CLI 装配的 Agent 工具能力（与 Android {@code ProductivityToolCapabilities} 文案对齐）。 */
public final class ProductivityToolCapabilities {

    private ProductivityToolCapabilities() {
    }

    public static boolean weizhiToolsOnClasspath() {
        try {
            Class.forName("com.weizhi.agent.tool.builtin.GrepTool");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static String summaryForCli(boolean weizhiScriptEnabled) {
        if (!weizhiToolsOnClasspath()) {
            return "工作区：read_file · write_file · edit_file · list_dir · chat_history（未编入 Weizhi，无脚本 / MCP）";
        }
        if (weizhiScriptEnabled) {
            return "工作区读写 + chat_history + execute_script（Weizhi）；扩展：grep / glob / zip / bash / load_skill / MCP（桌面无 WebView）";
        }
        return "工作区读写 + chat_history；扩展：grep / glob / zip / bash / load_skill / MCP（Weizhi 脚本未启用）";
    }

    public static List<String> detailBullets(boolean weizhiScriptEnabled) {
        List<String> lines = new ArrayList<>();
        lines.add("read_file · write_file · edit_file · list_dir · chat_history");
        if (!weizhiToolsOnClasspath()) {
            lines.add("未检测到 weizhi 源码 — 构建需 ./weizhi 或 AGENT1_WEIZHI_REPO");
            return lines;
        }
        if (weizhiScriptEnabled) {
            lines.add("execute_script（Weizhi 脚本，$tools 桥接）");
        } else {
            lines.add("execute_script：未启用（构建 ../weizhi native 或设置 AGENT1_WEIZHI_REPO）");
        }
        lines.add("grep · glob · zip · bash · load_skill");
        lines.add("MCP：mcp_call_tool / mcp_list_servers（配置见 .agent1/mcp_servers.json）");
        lines.add("WebView：桌面 CLI 不提供（仅 Android APK）");
        return lines;
    }
}
