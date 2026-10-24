package com.agent1.android.productivity.logic.business.platform

import android.content.Context
import android.os.Build
import com.agent1.javaagent.prompt.HostEnvironmentProvider

/**
 * Android 宿主的「当前环境状态」段信息来源（CLI 用 core 里的桌面默认实现）。
 * 只报影响模型决策的信息：平台（Build.VERSION）、JS 运行环境、目录权限。
 */
class AndroidHostEnvironmentProvider(
    appContext: Context,
) : HostEnvironmentProvider {

    override fun platformLine(): String = "Android ${(Build.VERSION.RELEASE ?: "").trim()}"

    override fun jsRuntimeLine(): String = "QuickJS 扩展，非 Node；不能 npm 或 require"

    override fun directoryLines(): List<String> =
        listOf(
            "workspace/：本会话工作区（唯一可写）；外层文件工具用相对工作区根的路径（不要 workspace/ 前缀）",
            "workspace/imports/：用户通过「选择文件」添加的附件（read_file 相对路径）",
            "shared/：公共能力库（只读），不能用 write_file 修改",
        )
}
