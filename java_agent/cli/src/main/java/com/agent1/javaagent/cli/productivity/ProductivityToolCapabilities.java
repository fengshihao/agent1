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
            Class.forName("com.weizhi.WeizhiEngine");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            // LinkageError：类在 classpath 但 native 库缺失（UnsatisfiedLinkError /
            // clinit 失败后的 NoClassDefFoundError）。视为不可用，走「未编入 Weizhi」降级文案。
            return false;
        }
    }

    public static String summaryForCli(boolean weizhiScriptEnabled) {
        if (!weizhiToolsOnClasspath()) {
            return "工作区：read_file · write_file · edit_file · list_dir · chat_history · read_url · web_search · todo_write"
                + "（未编入 Weizhi，无脚本 / MCP）";
        }
        String webview = desktopWebViewLabel();
        if (weizhiScriptEnabled) {
            return "工作区读写 + chat_history + read_url + web_search + run_js（Weizhi）；扩展：grep / glob / zip / bash / "
                + "load_skill / MCP / " + webview;
        }
        return "工作区读写 + chat_history + read_url + web_search；扩展：grep / glob / zip / bash / load_skill / MCP / "
            + webview + "（Weizhi 脚本未启用）";
    }

    public static List<String> detailBullets(boolean weizhiScriptEnabled) {
        List<String> lines = new ArrayList<>();
        lines.add("read_file · write_file · edit_file · list_dir · chat_history · read_url · web_search · todo_write");
        lines.add("todo_write：会话短清单，跨轮记住计划；空数组清空");
        lines.add("web_search：Tavily（环境变量 TAVILY_API_KEY，或 App 模型配置里的 Web Search Key）");
        if (!weizhiToolsOnClasspath()) {
            lines.add("未检测到 weizhi 源码 — 构建需 ./weizhi 或 AGENT1_WEIZHI_REPO");
            return lines;
        }
        if (weizhiScriptEnabled) {
            lines.add("run_js（Weizhi 脚本，$tools 桥接）");
        } else {
            lines.add("run_js：未启用（构建 ../weizhi native 或设置 AGENT1_WEIZHI_REPO）");
        }
        lines.add("grep · glob · zip · bash · load_skill");
        lines.add("MCP：脚本 $mcp.<server>.<tool>，经 weizhi mcp.connect（配置见 agentRoot/mcp_servers.json）");
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
