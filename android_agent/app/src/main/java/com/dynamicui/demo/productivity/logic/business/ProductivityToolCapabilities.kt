package com.dynamicui.demo.productivity.logic.business

import com.dynamicui.demo.BuildConfig

/** 当前 APK 装配的 Agent 工具能力（与 {@link ProductivityHostAssembly} / Weizhi 子工程一致）。 */
object ProductivityToolCapabilities {

    val weizhiIntegrated: Boolean
        get() = BuildConfig.WEIZHI_INTEGRATED

    /** 设置页 / 聊天详情里展示的一行摘要。 */
    fun summaryForUi(): String = if (weizhiIntegrated) {
        "工作区读写 + chat_history + execute_script（Weizhi）；扩展：grep / glob / zip / bash / load_skill / WebView / MCP"
    } else {
        "工作区：read_file · write_file · edit_file · list_dir · chat_history（本包未编入 Weizhi，无脚本 / WebView / MCP）"
    }

    fun detailBullets(): List<String> = buildList {
        add("read_file · write_file · edit_file · list_dir · chat_history")
        if (weizhiIntegrated) {
            add("execute_script（Weizhi 脚本，\$tools 桥接）")
            add("grep · glob · zip · bash · load_skill")
            add("WebView 工具 · MCP 扩展")
        } else {
            add("未检测到 weizhi 源码或 weizhi-prebuilt/maven — WEIZHI_INTEGRATED=false")
        }
    }
}
