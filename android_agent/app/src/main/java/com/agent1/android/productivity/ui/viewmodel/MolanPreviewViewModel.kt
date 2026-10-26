package com.agent1.android.productivity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.agent1.android.productivity.logic.business.SessionWorkspacePaths
import com.agent1.android.productivity.logic.business.WorkspaceFileActions
import java.io.File

/** 内置墨览 Markdown 预览的解析结果。 */
data class MolanPreviewState(
    val file: File? = null,
    val error: String? = null,
)

/**
 * 解析会话 workspace 内可用墨览预览的文件（.md / .markdown）。
 * 只做路径校验与存在性检查，不触 UI；WebView 渲染在 ui.view。
 */
class MolanPreviewViewModel(
    appContext: Context,
    sessionId: String,
    relativePath: String,
) : ViewModel() {

    val state: MolanPreviewState = resolve(
        workspaceRoot = SessionWorkspacePaths.workspaceRoot(appContext, sessionId),
        sessionId = sessionId,
        relativePath = relativePath,
    )

    companion object {
        /** 纯函数，便于 JVM 单测覆盖错误分支。 */
        fun resolve(workspaceRoot: java.nio.file.Path?, sessionId: String, relativePath: String): MolanPreviewState {
            if (sessionId.isBlank() || workspaceRoot == null) {
                return MolanPreviewState(error = "会话不存在，无法预览")
            }
            if (!WorkspaceFileActions.isMdPreviewableInApp(relativePath)) {
                return MolanPreviewState(error = "不支持的预览文件类型：$relativePath")
            }
            val file = SessionWorkspacePaths.resolveFile(workspaceRoot, relativePath)
                ?: return MolanPreviewState(error = "文件不存在或已被移动：$relativePath")
            return MolanPreviewState(file = file)
        }
    }
}
