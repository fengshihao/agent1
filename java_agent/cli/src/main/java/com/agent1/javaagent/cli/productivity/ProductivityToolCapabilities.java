package com.agent1.javaagent.cli.productivity;

import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
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
            return "工作区：read_file · write_file · edit_file · list_dir · chat_history · read_url · web_search"
                + "（未编入 Weizhi，无脚本 / MCP）";
        }
        String webview = desktopWebViewLabel();
        if (weizhiScriptEnabled) {
            return "工作区读写 + chat_history + read_url + web_search + execute_script（Weizhi）；扩展：grep / glob / zip / bash / "
                + "load_skill / MCP / " + webview;
        }
        return "工作区读写 + chat_history + read_url + web_search；扩展：grep / glob / zip / bash / load_skill / MCP / "
            + webview + "（Weizhi 脚本未启用）";
    }

    public static List<String> detailBullets(boolean weizhiScriptEnabled) {
        List<String> lines = new ArrayList<>();
        lines.add("read_file · write_file · edit_file · list_dir · chat_history · read_url · web_search");
        lines.add("web_search：Tavily（环境变量 TAVILY_API_KEY，或 App 模型配置里的 Web Search Key）");
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
        if (CdpWebViewRuntime.isAvailable()) {
            lines.add("webview_exec：Headless Chromium + CDP（与 Android bridge.js 同协议）");
        } else {
            lines.add("webview_exec：未启用（安装 chromium/google-chrome 或 AGENT1_CHROMIUM_PATH）");
        }
        return lines;
    }

    private static String desktopWebViewLabel() {
        return CdpWebViewRuntime.isAvailable() ? "webview_exec（CDP）" : "webview_exec（未检测到 Chromium）";
    }
}
