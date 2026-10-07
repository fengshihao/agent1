package com.agent1.android.productivity.logic.business.platform

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.agent1.javaagent.prompt.HostEnvironmentProvider

/**
 * Android 宿主的「当前环境状态」段信息来源（CLI 用 core 里的桌面默认实现）。
 * 平台 / 内核来自 Build.VERSION 与 os.version；内存来自 ActivityManager.MemoryInfo。
 */
class AndroidHostEnvironmentProvider(
    private val appContext: Context,
) : HostEnvironmentProvider {

    override fun platformLine(): String {
        val release = Build.VERSION.RELEASE ?: ""
        val kernel = System.getProperty("os.version") ?: ""
        val platform = "Android ${release.trim()}"
        return if (kernel.isBlank()) platform else "$platform 内核 ${kernel.trim()}"
    }

    override fun jsRuntimeLine(): String = "QuickJS 扩展，非 Node；不能 npm 或 require"

    override fun memoryLine(): String {
        val info = ActivityManager.MemoryInfo()
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return ""
        manager.getMemoryInfo(info)
        if (info.totalMem <= 0) {
            return ""
        }
        val totalMb = info.totalMem / (1024 * 1024)
        val availMb = info.availMem / (1024 * 1024)
        return "总内存 ${totalMb}MB，可用 ${availMb}MB"
    }

    override fun directoryLines(): List<String> =
        listOf(
            "workspace/：本会话工作区（唯一可写）；外层文件工具用相对工作区根的路径（不要 workspace/ 前缀），脚本内 fs 可用相对或绝对（须在根下）",
            "workspace/imports/：用户通过「选择文件」添加的附件（read_file 相对路径）",
            "shared/：公共能力库（只读），不能用 write_file 修改",
        )
}