package com.agent1.android.productivity.logic.business

import com.agent1.android.BuildConfig

/** 当前 APK 装配的 Agent 工具能力（与 {@link ProductivityHostAssembly} / Weizhi 子工程一致）。 */
object ProductivityToolCapabilities {

    val weizhiIntegrated: Boolean
        get() = BuildConfig.WEIZHI_INTEGRATED

    /** 设置页 / 聊天详情里展示的一行摘要。 */
    fun summaryForUi(): String = if (weizhiIntegrated) {
        "工作区读写 + chat_history + read_url + web_search + run_js（Weizhi）；扩展：grep / glob / zip / bash / WebView / MCP"
    } else {
        "工作区：read_file · write_file · edit_file · list_dir · chat_history · read_url · web_search · todo_write（本包未编入 Weizhi，无脚本 / WebView / MCP）"
    }

    fun detailBullets(): List<String> = buildList {
        add("read_file · write_file · edit_file · list_dir · chat_history · read_url · web_search · todo_write · todo_read")
        add("媒体：weizhi:image / weizhi:video（probe/抽帧/抽音轨/剪辑，AAR）/ weizhi:audio")
        add("todo_write：会话短清单，整表替换；todo_read：按需读清单。清单不进系统提示；空数组清空")
        add("web_search：Tavily（在模型配置中填写 Web Search Key 后生效）")
        if (weizhiIntegrated) {
            add("run_js（Weizhi 脚本，\$tools 桥接）；文档等功能写在包里，例如 import from \"weizhi:docx\"")
            add("android.calendar / location / contacts；android.reminders（设备能力闸）；android.files.pickDirectory（SAF）")
            add("grep · glob · zip · bash · load_skill")
            add("WebView 工具 · MCP（侧栏配置 HTTP，脚本里 \$mcp.名称.工具）")
        } else {
            add("未检测到 weizhi 源码或 weizhi-prebuilt/maven — WEIZHI_INTEGRATED=false")
        }
    }
}
